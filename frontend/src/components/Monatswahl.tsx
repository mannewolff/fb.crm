import TextField from '@mui/material/TextField';

import { laufenderMonat, monatWort, vormonat } from '../lib/arbeitszeit';

/**
 * Die Wahl des Monats, dessen Arbeitszeit eine neue Rechnung vorbelegt (Issue #203; Plan A17, E7).
 *
 * <b>Eine Komponente fuer beide Einstiege</b> — die Wahl „Neue Rechnung" in {@link RechnungenPage}
 * und „Rechnung schreiben" in {@link AngebotPage}. Beide stellen dieselbe Frage; sie zweimal zu
 * bauen hiesse, die Liste der Monate und die Vorbelegung an zwei Stellen zu pflegen.
 *
 * <b>Eine feste Liste und kein freies Monatsfeld</b> (Entscheidung am Issue): Abgerechnet wird
 * nachtraeglich, der laufende Monat und die elf davor decken den Alltag, und eine Liste verlangt
 * dem Betrachter kein Format ab. Vorbelegt ist der laufende Monat; „ohne Arbeitszeit" steht
 * zuletzt, weil es der Ausnahmefall ist — dann entsteht der Entwurf ohne vorbelegte Mengen.
 *
 * <b>Die Wahl fuehrt ihren Wert nicht selbst.</b> Sie bekommt ihn und gibt ihn zurueck: Beide
 * Einstiege brauchen ihn im selben Moment, in dem sie anlegen, und ein Wert an zwei Orten waere
 * eine Quelle zu viel. Was die Komponente vorbelegt, sagt {@link monatswahlWert}.
 *
 * <b>Beschriftung und zugaenglicher Name sind dasselbe Wort</b> („Monat der Arbeitszeit"): Die
 * Wahl steht in einem Dialog und nicht in einer Werkzeugleiste, also traegt sie ihr Etikett
 * sichtbar (CLAUDE-design.md, „Felder"). Die Eintraege stehen in Satzschreibung.
 */

/** Der Wert von „ohne Arbeitszeit" — leer, denn es ist kein Monat. */
export const OHNE_ARBEITSZEIT = '';

/** Was in der Liste steht, wo gar kein Monat gemeint ist. */
const OHNE_WORT = 'ohne Arbeitszeit';

/** Der zugaengliche Name und die sichtbare Beschriftung der Wahl. */
const NAME = 'Monat der Arbeitszeit';

/** So viele Monate stehen zur Wahl: der laufende und die elf davor. */
const ANZAHL = 12;

/** Die Vorbelegung der Wahl — der laufende Monat (E7). */
export function monatswahlWert(): string {
  return laufenderMonat();
}

/**
 * Der Monat als Angabe an {@link rechnungAnlegen}, oder `undefined` bei „ohne Arbeitszeit".
 *
 * Die Umrechnung steht hier und nicht an den Einstiegen: Dass der leere Wert „kein Monat" heisst,
 * weiss die Wahl, und beide Einstiege sollen dieselbe Antwort daraus ziehen.
 */
export function monatOderKeiner(monat: string): string | undefined {
  return monat === OHNE_ARBEITSZEIT ? undefined : monat;
}

/** Die Monate der Liste, der laufende zuerst. */
function monate(): readonly string[] {
  const liste: string[] = [];
  let monat = laufenderMonat();
  for (let zaehler = 0; zaehler < ANZAHL; zaehler += 1) {
    liste.push(monat);
    monat = vormonat(monat);
  }
  return liste;
}

export interface MonatswahlProps {
  /** Der gewaehlte Monat als `JJJJ-MM`, oder {@link OHNE_ARBEITSZEIT}. */
  readonly monat: string;
  readonly setzeMonat: (monat: string) => void;
  /** Gesperrt, solange eine Anfrage laeuft. */
  readonly disabled?: boolean;
}

export default function Monatswahl({ monat, setzeMonat, disabled = false }: MonatswahlProps) {
  return (
    <TextField
      select
      label={NAME}
      value={monat}
      onChange={(ereignis) => {
        setzeMonat(ereignis.target.value);
      }}
      disabled={disabled}
      fullWidth
      slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
    >
      {monate().map((wert) => (
        <option key={wert} value={wert}>
          {monatWort(wert)}
        </option>
      ))}
      <option value={OHNE_ARBEITSZEIT}>{OHNE_WORT}</option>
    </TextField>
  );
}
