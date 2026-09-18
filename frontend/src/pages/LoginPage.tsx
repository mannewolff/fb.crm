import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import TextField from '@mui/material/TextField';
import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';

import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import AuthCard from '../components/AuthCard';
import { CARD_RADIUS } from '../theme';

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
  const [email, setEmail] = useState('');
  const [passwort, setPasswort] = useState('');
  const [fehler, setFehler] = useState<string | null>(null);
  const [laeuft, setLaeuft] = useState(false);

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
        <Button
          type="submit"
          disabled={laeuft}
          sx={(theme) => ({
            borderRadius: `${CARD_RADIUS}px`,
            paddingBlock: '9px',
            color: theme.vars.palette.kupferwarte.kupferSchrift,
            background: `linear-gradient(180deg, ${theme.vars.palette.kupferwarte.kupferHell}, ${theme.vars.palette.kupferwarte.kupfer})`,
            border: `1px solid ${theme.vars.palette.kupferwarte.kupferTief}`,
            boxShadow: theme.vars.palette.kupferwarte.schatten.taste,
            '&:hover': {
              background: `linear-gradient(180deg, ${theme.vars.palette.kupferwarte.kupferHell}, ${theme.vars.palette.kupferwarte.kupfer})`,
              boxShadow: theme.vars.palette.kupferwarte.schatten.platte,
            },
            '&:active': { transform: 'translateY(1px)' },
          })}
        >
          Anmelden
        </Button>
      </Box>
    </AuthCard>
  );
}
