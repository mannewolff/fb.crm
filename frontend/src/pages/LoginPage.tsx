import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import TextField from '@mui/material/TextField';
import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Link as RouterLink, useLocation, useNavigate } from 'react-router-dom';

import { setupStatus } from '../api/auth';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import AuthCard from '../components/AuthCard';
import KupferTaste from '../components/KupferTaste';
import { hinweisAus } from '../lib/feldmeldung';

/**
 * Die Anmeldeseite (K4, K5).
 *
 * Zwei Zusagen stehen hier im Code und nicht bloss in der Erwartung an den Server:
 *
 * <ul>
 *   <li><b>Keine Registrierung</b> — fb.crm legt Konten nicht selbst an; der erste Betreiber
 *       kommt ueber den Einmal-Schluessel herein. Die Seite bietet deshalb keinen Weg dorthin
 *       an, auch keinen Hinweis (K4).</li>
 *   <li><b>Eine Meldung fuer beide Faelle</b> — unbekannte Adresse und falsches Passwort
 *       ergeben denselben Satz. Er steht als Konstante hier, statt aus der Antwort zu
 *       stammen: Sonst haengt die Zusage daran, dass der Server fuer beide Faelle zufaellig
 *       denselben Text schickt (K5).</li>
 * </ul>
 *
 * Eine frische Instanz hat noch kein Konto, an dem sich jemand anmelden koennte. Die Seite fragt
 * deshalb beim Aufruf den Einrichtungsstand (E11) und fuehrt auf `/einrichten`. Ist er nicht zu
 * erfahren, bleibt sie, wo sie ist — die Anmeldung scheitert dann schlimmstenfalls wie jede
 * andere.
 */

/** Der eine Satz fuer jede gescheiterte Anmeldung (K5). */
const ANMELDUNG_GESCHEITERT = 'E-Mail-Adresse oder Passwort ist falsch.';

/** Wenn die Schnittstelle gar nicht antwortet — ohne technische Einzelheiten. */
const AUSFALL = 'Die Anmeldung ist gerade nicht möglich. Bitte später erneut versuchen.';

function meldungZu(ursache: unknown): string {
  if (!(ursache instanceof ApiError)) {
    return AUSFALL;
  }
  // 401 ist der einzige Status, der etwas ueber das Konto verraten koennte. Jeder andere
  // Fall — Zaehlbremse, ungueltige Eingabe, Serverfehler — darf seine Meldung behalten.
  return ursache.status === 401 ? ANMELDUNG_GESCHEITERT : ursache.message;
}

export default function LoginPage() {
  const { anmelden } = useAuth();
  const navigate = useNavigate();
  // Der Zustand einer Navigation ist fuer React Router `any`; hier wird er, was er ist: unbekannt.
  const zustand: unknown = useLocation().state;
  const hinweis = hinweisAus(zustand);
  const [email, setEmail] = useState('');
  const [passwort, setPasswort] = useState('');
  const [fehler, setFehler] = useState<string | null>(null);
  const [laeuft, setLaeuft] = useState(false);

  useEffect(() => {
    setupStatus()
      .then((antwort) => {
        if (!antwort.initialized) {
          navigate('/einrichten', { replace: true });
        }
      })
      .catch(() => {
        // Nichts zu tun: ohne Auskunft bleibt die Anmeldeseite stehen.
      });
  }, [navigate]);

  const absenden = async (ereignis: FormEvent<HTMLFormElement>) => {
    ereignis.preventDefault();
    setFehler(null);
    setLaeuft(true);
    try {
      await anmelden(email, passwort);
      navigate('/', { replace: true });
    } catch (ursache) {
      setFehler(meldungZu(ursache));
      setLaeuft(false);
    }
  };

  return (
    <AuthCard
      titel="Anmelden"
      fuss={
        <Link component={RouterLink} to="/passwort-vergessen" underline="hover">
          Passwort vergessen?
        </Link>
      }
    >
      <Box
        component="form"
        onSubmit={(ereignis: FormEvent<HTMLFormElement>) => {
          void absenden(ereignis);
        }}
        sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}
      >
        {hinweis === null ? null : (
          <Alert severity="success" role="status">
            {hinweis}
          </Alert>
        )}
        {fehler === null ? null : <Alert severity="error">{fehler}</Alert>}
        <TextField
          label="E-Mail-Adresse"
          type="email"
          value={email}
          onChange={(ereignis) => {
            setEmail(ereignis.target.value);
          }}
          autoComplete="username"
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
          autoComplete="current-password"
          required
          fullWidth
        />
        <KupferTaste disabled={laeuft}>Anmelden</KupferTaste>
      </Box>
    </AuthCard>
  );
}
