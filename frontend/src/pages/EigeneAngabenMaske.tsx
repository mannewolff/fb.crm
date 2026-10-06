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
import Karte from '../components/Karte';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import KupferTaste from '../components/KupferTaste';
import WeicheTaste from '../components/WeicheTaste';
import { feldMeldungen } from '../lib/apifehler';
import { meldungAm } from '../lib/feldmeldung';

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
 * „Abbrechen" ist hier ein **Schalter, kein Weg**: Ueber „Eigene Angaben" liegt keine Ebene, auf
 * die es fuehren koennte — bei der Firma ist das die Detailansicht. Es nimmt die Aenderungen
 * stattdessen auf den gespeicherten Stand zurueck.
 */

const AUSFALL_LESEN =
  'Die eigenen Angaben sind gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_SPEICHERN =
  'Die Angaben wurden nicht gespeichert. Bitte später erneut versuchen.';
const GESPEICHERT = 'Die Angaben sind gespeichert.';

/** Die zwoelf Felder der Maske, jedes als Zeichenkette — leer heisst „keine Angabe". */
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
  readonly bankverbindung: string;
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
  bankverbindung: '',
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
    bankverbindung: alsText(angaben.bankverbindung),
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
    bankverbindung: oderNull(werte.bankverbindung),
  };
}

interface EingabeProps {
  readonly label: string;
  readonly feld: keyof Werte;
  readonly werte: Werte;
  readonly setzeWerte: Dispatch<SetStateAction<Werte>>;
  readonly meldung?: string;
}

/** Ein Feld der Maske samt seiner Meldung — zwoelfmal derselbe Bau, einmal aufgeschrieben. */
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
  const [stand, setzeStand] = useState<Stand>('laedt');
  const [werte, setzeWerte] = useState<Werte>(LEERE_WERTE);
  // Der zuletzt gespeicherte Stand — das Ziel der Ruecknahme. Kein `null`: Solange nichts geladen
  // ist, steht kein Formular da, an dem man etwas zuruecknehmen koennte.
  const [gespeichert, setzeGespeichert] = useState<Werte>(LEERE_WERTE);
  const [erfolg, setzeErfolg] = useState(false);
  const [feldFehler, setzeFeldFehler] = useState<FieldErrors>({});
  const [fehler, setzeFehler] = useState<string | null>(null);
  const [laeuft, setzeLaeuft] = useState(false);

  useKopfPfad(KEIN_WEG, TITEL);

  useEffect(() => {
    eigeneAngabenLesen()
      .then((angaben) => {
        const geladen = alsWerte(angaben);
        setzeWerte(geladen);
        setzeGespeichert(geladen);
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
    setzeLaeuft(true);
    const eingabe = alsEingabe(werte);
    try {
      await eigeneAngabenPflegen(eingabe);
      // Was gespeichert wurde, ist der neue Stand — auch fuer die Ruecknahme. Die Felder
      // uebernehmen dabei die abgeschnittene Form; sonst stuende im Feld ein Leerzeichen, das
      // der Server nicht bekommen hat.
      const neu = alsWerte(eingabe);
      setzeWerte(neu);
      setzeGespeichert(neu);
      setzeErfolg(true);
    } catch (ursache) {
      const felder = feldMeldungen(ursache);
      if (Object.keys(felder).length > 0) {
        setzeFeldFehler(felder);
      } else {
        setzeFehler(AUSFALL_SPEICHERN);
      }
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

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        // Aussenabstand und Spalt bringt der Rahmen mit; hier bleibt nur der Abstand zwischen
        // den Bereichen der Buehne (CLAUDE-design.md, „Rahmen").
        gap: '22px',
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
            <Eingabe
              label="Name"
              feld="name"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldungAm(feldFehler, 'name')}
            />
            <Eingabe
              label="Berufsbezeichnung"
              feld="berufsbezeichnung"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldungAm(feldFehler, 'berufsbezeichnung')}
            />
            <AnschriftFelder werte={werte} setzeWerte={setzeWerte} feldFehler={feldFehler} />
            <Eingabe
              label="E-Mail-Adresse"
              feld="email"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldungAm(feldFehler, 'email')}
            />
            <Eingabe
              label="Telefon"
              feld="telefon"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldungAm(feldFehler, 'telefon')}
            />
            <Eingabe
              label="Webadresse"
              feld="webadresse"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldungAm(feldFehler, 'webadresse')}
            />
            <Eingabe
              label="Steuernummer"
              feld="steuernummer"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldungAm(feldFehler, 'steuernummer')}
            />
            <Eingabe
              label="Umsatzsteuer-Identifikationsnummer"
              feld="umsatzsteuerId"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldungAm(feldFehler, 'umsatzsteuerId')}
            />
            <Eingabe
              label="Bankverbindung"
              feld="bankverbindung"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldungAm(feldFehler, 'bankverbindung')}
            />
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, flexWrap: 'wrap' }}>
              <KupferTaste disabled={laeuft}>Speichern</KupferTaste>
              <WeicheTaste onClick={zuruecknehmen}>Abbrechen</WeicheTaste>
            </Box>
          </Box>
        ) : (
          nichtBereit(stand)
        )}
      </Karte>
    </Box>
  );
}
