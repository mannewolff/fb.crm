import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';

import { einrichtungCodeAnfordern, setupStatus } from '../api/auth';
import { ApiError } from '../api/client';
import type { FieldErrors } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import AuthCard from '../components/AuthCard';
import CodeFeld from '../components/CodeFeld';
import EmailFeld from '../components/EmailFeld';
import KupferTaste from '../components/KupferTaste';
import WeicheTaste from '../components/WeicheTaste';
import { meldungAm } from '../lib/feldmeldung';

/**
 * Die Einrichtung einer frischen Instanz (K3, E11) — in zwei Schritten: erst Einmal-Schluessel,
 * Anzeigename und Adresse, dann der Code, den fb.crm an diese Adresse schickt.
 *
 * Ein Passwort und eine Wiederholung der Adresse gibt es nicht (E15). Den Vertipper in der Adresse
 * faengt der Code ab: Er geht an die vertippte Adresse, kommt nie an, und ohne ihn entsteht kein
 * Konto. Der Schluessel wird in beiden Schritten mitgeschickt — der Server legt zwischen ihnen
 * nichts ab und prueft ihn beim Anlegen des Kontos erneut.
 *
 * Ob die Instanz schon eingerichtet ist, fragt die Seite beim Aufruf. Laesst sich das nicht
 * erfahren, zeigt sie das Formular: Der Server weist eine Einrichtung auf einer eingerichteten
 * Instanz ohnehin ab, und eine Seite ohne Formular waere dann eine Sackgasse ohne Grund.
 */

const BEREITS_EINGERICHTET =
  'Diese Instanz ist bereits eingerichtet. Die Anmeldung erfolgt mit dem angelegten Konto.';

/** Der Hinweis nach dem Anfordern des Codes. */
const CODE_UNTERWEGS =
  'Ein Code ist unterwegs an die angegebene Adresse. Bitte den sechsstelligen Code aus der Mail eingeben.';

/** Ein abgewiesener Schluessel sagt nicht, woran es lag — der Server sagt es auch nicht. */
const ABGELEHNT = 'Die Einrichtung wurde abgelehnt. Bitte den Einmal-Schlüssel prüfen.';
const AUSFALL = 'Die Einrichtung ist gerade nicht möglich. Bitte später erneut versuchen.';

type Stand = 'pruefen' | 'offen' | 'eingerichtet';
type Schritt = 'angaben' | 'code';

export default function SetupPage() {
  const { einrichten } = useAuth();
  const navigate = useNavigate();
  const [stand, setStand] = useState<Stand>('pruefen');
  const [schritt, setSchritt] = useState<Schritt>('angaben');
  const [schluessel, setSchluessel] = useState('');
  const [anzeigename, setAnzeigename] = useState('');
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
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

  const melden = (ursache: unknown) => {
    if (!(ursache instanceof ApiError)) {
      setFehler(AUSFALL);
    } else if (Object.keys(ursache.fieldErrors).length > 0) {
      setFeldFehler(ursache.fieldErrors);
    } else {
      setFehler(ursache.status === 403 ? ABGELEHNT : ursache.message);
    }
  };

  const angabenAbsenden = async () => {
    try {
      await einrichtungCodeAnfordern(schluessel, anzeigename, email);
      setCode('');
      setSchritt('code');
    } catch (ursache) {
      melden(ursache);
    }
    setLaeuft(false);
  };

  const codeAbsenden = async () => {
    try {
      await einrichten({ bootstrapToken: schluessel, displayName: anzeigename, email, code });
      navigate('/', { replace: true });
    } catch (ursache) {
      melden(ursache);
      setLaeuft(false);
    }
  };

  const absenden = (ereignis: FormEvent<HTMLFormElement>) => {
    ereignis.preventDefault();
    setFehler(null);
    setFeldFehler({});
    setLaeuft(true);
    void (schritt === 'angaben' ? angabenAbsenden() : codeAbsenden());
  };

  const angabenAendern = () => {
    setFehler(null);
    setFeldFehler({});
    setSchritt('angaben');
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

  return (
    <AuthCard titel="Einrichten">
      <Box
        component="form"
        onSubmit={absenden}
        sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}
      >
        {schritt === 'code' ? (
          <Alert severity="info" role="status">
            {CODE_UNTERWEGS}
          </Alert>
        ) : null}
        {fehler === null ? null : <Alert severity="error">{fehler}</Alert>}
        {schritt === 'angaben' ? (
          <>
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
            <EmailFeld
              wert={email}
              setzeWert={setEmail}
              feldFehler={feldFehler}
              autoComplete="email"
            />
            <KupferTaste disabled={laeuft}>Code anfordern</KupferTaste>
          </>
        ) : (
          <>
            <CodeFeld wert={code} setzeWert={setCode} feldFehler={feldFehler} />
            <KupferTaste disabled={laeuft}>Einrichten</KupferTaste>
            <WeicheTaste onClick={angabenAendern} disabled={laeuft}>
              Angaben ändern
            </WeicheTaste>
          </>
        )}
      </Box>
    </AuthCard>
  );
}
