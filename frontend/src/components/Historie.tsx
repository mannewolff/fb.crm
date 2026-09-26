import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';

import { anhangPfad } from '../api/vorgaenge';
import type { Eintrag, Eintragsart, Herkunft } from '../api/vorgaenge';
import { dateigroesse } from '../lib/dateigroesse';

/**
 * Die Historie eines Vorgangs (Kriterien 15, 16, 17, 19, 26).
 *
 * Gestalt nach den „Anlaeufen" der Vorlagen-Ansicht „Karte" (`docs/entwurf-leitstand.html`,
 * HTML Z. 2137–2162, CSS `.anlaeufe`/`.anlauf` Z. 1045–1058): eine Spalte aus Zeilen, jede mit
 * einem Melder links am durchlaufenden Strahl, dem Kern in der Mitte und dem Zeitpunkt rechts in
 * Tabellenziffern. **Einzige gewollte Abweichung von der Vorlage:** Der juengste Eintrag steht
 * oben (Kriterium 15).
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
      sx={(theme) => ({
        fontFamily: theme.vars.palette.kupferwarte.monoFontFamily,
        fontVariantNumeric: 'tabular-nums',
        fontSize: 11,
        color: theme.vars.palette.kupferwarte.textSchwach,
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
        color: theme.vars.palette.kupferwarte.textMatt,
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
function Zeile({ vorgangId, eintrag }: { readonly vorgangId: number; readonly eintrag: Eintrag }) {
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
        // Der Strahl, der die Zeilen verbindet — eingelassen wie eine Nut, und am letzten
        // Eintrag endet er, statt ins Leere zu laufen (Vorlage Z. 1048–1052).
        '&::before': {
          content: '""',
          position: 'absolute',
          left: '7px',
          top: '15px',
          bottom: 0,
          width: '2px',
          borderRadius: '2px',
          background: theme.vars.palette.kupferwarte.nute,
          boxShadow: theme.vars.palette.kupferwarte.schatten.nute,
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
          background: theme.vars.palette.kupferwarte.platte,
          border: `1px solid ${theme.vars.palette.kupferwarte.rand}`,
          boxShadow: theme.vars.palette.kupferwarte.schatten.taste,
        })}
      >
        <Box
          component="span"
          sx={(theme) => ({
            width: 9,
            height: 9,
            borderRadius: '50%',
            color: theme.vars.palette.kupferwarte[ART_MELDER[eintrag.art]],
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
              color: theme.vars.palette.kupferwarte.textSchwach,
            })}
          >
            {HERKUNFT_TEXT[eintrag.herkunft]}
          </Typography>
          <Datei vorgangId={vorgangId} eintrag={eintrag} />
          <Groesse bytes={eintrag.dateiGroesse} />
        </Box>
        <Text text={eintrag.text} />
      </Box>
      <Box
        sx={(theme) => ({
          fontFamily: theme.vars.palette.kupferwarte.monoFontFamily,
          fontVariantNumeric: 'tabular-nums',
          fontSize: 11.5,
          color: theme.vars.palette.kupferwarte.textMatt,
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
              color: theme.vars.palette.kupferwarte.textSchwach,
            })}
          >
            {vermerkZu(eintrag.geaendertAm)}
          </Box>
        )}
      </Box>
    </Box>
  );
}

export default function Historie({ vorgangId, eintraege }: HistorieProps) {
  if (eintraege.length === 0) {
    return (
      <Typography
        role="status"
        sx={(theme) => ({
          padding: '18px 16px',
          fontSize: 12.5,
          color: theme.vars.palette.kupferwarte.textMatt,
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
        <Zeile key={eintrag.id} vorgangId={vorgangId} eintrag={eintrag} />
      ))}
    </Box>
  );
}
