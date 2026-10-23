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
import EigeneEingaben from '../components/EigeneEingaben';
import type { EigeneEingabe } from '../components/EigeneEingaben';
import Karte from '../components/Karte';
import Konfliktmeldung from '../components/Konfliktmeldung';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import { useRechte } from '../auth/rechte';
import KupferTaste from '../components/KupferTaste';
import WeicheTaste from '../components/WeicheTaste';
import { feldMeldungen, istBearbeitungskonflikt } from '../lib/apifehler';
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
 *
 * Ohne das Recht, die Einstellungen zu aendern, stehen die Werte ohne „Speichern" und „Abbrechen"
 * da — ReadOnly liest sie nur (Kriterium 10 von #409, Plan #414, A10).
 *
 * **Speichern legt den Stand vor** (#411, Kriterien 1 bis 3; Plan #415, E2, E12): die Version des
 * zuletzt gelesenen oder gespeicherten Stands. Hat inzwischen jemand anderes gespeichert, sagt
 * {@link Konfliktmeldung}, wer wann; die eigenen Eingaben bleiben im Formular. „Neu laden" holt den
 * Stand des Servers und stellt die eigenen Werte daneben in {@link EigeneEingaben}, bis
 * „Verwerfen" gewaehlt wird. Die Einstellungen werden nie geloescht; der Weg zur Uebersicht fuehrt
 * darum hierher zurueck.
 */

const AUSFALL_LESEN =
  'Die Einstellungen zur Rechnung sind gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_SPEICHERN =
  'Die Einstellungen wurden nicht gespeichert. Bitte später erneut versuchen.';
const GESPEICHERT = 'Die Einstellungen sind gespeichert.';
const AUSFALL_NEU_LADEN =
  'Der aktuelle Stand ist gerade nicht zu erreichen. Bitte später erneut versuchen.';

/** Wohin die Konfliktmeldung fuehrt, wenn es nichts neu zu laden gibt — hierher. */
const UEBERSICHT = '/administration';

/** Die Beschriftungen der vier Felder — an der Maske und unter „Deine Eingaben" dieselben. */
const BESCHRIFTUNG = {
  nummerMuster: 'Muster der Rechnungsnummer',
  naechsteNummer: 'Nächste laufende Nummer',
  steuersatz: 'Mehrwertsteuersatz in Prozent',
  zahlungszielTage: 'Zahlungsziel in Tagen',
} as const;

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

/** Was gerade in der Maske steht, als eigene Eingaben zum Kopieren (#411, Kriterium 3). */
function eigeneEingabenAus(werte: Werte): readonly EigeneEingabe[] {
  return [
    { bezeichnung: BESCHRIFTUNG.nummerMuster, wert: werte.nummerMuster },
    { bezeichnung: BESCHRIFTUNG.naechsteNummer, wert: werte.naechsteNummer },
    { bezeichnung: BESCHRIFTUNG.steuersatz, wert: werte.steuersatz },
    { bezeichnung: BESCHRIFTUNG.zahlungszielTage, wert: werte.zahlungszielTage },
  ];
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
  const { darfEinstellungenAendern } = useRechte();
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
  // Der Stand, den das naechste Speichern vorlegt: gelesen, oder aus der Antwort des letzten.
  const [version, setzeVersion] = useState(0);
  // Die Abweisung eines Speicherns auf altem Stand, ungeprueft — die Konfliktmeldung liest sie.
  const [abweisung, setzeAbweisung] = useState<unknown>(null);
  // Die eigenen Werte nach „Neu laden", bis „Verwerfen" gewaehlt wird (#411, Kriterium 3).
  const [eigene, setzeEigene] = useState<readonly EigeneEingabe[] | null>(null);

  useKopfPfad(KEIN_WEG, TITEL);

  useEffect(() => {
    rechnungseinstellungenLesen()
      .then((einstellungen) => {
        const geladen = alsWerte(einstellungen);
        setzeWerte(geladen);
        setzeGespeichert(geladen);
        setzeVersion(einstellungen.version);
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
    setzeAbweisung(null);
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
      const gesichert = await rechnungseinstellungenPflegen(version, geprueft.einstellungen);
      // Was gespeichert wurde, ist der neue Stand — auch fuer die Ruecknahme. Die Felder
      // uebernehmen dabei die gerundete Form: „19,5" wird zu „19,50", so wie der Server sie
      // zurueckgibt.
      const neu = alsWerte(gesichert);
      setzeWerte(neu);
      setzeGespeichert(neu);
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
      const einstellungen = await rechnungseinstellungenLesen();
      const geladen = alsWerte(einstellungen);
      setzeWerte(geladen);
      setzeGespeichert(geladen);
      setzeVersion(einstellungen.version);
      setzeEigene(meine);
      setzeAbweisung(null);
      setzeEigeneFehler({});
      setzeFeldFehler({});
    } catch {
      setzeFehler(AUSFALL_NEU_LADEN);
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
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.75 }}>
              <TextField
                label={BESCHRIFTUNG.nummerMuster}
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
              label={BESCHRIFTUNG.naechsteNummer}
              value={werte.naechsteNummer}
              onChange={aendere('naechsteNummer')}
              error={meldung('naechsteNummer') !== undefined}
              helperText={meldung('naechsteNummer')}
              fullWidth
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <TextField
              label={BESCHRIFTUNG.steuersatz}
              value={werte.steuersatz}
              onChange={aendere('steuersatz')}
              error={meldung('steuersatz') !== undefined}
              helperText={meldung('steuersatz')}
              fullWidth
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <TextField
              label={BESCHRIFTUNG.zahlungszielTage}
              value={werte.zahlungszielTage}
              onChange={aendere('zahlungszielTage')}
              error={meldung('zahlungszielTage') !== undefined}
              helperText={meldung('zahlungszielTage')}
              fullWidth
              slotProps={{ inputLabel: { shrink: true } }}
            />
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
    </Box>
  );
}
