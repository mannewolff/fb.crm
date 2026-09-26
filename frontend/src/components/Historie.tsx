import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';
import { useEffect, useRef, useState } from 'react';

import { anhangPfad } from '../api/vorgaenge';
import type { Eintrag, Eintragsart, Herkunft } from '../api/vorgaenge';
import { dateigroesse } from '../lib/dateigroesse';
import { ZAHLEN_KLASSE } from '../theme';
import EintragMaske from './EintragMaske';

/**
 * Die Historie eines Vorgangs (Kriterien 15, 16, 17, 19, 26).
 *
 * Gestalt als Zeitleiste (CLAUDE-design.md, „Bausteine"): eine Spalte aus Zeilen, jede mit einem
 * Melder links am durchlaufenden Strahl, dem Kern in der Mitte und dem Zeitpunkt rechts in
 * Tabellenziffern. Der juengste Eintrag steht oben (Kriterium 15). Die Anordnung folgt erst ab
 * dem Rahmen-Paket der Kupferwolke; hier stehen nur ihre Werte.
 *
 * Drei Zusagen tragen den Baustein:
 *
 * <ul>
 *   <li><b>Die Reihenfolge kommt von der Schnittstelle</b> — hier wird nicht sortiert. Sortierte
 *       die Oberflaeche noch einmal nach, gaebe es zwei Reihenfolgen, und welche gilt, haengt
 *       daran, welche zuletzt lief.</li>
 *   <li><b>Der Anhang ist ein Verweis, kein Aufruf</b> (E14, Kriterium 17). Der Browser holt die
 *       Datei selbst — mit dem Sitzungs-Cookie und dem `Content-Disposition` des Servers. Ein
 *       `fetch` muesste sie in den Arbeitsspeicher holen, als Blob-URL wieder herausgeben und den
 *       Dateinamen noch einmal setzen.</li>
 *   <li><b>Jede Zeile traegt ihren Stand im zugaenglichen Namen</b> (Kriterium 26). Wer mit dem
 *       Screenreader durch die Eintraege geht, hoert Art, Zeitpunkt, Herkunft und den Vermerk
 *       „geaendert", ohne die Zeile betreten zu muessen.</li>
 *   <li><b>Geaendert wird an Ort und Stelle</b> (E20, Kriterium 19): Die Taste „Aendern" laesst
 *       {@link EintragMaske} in der Zeile selbst aufgehen, statt auf eine eigene Seite zu fuehren.
 *       Hoechstens eine Zeile ist gleichzeitig offen — deshalb steht der offene Eintrag hier und
 *       nicht in der Zeile: Zwei Masken gleichzeitig waeren zwei Faelle desselben Eintrags, und
 *       welcher gilt, haengt daran, welcher zuletzt gespeichert wurde.</li>
 * </ul>
 *
 * Der Melder ist Beiwerk und darum `aria-hidden`: Die Art steht in derselben Zeile als Wort da,
 * Farbe allein traegt hier keine Information (CLAUDE-react.md, Accessibility).
 */

const OHNE_EINTRAG = 'Noch kein Eintrag in der Historie.';

const ART_TEXT: Readonly<Record<Eintragsart, string>> = {
  KOMMENTAR: 'Kommentar',
  ANHANG: 'Anhang',
};

/** Heute wird alles von Hand erfasst (`Herkunft` in `api/vorgaenge.ts`, Kriterium 16). */
const HERKUNFT_TEXT: Readonly<Record<Herkunft, string>> = { VON_HAND: 'von Hand' };

/** Der Melder je Art — als Rolle des Themes, nicht als Farbwert (CLAUDE-design.md). */
const ART_MELDER: Readonly<Record<Eintragsart, 'grau' | 'stahl'>> = {
  KOMMENTAR: 'grau',
  ANHANG: 'stahl',
};

export interface HistorieProps {
  /** Kennung des Vorgangs — sie steht im Pfad zur Datei eines Anhangs. */
  readonly vorgangId: number;
  /** Die Eintraege in der Reihenfolge der Schnittstelle: der juengste zuerst. */
  readonly eintraege: readonly Eintrag[];
  /**
   * Ruft den Aufrufer zum Neuladen auf, sobald ein Eintrag geaendert wurde (Kriterium 19).
   *
   * Die Historie schreibt ihre eigene Liste nicht fort: Den Vermerk „geaendert" und die neue
   * Einordnung eines zurueckdatierten Eintrags vergibt der Server.
   */
  readonly geaendert: () => void;
}

/**
 * Tag und Uhrzeit auf die Minute.
 *
 * Anders als in der Uebersicht, wo nur der Tag steht: Die Historie ist die Stelle, an der die
 * Minute zaehlt — ein zurueckdatierter Kommentar und ein Anhang vom selben Tag ordnen sich nur
 * daran.
 */
function alsZeitpunkt(zeitstempel: string): string {
  return new Date(zeitstempel).toLocaleString('de-DE', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function vermerkZu(geaendertAm: string): string {
  return `geändert ${alsZeitpunkt(geaendertAm)}`;
}

/**
 * Der zugaengliche Name einer Zeile (Kriterium 26).
 *
 * Er wiederholt, was sichtbar in der Zeile steht. Das ist kein Beiwerk: `listitem` bildet seinen
 * Namen nicht aus dem Inhalt, also haette die Zeile ohne diese Beschriftung gar keinen — und wer
 * durch die Liste springt, hoerte nur „Listenelement".
 */
function benennung(eintrag: Eintrag): string {
  const teile = [
    ART_TEXT[eintrag.art],
    alsZeitpunkt(eintrag.geschehenAm),
    HERKUNFT_TEXT[eintrag.herkunft],
  ];
  if (eintrag.geaendertAm !== null) {
    teile.push(vermerkZu(eintrag.geaendertAm));
  }
  return teile.join(', ');
}

/** Der Verweis auf die Datei — nur wo ein Dateiname steht, also beim Anhang. */
function Datei({
  vorgangId,
  eintrag,
}: {
  readonly vorgangId: number;
  readonly eintrag: Eintrag;
}) {
  if (eintrag.dateiName === null) {
    return null;
  }
  return (
    <Link
      href={anhangPfad(vorgangId, eintrag.id)}
      download
      underline="hover"
      sx={{ fontSize: 11.5, fontWeight: 500 }}
    >
      {eintrag.dateiName}
    </Link>
  );
}

/** Die Groesse in einer Form, die ein Mensch liest — nur wo eine hinterlegt ist. */
function Groesse({ bytes }: { readonly bytes: number | null }) {
  if (bytes === null) {
    return null;
  }
  return (
    <Typography
      component="span"
      className={ZAHLEN_KLASSE}
      sx={(theme) => ({
        fontSize: 11,
        color: theme.vars.palette.kupferwolke.textSchwach,
      })}
    >
      {dateigroesse(bytes)}
    </Typography>
  );
}

/** Der Text des Eintrags — beim Kommentar die Sache selbst, beim Anhang die Beschreibung. */
function Text({ text }: { readonly text: string | null }) {
  if (text === null) {
    return null;
  }
  return (
    <Typography
      sx={(theme) => ({
        marginTop: '2px',
        fontSize: 11.5,
        color: theme.vars.palette.kupferwolke.textMatt,
        // Der Umbruch, den der Benutzer eingegeben hat, ist Teil seines Textes. Ohne `pre-wrap`
        // faltete der Browser ihn zu einem Leerzeichen zusammen.
        whiteSpace: 'pre-wrap',
        overflowWrap: 'anywhere',
      })}
    >
      {text}
    </Typography>
  );
}

/** Eine Zeile der Historie (Vorlage `.anlauf` CSS Z. 1046–1058). */
function Zeile({
  vorgangId,
  eintrag,
  offen,
  oeffne,
  schliesse,
  gespeichert,
}: {
  readonly vorgangId: number;
  readonly eintrag: Eintrag;
  /** Steht diese Zeile im Aendern? Die Antwort haelt {@link Historie} fuer alle Zeilen. */
  readonly offen: boolean;
  readonly oeffne: () => void;
  readonly schliesse: () => void;
  readonly gespeichert: () => void;
}) {
  const taste = useRef<HTMLButtonElement>(null);
  /**
   * Merkt sich, dass der Fokus nach dem Schliessen zurueck auf die Taste gehoert.
   *
   * Nicht jedes Schliessen ist ein Abbrechen: Die Zeile geht auch zu, wenn der Benutzer eine
   * andere Zeile aufmacht. Dort haette ein Sprung auf diese Taste den Fokus aus der Maske
   * gerissen, in die er gerade gesetzt wurde.
   */
  const zurueckZurTaste = useRef(false);

  useEffect(() => {
    if (offen || !zurueckZurTaste.current) {
      return;
    }
    zurueckZurTaste.current = false;
    taste.current?.focus();
  }, [offen]);

  const abbrechen = () => {
    zurueckZurTaste.current = true;
    schliesse();
  };

  return (
    <Box
      component="li"
      aria-label={benennung(eintrag)}
      sx={(theme) => ({
        display: 'grid',
        gridTemplateColumns: '16px minmax(0, 1fr) auto',
        gap: '11px',
        alignItems: 'start',
        position: 'relative',
        paddingBottom: '13px',
        '&:last-of-type': { paddingBottom: 0 },
        // Der Strahl, der die Zeilen verbindet — eine Haarlinie auf der Karte, und am letzten
        // Eintrag endet er, statt ins Leere zu laufen. Eingelassene Flaechen gibt es in der
        // Kupferwolke nicht mehr (CLAUDE-design.md, „Tiefe").
        '&::before': {
          content: '""',
          position: 'absolute',
          left: '7px',
          top: '15px',
          bottom: 0,
          width: '2px',
          borderRadius: '2px',
          background: theme.vars.palette.kupferwolke.linie,
        },
        '&:last-of-type::before': { display: 'none' },
      })}
    >
      <Box
        component="span"
        aria-hidden="true"
        sx={(theme) => ({
          width: 15,
          height: 15,
          borderRadius: '50%',
          display: 'grid',
          placeItems: 'center',
          zIndex: 1,
          background: theme.vars.palette.kupferwolke.flaeche,
          border: `1px solid ${theme.vars.palette.kupferwolke.linie}`,
        })}
      >
        <Box
          component="span"
          sx={(theme) => ({
            width: 9,
            height: 9,
            borderRadius: '50%',
            color: theme.vars.palette.kupferwolke.melder[ART_MELDER[eintrag.art]],
            background: 'currentColor',
            boxShadow: '0 0 0 1px rgba(0,0,0,.22) inset, 0 0 8px -1px currentColor',
          })}
        />
      </Box>
      <Box sx={{ minWidth: 0 }}>
        <Box sx={{ display: 'flex', alignItems: 'baseline', gap: '8px', flexWrap: 'wrap' }}>
          <Typography component="span" sx={{ fontSize: 12.5, fontWeight: 500 }}>
            {ART_TEXT[eintrag.art]}
          </Typography>
          <Typography
            component="span"
            sx={(theme) => ({
              fontSize: 11,
              color: theme.vars.palette.kupferwolke.textSchwach,
            })}
          >
            {HERKUNFT_TEXT[eintrag.herkunft]}
          </Typography>
          <Datei vorgangId={vorgangId} eintrag={eintrag} />
          <Groesse bytes={eintrag.dateiGroesse} />
          {offen ? null : (
            // Der Name nennt den Eintrag mit: „Aendern" allein waere in jeder Zeile derselbe,
            // und wer die Tasten mit dem Screenreader durchgeht, hoerte nicht, welche wohin
            // gehoert (Kriterium 26).
            <Button
              ref={taste}
              onClick={oeffne}
              aria-label={`Ändern: ${benennung(eintrag)}`}
              sx={(theme) => ({
                minWidth: 0,
                padding: '0 4px',
                fontSize: 11,
                fontWeight: 600,
                textTransform: 'none',
                color: theme.vars.palette.kupferwolke.kupfer,
              })}
            >
              Ändern
            </Button>
          )}
        </Box>
        {offen ? (
          <EintragMaske
            vorgangId={vorgangId}
            modus={{ art: 'aendern', eintrag, abgebrochen: abbrechen }}
            gespeichert={gespeichert}
          />
        ) : (
          <Text text={eintrag.text} />
        )}
      </Box>
      <Box
        className={ZAHLEN_KLASSE}
        sx={(theme) => ({
          fontSize: 11.5,
          color: theme.vars.palette.kupferwolke.textMatt,
          textAlign: 'right',
          whiteSpace: 'nowrap',
        })}
      >
        <Box component="span" sx={{ display: 'block' }}>
          {alsZeitpunkt(eintrag.geschehenAm)}
        </Box>
        {eintrag.geaendertAm === null ? null : (
          <Box
            component="span"
            sx={(theme) => ({
              display: 'block',
              fontSize: 11,
              color: theme.vars.palette.kupferwolke.textSchwach,
            })}
          >
            {vermerkZu(eintrag.geaendertAm)}
          </Box>
        )}
      </Box>
    </Box>
  );
}

export default function Historie({ vorgangId, eintraege, geaendert }: HistorieProps) {
  /** Der Eintrag, dessen Zeile gerade im Aendern steht — `null`, wenn keiner offen ist. */
  const [imAendern, setzeImAendern] = useState<number | null>(null);

  if (eintraege.length === 0) {
    return (
      <Typography
        role="status"
        sx={(theme) => ({
          padding: '18px 16px',
          fontSize: 12.5,
          color: theme.vars.palette.kupferwolke.textMatt,
        })}
      >
        {OHNE_EINTRAG}
      </Typography>
    );
  }
  return (
    <Box
      component="ul"
      aria-label="Historie"
      sx={{
        listStyle: 'none',
        margin: 0,
        padding: '16px',
        display: 'flex',
        flexDirection: 'column',
        gap: 0,
      }}
    >
      {eintraege.map((eintrag) => (
        <Zeile
          key={eintrag.id}
          vorgangId={vorgangId}
          eintrag={eintrag}
          offen={imAendern === eintrag.id}
          oeffne={() => {
            setzeImAendern(eintrag.id);
          }}
          schliesse={() => {
            setzeImAendern(null);
          }}
          gespeichert={() => {
            setzeImAendern(null);
            geaendert();
          }}
        />
      ))}
    </Box>
  );
}
