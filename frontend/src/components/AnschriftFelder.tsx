import TextField from '@mui/material/TextField';
import type { Dispatch, SetStateAction } from 'react';

import type { FieldErrors } from '../api/client';
import { meldungAm } from '../lib/feldmeldung';

/** Die vier Werte einer Anschrift, wie die Masken sie in ihren Feldwerten tragen. */
export interface Anschriftwerte {
  readonly strasse: string;
  readonly plz: string;
  readonly ort: string;
  readonly land: string;
}

/** Feld und Beschriftung, in der Reihenfolge, in der die Masken sie zeigen. */
const FELDER: readonly { readonly feld: keyof Anschriftwerte; readonly label: string }[] = [
  { feld: 'strasse', label: 'Straße und Hausnummer' },
  { feld: 'plz', label: 'Postleitzahl' },
  { feld: 'ort', label: 'Ort' },
  { feld: 'land', label: 'Land' },
];

interface AnschriftFelderProps<W extends Anschriftwerte> {
  readonly werte: W;
  readonly setzeWerte: Dispatch<SetStateAction<W>>;
  readonly feldFehler: FieldErrors;
}

/**
 * Die Felder einer Anschrift samt der Meldung des Servers je Feld.
 *
 * Firma und eigene Angaben fragen dieselbe Anschrift ab; im Backend ist sie dieselbe Einbettung
 * (`AnschriftSpalten`, Issue #242). Die Maske reicht ihre ganzen Feldwerte herein, der Baustein
 * aendert davon nur die vier Felder der Anschrift und laesst alle uebrigen stehen.
 */
export default function AnschriftFelder<W extends Anschriftwerte>({
  werte,
  setzeWerte,
  feldFehler,
}: AnschriftFelderProps<W>) {
  return (
    <>
      {FELDER.map(({ feld, label }) => {
        const meldung = meldungAm(feldFehler, feld);
        return (
          <TextField
            key={feld}
            label={label}
            value={werte[feld]}
            onChange={(ereignis) => {
              setzeWerte((alt) => ({ ...alt, [feld]: ereignis.target.value }));
            }}
            error={meldung !== undefined}
            helperText={meldung}
            fullWidth
          />
        );
      })}
    </>
  );
}
