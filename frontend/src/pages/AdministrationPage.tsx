import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import type { FormEvent, ReactNode } from 'react';

import type { FieldErrors } from '../api/client';
import {
  rechnungseinstellungenLesen,
  rechnungseinstellungenPflegen,
} from '../api/rechnungseinstellungen';
import type { Rechnungseinstellungen } from '../api/rechnungseinstellungen';
import Karte from '../components/Karte';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import KupferTaste from '../components/KupferTaste';
import WeicheTaste from '../components/WeicheTaste';
import { feldMeldungen } from '../lib/apifehler';
import { meldungAm } from '../lib/feldmeldung';
import { dezimal, hundertstel } from '../lib/geld';
import { istGueltigesMuster, rechnungsnummer } from '../lib/nummernmuster';
import { ABSTAND_BUEHNE } from '../theme';

/**
 * Die Seite „Administration" unter `/administration` mit ihrem Bereich „Rechnung" (#159,
 * Kriterien 1 bis 11; Plan #161, E7).
 *
 * **Ein Formular, keine Leseansicht mit Bearbeiten-Schritt** — derselbe Bau wie
 * {@link EigeneAngabenMaske}: Es gibt genau einen Satz Einstellungen, und die eine Zeile gibt es
 * von der Migration an. Geladen, geaendert, fortgeschrieben; kein Anlegen, kein Loeschen.
 *
 * Die `h1` traegt die **Seite** und nicht die Karte: Unter „Administration" wird es mehr als einen
 * Bereich geben, und „Rechnung" ist einer davon. Eine Karte mit `titelEbene={1}` haette den
 * ersten Bereich zur ganzen Seite erklaert, und der zweite stuende dann auf derselben Ebene wie
 * die Ansicht.
 *
 * **Die Werte stehen als Zeichenketten im Zustand**, nicht als Zahlen. Wer „19," getippt hat, ist
 * mitten in einer Eingabe und nicht bei einem ungueltigen Wert; eine Umwandlung bei jedem
 * Tastendruck naehme ihm das Komma wieder weg. Gerechnet wird erst beim Absenden — und fuer die
 * Vorschau, die dabei auch scheitern darf.
 *
 * **Die Vorpruefung nimmt dem Server nichts ab.** Sie haelt nur zurueck, was er ohnehin abwiese,
 * damit die Meldung sofort am Feld steht (#159, Kriterium 10); entschieden wird im Backend
 * (`RechnungseinstellungenRequest`), und seine Feldmeldungen erscheinen an denselben Feldern.
 *
 * Die Regeln selbst sind geliehen und nicht abgeschrieben: das Muster aus `lib/nummernmuster.ts`,
 * die Dezimalzahl aus `lib/geld.ts`. Eine zweite Regel fuer Komma-Zahlen liefe beim ersten
 * Nachziehen auseinander.
 *
 * „Abbrechen" ist hier ein **Schalter, kein Weg**: Ueber „Administration" liegt keine Ebene, auf
 * die es fuehren koennte. Es nimmt die Aenderungen stattdessen auf den gespeicherten Stand zurueck.
 */

const AUSFALL_LESEN =
  'Die Einstellungen zur Rechnung sind gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_SPEICHERN =
  'Die Einstellungen wurden nicht gespeichert. Bitte später erneut versuchen.';
const GESPEICHERT = 'Die Einstellungen sind gespeichert.';

const HILFE_MUSTER =
  'Platzhalter: {NNNN} für die laufende Nummer — die Zahl der N ist die Mindestbreite —, ' +
  '{JJJJ} für das vierstellige und {JJ} für das zweistellige Jahr. ' +
  'Beispiel: {NNNN}-{JJJJ} ergibt 0003-2026.';

const OHNE_VORSCHAU = 'Keine Vorschau: Muster oder nächste Nummer sind nicht gültig.';

const MUSTER_FEHLT =
  'Das Muster braucht genau einen Platzhalter für die laufende Nummer, höchstens einen für das ' +
  'Jahr und sonst nur Buchstaben, Ziffern und die Zeichen - _ / . — höchstens 50 Zeichen.';
const NUMMER_FEHLT = 'Die nächste Nummer ist eine ganze Zahl ab 1.';
const STEUER_FEHLT =
  'Der Steuersatz ist eine Zahl von 0 bis 100 mit höchstens zwei Nachkommastellen.';
const ZIEL_FEHLT = 'Das Zahlungsziel ist eine ganze Zahl von Tagen ab 0.';

/** Hundert Prozent in Hundertstel-Prozent — die obere Grenze des Steuersatzes. */
const STEUER_MAX = 10_000;

/** Eine ganze Zahl ohne Vorzeichen; alles andere ist keine. */
const GANZE_ZAHL = /^\d+$/u;

/** Die vier Felder der Karte, jedes als Zeichenkette — so, wie sie im Feld stehen. */
interface Werte {
  readonly nummerMuster: string;
  readonly naechsteNummer: string;
  readonly steuersatz: string;
  readonly zahlungszielTage: string;
}

/** Der Stand vor dem Laden. Er wird nie angezeigt: Erst mit `bereit` steht das Formular. */
const LEERE_WERTE: Werte = {
  nummerMuster: '',
  naechsteNummer: '',
  steuersatz: '',
  zahlungszielTage: '',
};

/** Was die Seite gerade weiss. */
type Stand = 'laedt' | 'bereit' | 'ausfall';

/** Die Antwort der Schnittstelle als Feldwerte. */
function alsWerte(einstellungen: Rechnungseinstellungen): Werte {
  return {
    nummerMuster: einstellungen.nummerMuster,
    naechsteNummer: String(einstellungen.naechsteNummer),
    // Mit Komma, weil das Feld deutsch gelesen wird; der Punkt entsteht erst an der Systemgrenze.
    steuersatz: dezimal(einstellungen.steuersatzInHundertsteln, ','),
    zahlungszielTage: String(einstellungen.zahlungszielTage),
  };
}

/** Eine ganze Zahl aus einem Feld — `null`, wenn dort keine steht. */
function ganzeZahl(wert: string): number | null {
  const sauber = wert.trim();
  return GANZE_ZAHL.test(sauber) ? Number(sauber) : null;
}

/**
 * Das Ergebnis der Vorpruefung: entweder die vier Werte in ihrer Einheit oder die Meldungen.
 *
 * Zwei Faelle statt eines Wertes mit `null`-Feldern: So gibt es keine Stelle, an der ein gepruefter
 * Wert noch einmal ausgepackt werden muesste — und kein `!`, das die Pruefung wieder aufgaebe.
 */
type Pruefung =
  | { readonly art: 'gueltig'; readonly einstellungen: Rechnungseinstellungen }
  | { readonly art: 'fehler'; readonly fehler: FieldErrors };

/** Prueft alle vier Felder auf einmal — der Benutzer soll nicht Feld fuer Feld erfahren, was fehlt. */
function pruefe(werte: Werte): Pruefung {
  const fehler: Record<string, readonly string[]> = {};
  const nummer = ganzeZahl(werte.naechsteNummer);
  const steuer = hundertstel(werte.steuersatz);
  const ziel = ganzeZahl(werte.zahlungszielTage);
  if (!istGueltigesMuster(werte.nummerMuster)) {
    fehler.nummerMuster = [MUSTER_FEHLT];
  }
  if (nummer === null || nummer < 1) {
    fehler.naechsteNummer = [NUMMER_FEHLT];
  }
  if (steuer === null || steuer > STEUER_MAX) {
    fehler.steuersatz = [STEUER_FEHLT];
  }
  if (ziel === null) {
    fehler.zahlungszielTage = [ZIEL_FEHLT];
  }
  if (nummer === null || steuer === null || ziel === null || Object.keys(fehler).length > 0) {
    return { art: 'fehler', fehler };
  }
  return {
    art: 'gueltig',
    einstellungen: {
      nummerMuster: werte.nummerMuster,
      naechsteNummer: nummer,
      steuersatzInHundertsteln: steuer,
      zahlungszielTage: ziel,
    },
  };
}

/**
 * Die naechste Rechnungsnummer, wie sie mit diesen Eingaben lauten wuerde — `null`, wenn sich aus
 * ihnen keine bilden laesst.
 *
 * Gerechnet wird mit dem **heutigen** Jahr: Die Vorschau nimmt den heutigen Tag als Rechnungsdatum
 * an (#159, Kriterium 6).
 */
function vorschauZu(muster: string, nummerText: string, jahr: number): string | null {
  const nummer = ganzeZahl(nummerText);
  if (nummer === null || nummer < 1) {
    return null;
  }
  return rechnungsnummer(muster, nummer, jahr);
}

/** „Administration" ist die oberste Stufe ihres Bereichs — kein Weg fuehrt darueber hinaus. */
const KEIN_WEG: readonly PfadVerweis[] = [];

const TITEL = 'Administration';

/** Was die Karte zeigt, solange das Formular nicht bereitsteht: Ladehinweis oder Meldung. */
function nichtBereit(stand: Exclude<Stand, 'bereit'>): ReactNode {
  if (stand === 'laedt') {
    return (
      <Typography
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textSchwach })}
      >
        Die Einstellungen werden geladen …
      </Typography>
    );
  }
  return <Alert severity="error">{AUSFALL_LESEN}</Alert>;
}

export default function AdministrationPage() {
  const [stand, setzeStand] = useState<Stand>('laedt');
  const [werte, setzeWerte] = useState<Werte>(LEERE_WERTE);
  // Der zuletzt gespeicherte Stand — das Ziel der Ruecknahme. Kein `null`: Solange nichts geladen
  // ist, steht kein Formular da, an dem man etwas zuruecknehmen koennte.
  const [gespeichert, setzeGespeichert] = useState<Werte>(LEERE_WERTE);
  const [erfolg, setzeErfolg] = useState(false);
  const [eigeneFehler, setzeEigeneFehler] = useState<FieldErrors>({});
  const [feldFehler, setzeFeldFehler] = useState<FieldErrors>({});
  const [fehler, setzeFehler] = useState<string | null>(null);
  const [laeuft, setzeLaeuft] = useState(false);

  useKopfPfad(KEIN_WEG, TITEL);

  useEffect(() => {
    rechnungseinstellungenLesen()
      .then((einstellungen) => {
        const geladen = alsWerte(einstellungen);
        setzeWerte(geladen);
        setzeGespeichert(geladen);
        setzeStand('bereit');
      })
      .catch(() => {
        setzeStand('ausfall');
      });
  }, []);

  const absenden = async () => {
    setzeFehler(null);
    setzeFeldFehler({});
    setzeErfolg(false);
    const geprueft = pruefe(werte);
    if (geprueft.art === 'fehler') {
      // Was nicht stimmt, steht am Feld; hinaus geht nichts, und die uebrigen Eingaben bleiben
      // stehen (#159, Kriterium 10).
      setzeEigeneFehler(geprueft.fehler);
      return;
    }
    setzeEigeneFehler({});
    setzeLaeuft(true);
    try {
      await rechnungseinstellungenPflegen(geprueft.einstellungen);
      // Was gespeichert wurde, ist der neue Stand — auch fuer die Ruecknahme. Die Felder
      // uebernehmen dabei die gerundete Form: „19,5" wird zu „19,50", so wie der Server sie
      // zurueckgaebe.
      const neu = alsWerte(geprueft.einstellungen);
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
    setzeEigeneFehler({});
    setzeFeldFehler({});
    setzeFehler(null);
    setzeErfolg(false);
  };

  // Die eigene Vorpruefung steht vor der Meldung des Servers: Sie ist die juengere Aussage ueber
  // dasselbe Feld.
  const meldung = (feld: string) =>
    meldungAm(eigeneFehler, feld) ?? meldungAm(feldFehler, feld);

  const aendere = (feld: keyof Werte) => (ereignis: { target: { value: string } }) => {
    const wert = ereignis.target.value;
    setzeWerte((alt) => ({ ...alt, [feld]: wert }));
  };

  const musterMeldung = meldung('nummerMuster');
  const vorschau = vorschauZu(
    werte.nummerMuster,
    werte.naechsteNummer,
    new Date().getFullYear(),
  );

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
      <Typography variant="h1" component="h1">
        {TITEL}
      </Typography>
      <Karte titel="Rechnung">
        {stand === 'bereit' ? (
          <Box
            component="form"
            noValidate
            onSubmit={(ereignis: FormEvent<HTMLFormElement>) => {
              ereignis.preventDefault();
              void absenden();
            }}
            sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}
          >
            {fehler === null ? null : <Alert severity="error">{fehler}</Alert>}
            {erfolg ? <Alert severity="success">{GESPEICHERT}</Alert> : null}
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.75 }}>
              <TextField
                label="Muster der Rechnungsnummer"
                value={werte.nummerMuster}
                onChange={aendere('nummerMuster')}
                error={musterMeldung !== undefined}
                // Der Hilfetext bleibt neben der Meldung stehen: Wer ein ungueltiges Muster
                // getippt hat, braucht die Schreibweise gerade jetzt (#159, Kriterium 4).
                helperText={
                  musterMeldung === undefined
                    ? HILFE_MUSTER
                    : `${musterMeldung} ${HILFE_MUSTER}`
                }
                fullWidth
                slotProps={{ inputLabel: { shrink: true } }}
              />
              <Typography
                data-testid="nummer-vorschau"
                sx={(theme) => ({
                  fontSize: 12.5,
                  color: theme.vars.palette.kupferwolke.textMatt,
                })}
              >
                {vorschau === null
                  ? OHNE_VORSCHAU
                  : `Nächste Rechnungsnummer: ${vorschau}`}
              </Typography>
            </Box>
            <TextField
              label="Nächste laufende Nummer"
              value={werte.naechsteNummer}
              onChange={aendere('naechsteNummer')}
              error={meldung('naechsteNummer') !== undefined}
              helperText={meldung('naechsteNummer')}
              fullWidth
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <TextField
              label="Mehrwertsteuersatz in Prozent"
              value={werte.steuersatz}
              onChange={aendere('steuersatz')}
              error={meldung('steuersatz') !== undefined}
              helperText={meldung('steuersatz')}
              fullWidth
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <TextField
              label="Zahlungsziel in Tagen"
              value={werte.zahlungszielTage}
              onChange={aendere('zahlungszielTage')}
              error={meldung('zahlungszielTage') !== undefined}
              helperText={meldung('zahlungszielTage')}
              fullWidth
              slotProps={{ inputLabel: { shrink: true } }}
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
