import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { Link as RouterLink } from 'react-router-dom';

import type { AngebotZeile } from '../api/angebote';
import { euro } from '../lib/geld';
import { tagWort } from '../lib/tag';
import { ZAHLEN_KLASSE } from '../theme';
import AngebotsstatusChip from './AngebotsstatusChip';
import InternChip from './InternChip';
import StorniertChip from './StorniertChip';
import Tafel, { type TafelSpalte } from './Tafel';

/**
 * Die Angebote einer Firma als Tafel (Kriterium 7).
 *
 * <b>Nicht sortiert.</b> Die Reihenfolge kommt vom Server — neueste zuerst. Eine zweite Sortierung
 * hier waere eine zweite Wahrheit.
 *
 * <b>Der Weg liegt auf dem Titel, nicht auf der Zeile</b> (Issue #319, Plan #315, E12). Eine ganze
 * Tabellenzeile klickbar zu machen hiesse, ein `tr` mit `onClick` zu versehen — kein Ziel des
 * Tabulators und fuer den Screenreader kein Weg. Ein Angebot hat keine Nummer; der Titel ist, was es
 * in der Liste benennt. Beim Angebot ohne Titel liegt der Weg weiter auf dem Datum: Der Strich an
 * der Stelle des Titels wird nie verlinkt — ein Verweis auf „–" waere ein Link ohne zugaenglichen
 * Namen. Die jeweils andere Zelle traegt ihren Text ohne Verweis, damit eine Zeile nicht zwei
 * Tabulatorwege zum selben Ziel hat.
 *
 * <b>Der Titel bricht um, statt gekuerzt zu werden</b> (E11): Bei einer Kuerzung verschwaende
 * gerade der unterscheidende Teil. `overflowWrap: 'anywhere'` faengt auch den Titel ohne ein
 * einziges Leerzeichen.
 *
 * <b>Das Kennzeichen „Intern" steht hinter dem Datum</b> (Issue #233) — in der Spalte, die das
 * Angebot benennt, und nicht in einer sechsten Spalte, die bei 768 px der Summe den Platz nimmt.
 * Aus demselben Grund steht dort auch der Chip „Storniert" (Plan #332, E13); der Statuschip in
 * „Status" bleibt, denn ein storniertes Angebot behaelt seinen Status.
 *
 * <b>Der Strich in „Summe" haengt am Kennzeichen, nicht an einer 0 vom Server</b> (Plan #218, E16).
 * Menge und Preis bleiben beim Wechsel extern -> intern erhalten, der Server liefert die
 * gespeicherte Summe weiter; eine 0 zu zeigen behauptete, es gaebe sie nicht. Der Halbgeviertstrich
 * steht in derselben Zelle mit denselben Formatangaben wie der Betrag: Eine leere Zelle liest sich
 * fuer den Screenreader wie eine fehlende Angabe, der Strich sagt „hier gibt es keinen Betrag".
 */

const SPALTEN: readonly TafelSpalte[] = [
  'Titel',
  'Datum',
  { beschriftung: 'Summe', zahl: true },
  'Status',
];
const LEER = 'Noch kein Angebot an diese Firma.';

/**
 * Was in „Titel" steht, wenn es keinen Titel gibt, und in „Summe", wenn es keinen Betrag gibt
 * (Halbgeviertstrich). Eine Konstante fuer beide Zellen (E10): Kriterium 4 verlangt denselben Strich.
 */
const OHNE_ANGABE = '\u2013';

/** Der Weg zum Angebot — er liegt auf dem Titel oder, beim Angebot ohne Titel, auf dem Datum. */
function Weg({
  angebot,
  className,
  children,
}: {
  readonly angebot: AngebotZeile;
  readonly className?: string;
  readonly children: string;
}) {
  return (
    <Box
      component={RouterLink}
      to={`/angebote/${String(angebot.id)}`}
      className={className}
      sx={(theme) => ({
        color: 'inherit',
        textDecoration: 'none',
        '&:hover': { color: theme.vars.palette.kupferwolke.kupfer },
      })}
    >
      {children}
    </Box>
  );
}

function Zeile({ angebot }: { readonly angebot: AngebotZeile }) {
  const datum = tagWort(angebot.angebotDatum);
  return (
    <Box component="tr">
      <Box component="td" sx={{ overflowWrap: 'anywhere' }}>
        {angebot.titel === null ? OHNE_ANGABE : <Weg angebot={angebot}>{angebot.titel}</Weg>}
      </Box>
      <Box
        component="td"
        sx={{
          fontWeight: 500,
          whiteSpace: 'nowrap',
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
        }}
      >
        {angebot.titel === null ? (
          <Weg angebot={angebot} className={ZAHLEN_KLASSE}>
            {datum}
          </Weg>
        ) : (
          <span className={ZAHLEN_KLASSE}>{datum}</span>
        )}
        {angebot.intern ? <InternChip /> : null}
        {angebot.storniert ? <StorniertChip /> : null}
      </Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={{ fontWeight: 600, whiteSpace: 'nowrap', textAlign: 'right' }}
      >
        {angebot.intern ? OHNE_ANGABE : euro(angebot.summeInCent)}
      </Box>
      <Box component="td">
        <AngebotsstatusChip status={angebot.status} />
      </Box>
    </Box>
  );
}

export interface AngebotslisteProps {
  readonly angebote: readonly AngebotZeile[];
}

export default function Angebotsliste({ angebote }: AngebotslisteProps) {
  if (angebote.length === 0) {
    return (
      <Typography
        role="status"
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textMatt })}
      >
        {LEER}
      </Typography>
    );
  }
  return (
    <Tafel beschriftung="Angebote" spalten={SPALTEN}>
      {angebote.map((angebot) => (
        <Zeile key={angebot.id} angebot={angebot} />
      ))}
    </Tafel>
  );
}
