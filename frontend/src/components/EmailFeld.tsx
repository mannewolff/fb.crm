import TextField from '@mui/material/TextField';

import type { FieldErrors } from '../api/client';
import { meldungAm } from '../lib/feldmeldung';

interface EmailFeldProps {
  readonly wert: string;
  readonly setzeWert: (wert: string) => void;
  readonly feldFehler: FieldErrors;
  /**
   * Was der Browser vorschlagen soll: `email` beim Einrichten des Kontos, `username` dort, wo die
   * Adresse das Konto bezeichnet (Passwort vergessen) — so findet der Passwortmanager den Eintrag.
   */
  readonly autoComplete: 'email' | 'username';
}

/**
 * Das Pflichtfeld „E-Mail-Adresse" der Seiten rund um das Konto, mit der Meldung des Servers zum
 * Feld `email`.
 */
export default function EmailFeld({ wert, setzeWert, feldFehler, autoComplete }: EmailFeldProps) {
  const meldung = meldungAm(feldFehler, 'email');
  return (
    <TextField
      label="E-Mail-Adresse"
      type="email"
      value={wert}
      onChange={(ereignis) => {
        setzeWert(ereignis.target.value);
      }}
      error={meldung !== undefined}
      helperText={meldung}
      autoComplete={autoComplete}
      required
      fullWidth
    />
  );
}
