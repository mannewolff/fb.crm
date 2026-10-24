import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';

import type { Einrichtung, Konto } from '../api/auth';
import { codeAnfordern, login, logout, me, setup } from '../api/auth';
import { ApiError, beiVerboten } from '../api/client';

/**
 * Der Sitzungszustand der Oberflaeche.
 *
 * <b>Kein Token im JS-Speicher.</b> Die Sitzung steckt im HttpOnly-Cookie; was hier liegt,
 * ist nur die Antwort von `GET /api/auth/me` (CLAUDE-security.md). Deshalb gibt es drei
 * Zustaende und nicht zwei: Solange die erste Abfrage laeuft, ist „abgemeldet" noch nicht
 * wahr — wer das zusammenlegt, wirft jeden angemeldeten Besucher beim Neuladen kurz auf
 * die Anmeldeseite.
 *
 * Das Konto im Zustand `angemeldet` traegt die Rolle (Plan #414, A8). Sie wird zu Beginn der
 * Sitzung gelesen und danach nur noch nach einem 403 (E5): Dann haelt die API die Rolle fuer
 * kleiner, als die Oberflaeche sie kennt. Eine wiederkehrende Abfrage gibt es nicht — sie hielte
 * die Sitzung am Leben (`keinHintergrundabruf.test.ts`).
 */
export type Sitzung =
  | { readonly status: 'unbekannt' }
  | { readonly status: 'angemeldet'; readonly konto: Konto }
  | { readonly status: 'abgemeldet' };

export interface AuthWert {
  readonly sitzung: Sitzung;
  /** Fordert einen Code an; die Sitzung bleibt dabei, wie sie ist. */
  readonly codeAnfordern: (email: string) => Promise<void>;
  readonly anmelden: (email: string, code: string) => Promise<void>;
  /** Richtet die Instanz ein; die Einrichtung meldet den Betreiber zugleich an (K3). */
  readonly einrichten: (eingaben: Einrichtung) => Promise<void>;
  readonly abmelden: () => Promise<void>;
  /**
   * Liest das eigene Konto samt Rolle neu, solange eine Sitzung besteht. Antwortet die API mit
   * 401, ist die Sitzung zu Ende — etwa weil das Konto stillgelegt wurde; jeder andere
   * Fehlschlag laesst den Zustand stehen.
   */
  readonly rolleNeuLesen: () => Promise<void>;
}

const AuthContext = createContext<AuthWert | null>(null);

export function AuthProvider({ children }: { readonly children: ReactNode }) {
  const [sitzung, setSitzung] = useState<Sitzung>({ status: 'unbekannt' });
  const angemeldet = useRef(false);
  const liestNach = useRef(false);

  useEffect(() => {
    angemeldet.current = sitzung.status === 'angemeldet';
  }, [sitzung]);

  const rolleNeuLesen = useCallback(async () => {
    // Ohne Sitzung gibt es keine Rolle nachzulesen — ein 403 der Einrichtung ist ein falscher
    // Schluessel. Und laeuft schon ein Nachlesen, genuegt es: Antwortete es selbst mit 403, riefe
    // es sich sonst ohne Ende neu auf.
    if (!angemeldet.current || liestNach.current) {
      return;
    }
    liestNach.current = true;
    try {
      setSitzung({ status: 'angemeldet', konto: await me() });
    } catch (ursache) {
      if (ursache instanceof ApiError && ursache.status === 401) {
        setSitzung({ status: 'abgemeldet' });
      }
    } finally {
      liestNach.current = false;
    }
  }, []);

  useEffect(() => {
    beiVerboten(() => {
      void rolleNeuLesen();
    });
    return () => {
      beiVerboten(null);
    };
  }, [rolleNeuLesen]);

  useEffect(() => {
    let abgebrochen = false;
    const pruefen = async () => {
      try {
        const konto = await me();
        if (!abgebrochen) {
          setSitzung({ status: 'angemeldet', konto });
        }
      } catch {
        // Jeder Fehlschlag heisst dasselbe: keine Sitzung. Ob 401, Netzausfall oder eine
        // Antwort in unerwarteter Form — mehr darf die Oberflaeche daraus nicht ableiten.
        if (!abgebrochen) {
          setSitzung({ status: 'abgemeldet' });
        }
      }
    };
    void pruefen();
    return () => {
      abgebrochen = true;
    };
  }, []);

  const wert = useMemo<AuthWert>(
    () => ({
      sitzung,
      codeAnfordern,
      anmelden: async (email: string, code: string) => {
        setSitzung({ status: 'angemeldet', konto: await login(email, code) });
      },
      einrichten: async (eingaben: Einrichtung) => {
        setSitzung({ status: 'angemeldet', konto: await setup(eingaben) });
      },
      abmelden: async () => {
        await logout();
        setSitzung({ status: 'abgemeldet' });
      },
      rolleNeuLesen,
    }),
    [sitzung, rolleNeuLesen],
  );

  return <AuthContext.Provider value={wert}>{children}</AuthContext.Provider>;
}

/** Zugang zum Sitzungszustand. Ausserhalb des Providers gibt es keinen. */
export function useAuth(): AuthWert {
  const wert = useContext(AuthContext);
  if (wert === null) {
    throw new Error('useAuth wurde ausserhalb von AuthProvider benutzt.');
  }
  return wert;
}
