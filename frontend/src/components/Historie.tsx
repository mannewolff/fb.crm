import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';
import { IconFileText, IconMessageCircle, IconPaperclip } from '@tabler/icons-react';
import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';

import { anhangPfad } from '../api/vorgaenge';
import type { Eintrag, Eintragsart, Herkunft } from '../api/vorgaenge';
import { dateigroesse } from '../lib/dateigroesse';
import type { ToenungName } from '../theme';
import { ZAHLEN_KLASSE } from '../theme';
import EintragMaske from './EintragMaske';
import Zeitleiste from './Zeitleiste';
import type { ZeitleisteEintrag } from './Zeitleiste';

/**
 * Die Historie eines Vorgangs (Kriterien 15, 16, 17, 19, 26).
 *
 * Sie **befuellt die {@link Zeitleiste}** aus #79, statt eine eigene Liste zu bauen (Plan E11): Die
 * Vorlage zeigt die Zeitleiste als Baustein (`.zeit` Z. 94–99, HTML Z. 189–196), und ein zweiter
 * Nachbau hier waere dieselbe Gestalt an zwei Orten. Der juengste Eintrag steht oben
 * (Kriterium 15) — in der Reihenfolge, in der die Schnittstelle ihn liefert.
 *
 * Fuenf Zusagen tragen den Baustein:
 *
 * <ul>
 *   <li><b>Die Reihenfolge kommt von der Schnittstelle</b> — hier wird nicht sortiert. Sortierte
 *       die Oberflaeche noch einmal nach, gaebe es zwei Reihenfolgen, und welche gilt, haengt
 *       daran, welche zuletzt lief.</li>
 *   <li><b>Die Art steht als Wort im Titel</b> (CLAUDE-design.md, „Zustandsformen"). Toenung und
 *       Symbol stuetzen sie: Pfirsich mit Sprechblase am Kommentar, Flieder mit Klammer am
 *       Anhang. Wer die Toenungen nicht auseinanderhaelt, liest dieselbe Angabe im Wort.</li>
 *   <li><b>Der Anhang ist ein Verweis, kein Aufruf</b> (E14, Kriterium 17). Der Browser holt die
 *       Datei selbst — mit dem Sitzungs-Cookie und dem `Content-Disposition` des Servers. Ein
 *       `fetch` muesste sie in den Arbeitsspeicher holen, als Blob-URL wieder herausgeben und den
 *       Dateinamen noch einmal setzen.</li>
 *   <li><b>Jeder Eintrag traegt seinen Stand im zugaenglichen Namen</b> (Kriterium 26). Wer mit dem
 *       Screenreader durch die Eintraege geht, hoert Art, Zeitpunkt, Herkunft und den Vermerk
 *       „geaendert", ohne den Eintrag betreten zu muessen.</li>
 *   <li><b>Ein Ereignis laesst sich nicht aendern</b> (Kriterium 19). Es hat niemand erfasst —
 *       die Anwendung hat vermerkt, was mit einem Dokument geschehen ist. Eine Taste „Aendern"
 *       daran waere ein Angebot, den Nachweis umzuschreiben; das Backend nimmt die Aenderung
 *       ohnehin nicht an. Alle Ereignisse tragen dieselbe Toenung und dasselbe Symbol: Die Zeile
 *       meldet eine Tatsache und wertet sie nicht (E3).</li>
 *   <li><b>Geaendert wird an Ort und Stelle</b> (E20, Kriterium 19): Die Taste „Aendern" laesst
 *       {@link EintragMaske} im Eintrag selbst aufgehen, statt auf eine eigene Seite zu fuehren.
 *       Hoechstens ein Eintrag ist gleichzeitig offen — deshalb steht der offene hier und nicht im
 *       Eintrag: Zwei Masken gleichzeitig waeren zwei Faelle desselben Eintrags, und welcher gilt,
 *       haengt daran, welcher zuletzt gespeichert wurde.</li>
 * </ul>
 */

const OHNE_EINTRAG = 'Noch kein Eintrag in der Historie.';

const ART_TEXT: Readonly<Record<Eintragsart, string>> = {
  KOMMENTAR: 'Kommentar',
  ANHANG: 'Anhang',
  EREIGNIS: 'Ereignis',
};

/** Von Hand erfasst oder von der Anwendung vermerkt (Kriterium 16, Kriterium 19). */
const HERKUNFT_TEXT: Readonly<Record<Herkunft, string>> = {
  VON_HAND: 'von Hand',
  AUTOMATISCH: 'automatisch',
};

/**
 * Die Toenung je Art — als Name, nicht als Farbpaar (CLAUDE-design.md, „Toenungen").
 *
 * Pfirsich ist die Kupfer-Familie und traegt den Vorgang selbst; Flieder ist die neutrale
 * Kategorie und traegt hier die angehaengte Datei.
 */
const ART_TOENUNG: Readonly<Record<Eintragsart, ToenungName>> = {
  KOMMENTAR: 'pfirsich',
  ANHANG: 'flieder',
  // Himmel heisst „Information, laufend" — und genau das ist ein Ereignis: eine Tatsachenmeldung
  // ueber ein Dokument. Alle Ereignisse tragen dieselbe Toenung, auch „abgelehnt": Die Zeile
  // meldet, was geschehen ist, sie bewertet es nicht (E3, Vorlage Z. 192).
  EREIGNIS: 'himmel',
};

/** Kantenlaenge des Symbols im Feld der Zeitleiste (Vorlage `.zeit .punkt` Z. 97: 17 px). */
const SYMBOL = 17;

/** Das Symbol je Art. Dekorativ — die Zeitleiste haengt das Feld aus dem Baum aus. */
const ART_SYMBOL: Readonly<Record<Eintragsart, typeof IconPaperclip>> = {
  KOMMENTAR: IconMessageCircle,
  ANHANG: IconPaperclip,
  // Das Blatt mit Text steht in der Vorlage an der Ereigniszeile (Z. 192) — ein Dokument hat
  // seinen Zustand gewechselt.
  EREIGNIS: IconFileText,
};

function symbolZu(art: Eintragsart): ReactNode {
  const Symbol = ART_SYMBOL[art];
  return <Symbol size={SYMBOL} stroke={1.8} />;
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
 * Der zugaengliche Name eines Eintrags (Kriterium 26).
 *
 * Er wiederholt, was sichtbar im Eintrag steht. Das ist kein Beiwerk: `listitem` bildet seinen
 * Namen nicht aus dem Inhalt, also haette der Eintrag ohne diese Benennung gar keinen — und wer
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
      sx={{ fontSize: 12.5, fontWeight: 500 }}
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
        fontSize: 11.5,
        fontWeight: 400,
        color: theme.vars.palette.kupferwolke.textSchwach,
      })}
    >
      {dateigroesse(bytes)}
    </Typography>
  );
}

/** Der Text des Eintrags — beim Kommentar die Sache selbst, beim Anhang die Beschreibung. */
function Text({ text }: { readonly text: string }) {
  return (
    <Typography
      sx={(theme) => ({
        fontSize: 12.5,
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

/**
 * Die Taste, die die Maske im Eintrag aufgehen laesst.
 *
 * Sie holt den Fokus zurueck, sobald der Benutzer selbst abbricht — und nur dann: Der Eintrag geht
 * auch zu, wenn ein anderer aufgemacht wird, und dort haette ein Sprung auf diese Taste den Fokus
 * aus der Maske gerissen, in die er gerade gesetzt wurde.
 */
function AendernTaste({
  eintrag,
  oeffne,
  offen,
  zurueckZurTaste,
}: {
  readonly eintrag: Eintrag;
  readonly oeffne: () => void;
  readonly offen: boolean;
  readonly zurueckZurTaste: { current: boolean };
}) {
  const taste = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (offen || !zurueckZurTaste.current) {
      return;
    }
    zurueckZurTaste.current = false;
    taste.current?.focus();
  }, [offen, zurueckZurTaste]);

  if (offen) {
    return null;
  }
  return (
    // Der Name nennt den Eintrag mit: „Aendern" allein waere in jeder Zeile derselbe, und wer die
    // Tasten mit dem Screenreader durchgeht, hoerte nicht, welche wohin gehoert (Kriterium 26).
    <Button
      ref={taste}
      onClick={oeffne}
      aria-label={`Ändern: ${benennung(eintrag)}`}
      sx={(theme) => ({
        minWidth: 0,
        padding: '0 4px',
        fontSize: 11.5,
        fontWeight: 600,
        textTransform: 'none',
        color: theme.vars.palette.kupferwolke.kupfer,
      })}
    >
      Ändern
    </Button>
  );
}

/**
 * Der Titel eines Eintrags: die Art als Wort, beim Anhang Datei und Groesse, dann die Taste.
 *
 * Die Taste steht im Titel und nicht in einer eigenen Spalte: Die Zeitleiste kennt Symbolfeld,
 * Titel, Unterzeile und Inhalt — mehr Spalten haette sie nur fuer diesen einen Fall.
 */
function Titel({
  vorgangId,
  eintrag,
  offen,
  oeffne,
  zurueckZurTaste,
}: {
  readonly vorgangId: number;
  readonly eintrag: Eintrag;
  readonly offen: boolean;
  readonly oeffne: () => void;
  readonly zurueckZurTaste: { current: boolean };
}) {
  return (
    <Box
      component="span"
      sx={{ display: 'flex', alignItems: 'baseline', gap: '8px', flexWrap: 'wrap' }}
    >
      <Box component="span">{ART_TEXT[eintrag.art]}</Box>
      <Datei vorgangId={vorgangId} eintrag={eintrag} />
      <Groesse bytes={eintrag.dateiGroesse} />
      {eintrag.art === 'EREIGNIS' ? null : (
        <AendernTaste
          eintrag={eintrag}
          oeffne={oeffne}
          offen={offen}
          zurueckZurTaste={zurueckZurTaste}
        />
      )}
    </Box>
  );
}

/** Die Unterzeile: Zeitpunkt in Tabellenziffern, Herkunft und — wo es sie gibt — der Vermerk. */
function Unterzeile({ eintrag }: { readonly eintrag: Eintrag }) {
  return (
    <Box component="span" sx={{ display: 'flex', alignItems: 'baseline', gap: '8px', flexWrap: 'wrap' }}>
      <Box component="span" className={ZAHLEN_KLASSE}>
        {alsZeitpunkt(eintrag.geschehenAm)}
      </Box>
      <Box component="span">{HERKUNFT_TEXT[eintrag.herkunft]}</Box>
      {eintrag.geaendertAm === null ? null : (
        <Box component="span" className={ZAHLEN_KLASSE}>
          {vermerkZu(eintrag.geaendertAm)}
        </Box>
      )}
    </Box>
  );
}

export default function Historie({ vorgangId, eintraege, geaendert }: HistorieProps) {
  /** Der Eintrag, dessen Maske gerade offen steht — `null`, wenn keiner offen ist. */
  const [imAendern, setzeImAendern] = useState<number | null>(null);
  /**
   * Merkt sich, dass der Fokus nach dem Schliessen zurueck auf die Taste gehoert.
   *
   * Er liegt hier und nicht je Eintrag, weil es hoechstens einen offenen gibt: Nur dessen Taste
   * kann den Fokus zurueckverlangen, und nur sein „Abbrechen" setzt den Vermerk.
   */
  const zurueckZurTaste = useRef(false);

  if (eintraege.length === 0) {
    return (
      <Typography
        role="status"
        sx={(theme) => ({
          fontSize: 12.5,
          color: theme.vars.palette.kupferwolke.textMatt,
        })}
      >
        {OHNE_EINTRAG}
      </Typography>
    );
  }

  const zeitleiste: readonly ZeitleisteEintrag[] = eintraege.map((eintrag) => {
    const offen = imAendern === eintrag.id;
    /**
     * Der Zusatz im Eintrag: die Maske, der Text — oder nichts.
     *
     * `undefined` und nicht ein `Text`, der `null` liefert: Sonst legte die Zeitleiste ihren
     * Abstand unter einen Eintrag ohne Zusatz (Anhang ohne Beschreibung, Kriterium 14).
     */
    let inhalt: ReactNode = undefined;
    if (offen) {
      inhalt = (
        <EintragMaske
          vorgangId={vorgangId}
          modus={{
            art: 'aendern',
            eintrag,
            abgebrochen: () => {
              zurueckZurTaste.current = true;
              setzeImAendern(null);
            },
          }}
          gespeichert={() => {
            setzeImAendern(null);
            geaendert();
          }}
        />
      );
    } else if (eintrag.text !== null) {
      inhalt = <Text text={eintrag.text} />;
    }
    return {
      id: String(eintrag.id),
      symbol: symbolZu(eintrag.art),
      toenung: ART_TOENUNG[eintrag.art],
      benennung: benennung(eintrag),
      titel: (
        <Titel
          vorgangId={vorgangId}
          eintrag={eintrag}
          offen={offen}
          oeffne={() => {
            setzeImAendern(eintrag.id);
          }}
          zurueckZurTaste={zurueckZurTaste}
        />
      ),
      unterzeile: <Unterzeile eintrag={eintrag} />,
      inhalt,
    };
  });

  return <Zeitleiste beschriftung="Historie" eintraege={zeitleiste} />;
}
