import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Link as RouterLink, useNavigate, useSearchParams } from 'react-router-dom';

import { checkResetToken, confirmPasswordReset } from '../api/auth';
import { ApiError } from '../api/client';
import type { FieldErrors } from '../api/client';
import AuthCard from '../components/AuthCard';
import KupferTaste from '../components/KupferTaste';
import { meldungAm } from '../lib/feldmeldung';

/**
 * Ein neues Passwort setzen (K6, K7, E24).
 *
 * Der Token aus dem Link wird geprueft, <b>bevor</b> ein Formular erscheint. Wer einen
 * verbrauchten oder abgelaufenen Link oeffnet, soll kein Passwort eintippen, das dann abgewiesen
 * wird — er bekommt die Meldung und den Weg zu einem neuen Link. Warum der Link nicht gilt, sagt
 * die Seite nicht; der Server sagt es auch nicht.
 *
 * Ein Ausfall der Schnittstelle ist etwas anderes als ein ungueltiger Link und wird anders
 * gemeldet: Wer einen frischen Link hat, soll nicht glauben, er sei verbraucht.
 */

const UNGUELTIG = 'Der Link gilt nicht mehr. Ein neuer lässt sich jederzeit anfordern.';
const NICHT_PRUEFBAR = 'Der Link lässt sich gerade nicht prüfen. Bitte später erneut versuchen.';
const WIEDERHOLUNG_FALSCH = 'Die Wiederholung stimmt nicht mit dem neuen Passwort überein.';
const AUSFALL = 'Das Passwort lässt sich gerade nicht setzen. Bitte später erneut versuchen.';

/** Der Hinweis, mit dem die Anmeldeseite den Erfolg bestaetigt. */
const PASSWORT_GESETZT = 'Das neue Passwort ist gesetzt. Bitte damit anmelden.';

type Stand = 'pruefen' | 'gueltig' | 'ungueltig' | 'nichtPruefbar';

export default function ResetPasswordPage() {
  const [parameter] = useSearchParams();
  const token = parameter.get('token') ?? '';
  const navigate = useNavigate();
  const [stand, setStand] = useState<Stand>(token === '' ? 'ungueltig' : 'pruefen');
  const [passwort, setPasswort] = useState('');
  const [wiederholung, setWiederholung] = useState('');
  const [wiederholungFalsch, setWiederholungFalsch] = useState(false);
  const [feldFehler, setFeldFehler] = useState<FieldErrors>({});
  const [fehler, setFehler] = useState<string | null>(null);
  const [laeuft, setLaeuft] = useState(false);

  useEffect(() => {
    if (token === '') {
      return;
    }
    checkResetToken(token)
      .then(() => {
        setStand('gueltig');
      })
      .catch((ursache: unknown) => {
        setStand(ursache instanceof ApiError ? 'ungueltig' : 'nichtPruefbar');
      });
  }, [token]);

  const absenden = async (ereignis: FormEvent<HTMLFormElement>) => {
    ereignis.preventDefault();
    setFehler(null);
    setFeldFehler({});
    const passt = wiederholung === passwort;
    setWiederholungFalsch(!passt);
    if (!passt) {
      return;
    }
    setLaeuft(true);
    try {
      await confirmPasswordReset(token, passwort);
      navigate('/anmelden', { replace: true, state: { hinweis: PASSWORT_GESETZT } });
    } catch (ursache) {
      setLaeuft(false);
      if (!(ursache instanceof ApiError)) {
        setFehler(AUSFALL);
      } else if (ursache.status === 410) {
        setStand('ungueltig');
      } else if (Object.keys(ursache.fieldErrors).length > 0) {
        setFeldFehler(ursache.fieldErrors);
      } else {
        setFehler(ursache.message);
      }
    }
  };

  if (stand === 'pruefen') {
    return null;
  }

  if (stand === 'ungueltig' || stand === 'nichtPruefbar') {
    return (
      <AuthCard
        titel="Neues Passwort"
        fuss={
          <Link component={RouterLink} to="/passwort-vergessen" underline="hover">
            Neuen Link anfordern
          </Link>
        }
      >
        {stand === 'ungueltig' ? (
          <Typography>{UNGUELTIG}</Typography>
        ) : (
          <Alert severity="error">{NICHT_PRUEFBAR}</Alert>
        )}
      </AuthCard>
    );
  }

  const wiederholungMeldung = wiederholungFalsch ? WIEDERHOLUNG_FALSCH : undefined;

  return (
    <AuthCard titel="Neues Passwort">
      <Box
        component="form"
        onSubmit={(ereignis: FormEvent<HTMLFormElement>) => {
          void absenden(ereignis);
        }}
        sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}
      >
        {fehler === null ? null : <Alert severity="error">{fehler}</Alert>}
        <TextField
          label="Neues Passwort"
          type="password"
          value={passwort}
          onChange={(ereignis) => {
            setPasswort(ereignis.target.value);
          }}
          error={meldungAm(feldFehler, 'password') !== undefined}
          helperText={meldungAm(feldFehler, 'password')}
          autoComplete="new-password"
          required
          fullWidth
        />
        <TextField
          label="Wiederholung des Passworts"
          type="password"
          value={wiederholung}
          onChange={(ereignis) => {
            setWiederholung(ereignis.target.value);
          }}
          error={wiederholungMeldung !== undefined}
          helperText={wiederholungMeldung}
          autoComplete="new-password"
          required
          fullWidth
        />
        <KupferTaste disabled={laeuft}>Passwort setzen</KupferTaste>
      </Box>
    </AuthCard>
  );
}
