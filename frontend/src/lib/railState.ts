/**
 * Der Einklapp-Zustand der Schiene (E13).
 *
 * Er liegt in `localStorage`, weil er eine Eigenschaft des Geraets ist und kein Datum des
 * Kontos: Auf dem schmalen Laptop darf die Schiene eingeklappt sein und auf dem breiten
 * Bildschirm offen. `CLAUDE-security.md` verbietet dort nur Sitzungsmerkmale; der Schluessel
 * traegt deshalb bewusst nichts, was nach Sitzung oder Konto klingt.
 *
 * Jeder Zugriff darf scheitern — ein Browser im privaten Modus wirft schon beim Lesen. Dann
 * gilt „ausgeklappt", und das Merken entfaellt still: Eine Schiene, die sich ihren Zustand
 * nicht merkt, ist ein kleinerer Schaden als eine Anwendung, die daran nicht startet.
 */

export const SCHIENE_SCHLUESSEL = 'fbcrm.schiene.eingeklappt';

export function liesEingeklappt(): boolean {
  try {
    return localStorage.getItem(SCHIENE_SCHLUESSEL) === 'true';
  } catch {
    return false;
  }
}

export function merkeEingeklappt(eingeklappt: boolean): void {
  try {
    localStorage.setItem(SCHIENE_SCHLUESSEL, String(eingeklappt));
  } catch {
    // Ohne Speicher bleibt es beim Zustand dieser Sitzung im Browser.
  }
}
