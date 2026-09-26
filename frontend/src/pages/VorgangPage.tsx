import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';
import { IconCircleCheck, IconPencil, IconPointFilled } from '@tabler/icons-react';
import { Fragment, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';

import { vorgangAbschliessen, vorgangLesen, vorgangWiederEroeffnen } from '../api/vorgaenge';
import type { Phase, Vorgang, Zuordnung } from '../api/vorgaenge';
import EintragMaske from '../components/EintragMaske';
import Historie from '../components/Historie';
import Karte from '../components/Karte';
import Kopfkarte from '../components/Kopfkarte';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import KupferTaste from '../components/KupferTaste';
import WeicheTaste from '../components/WeicheTaste';
import ZustandsChip from '../components/ZustandsChip';
import { nichtGefunden } from '../lib/apifehler';
import { kennungAus } from '../lib/kennung';
import { ZAHLEN_KLASSE } from '../theme';

/**
 * Die Detailansicht eines Vorgangs (Kriterien 9, 11, 20, 21, 23, 25, 26) in der Kupferwolke.
 *
 * Oben die {@link Kopfkarte} mit dem Mal der Firma, `#Nummer Titel` als der einen `h1`, der Zeile
 * „Firma · Ansprechpartner" und den Chips fuer Phase und Abschluss; rechts die Aktionen. Darunter
 * links die Maske fuer Eintraege und die Historie als Zeitleiste, rechts die Karte „Felder".
 * Unterhalb von 1200 px liegen beide Spalten uebereinander.
 *
 * <b>Die Wege zur Firma stehen in der Karte „Felder", nicht in der Zeile der Kopfkarte.</b> Die
 * Zeile nennt Firma und Ansprechpartner als Text; derselbe Weg zweimal auf einer Seite waere fuer
 * den Screenreader zwei Ziele mit demselben Namen, und wer die Links durchgeht, muesste beide
 * pruefen, um zu merken, dass sie dasselbe sind. Stillgelegtes sagt die Zeile darum als Zusatz am
 * Namen, die Felder als Schild neben dem Weg (Kriterien 23, 26).
 *
 * Fuenf Zusagen tragen die Ansicht:
 *
 * <ul>
 *   <li><b>Nach dem Schalten wird neu gelesen</b> (Kriterium 20), statt den Stand selbst
 *       umzuschalten. Der Server ist die Quelle der Wahrheit — und nur so sieht der Benutzer, was
 *       dort tatsaechlich steht.</li>
 *   <li><b>Der abgeschlossene Vorgang bleibt vollstaendig</b> (Kriterium 21): Phase, Zuordnung und
 *       Historie stehen weiter da, und „Wieder oeffnen" nimmt den Abschluss zurueck. Abgeschlossen
 *       heisst „zu Ende gegangen", nicht „gesperrt" — es traegt darum <b>keine</b> Kupfertaste:
 *       Am abgeschlossenen Vorgang ist nichts mehr die eine Hauptaktion.</li>
 *   <li><b>Eine stillgelegte Zuordnung bleibt sichtbar und wird angesagt</b> (Kriterien 23, 26):
 *       Das Schild steht als Wort in der Zeile, und der Stand gehoert zum Namen des Weges — wer mit
 *       dem Screenreader durch die Wege springt, hoert ihn ohne die Nachbarschaft.</li>
 *   <li><b>Nach dem Hinzufuegen und nach dem Aendern eines Eintrags wird neu gelesen</b> (E20,
 *       Kriterien 13, 14, 19). Die Maske steht als Karte ueber der Historie, das Aendern im
 *       Eintrag selbst; beide melden nur, dass etwas geschrieben wurde — wie die Liste danach
 *       aussieht, sagt der Server, nicht die Oberflaeche. Auch am abgeschlossenen Vorgang steht
 *       sie da, und das Backend nimmt dort weiter Eintraege an.</li>
 *   <li><b>„Bearbeiten" steht auch am abgeschlossenen Vorgang</b> (Kriterium 10): Titel und
 *       Zuordnung sind dort weiter aenderbar. Es ist ein Weg und keine Schaltflaeche — die
 *       {@link WeicheTaste} fuehrt mit `to` auf `/vorgaenge/:id/bearbeiten`, also liegt sie als
 *       Link im Tabulatorweg und nicht als Knopf mit `onClick`.</li>
 * </ul>
 */

const NICHT_GEFUNDEN = 'Diesen Vorgang gibt es nicht.';
const AUSFALL = 'Der Vorgang ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const SCHALTEN_FEHLT =
  'Der Abschlussstand des Vorgangs wurde nicht geändert. Bitte später erneut versuchen.';
const LAEDT = 'Der Vorgang wird geladen …';

/** Die Phase als Wort. Heute kennt das Backend genau eine (Kriterium 11). */
const PHASE_TEXT: Readonly<Record<Phase, string>> = { ANBAHNUNG: 'Anbahnung' };

/** Die Symbolgroessen: 13 px im Chip, 16 px in den Tasten (wie in {@link FirmaPage}). */
const SYMBOL_CHIP = 13;
const SYMBOL_TASTE = 16;

/** Was die Ansicht gerade weiss. */
type Stand =
  | { readonly art: 'laedt' }
  | { readonly art: 'daten'; readonly vorgang: Vorgang }
  | { readonly art: 'unbekannt' }
  | { readonly art: 'ausfall' };

/**
 * Das Schild einer stillgelegten Zuordnung.
 *
 * Kein {@link ZustandsChip}: Es steht in einer Zeile neben einem Weg und nicht als Zustand des
 * ganzen Objekts — dieselbe Form wie in {@link FirmaPage}. Der Stand steht als Wort da, nicht nur
 * als Farbe (CLAUDE-react.md, Accessibility).
 */
function Schild({ text }: { readonly text: string }) {
  return (
    <Box
      component="span"
      sx={(theme) => ({
        flex: 'none',
        fontSize: 10,
        fontWeight: 500,
        padding: '1px 6px',
        borderRadius: '5px',
        color: theme.vars.palette.kupferwolke.melder.grau,
        border: '1px solid currentColor',
        background: 'color-mix(in srgb, currentColor 13%, transparent)',
      })}
    >
      {text}
    </Box>
  );
}

/**
 * Der Weg zur Detailansicht der Firma (Kriterium 9).
 *
 * Firma und Ansprechpartner fuehren beide dorthin: Der Ansprechpartner hat keine eigene Ansicht —
 * er steht in der Liste der Firma. Sein Stand gehoert in den Namen des Weges, damit der
 * Screenreader ihn mit dem Weg vorliest (Kriterien 23, 26).
 */
function Weg({
  zuordnung,
  firmaId,
}: {
  readonly zuordnung: Zuordnung;
  readonly firmaId: number;
}) {
  return (
    <>
      <Link
        component={RouterLink}
        to={`/firmen/${String(firmaId)}`}
        aria-label={zuordnung.aktiv ? undefined : `${zuordnung.name} (stillgelegt)`}
        underline="hover"
        sx={{ fontSize: 13.5, fontWeight: 500 }}
      >
        {zuordnung.name}
      </Link>
      {zuordnung.aktiv ? null : <Schild text="stillgelegt" />}
    </>
  );
}

/** Der Name einer Zuordnung fuer die Textzeile der Kopfkarte — stillgelegtes sagt der Zusatz. */
function nameMitStand(zuordnung: Zuordnung): string {
  return zuordnung.aktiv ? zuordnung.name : `${zuordnung.name} (stillgelegt)`;
}

/** Die Zeile der Kopfkarte: „Firma · Ansprechpartner", ohne Ansprechpartner nur die Firma. */
function zuordnungsZeile(vorgang: Vorgang): ReactNode {
  const teile = [vorgang.firma, vorgang.ansprechpartner]
    .filter((teil): teil is Zuordnung => teil !== null)
    .map(nameMitStand);
  return <Box component="span">{teile.join(' · ')}</Box>;
}

/**
 * Die Chips der Kopfkarte: die Phase auf Himmel, der Abschluss auf Salbei.
 *
 * Himmel heisst „laufend, Information", Salbei „erfolgreich, zu Ende gegangen"
 * (CLAUDE-design.md, „Toenungen"). Am offenen Vorgang steht nur die Phase: Ein Chip „Offen" waere
 * die Abwesenheit des anderen noch einmal.
 */
function chipsZu(vorgang: Vorgang): ReactNode {
  return (
    <>
      <ZustandsChip
        wort={PHASE_TEXT[vorgang.phase]}
        toenung="himmel"
        symbol={<IconPointFilled size={SYMBOL_CHIP} />}
      />
      {vorgang.abgeschlossen ? (
        <ZustandsChip
          wort="Abgeschlossen"
          toenung="salbei"
          symbol={<IconCircleCheck size={SYMBOL_CHIP} stroke={1.8} />}
        />
      ) : null}
    </>
  );
}

/**
 * Die Felder des Vorgangs als Stammdaten-Liste (Vorlage `.stamm` Z. 100–102).
 *
 * Kein Platzhalter, wo keine Zuordnung steht: Ein „—" waere eine Zeile, die der Screenreader
 * vorliest, ohne dass sie etwas sagt (Kriterium 9).
 */
function Felderkarte({ vorgang }: { readonly vorgang: Vorgang }) {
  const zeilen: readonly { name: string; wert: ReactNode }[] = [
    { name: 'Firma', wert: <Weg zuordnung={vorgang.firma} firmaId={vorgang.firma.id} /> },
    ...(vorgang.ansprechpartner === null
      ? []
      : [
          {
            name: 'Ansprechpartner',
            wert: <Weg zuordnung={vorgang.ansprechpartner} firmaId={vorgang.firma.id} />,
          },
        ]),
  ];
  return (
    <Karte titel="Felder">
      <Box
        component="dl"
        data-testid="vorgang-felder"
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: 'auto minmax(0, 1fr)' },
          gap: '10px 20px',
          margin: 0,
          fontSize: 13.5,
        }}
      >
        {zeilen.map((zeile) => (
          <Fragment key={zeile.name}>
            <Box
              component="dt"
              sx={(theme) => ({ color: theme.vars.palette.kupferwolke.textSchwach })}
            >
              {zeile.name}
            </Box>
            <Box
              component="dd"
              sx={{ margin: 0, display: 'flex', alignItems: 'center', gap: '7px', flexWrap: 'wrap' }}
            >
              {zeile.wert}
            </Box>
          </Fragment>
        ))}
      </Box>
    </Karte>
  );
}

/** Ueber jeder Vorgangs-Ansicht steht die Uebersicht (E6). */
const ZU_VORGAENGEN: readonly PfadVerweis[] = [{ titel: 'Vorgänge', ziel: '/vorgaenge' }];

export default function VorgangPage() {
  const { id } = useParams();
  const kennung = kennungAus(id);
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });
  const [schaltFehler, setzeSchaltFehler] = useState<string | null>(null);
  const [schaltet, setzeSchaltet] = useState(false);
  // Solange der Vorgang nicht gelesen ist, traegt die Endstufe das Wort „Vorgang": Ein Pfad, der
  // erst spaeter erscheint, liesse den Kopf bei jedem Aufruf einmal springen.
  useKopfPfad(
    ZU_VORGAENGEN,
    stand.art === 'daten' ? `#${String(stand.vorgang.nummer)} ${stand.vorgang.titel}` : 'Vorgang',
  );
  /**
   * Zaehlt die Anlaesse zum Neulesen.
   *
   * Ein Zaehler und kein eigener Ladepfad neben dem Effekt: So geht das Neulesen nach einem
   * Eintrag denselben Weg wie das erste Lesen — samt seiner Behandlung von „gibt es nicht" und
   * „nicht zu erreichen". Zwei Wege auf dieselben Daten liefen frueher oder spaeter auseinander.
   */
  const [runde, setzeRunde] = useState(0);

  /** Der eine Anlass zum Neulesen — die Maske ueber der Historie und der Eintrag darin teilen ihn. */
  const neuLesen = () => {
    setzeRunde((bisher) => bisher + 1);
  };

  useEffect(() => {
    if (kennung === null) {
      // Eine Kennung, die keine ist, geht gar nicht erst ans Netz (kennung.ts).
      setzeStand({ art: 'unbekannt' });
      return;
    }
    vorgangLesen(kennung)
      .then((vorgang) => {
        setzeStand({ art: 'daten', vorgang });
      })
      .catch((ursache: unknown) => {
        setzeStand(nichtGefunden(ursache) ? { art: 'unbekannt' } : { art: 'ausfall' });
      });
  }, [kennung, runde]);

  const schalten = async (vorgang: Vorgang) => {
    setzeSchaltFehler(null);
    setzeSchaltet(true);
    try {
      await (vorgang.abgeschlossen
        ? vorgangWiederEroeffnen(vorgang.id)
        : vorgangAbschliessen(vorgang.id));
      setzeStand({ art: 'daten', vorgang: await vorgangLesen(vorgang.id) });
    } catch {
      // Welcher Grund es war, hilft dem Benutzer nicht (CLAUDE-react.md).
      setzeSchaltFehler(SCHALTEN_FEHLT);
    } finally {
      setzeSchaltet(false);
    }
  };

  let inhalt: ReactNode;
  if (stand.art === 'daten') {
    const vorgang = stand.vorgang;
    // Die Taste ist waehrend des Schaltens abgeschaltet: Ein zweiter Klick waere ein zweiter
    // Aufruf auf denselben Stand (CLAUDE-react.md, Datenzugriff).
    const schalte = () => {
      void schalten(vorgang);
    };
    inhalt = (
      <>
        <Kopfkarte
          malName={vorgang.firma.name}
          titel={
            <>
              <Box component="span" className={ZAHLEN_KLASSE}>
                {`#${String(vorgang.nummer)}`}
              </Box>{' '}
              {vorgang.titel}
            </>
          }
          zeile={zuordnungsZeile(vorgang)}
          chips={chipsZu(vorgang)}
          aktionen={
            <>
              <WeicheTaste
                to={`/vorgaenge/${String(vorgang.id)}/bearbeiten`}
                symbol={<IconPencil size={SYMBOL_TASTE} stroke={1.8} />}
              >
                Bearbeiten
              </WeicheTaste>
              {vorgang.abgeschlossen ? (
                <WeicheTaste onClick={schalte} disabled={schaltet}>
                  Wieder öffnen
                </WeicheTaste>
              ) : (
                <KupferTaste onClick={schalte} disabled={schaltet}>
                  Abschließen
                </KupferTaste>
              )}
            </>
          }
        />
        <Box
          sx={{
            display: 'grid',
            // Eine Spalte, sobald der Platz nicht mehr reicht; die Historie ist die breitere.
            gridTemplateColumns: { xs: 'minmax(0, 1fr)', lg: 'minmax(0, 1.9fr) minmax(0, 1fr)' },
            gap: '22px',
            alignItems: 'start',
          }}
        >
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: '22px', minWidth: 0 }}>
            <Karte titel="Eintrag hinzufügen">
              <EintragMaske
                vorgangId={vorgang.id}
                modus={{ art: 'hinzufuegen' }}
                gespeichert={neuLesen}
              />
            </Karte>
            <Karte titel="Historie" anzahl={vorgang.historie.length}>
              <Historie vorgangId={vorgang.id} eintraege={vorgang.historie} geaendert={neuLesen} />
            </Karte>
          </Box>
          <Felderkarte vorgang={vorgang} />
        </Box>
      </>
    );
  } else if (stand.art === 'laedt') {
    inhalt = (
      <Karte>
        <Typography
          sx={(theme) => ({
            fontSize: 12.5,
            color: theme.vars.palette.kupferwolke.textSchwach,
          })}
        >
          {LAEDT}
        </Typography>
      </Karte>
    );
  } else {
    inhalt = (
      <Karte>
        <Alert severity="error">{stand.art === 'unbekannt' ? NICHT_GEFUNDEN : AUSFALL}</Alert>
      </Karte>
    );
  }

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
      {schaltFehler === null ? null : <Alert severity="error">{schaltFehler}</Alert>}
      {inhalt}
    </Box>
  );
}
