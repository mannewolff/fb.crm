import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';

import type { Einrichtung, Konto } from '../api/auth';
import { login, logout, me, setup } from '../api/auth';

/**
 * Der Sitzungszustand der Oberflaeche.
 *
 * <b>Kein Token im JS-Speicher.</b> Die Sitzung steckt im HttpOnly-Cookie; was hier liegt,
 * ist nur die Antwort von `GET /api/auth/me` (CLAUDE-security.md). Deshalb gibt es drei
 * Zustaende und nicht zwei: Solange die erste Abfrage laeuft, ist „abgemeldet" noch nicht
 * wahr — wer das zusammenlegt, wirft jeden angemeldeten Besucher beim Neuladen kurz auf
 * die Anmeldeseite.
 */
export type Sitzung =
  | { readonly status: 'unbekannt' }
  | { readonly status: 'angemeldet'; readonly konto: Konto }
  | { readonly status: 'abgemeldet' };

export interface AuthWert {
  readonly sitzung: Sitzung;
  readonly anmelden: (email: string, passwort: string) => Promise<void>;
  /** Richtet die Instanz ein; die Einrichtung meldet den Betreiber zugleich an (K3). */
  readonly einrichten: (eingaben: Einrichtung) => Promise<void>;
  readonly abmelden: () => Promise<void>;
}

const AuthContext = createContext<AuthWert | null>(null);

export function AuthProvider({ children }: { readonly children: ReactNode }) {
  const [sitzung, setSitzung] = useState<Sitzung>({ status: 'unbekannt' });

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
      anmelden: async (email: string, passwort: string) => {
        setSitzung({ status: 'angemeldet', konto: await login(email, passwort) });
      },
      einrichten: async (eingaben: Einrichtung) => {
        setSitzung({ status: 'angemeldet', konto: await setup(eingaben) });
      },
      abmelden: async () => {
        await logout();
        setSitzung({ status: 'abgemeldet' });
      },
    }),
    [sitzung],
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
