import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';

import { setupStatus } from '../api/auth';
import { ApiError } from '../api/client';
import type { FieldErrors } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import AuthCard from '../components/AuthCard';
import KupferTaste from '../components/KupferTaste';
import { meldungAm } from '../lib/feldmeldung';

/**
 * Die Einrichtung einer frischen Instanz (K3, E6, E11).
 *
 * Die Adresse steht zweimal im Formular, und der Vergleich laeuft schon hier: Nach der
 * Einrichtung wirkt der Einmal-Schluessel nicht mehr, und eine Profilbearbeitung gibt es nicht
 * — ein Tippfehler in der Adresse sperrte den Betreiber aus. Der Server prueft dasselbe noch
 * einmal; die Pruefung hier erspart nur den Umweg ueber das Netz. Verglichen wird wie dort ohne
 * Ruecksicht auf Gross- und Kleinschreibung.
 *
 * Ob die Instanz schon eingerichtet ist, fragt die Seite beim Aufruf. Laesst sich das nicht
 * erfahren, zeigt sie das Formular: Der Server weist eine Einrichtung auf einer eingerichteten
 * Instanz ohnehin ab, und eine Seite ohne Formular waere dann eine Sackgasse ohne Grund.
 */

const BEREITS_EINGERICHTET =
  'Diese Instanz ist bereits eingerichtet. Die Anmeldung erfolgt mit dem angelegten Konto.';
const WIEDERHOLUNG_FALSCH = 'Die Wiederholung stimmt nicht mit der E-Mail-Adresse überein.';

/** Ein abgewiesener Schluessel sagt nicht, woran es lag — der Server sagt es auch nicht. */
const ABGELEHNT = 'Die Einrichtung wurde abgelehnt. Bitte den Einmal-Schlüssel prüfen.';
const AUSFALL = 'Die Einrichtung ist gerade nicht möglich. Bitte später erneut versuchen.';

type Stand = 'pruefen' | 'offen' | 'eingerichtet';

export default function SetupPage() {
  const { einrichten } = useAuth();
  const navigate = useNavigate();
  const [stand, setStand] = useState<Stand>('pruefen');
  const [email, setEmail] = useState('');
  const [wiederholung, setWiederholung] = useState('');
  const [anzeigename, setAnzeigename] = useState('');
  const [passwort, setPasswort] = useState('');
  const [schluessel, setSchluessel] = useState('');
  const [wiederholungFalsch, setWiederholungFalsch] = useState(false);
  const [feldFehler, setFeldFehler] = useState<FieldErrors>({});
  const [fehler, setFehler] = useState<string | null>(null);
  const [laeuft, setLaeuft] = useState(false);

  useEffect(() => {
    setupStatus()
      .then((antwort) => {
        setStand(antwort.initialized ? 'eingerichtet' : 'offen');
      })
      .catch(() => {
        setStand('offen');
      });
  }, []);

  const absenden = async (ereignis: FormEvent<HTMLFormElement>) => {
    ereignis.preventDefault();
    setFehler(null);
    setFeldFehler({});
    const passt = wiederholung.toLowerCase() === email.toLowerCase();
    setWiederholungFalsch(!passt);
    if (!passt) {
      return;
    }
    setLaeuft(true);
    try {
      await einrichten({
        email,
        emailRepeat: wiederholung,
        displayName: anzeigename,
        password: passwort,
        bootstrapToken: schluessel,
      });
      navigate('/', { replace: true });
    } catch (ursache) {
      setLaeuft(false);
      if (!(ursache instanceof ApiError)) {
        setFehler(AUSFALL);
      } else if (Object.keys(ursache.fieldErrors).length > 0) {
        setFeldFehler(ursache.fieldErrors);
      } else {
        setFehler(ursache.status === 403 ? ABGELEHNT : ursache.message);
      }
    }
  };

  if (stand === 'pruefen') {
    return null;
  }

  if (stand === 'eingerichtet') {
    return (
      <AuthCard
        titel="Einrichten"
        fuss={
          <Link component={RouterLink} to="/anmelden" underline="hover">
            Zur Anmeldung
          </Link>
        }
      >
        <Typography>{BEREITS_EINGERICHTET}</Typography>
      </AuthCard>
    );
  }

  const wiederholungMeldung = wiederholungFalsch
    ? WIEDERHOLUNG_FALSCH
    : meldungAm(feldFehler, 'emailRepeat');

  return (
    <AuthCard titel="Einrichten">
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
          autoComplete="email"
          required
          fullWidth
        />
        <TextField
          label="Wiederholung der E-Mail-Adresse"
          type="email"
          value={wiederholung}
          onChange={(ereignis) => {
            setWiederholung(ereignis.target.value);
          }}
          error={wiederholungMeldung !== undefined}
          helperText={wiederholungMeldung}
          autoComplete="off"
          required
          fullWidth
        />
        <TextField
          label="Anzeigename"
          value={anzeigename}
          onChange={(ereignis) => {
            setAnzeigename(ereignis.target.value);
          }}
          error={meldungAm(feldFehler, 'displayName') !== undefined}
          helperText={meldungAm(feldFehler, 'displayName')}
          autoComplete="name"
          required
          fullWidth
        />
        <TextField
          label="Passwort"
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
          label="Einmal-Schlüssel"
          value={schluessel}
          onChange={(ereignis) => {
            setSchluessel(ereignis.target.value);
          }}
          error={meldungAm(feldFehler, 'bootstrapToken') !== undefined}
          helperText={meldungAm(feldFehler, 'bootstrapToken')}
          autoComplete="off"
          required
          fullWidth
        />
        <KupferTaste disabled={laeuft}>Einrichten</KupferTaste>
      </Box>
    </AuthCard>
  );
}
