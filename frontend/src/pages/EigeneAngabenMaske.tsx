import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import type { Dispatch, FormEvent, ReactNode, SetStateAction } from 'react';

import { eigeneAngabenLesen, eigeneAngabenPflegen } from '../api/eigeneAngaben';
import type { EigeneAngaben } from '../api/eigeneAngaben';
import type { FieldErrors } from '../api/client';
import AnschriftFelder from '../components/AnschriftFelder';
import { useRechte } from '../auth/rechte';
import BriefbogenKarte from '../components/BriefbogenKarte';
import EigeneEingaben from '../components/EigeneEingaben';
import type { EigeneEingabe } from '../components/EigeneEingaben';
import Karte from '../components/Karte';
import Konfliktmeldung from '../components/Konfliktmeldung';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import KupferTaste from '../components/KupferTaste';
import WeicheTaste from '../components/WeicheTaste';
import { feldMeldungen, istBearbeitungskonflikt } from '../lib/apifehler';
import { meldungAm } from '../lib/feldmeldung';
import { ABSTAND_BUEHNE } from '../theme';

/**
 * Die Maske „Eigene Angaben" unter `/eigene-angaben` (Kriterium 1).
 *
 * **Ein Formular, keine Leseansicht mit Bearbeiten-Schritt.** Es gibt genau einen Satz Angaben und
 * keine Liste, von der aus man ihn „oeffnen" wuerde; eine Leseansicht davor waere ein Klick ohne
 * Aussage. Die Maske laedt den Satz, aendert ihn und schreibt ihn fort — kein Anlegen, kein
 * Loeschen, denn die eine Zeile gibt es von der Migration an.
 *
 * Keine Pflichtangabe: Die Angaben werden nach und nach vervollstaendigt, und wer noch keine
 * Steuernummer hat, soll trotzdem seinen Namen speichern koennen. Die Maske prueft darum nichts
 * selbst — was zu lang ist, meldet der Server feldweise zurueck (`lib/feldmeldung.ts`).
 *
 * Die Karte traegt die **eine `h1` der Ansicht** (`titelEbene={1}`): Es gibt hier keine Kopfkarte,
 * unter der sie stehen koennte, und eine Ansicht ohne `h1` liesse den Screenreader ohne Einstieg.
 *
 * Unter dem Formular steht die Karte „Briefpapier" ({@link BriefbogenKarte}, fachliche Quelle
 * #361, Punkt 11 des geklaerten Stands). Sie laedt und speichert selbst und traegt eine `h2` — die
 * `h1` bleibt die eine dieser Karte.
 *
 * „Abbrechen" ist hier ein **Schalter, kein Weg**: Ueber „Eigene Angaben" liegt keine Ebene, auf
 * die es fuehren koennte — bei der Firma ist das die Detailansicht. Es nimmt die Aenderungen
 * stattdessen auf den gespeicherten Stand zurueck.
 *
 * **Die Bankverbindung steht in drei Feldern** — Kontoinhaber, IBAN und BIC (Plan #461, E13). Ob
 * die IBAN gilt, prueft der Server beim Speichern und meldet es am Feld (E15). Solange in dem, was
 * gespeichert ist, IBAN oder Kontoinhaber fehlt, steht unter den Feldern der fruehere Freitext zum
 * Abschreiben (#460, Kriterium 11). Gemessen wird am gespeicherten Stand, nicht am getippten: Der
 * Hinweis verschwindet erst, wenn der Server die Felder angenommen hat — eine abgewiesene IBAN
 * laesst ihn stehen.
 *
 * Ohne das Recht, die Einstellungen zu aendern, stehen die Angaben ohne „Speichern" und
 * „Abbrechen" da — ReadOnly liest sie nur (Kriterium 10 von #409, Plan #414, A10, E1).
 */

const AUSFALL_LESEN =
  'Die eigenen Angaben sind gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_SPEICHERN =
  'Die Angaben wurden nicht gespeichert. Bitte später erneut versuchen.';
const GESPEICHERT = 'Die Angaben sind gespeichert.';
const AUSFALL_NEU_LADEN =
  'Der aktuelle Stand ist gerade nicht zu erreichen. Bitte später erneut versuchen.';

/** Wohin die Konfliktmeldung fuehrt, wenn es nichts neu zu laden gibt — hierher. */
const UEBERSICHT = '/eigene-angaben';

/**
 * Die Beschriftungen der vierzehn Felder — an der Maske und unter „Deine Eingaben" dieselben. Die
 * vier der Anschrift setzt {@link AnschriftFelder}; hier stehen sie nur fuer „Deine Eingaben".
 */
const BESCHRIFTUNG: Readonly<Record<keyof Werte, string>> = {
  name: 'Name',
  berufsbezeichnung: 'Berufsbezeichnung',
  strasse: 'Straße und Hausnummer',
  plz: 'Postleitzahl',
  ort: 'Ort',
  land: 'Land',
  email: 'E-Mail-Adresse',
  telefon: 'Telefon',
  webadresse: 'Webadresse',
  steuernummer: 'Steuernummer',
  umsatzsteuerId: 'Umsatzsteuer-Identifikationsnummer',
  kontoinhaber: 'Kontoinhaber',
  iban: 'IBAN',
  bic: 'BIC (optional)',
};

/** Die vierzehn Felder der Maske, jedes als Zeichenkette — leer heisst „keine Angabe". */
interface Werte {
  readonly name: string;
  readonly berufsbezeichnung: string;
  readonly strasse: string;
  readonly plz: string;
  readonly ort: string;
  readonly land: string;
  readonly email: string;
  readonly telefon: string;
  readonly webadresse: string;
  readonly steuernummer: string;
  readonly umsatzsteuerId: string;
  readonly kontoinhaber: string;
  readonly iban: string;
  readonly bic: string;
}

/** Der Stand vor dem Laden. Er wird nie angezeigt: Erst mit `bereit` steht das Formular. */
const LEERE_WERTE: Werte = {
  name: '',
  berufsbezeichnung: '',
  strasse: '',
  plz: '',
  ort: '',
  land: '',
  email: '',
  telefon: '',
  webadresse: '',
  steuernummer: '',
  umsatzsteuerId: '',
  kontoinhaber: '',
  iban: '',
  bic: '',
};

/** Was die Maske gerade weiss. */
type Stand = 'laedt' | 'bereit' | 'ausfall';

/** Eine fehlende Angabe wird zum leeren Feld — nie zu einem Platzhalter im Eingabefeld. */
function alsText(wert: string | null): string {
  return wert ?? '';
}

/**
 * Eine optionale Angabe auf dem Weg zum Server.
 *
 * Der Leerstring waere dort eine Angabe, die aus null Zeichen besteht; `null` ist „nicht
 * hinterlegt". Nur das zweite laesst ein Dokument das Feld spaeter weglassen statt es leer zu
 * drucken.
 */
function oderNull(wert: string): string | null {
  const sauber = wert.trim();
  return sauber === '' ? null : sauber;
}

/** Die Antwort der Schnittstelle als Feldwerte der Maske. */
function alsWerte(angaben: EigeneAngaben): Werte {
  return {
    name: alsText(angaben.name),
    berufsbezeichnung: alsText(angaben.berufsbezeichnung),
    strasse: alsText(angaben.strasse),
    plz: alsText(angaben.plz),
    ort: alsText(angaben.ort),
    land: alsText(angaben.land),
    email: alsText(angaben.email),
    telefon: alsText(angaben.telefon),
    webadresse: alsText(angaben.webadresse),
    steuernummer: alsText(angaben.steuernummer),
    umsatzsteuerId: alsText(angaben.umsatzsteuerId),
    kontoinhaber: alsText(angaben.kontoinhaber),
    iban: alsText(angaben.iban),
    bic: alsText(angaben.bic),
  };
}

/** Die Feldwerte als Eingabe fuer die Schnittstelle. */
function alsEingabe(werte: Werte): EigeneAngaben {
  return {
    name: oderNull(werte.name),
    berufsbezeichnung: oderNull(werte.berufsbezeichnung),
    strasse: oderNull(werte.strasse),
    plz: oderNull(werte.plz),
    ort: oderNull(werte.ort),
    land: oderNull(werte.land),
    email: oderNull(werte.email),
    telefon: oderNull(werte.telefon),
    webadresse: oderNull(werte.webadresse),
    steuernummer: oderNull(werte.steuernummer),
    umsatzsteuerId: oderNull(werte.umsatzsteuerId),
    iban: oderNull(werte.iban),
    bic: oderNull(werte.bic),
    kontoinhaber: oderNull(werte.kontoinhaber),
  };
}

/**
 * Der Hinweis auf die bisherige Bankverbindung — oder nichts, wenn es keine gibt oder IBAN und
 * Kontoinhaber gespeichert sind (#460, Kriterium 11).
 */
function bisherigeAus(bisherige: string | null, gespeichert: Werte): string | null {
  if (bisherige === null || (gespeichert.iban !== '' && gespeichert.kontoinhaber !== '')) {
    return null;
  }
  return `Bisherige Bankverbindung: ${bisherige}. Bitte in die Felder oben übernehmen.`;
}

/** Was gerade in der Maske steht, als eigene Eingaben zum Kopieren (#411, Kriterium 3). */
function eigeneEingabenAus(werte: Werte): readonly EigeneEingabe[] {
  return (Object.keys(BESCHRIFTUNG) as (keyof Werte)[]).map((feld) => ({
    bezeichnung: BESCHRIFTUNG[feld],
    wert: werte[feld],
  }));
}

interface EingabeProps {
  readonly label: string;
  readonly feld: keyof Werte;
  readonly werte: Werte;
  readonly setzeWerte: Dispatch<SetStateAction<Werte>>;
  readonly meldung?: string;
}

/** Ein Feld der Maske samt seiner Meldung — vierzehnmal derselbe Bau, einmal aufgeschrieben. */
function Eingabe({ label, feld, werte, setzeWerte, meldung }: EingabeProps) {
  return (
    <TextField
      label={label}
      value={werte[feld]}
      onChange={(ereignis) => {
        setzeWerte((alt) => ({ ...alt, [feld]: ereignis.target.value }));
      }}
      error={meldung !== undefined}
      helperText={meldung}
      fullWidth
    />
  );
}

/** „Eigene Angaben" ist die oberste Stufe ihres Bereichs — kein Weg fuehrt darueber hinaus. */
const KEIN_WEG: readonly PfadVerweis[] = [];

const TITEL = 'Eigene Angaben';

/** Was die Karte zeigt, solange das Formular nicht bereitsteht: Ladehinweis oder Meldung. */
function nichtBereit(stand: Exclude<Stand, 'bereit'>): ReactNode {
  if (stand === 'laedt') {
    return (
      <Typography
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textSchwach })}
      >
        Die eigenen Angaben werden geladen …
      </Typography>
    );
  }
  return <Alert severity="error">{AUSFALL_LESEN}</Alert>;
}

export default function EigeneAngabenMaske() {
  const { darfEinstellungenAendern } = useRechte();
  const [stand, setzeStand] = useState<Stand>('laedt');
  const [werte, setzeWerte] = useState<Werte>(LEERE_WERTE);
  // Der zuletzt gespeicherte Stand — das Ziel der Ruecknahme. Kein `null`: Solange nichts geladen
  // ist, steht kein Formular da, an dem man etwas zuruecknehmen koennte.
  const [gespeichert, setzeGespeichert] = useState<Werte>(LEERE_WERTE);
  // Der fruehere Freitext der Bankverbindung, wie der Server ihn zuletzt geliefert hat.
  const [bisherige, setzeBisherige] = useState<string | null>(null);
  const [erfolg, setzeErfolg] = useState(false);
  const [feldFehler, setzeFeldFehler] = useState<FieldErrors>({});
  const [fehler, setzeFehler] = useState<string | null>(null);
  const [laeuft, setzeLaeuft] = useState(false);
  // Der Stand, den das naechste Speichern vorlegt: gelesen, oder aus der Antwort des letzten.
  const [version, setzeVersion] = useState(0);
  // Die Abweisung eines Speicherns auf altem Stand, ungeprueft — die Konfliktmeldung liest sie.
  const [abweisung, setzeAbweisung] = useState<unknown>(null);
  // Die eigenen Werte nach „Neu laden", bis „Verwerfen" gewaehlt wird (#411, Kriterium 3).
  const [eigene, setzeEigene] = useState<readonly EigeneEingabe[] | null>(null);

  useKopfPfad(KEIN_WEG, TITEL);

  useEffect(() => {
    eigeneAngabenLesen()
      .then((angaben) => {
        const geladen = alsWerte(angaben);
        setzeWerte(geladen);
        setzeGespeichert(geladen);
        setzeBisherige(angaben.bisherigeBankverbindung);
        setzeVersion(angaben.version);
        setzeStand('bereit');
      })
      .catch(() => {
        setzeStand('ausfall');
      });
  }, []);

  const absenden = async (ereignis: FormEvent<HTMLFormElement>) => {
    ereignis.preventDefault();
    setzeFehler(null);
    setzeFeldFehler({});
    setzeErfolg(false);
    setzeAbweisung(null);
    setzeLaeuft(true);
    try {
      const gesichert = await eigeneAngabenPflegen(version, alsEingabe(werte));
      // Was gespeichert wurde, ist der neue Stand — auch fuer die Ruecknahme. Die Felder
      // uebernehmen dabei die abgeschnittene Form; sonst stuende im Feld ein Leerzeichen, das
      // der Server nicht bekommen hat.
      const neu = alsWerte(gesichert);
      setzeWerte(neu);
      setzeGespeichert(neu);
      setzeBisherige(gesichert.bisherigeBankverbindung);
      setzeVersion(gesichert.version);
      setzeErfolg(true);
    } catch (ursache) {
      const felder = feldMeldungen(ursache);
      if (istBearbeitungskonflikt(ursache)) {
        // Die Eingaben bleiben stehen; den Weg weiter zeigt die Konfliktmeldung (Kriterium 3).
        setzeAbweisung(ursache);
      } else if (Object.keys(felder).length > 0) {
        setzeFeldFehler(felder);
      } else {
        setzeFehler(AUSFALL_SPEICHERN);
      }
    } finally {
      setzeLaeuft(false);
    }
  };

  /**
   * „Neu laden" nach einer Abweisung: der Stand des Servers ins Formular, die eigenen Werte daneben
   * (#411, Kriterium 3; Plan #415, E12). Genommen wird, was in diesem Augenblick in der Maske steht
   * — auch was nach der Abweisung noch getippt wurde.
   */
  const neuLaden = async () => {
    const meine = eigeneEingabenAus(werte);
    setzeFehler(null);
    setzeLaeuft(true);
    try {
      const angaben = await eigeneAngabenLesen();
      const geladen = alsWerte(angaben);
      setzeWerte(geladen);
      setzeGespeichert(geladen);
      setzeBisherige(angaben.bisherigeBankverbindung);
      setzeVersion(angaben.version);
      setzeEigene(meine);
      setzeAbweisung(null);
      setzeFeldFehler({});
    } catch {
      setzeFehler(AUSFALL_NEU_LADEN);
    } finally {
      setzeLaeuft(false);
    }
  };

  const zuruecknehmen = () => {
    setzeWerte(gespeichert);
    setzeFeldFehler({});
    setzeFehler(null);
    setzeErfolg(false);
  };

  const hinweisBisherige = bisherigeAus(bisherige, gespeichert);

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        // Aussenabstand und Spalt bringt der Rahmen mit; hier bleibt nur der Abstand zwischen
        // den Bereichen der Buehne (CLAUDE-design.md, „Rahmen").
        gap: ABSTAND_BUEHNE,
      }}
    >
      <Karte titel={TITEL} titelEbene={1}>
        {stand === 'bereit' ? (
          <Box
            component="form"
            noValidate
            onSubmit={(ereignis: FormEvent<HTMLFormElement>) => {
              void absenden(ereignis);
            }}
            sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}
          >
            {fehler === null ? null : <Alert severity="error">{fehler}</Alert>}
            {erfolg ? <Alert severity="success">{GESPEICHERT}</Alert> : null}
            <Konfliktmeldung
              ursache={abweisung}
              onNeuLaden={() => {
                void neuLaden();
              }}
              uebersicht={UEBERSICHT}
            />
            {eigene === null ? null : (
              <EigeneEingaben
                werte={eigene}
                onVerwerfen={() => {
                  setzeEigene(null);
                }}
              />
            )}
            <Eingabe
              label={BESCHRIFTUNG.name}
              feld="name"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldungAm(feldFehler, 'name')}
            />
            <Eingabe
              label={BESCHRIFTUNG.berufsbezeichnung}
              feld="berufsbezeichnung"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldungAm(feldFehler, 'berufsbezeichnung')}
            />
            <AnschriftFelder werte={werte} setzeWerte={setzeWerte} feldFehler={feldFehler} />
            <Eingabe
              label={BESCHRIFTUNG.email}
              feld="email"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldungAm(feldFehler, 'email')}
            />
            <Eingabe
              label={BESCHRIFTUNG.telefon}
              feld="telefon"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldungAm(feldFehler, 'telefon')}
            />
            <Eingabe
              label={BESCHRIFTUNG.webadresse}
              feld="webadresse"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldungAm(feldFehler, 'webadresse')}
            />
            <Eingabe
              label={BESCHRIFTUNG.steuernummer}
              feld="steuernummer"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldungAm(feldFehler, 'steuernummer')}
            />
            <Eingabe
              label={BESCHRIFTUNG.umsatzsteuerId}
              feld="umsatzsteuerId"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldungAm(feldFehler, 'umsatzsteuerId')}
            />
            <Eingabe
              label={BESCHRIFTUNG.kontoinhaber}
              feld="kontoinhaber"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldungAm(feldFehler, 'kontoinhaber')}
            />
            <Eingabe
              label={BESCHRIFTUNG.iban}
              feld="iban"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldungAm(feldFehler, 'iban')}
            />
            <Eingabe
              label={BESCHRIFTUNG.bic}
              feld="bic"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldungAm(feldFehler, 'bic')}
            />
            {hinweisBisherige === null ? null : (
              // Ein stehender Hinweis, keine Meldung: `note` statt der Vorgabe `alert`, damit der
              // Screenreader ihn nicht bei jedem Laden ausruft.
              <Alert severity="info" role="note">
                {hinweisBisherige}
              </Alert>
            )}
            {darfEinstellungenAendern ? (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, flexWrap: 'wrap' }}>
                <KupferTaste disabled={laeuft}>Speichern</KupferTaste>
                <WeicheTaste onClick={zuruecknehmen}>Abbrechen</WeicheTaste>
              </Box>
            ) : null}
          </Box>
        ) : (
          nichtBereit(stand)
        )}
      </Karte>
      <BriefbogenKarte />
    </Box>
  );
}
