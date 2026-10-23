import type { Rolle } from '../api/auth';
import { useAuth } from './AuthContext';

/**
 * Was die Rolle der Sitzung in der Oberflaeche darf — an genau einer Stelle (Plan #414, A10).
 *
 * <b>Die Rechte schuetzen keine Daten.</b> Das tut die Zugangsregel der API (`SecurityConfig`);
 * hier entscheidet sich nur, welche Ansicht und welche Taste die Oberflaeche gar nicht erst zeigt
 * (Geklaerter Stand 7 von #409). Eine Ansicht fragt darum ein Recht ab und nie die Rolle selbst:
 * Wer eine Rolle vergleicht, verteilt die Rechte ueber die Dateien, und die naechste Rolle muesste
 * jede Stelle finden.
 */
export interface Rechte {
  /** Kunden, Angebote, Rechnungen und Arbeitszeit anlegen und aendern (C-Level, Employee). */
  readonly darfFachlichSchreiben: boolean;
  /** Eigene Angaben und Einstellungen zur Rechnung sehen (C-Level, ReadOnly lesend). */
  readonly darfEinstellungenSehen: boolean;
  /** Eigene Angaben und Einstellungen zur Rechnung aendern (nur C-Level). */
  readonly darfEinstellungenAendern: boolean;
  /** Konten anlegen, aendern und stilllegen (nur C-Level). */
  readonly darfKontenVerwalten: boolean;
}

/** Der Name eines Rechts — so nimmt {@link RollenRoute} das verlangte Recht entgegen. */
export type Recht = keyof Rechte;

const KEINE: Rechte = {
  darfFachlichSchreiben: false,
  darfEinstellungenSehen: false,
  darfEinstellungenAendern: false,
  darfKontenVerwalten: false,
};

/**
 * Die Rechte je Rolle (Geklaerter Stand 5 und 6 von #409): C-Level darf alles, Employee alles
 * Fachliche ohne Konten und Einstellungen, ReadOnly liest — die Einstellungen eingeschlossen.
 */
const RECHTE: Readonly<Record<Rolle, Rechte>> = {
  C_LEVEL: {
    darfFachlichSchreiben: true,
    darfEinstellungenSehen: true,
    darfEinstellungenAendern: true,
    darfKontenVerwalten: true,
  },
  EMPLOYEE: { ...KEINE, darfFachlichSchreiben: true },
  READ_ONLY: { ...KEINE, darfEinstellungenSehen: true },
};

/** Die Namen der Rollen, wie die Oberflaeche sie zeigt — woertlich wie im Fachplan (E8). */
const ROLLENNAMEN: Readonly<Record<Rolle, string>> = {
  C_LEVEL: 'C-Level',
  EMPLOYEE: 'Employee',
  READ_ONLY: 'ReadOnly',
};

/** Die Rechte einer Rolle; ohne Rolle — ohne angemeldete Sitzung — ist jedes Recht `false`. */
export function rechteAus(rolle: Rolle | null): Rechte {
  return rolle === null ? KEINE : RECHTE[rolle];
}

/** Der Anzeigename einer Rolle. */
export function rollenname(rolle: Rolle): string {
  return ROLLENNAMEN[rolle];
}

/** Die Rechte der laufenden Sitzung. */
export function useRechte(): Rechte {
  const { sitzung } = useAuth();
  return rechteAus(sitzung.status === 'angemeldet' ? sitzung.konto.role : null);
}
