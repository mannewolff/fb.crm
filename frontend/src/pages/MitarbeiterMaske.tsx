import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import FormControl from '@mui/material/FormControl';
import FormControlLabel from '@mui/material/FormControlLabel';
import FormHelperText from '@mui/material/FormHelperText';
import FormLabel from '@mui/material/FormLabel';
import Link from '@mui/material/Link';
import Radio from '@mui/material/Radio';
import RadioGroup from '@mui/material/RadioGroup';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useCallback, useEffect, useId, useState } from 'react';
import type { FormEvent, ReactNode } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';

import type { Rolle } from '../api/auth';
import { istRolle } from '../api/auth';
import type { FieldErrors } from '../api/client';
import { mitarbeiterAendern, mitarbeiterAnlegen, mitarbeitendeListe } from '../api/mitarbeitende';
import type { Mitarbeiter } from '../api/mitarbeitende';
import { rollenname } from '../auth/rechte';
import EigeneEingaben from '../components/EigeneEingaben';
import type { EigeneEingabe } from '../components/EigeneEingaben';
import EmailFeld from '../components/EmailFeld';
import Karte from '../components/Karte';
import Konfliktmeldung from '../components/Konfliktmeldung';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import KupferTaste from '../components/KupferTaste';
import WeicheTaste from '../components/WeicheTaste';
import {
  feldMeldungen,
  istBearbeitungskonflikt,
  konflikt,
  serverMeldung,
} from '../lib/apifehler';
import { meldungAm } from '../lib/feldmeldung';
import { kennungAus } from '../lib/kennung';
import { ABSTAND_BUEHNE } from '../theme';

/**
 * Die Maske eines Kontos — Anlegen unter `/mitarbeitende/neu`, Aendern unter
 * `/mitarbeitende/:id/bearbeiten` (Plan #414, A12, A13; fachliche Quelle #409, Kriterien 4, 6, 8).
 *
 * Eine Komponente fuer beide Wege, wie die Maske der Firma: Felder und Meldungen sind dieselben,
 * verschieden ist nur, woher die Werte kommen. Einen Leseweg fuer ein einzelnes Konto gibt es
 * nicht; die Maske findet das Konto in der Liste.
 *
 * Alle drei Angaben sind Pflicht. Die Rolle ist beim Anlegen **nicht vorgewaehlt**: Eine Vorgabe
 * vergaebe Rechte, die niemand bewusst gewaehlt hat.
 *
 * Nach dem Anlegen fuehrt die Maske zur Liste — ausser, es ging keine Begruessung hinaus, weil der
 * Mailversand ausgeschaltet ist (A13). Dann bleibt sie mit dem Hinweis stehen, denn die Person
 * erfaehrt sonst von niemandem, dass es ihr Konto gibt.
 *
 * Eine Abweisung des Servers, die dem Stand widerspricht — das letzte aktive C-Level-Konto soll
 * herabgestuft werden —, steht mit dem Satz des Servers da (Kriterium 8).
 *
 * <b>Gespeichert wird auf dem gelesenen Stand</b> (#411, Kriterien 1 bis 4; Plan #415, E19). Die
 * Maske fuehrt die Version des Kontos mit und legt sie beim Speichern vor. Hat inzwischen ein
 * anderer C-Level das Konto verwaltet — oder dieselbe Person in einem anderen Fenster —, weist der
 * Server ab, und die {@link Konfliktmeldung} sagt, wer wann geaendert hat. Die eigenen Eingaben
 * bleiben im Formular. „Neu laden" holt den Stand des Servers samt neuer Version ins Formular und
 * stellt die eigenen Werte daneben in {@link EigeneEingaben}, bis „Verwerfen" gewaehlt wird. Ein
 * Konto wird nie geloescht; die Meldung kennt hier nur „veraltet".
 */

const ROLLEN: readonly Rolle[] = ['C_LEVEL', 'EMPLOYEE', 'READ_ONLY'];

const EMAIL_FEHLT = 'Bitte eine E-Mail-Adresse angeben.';
const NAME_FEHLT = 'Bitte einen Namen angeben.';
const ROLLE_FEHLT = 'Bitte eine Rolle wählen.';
const NICHT_GEFUNDEN = 'Dieses Konto gibt es nicht.';
const AUSFALL_LESEN = 'Das Konto ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_SPEICHERN = 'Das Konto wurde nicht gespeichert. Bitte später erneut versuchen.';
/** Wo „Neu laden" nach einer Abweisung scheitert. */
const AUSFALL_NEU_LADEN = 'Der aktuelle Stand ist gerade nicht zu erreichen. Bitte erneut versuchen.';

/** Wohin die Konfliktmeldung fuehrt — bei Konten nur der Form halber, sie werden nie geloescht. */
const UEBERSICHT = '/mitarbeitende';

/** Ueber der Maske steht die Liste. */
const ZU_MITARBEITENDEN: readonly PfadVerweis[] = [
  { titel: 'Mitarbeitende', ziel: '/mitarbeitende' },
];

type Stand = 'laedt' | 'bereit' | 'unbekannt' | 'ausfall';

/** Die Maske selbst prueft, was fehlt — in derselben Form wie die Meldungen des Servers. */
function fehlendeAngaben(email: string, name: string, rolle: Rolle | null): FieldErrors {
  const fehler: Record<string, readonly string[]> = {};
  if (email.trim() === '') {
    fehler.email = [EMAIL_FEHLT];
  }
  if (name.trim() === '') {
    fehler.displayName = [NAME_FEHLT];
  }
  if (rolle === null) {
    fehler.role = [ROLLE_FEHLT];
  }
  return fehler;
}

/**
 * Was gerade in der Maske steht, als eigene Eingaben zum Kopieren (#411, Kriterium 3) — beschriftet
 * wie die Felder, damit sich jeder Wert seinem Feld zuordnen laesst.
 */
function eigeneEingabenAus(
  email: string,
  name: string,
  rolle: Rolle | null,
): readonly EigeneEingabe[] {
  return [
    { bezeichnung: 'E-Mail-Adresse', wert: email },
    { bezeichnung: 'Name', wert: name },
    // Ohne gewaehlte Rolle bleibt der Wert leer — so wie ein leeres Feld (EigeneEingaben).
    { bezeichnung: 'Rolle', wert: ROLLEN.filter((wert) => wert === rolle).map(rollenname).join('') },
  ];
}

/** Das Konto zur Kennung aus der Liste — einen Leseweg fuer ein einzelnes Konto gibt es nicht. */
async function kontoLesen(kennung: number): Promise<Mitarbeiter | undefined> {
  const konten = await mitarbeitendeListe();
  return konten.find((kandidat) => kandidat.id === kennung);
}

function nichtBereit(stand: Exclude<Stand, 'bereit'>): ReactNode {
  if (stand === 'laedt') {
    return (
      <Typography
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textSchwach })}
      >
        Das Konto wird geladen …
      </Typography>
    );
  }
  return <Alert severity="error">{stand === 'unbekannt' ? NICHT_GEFUNDEN : AUSFALL_LESEN}</Alert>;
}

/** Der Hinweis, wenn das Konto steht, aber keine Begruessung hinausging (A13). */
function OhneBegruessung({ konto }: { readonly konto: Mitarbeiter }) {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Alert severity="info" role="status">
        {`Das Konto für ${konto.displayName} ist angelegt. Eine Begrüßungsmail ging nicht hinaus, weil der Mailversand nicht eingerichtet ist. Bitte ${konto.email} selbst Bescheid geben, sich mit dieser Adresse bei fb.crm anzumelden.`}
      </Alert>
      <Box>
        <Link component={RouterLink} to="/mitarbeitende" underline="hover">
          Zur Liste
        </Link>
      </Box>
    </Box>
  );
}

export default function MitarbeiterMaske() {
  const { id } = useParams();
  const aendern = id !== undefined;
  const kennung = kennungAus(id);
  const navigate = useNavigate();
  const rollenLabel = useId();
  const [stand, setzeStand] = useState<Stand>(aendern ? 'laedt' : 'bereit');
  const [email, setzeEmail] = useState('');
  const [name, setzeName] = useState('');
  const [rolle, setzeRolle] = useState<Rolle | null>(null);
  const [feldFehler, setzeFeldFehler] = useState<FieldErrors>({});
  const [fehler, setzeFehler] = useState<string | null>(null);
  const [laeuft, setzeLaeuft] = useState(false);
  const [ohneBegruessung, setzeOhneBegruessung] = useState<Mitarbeiter | null>(null);
  // Der Stand, den das naechste Speichern vorlegt: gelesen, oder aus „Neu laden".
  const [version, setzeVersion] = useState(0);
  // Die Abweisung eines Speicherns auf altem Stand, ungeprueft — die Konfliktmeldung liest sie selbst.
  const [abweisung, setzeAbweisung] = useState<unknown>(null);
  // Die eigenen Werte nach „Neu laden", bis „Verwerfen" gewaehlt wird (#411, Kriterium 3).
  const [eigene, setzeEigene] = useState<readonly EigeneEingabe[] | null>(null);

  // Die Setzer sind stabil; so kann der Leseweg beim Oeffnen dieselbe Uebernahme benutzen.
  const uebernimm = useCallback((konto: Mitarbeiter) => {
    setzeEmail(konto.email);
    setzeName(konto.displayName);
    setzeRolle(konto.role);
    setzeVersion(konto.version);
  }, []);

  useEffect(() => {
    if (!aendern) {
      return;
    }
    if (kennung === null) {
      // Eine Kennung, die keine ist, geht gar nicht erst ans Netz (kennung.ts).
      setzeStand('unbekannt');
      return;
    }
    kontoLesen(kennung)
      .then((konto) => {
        if (konto === undefined) {
          setzeStand('unbekannt');
          return;
        }
        uebernimm(konto);
        setzeStand('bereit');
      })
      .catch(() => {
        setzeStand('ausfall');
      });
  }, [aendern, kennung, uebernimm]);

  const absenden = async (ereignis: FormEvent<HTMLFormElement>) => {
    ereignis.preventDefault();
    setzeFehler(null);
    setzeAbweisung(null);
    const fehlend = fehlendeAngaben(email, name, rolle);
    setzeFeldFehler(fehlend);
    if (rolle === null || Object.keys(fehlend).length > 0) {
      return;
    }
    setzeLaeuft(true);
    const eingabe = { email: email.trim(), displayName: name.trim(), role: rolle };
    try {
      if (kennung === null) {
        const angelegt = await mitarbeiterAnlegen(eingabe);
        if (angelegt.begruessungMailVersandt === false) {
          setzeOhneBegruessung(angelegt);
          return;
        }
      } else {
        await mitarbeiterAendern(kennung, version, eingabe);
      }
      navigate('/mitarbeitende', { replace: true });
    } catch (ursache) {
      setzeLaeuft(false);
      const felder = feldMeldungen(ursache);
      if (istBearbeitungskonflikt(ursache)) {
        // Die Eingaben bleiben stehen; den Weg weiter zeigt die Konfliktmeldung (Kriterium 3).
        setzeAbweisung(ursache);
      } else if (Object.keys(felder).length > 0) {
        setzeFeldFehler(felder);
      } else {
        setzeFehler(
          konflikt(ursache) ? serverMeldung(ursache, AUSFALL_SPEICHERN) : AUSFALL_SPEICHERN,
        );
      }
    }
  };

  /**
   * „Neu laden" nach einer Abweisung: der Stand des Servers ins Formular, die eigenen Werte daneben
   * (#411, Kriterium 3; Plan #415, E12).
   *
   * Die eigenen Werte werden genommen, wie sie in diesem Augenblick in der Maske stehen — auch was
   * nach der Abweisung noch getippt wurde, soll nicht verloren gehen.
   */
  const neuLaden = async (id: number) => {
    const meine = eigeneEingabenAus(email, name, rolle);
    setzeFehler(null);
    setzeLaeuft(true);
    try {
      const konto = await kontoLesen(id);
      if (konto === undefined) {
        setzeFehler(AUSFALL_NEU_LADEN);
        return;
      }
      uebernimm(konto);
      setzeEigene(meine);
      setzeAbweisung(null);
      setzeFeldFehler({});
    } catch {
      setzeFehler(AUSFALL_NEU_LADEN);
    } finally {
      setzeLaeuft(false);
    }
  };

  const titel = aendern ? 'Konto bearbeiten' : 'Neues Konto';
  useKopfPfad(ZU_MITARBEITENDEN, titel);
  const nameMeldung = meldungAm(feldFehler, 'displayName');
  const rolleMeldung = meldungAm(feldFehler, 'role');

  const formular = (
    <Box
      component="form"
      noValidate
      onSubmit={(ereignis: FormEvent<HTMLFormElement>) => {
        void absenden(ereignis);
      }}
      sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}
    >
      {fehler === null ? null : <Alert severity="error">{fehler}</Alert>}
      {kennung === null ? null : (
        <Konfliktmeldung
          ursache={abweisung}
          onNeuLaden={() => {
            void neuLaden(kennung);
          }}
          uebersicht={UEBERSICHT}
        />
      )}
      {eigene === null ? null : (
        <EigeneEingaben
          werte={eigene}
          onVerwerfen={() => {
            setzeEigene(null);
          }}
        />
      )}
      <EmailFeld
        wert={email}
        setzeWert={setzeEmail}
        feldFehler={feldFehler}
        autoComplete="off"
      />
      <TextField
        label="Name"
        value={name}
        onChange={(ereignis) => {
          setzeName(ereignis.target.value);
        }}
        error={nameMeldung !== undefined}
        helperText={nameMeldung}
        autoComplete="off"
        required
        fullWidth
      />
      <FormControl error={rolleMeldung !== undefined} required>
        <FormLabel id={rollenLabel}>Rolle</FormLabel>
        <RadioGroup
          aria-labelledby={rollenLabel}
          value={rolle ?? ''}
          onChange={(ereignis) => {
            const gewaehlt = ereignis.target.value;
            if (istRolle(gewaehlt)) {
              setzeRolle(gewaehlt);
            }
          }}
        >
          {ROLLEN.map((wert) => (
            <FormControlLabel
              key={wert}
              value={wert}
              control={<Radio />}
              label={rollenname(wert)}
            />
          ))}
        </RadioGroup>
        {rolleMeldung === undefined ? null : <FormHelperText>{rolleMeldung}</FormHelperText>}
      </FormControl>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, flexWrap: 'wrap' }}>
        <KupferTaste disabled={laeuft}>{aendern ? 'Speichern' : 'Anlegen'}</KupferTaste>
        <WeicheTaste to="/mitarbeitende">Abbrechen</WeicheTaste>
      </Box>
    </Box>
  );

  let inhalt: ReactNode;
  if (ohneBegruessung !== null) {
    inhalt = <OhneBegruessung konto={ohneBegruessung} />;
  } else if (stand === 'bereit') {
    inhalt = formular;
  } else {
    inhalt = nichtBereit(stand);
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: ABSTAND_BUEHNE }}>
      <Karte titel={titel} titelEbene={1}>
        {inhalt}
      </Karte>
    </Box>
  );
}
