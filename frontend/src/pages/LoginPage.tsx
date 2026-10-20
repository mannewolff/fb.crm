import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import TextField from '@mui/material/TextField';
import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

import { setupStatus } from '../api/auth';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import AuthCard from '../components/AuthCard';
import CodeFeld from '../components/CodeFeld';
import KupferTaste from '../components/KupferTaste';
import WeicheTaste from '../components/WeicheTaste';
import { hinweisAus } from '../lib/feldmeldung';

/**
 * Die Anmeldeseite (K4, K5) — in zwei Schritten: erst die Adresse, dann der Code aus der Mail.
 *
 * Drei Zusagen stehen hier im Code und nicht bloss in der Erwartung an den Server:
 *
 * <ul>
 *   <li><b>Keine Registrierung</b> — fb.crm legt Konten nicht selbst an; der erste Betreiber
 *       kommt ueber den Einmal-Schluessel herein. Die Seite bietet deshalb keinen Weg dorthin
 *       an, auch keinen Hinweis (K4).</li>
 *   <li><b>Ein Hinweis fuer jede Adresse</b> — nach dem Absenden der Adresse steht derselbe Satz,
 *       ob es zu ihr ein Konto gibt oder nicht und ob der Mailversand laeuft oder nicht (E17).
 *       Die Seite erfaehrt beides nicht, und sie soll es auch nicht.</li>
 *   <li><b>Eine Meldung fuer jede gescheiterte Eingabe</b> — falscher, abgelaufener und
 *       verbrauchter Code und unbekannte Adresse ergeben denselben Satz. Er steht als Konstante
 *       hier, statt aus der Antwort zu stammen: Sonst haengt die Zusage daran, dass der Server
 *       fuer alle Faelle zufaellig denselben Text schickt (K5).</li>
 * </ul>
 *
 * Eine frische Instanz hat noch kein Konto, an dem sich jemand anmelden koennte. Die Seite fragt
 * deshalb beim Aufruf den Einrichtungsstand (E11) und fuehrt auf `/einrichten`. Ist er nicht zu
 * erfahren, bleibt sie, wo sie ist — die Anmeldung scheitert dann schlimmstenfalls wie jede
 * andere.
 */

/** Der eine Hinweis nach dem Absenden der Adresse, fuer jede Adresse derselbe (E17). */
const CODE_UNTERWEGS =
  'Ein Code ist unterwegs, falls es zu dieser Adresse ein Konto gibt. Bitte den sechsstelligen Code aus der Mail eingeben.';

/** Der eine Satz fuer jede gescheiterte Eingabe (K5). */
const ANMELDUNG_GESCHEITERT =
  'Der Code ist falsch, abgelaufen oder schon benutzt. Bitte einen neuen Code anfordern.';

/** Wenn die Schnittstelle gar nicht antwortet — ohne technische Einzelheiten. */
const AUSFALL = 'Die Anmeldung ist gerade nicht möglich. Bitte später erneut versuchen.';

type Schritt = 'adresse' | 'code';

function meldungZu(ursache: unknown): string {
  if (!(ursache instanceof ApiError)) {
    return AUSFALL;
  }
  // 401 ist der einzige Status, der etwas ueber das Konto verraten koennte. Jeder andere
  // Fall — Zaehlbremse, ungueltige Eingabe, Serverfehler — darf seine Meldung behalten.
  return ursache.status === 401 ? ANMELDUNG_GESCHEITERT : ursache.message;
}

export default function LoginPage() {
  const { codeAnfordern, anmelden } = useAuth();
  const navigate = useNavigate();
  // Der Zustand einer Navigation ist fuer React Router `any`; hier wird er, was er ist: unbekannt.
  const zustand: unknown = useLocation().state;
  const hinweis = hinweisAus(zustand);
  const [schritt, setSchritt] = useState<Schritt>('adresse');
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
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

  const adresseAbsenden = async () => {
    try {
      await codeAnfordern(email);
      setCode('');
      setSchritt('code');
    } catch (ursache) {
      setFehler(meldungZu(ursache));
    }
    setLaeuft(false);
  };

  const codeAbsenden = async () => {
    try {
      await anmelden(email, code);
      navigate('/', { replace: true });
    } catch (ursache) {
      setFehler(meldungZu(ursache));
      setLaeuft(false);
    }
  };

  const absenden = (ereignis: FormEvent<HTMLFormElement>) => {
    ereignis.preventDefault();
    setFehler(null);
    setLaeuft(true);
    void (schritt === 'adresse' ? adresseAbsenden() : codeAbsenden());
  };

  const andereAdresse = () => {
    setFehler(null);
    setSchritt('adresse');
  };

  return (
    <AuthCard titel="Anmelden">
      <Box
        component="form"
        onSubmit={absenden}
        sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}
      >
        {schritt === 'adresse' && hinweis !== null ? (
          <Alert severity="success" role="status">
            {hinweis}
          </Alert>
        ) : null}
        {schritt === 'code' ? (
          <Alert severity="info" role="status">
            {CODE_UNTERWEGS}
          </Alert>
        ) : null}
        {fehler === null ? null : <Alert severity="error">{fehler}</Alert>}
        {schritt === 'adresse' ? (
          <>
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
            <KupferTaste disabled={laeuft}>Code anfordern</KupferTaste>
          </>
        ) : (
          <>
            <CodeFeld wert={code} setzeWert={setCode} feldFehler={{}} />
            <KupferTaste disabled={laeuft}>Anmelden</KupferTaste>
            <WeicheTaste onClick={andereAdresse} disabled={laeuft}>
              Andere Adresse
            </WeicheTaste>
          </>
        )}
      </Box>
    </AuthCard>
  );
}
