import TextField from '@mui/material/TextField';
import { useEffect, useRef } from 'react';

import type { FieldErrors } from '../api/client';
import { meldungAm } from '../lib/feldmeldung';

interface CodeFeldProps {
  readonly wert: string;
  readonly setzeWert: (wert: string) => void;
  readonly feldFehler: FieldErrors;
}

/**
 * Das Pflichtfeld „Code" fuer den sechsstelligen Code aus der Mail, mit der Meldung des Servers
 * zum Feld `code`.
 *
 * `one-time-code` laesst Browser und Telefon den Code aus der Mail vorschlagen, `numeric` bringt
 * auf dem Telefon die Zifferntastatur. Das Feld nimmt den Fokus, sobald es erscheint: Der Schritt
 * von der Adresse zum Code soll sich nicht wie ein Seitenwechsel anfuehlen, und wer mit der
 * Tastatur arbeitet, tippt ohne Umweg weiter. Eine Regel „genau sechs Ziffern" steht hier nicht —
 * wie im Backend ist ein Vertipper ein falscher Code wie jeder andere.
 */
export default function CodeFeld({ wert, setzeWert, feldFehler }: CodeFeldProps) {
  const feld = useRef<HTMLInputElement>(null);
  const meldung = meldungAm(feldFehler, 'code');

  useEffect(() => {
    feld.current?.focus();
  }, []);

  return (
    <TextField
      label="Code"
      value={wert}
      onChange={(ereignis) => {
        setzeWert(ereignis.target.value);
      }}
      error={meldung !== undefined}
      helperText={meldung}
      inputRef={feld}
      autoComplete="one-time-code"
      slotProps={{ htmlInput: { inputMode: 'numeric', maxLength: 6 } }}
      required
      fullWidth
    />
  );
}
