import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import type { Theme } from '@mui/material/styles';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';

import { vorgangAbschliessen, vorgangLesen, vorgangWiederEroeffnen } from '../api/vorgaenge';
import type { Phase, Vorgang, Zuordnung } from '../api/vorgaenge';
import EintragMaske from '../components/EintragMaske';
import Historie from '../components/Historie';
import Platte from '../components/Platte';
import { nichtGefunden } from '../lib/apifehler';
import { kennungAus } from '../lib/kennung';
import { CARD_RADIUS } from '../theme';

/**
 * Die Detailansicht eines Vorgangs (Kriterien 9, 11, 20, 21, 23, 25, 26).
 *
 * Gestalt nach der Vorlagen-Ansicht „Karte" (`docs/entwurf-leitstand.html`): links das Blatt mit
 * dem Kopf aus Nummer, Titel und Zustand (HTML Z. 2060–2066, CSS `.blatt-kopf`/`.blatt-nr`/
 * `.blatt-titel` Z. 1021–1024) und darunter die Historie, rechts die Saeule mit der Platte
 * „Felder" (HTML Z. 2168–2185, CSS `.saeule` Z. 541, `.felder`/`.feld` Z. 1061–1065). Unterhalb
 * von 1200 px liegen beide Spalten uebereinander, wie in der Vorlage (CSS Z. 1067).
 *
 * Drei Zusagen tragen die Ansicht:
 *
 * <ul>
 *   <li><b>Nach dem Schalten wird neu gelesen</b> (Kriterium 20), statt den Stand selbst
 *       umzuschalten. Der Server ist die Quelle der Wahrheit — und nur so sieht der Benutzer, was
 *       dort tatsaechlich steht.</li>
 *   <li><b>Der abgeschlossene Vorgang bleibt vollstaendig</b> (Kriterium 21): Phase, Zuordnung und
 *       Historie stehen weiter da, und die Taste „Wieder oeffnen" nimmt den Abschluss zurueck.
 *       Abgeschlossen heisst „zu Ende gegangen", nicht „gesperrt".</li>
 *   <li><b>Eine stillgelegte Zuordnung bleibt sichtbar und wird angesagt</b> (Kriterien 23, 26):
 *       Das Schild steht als Wort in der Zeile, und der Stand gehoert zum Namen des Weges — wer mit
 *       dem Screenreader durch die Wege springt, hoert ihn ohne die Nachbarschaft.</li>
 *   <li><b>Nach dem Hinzufuegen und nach dem Aendern eines Eintrags wird neu gelesen</b> (E20,
 *       Kriterien 13, 14, 19). Die Maske steht als Platte ueber der Historie, das Aendern in der
 *       Zeile selbst; beide melden nur, dass etwas geschrieben wurde —
 *       wie die Liste danach aussieht, sagt der Server, nicht die Oberflaeche. Auch am
 *       abgeschlossenen Vorgang steht sie da: Abgeschlossen heisst „zu Ende gegangen", nicht
 *       „gesperrt" (Kriterium 21), und das Backend nimmt dort weiter Eintraege an.</li>
 *   <li><b>„Bearbeiten" steht auch am abgeschlossenen Vorgang</b> (Kriterium 10): Titel und
 *       Zuordnung sind dort weiter aenderbar. Die Taste ist ein Weg und keine Schaltflaeche —
 *       sie fuehrt auf `/vorgaenge/:id/bearbeiten`, also gehoert sie in den Tabulatorweg als
 *       Link, nicht als Knopf mit `onClick`.</li>
 * </ul>
 */

const NICHT_GEFUNDEN = 'Diesen Vorgang gibt es nicht.';
const AUSFALL = 'Der Vorgang ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const SCHALTEN_FEHLT =
  'Der Abschlussstand des Vorgangs wurde nicht geändert. Bitte später erneut versuchen.';
const LAEDT = 'Der Vorgang wird geladen …';

/** Die Phase als Wort. Heute kennt das Backend genau eine (Kriterium 11). */
const PHASE_TEXT: Readonly<Record<Phase, string>> = { ANBAHNUNG: 'Anbahnung' };

/** Was die Ansicht gerade weiss. */
type Stand =
  | { readonly art: 'laedt' }
  | { readonly art: 'daten'; readonly vorgang: Vorgang }
  | { readonly art: 'unbekannt' }
  | { readonly art: 'ausfall' };

/**
 * Die flache Taste der Vorlage (`.taste` CSS Z. 322–334) — fuer die Schreibaktion im Kopf.
 *
 * Sie steht hier neben der gleichlautenden Hilfe in {@link FirmaPage} und nicht in einem
 * gemeinsamen Baustein: Die Umstellung auf die Kupferwolke schreibt alle Ansichten samt ihren
 * Flaechen und Tiefen neu (CLAUDE-design.md, „Uebergang"). Ein Baustein, der nur bis dahin haelt,
 * waere Bewegung ohne Ertrag.
 */
function flacheTasteSx(theme: Theme) {
  return {
    fontSize: 12.5,
    fontWeight: 600,
    textTransform: 'none',
    padding: '5px 13px',
    borderRadius: `${CARD_RADIUS}px`,
    color: theme.vars.palette.kupferwarte.text,
    background: `linear-gradient(180deg, ${theme.vars.palette.kupferwarte.platteHoch}, ${theme.vars.palette.kupferwarte.platteFuss})`,
    border: `1px solid ${theme.vars.palette.kupferwarte.rand}`,
    boxShadow: theme.vars.palette.kupferwarte.schatten.taste,
    '&:hover': { boxShadow: theme.vars.palette.kupferwarte.schatten.platte },
    '&:active': { transform: 'translateY(1px)' },
  } as const;
}

/**
 * Das Schild der Vorlage (`.schild` CSS Z. 780–790): Der Stand steht als Wort da, nicht nur als
 * Farbe — Farbe allein traegt keine Information (CLAUDE-react.md, Accessibility).
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
        color: theme.vars.palette.kupferwarte.grau,
        border: '1px solid currentColor',
        background: 'color-mix(in srgb, currentColor 13%, transparent)',
      })}
    >
      {text}
    </Box>
  );
}

/**
 * Die Zustandsplakette der Vorlage (`.zustand` CSS Z. 933–943) mit der Phase.
 *
 * Der Melder ist Beiwerk und darum `aria-hidden`: Die Phase steht daneben als Wort.
 */
function PhasenPlakette({ phase }: { readonly phase: Phase }) {
  return (
    <Box
      component="span"
      sx={(theme) => ({
        display: 'inline-flex',
        alignItems: 'center',
        gap: '6px',
        fontSize: 11.5,
        color: theme.vars.palette.kupferwarte.textMatt,
        padding: '2px 8px 2px 6px',
        borderRadius: '6px',
        border: `1px solid ${theme.vars.palette.kupferwarte.rand}`,
        background: theme.vars.palette.kupferwarte.nute,
        boxShadow: theme.vars.palette.kupferwarte.schatten.nute,
        whiteSpace: 'nowrap',
      })}
    >
      <Box
        component="span"
        aria-hidden="true"
        sx={(theme) => ({
          width: 9,
          height: 9,
          borderRadius: '50%',
          color: theme.vars.palette.kupferwarte.stahl,
          background: 'currentColor',
          boxShadow: '0 0 0 1px rgba(0,0,0,.22) inset, 0 0 8px -1px currentColor',
        })}
      />
      {PHASE_TEXT[phase]}
    </Box>
  );
}

/**
 * Der Kopf des Blattes (Vorlage HTML Z. 2060–2066).
 *
 * Die Taste ist waehrend des Schaltens abgeschaltet: Ein zweiter Klick waere ein zweiter Aufruf
 * auf denselben Stand (CLAUDE-react.md, Datenzugriff).
 */
function Kopf({
  vorgang,
  schaltet,
  schalte,
}: {
  readonly vorgang: Vorgang;
  readonly schaltet: boolean;
  readonly schalte: () => void;
}) {
  return (
    <Box
      sx={(theme) => ({
        display: 'flex',
        alignItems: 'flex-start',
        gap: '12px',
        flexWrap: 'wrap',
        padding: '16px 18px 14px',
        borderBottom: `1px solid ${theme.vars.palette.kupferwarte.rand}`,
        background: `linear-gradient(180deg, ${theme.vars.palette.kupferwarte.platteHoch}, ${theme.vars.palette.kupferwarte.platte})`,
      })}
    >
      <Box sx={{ minWidth: 0, flex: '1 1 240px' }}>
        <Typography
          sx={(theme) => ({
            fontFamily: theme.vars.palette.kupferwarte.monoFontFamily,
            fontVariantNumeric: 'tabular-nums',
            fontSize: 13,
            fontWeight: 500,
            color: theme.vars.palette.kupferwarte.kupfer,
          })}
        >
          {`#${String(vorgang.nummer)}`}
        </Typography>
        <Typography
          variant="h2"
          sx={{ fontSize: 19, fontWeight: 700, lineHeight: 1.25, letterSpacing: '-.01em' }}
        >
          {vorgang.titel}
        </Typography>
      </Box>
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          flexWrap: 'wrap',
          marginLeft: 'auto',
        }}
      >
        <PhasenPlakette phase={vorgang.phase} />
        {vorgang.abgeschlossen ? <Schild text="abgeschlossen" /> : null}
        <Button
          component={RouterLink}
          to={`/vorgaenge/${String(vorgang.id)}/bearbeiten`}
          sx={flacheTasteSx}
        >
          Bearbeiten
        </Button>
        <Button onClick={schalte} disabled={schaltet} sx={flacheTasteSx}>
          {vorgang.abgeschlossen ? 'Wieder öffnen' : 'Abschließen'}
        </Button>
      </Box>
    </Box>
  );
}

/**
 * Ein Feldpaar der Platte „Felder" (Vorlage `.feld` CSS Z. 1062–1065).
 *
 * Es nimmt `children` statt einer Zeichenkette wie das gleichnamige Feldpaar in
 * {@link FirmaPage}: Hier steht im Wert ein Weg mit Schild, nicht Text.
 */
function Feld({ name, children }: { readonly name: string; readonly children: ReactNode }) {
  return (
    <Box
      sx={(theme) => ({
        display: 'grid',
        gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: '200px minmax(0, 1fr)' },
        gap: '10px',
        alignItems: 'center',
        padding: '9px 16px',
        fontSize: 12.5,
        borderBottom: `1px solid color-mix(in srgb, ${theme.vars.palette.kupferwarte.rand} 50%, transparent)`,
        '&:last-of-type': { borderBottom: 0 },
      })}
    >
      <Box
        component="span"
        sx={(theme) => ({ fontSize: 11, color: theme.vars.palette.kupferwarte.textSchwach })}
      >
        {name}
      </Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: '7px', flexWrap: 'wrap' }}>
        {children}
      </Box>
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
        sx={{ fontSize: 12.5 }}
      >
        {zuordnung.name}
      </Link>
      {zuordnung.aktiv ? null : <Schild text="stillgelegt" />}
    </>
  );
}

export default function VorgangPage() {
  const { id } = useParams();
  const kennung = kennungAus(id);
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });
  const [schaltFehler, setzeSchaltFehler] = useState<string | null>(null);
  const [schaltet, setzeSchaltet] = useState(false);
  /**
   * Zaehlt die Anlaesse zum Neulesen.
   *
   * Ein Zaehler und kein eigener Ladepfad neben dem Effekt: So geht das Neulesen nach einem
   * Eintrag denselben Weg wie das erste Lesen — samt seiner Behandlung von „gibt es nicht" und
   * „nicht zu erreichen". Zwei Wege auf dieselben Daten liefen frueher oder spaeter auseinander.
   */
  const [runde, setzeRunde] = useState(0);

  /** Der eine Anlass zum Neulesen — die Maske ueber der Historie und die Zeile darin teilen ihn. */
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
    inhalt = (
      <Box
        sx={{
          display: 'grid',
          // Vorlage `.blatt` (CSS Z. 1020) — eine Spalte, sobald der Platz nicht mehr reicht.
          gridTemplateColumns: { xs: 'minmax(0, 1fr)', lg: 'minmax(0, 1.9fr) minmax(0, 1fr)' },
          gap: '16px',
          alignItems: 'start',
        }}
      >
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: '16px', minWidth: 0 }}>
          <Platte>
            <Kopf
              vorgang={vorgang}
              schaltet={schaltet}
              schalte={() => {
                void schalten(vorgang);
              }}
            />
          </Platte>
          <Platte titel="Eintrag hinzufügen">
            <EintragMaske
              vorgangId={vorgang.id}
              modus={{ art: 'hinzufuegen' }}
              gespeichert={neuLesen}
            />
          </Platte>
          <Platte titel="Historie">
            <Historie vorgangId={vorgang.id} eintraege={vorgang.historie} geaendert={neuLesen} />
          </Platte>
        </Box>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: '16px', minWidth: 0 }}>
          <Platte titel="Felder">
            <Box sx={{ display: 'flex', flexDirection: 'column' }}>
              <Feld name="Firma">
                <Weg zuordnung={vorgang.firma} firmaId={vorgang.firma.id} />
              </Feld>
              {vorgang.ansprechpartner === null ? null : (
                // Kein Platzhalter, wo keine Zuordnung steht: Ein „—" waere eine Zeile, die der
                // Screenreader vorliest, ohne dass sie etwas sagt.
                <Feld name="Ansprechpartner">
                  <Weg zuordnung={vorgang.ansprechpartner} firmaId={vorgang.firma.id} />
                </Feld>
              )}
            </Box>
          </Platte>
        </Box>
      </Box>
    );
  } else if (stand.art === 'laedt') {
    inhalt = (
      <Platte>
        <Typography
          sx={(theme) => ({
            padding: '18px 16px',
            fontSize: 12.5,
            color: theme.vars.palette.kupferwarte.textSchwach,
          })}
        >
          {LAEDT}
        </Typography>
      </Platte>
    );
  } else {
    inhalt = (
      <Platte>
        <Alert severity="error" sx={{ borderRadius: 0 }}>
          {stand.art === 'unbekannt' ? NICHT_GEFUNDEN : AUSFALL}
        </Alert>
      </Platte>
    );
  }

  return (
    <Box
      sx={{
        padding: { xs: 2, sm: '22px 26px 44px' },
        display: 'flex',
        flexDirection: 'column',
        gap: '20px',
      }}
    >
      {schaltFehler === null ? null : <Alert severity="error">{schaltFehler}</Alert>}
      {inhalt}
    </Box>
  );
}
