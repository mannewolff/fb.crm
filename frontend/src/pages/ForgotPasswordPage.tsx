import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link as RouterLink } from 'react-router-dom';

import { requestPasswordReset } from '../api/auth';
import { ApiError } from '../api/client';
import type { FieldErrors } from '../api/client';
import AuthCard from '../components/AuthCard';
import KupferTaste from '../components/KupferTaste';
import { meldungAm } from '../lib/feldmeldung';

/**
 * „Passwort vergessen" (K7).
 *
 * Nach dem Absenden steht derselbe Text, gleich ob es zu der Adresse ein Konto gibt. Er ist eine
 * Konstante und nimmt nichts aus der Eingabe auf — auch nicht die Adresse selbst: Sonst genuegte
 * ein Unterschied in der Antwort des Servers, um aus der Seite ein Werkzeug zum Abfragen von
 * Konten zu machen.
 */

const BESTAETIGUNG =
  'Wenn zu dieser Adresse ein Konto besteht, ist eine E-Mail mit einem Link zum Setzen eines ' +
  'neuen Passworts unterwegs. Der Link gilt nur begrenzte Zeit.';
const AUSFALL = 'Die Anfrage ist gerade nicht möglich. Bitte später erneut versuchen.';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [gesendet, setGesendet] = useState(false);
  const [feldFehler, setFeldFehler] = useState<FieldErrors>({});
  const [fehler, setFehler] = useState<string | null>(null);
  const [laeuft, setLaeuft] = useState(false);

  const absenden = async (ereignis: FormEvent<HTMLFormElement>) => {
    ereignis.preventDefault();
    setFehler(null);
    setFeldFehler({});
    setLaeuft(true);
    try {
      await requestPasswordReset(email);
      setGesendet(true);
    } catch (ursache) {
      setLaeuft(false);
      if (!(ursache instanceof ApiError)) {
        setFehler(AUSFALL);
      } else if (Object.keys(ursache.fieldErrors).length > 0) {
        setFeldFehler(ursache.fieldErrors);
      } else {
        setFehler(ursache.message);
      }
    }
  };

  const zurAnmeldung = (
    <Link component={RouterLink} to="/anmelden" underline="hover">
      Zur Anmeldung
    </Link>
  );

  if (gesendet) {
    return (
      <AuthCard titel="Passwort vergessen" fuss={zurAnmeldung}>
        <Typography role="status">{BESTAETIGUNG}</Typography>
      </AuthCard>
    );
  }

  return (
    <AuthCard titel="Passwort vergessen" fuss={zurAnmeldung}>
      <Box
        component="form"
        onSubmit={(ereignis: FormEvent<HTMLFormElement>) => {
          void absenden(ereignis);
        }}
        sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}
      >
        {fehler === null ? null : <Alert severity="error">{fehler}</Alert>}
        <TextField
          label="E-Mail-Adresse"
          type="email"
          value={email}
          onChange={(ereignis) => {
            setEmail(ereignis.target.value);
          }}
          error={meldungAm(feldFehler, 'email') !== undefined}
          helperText={meldungAm(feldFehler, 'email')}
          autoComplete="username"
          required
          fullWidth
        />
        <KupferTaste disabled={laeuft}>Link anfordern</KupferTaste>
      </Box>
    </AuthCard>
  );
}
