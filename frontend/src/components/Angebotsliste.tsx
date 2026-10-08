import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { Link as RouterLink } from 'react-router-dom';

import type { AngebotZeile } from '../api/angebote';
import { euro } from '../lib/geld';
import { tagWort } from '../lib/tag';
import { ZAHLEN_KLASSE } from '../theme';
import AngebotsstatusChip from './AngebotsstatusChip';
import InternChip from './InternChip';
import Tafel, { type TafelSpalte } from './Tafel';

/**
 * Die Angebote einer Firma als Tafel (Kriterium 7).
 *
 * <b>Nicht sortiert.</b> Die Reihenfolge kommt vom Server — neueste zuerst. Eine zweite Sortierung
 * hier waere eine zweite Wahrheit.
 *
 * <b>Der Weg liegt auf dem Datum, nicht auf der Zeile.</b> Eine ganze Tabellenzeile klickbar zu
 * machen hiesse, ein `tr` mit `onClick` zu versehen — kein Ziel des Tabulators und fuer den
 * Screenreader kein Weg. Ein Angebot hat keine Nummer; das Datum ist, was es in der Liste benennt.
 *
 * <b>Das Kennzeichen „Intern" steht hinter dem Datum</b> (Issue #233) — in der Spalte, die das
 * Angebot benennt, und nicht in einer sechsten Spalte, die bei 768 px der Summe den Platz nimmt.
 *
 * <b>Der Strich in „Summe" haengt am Kennzeichen, nicht an einer 0 vom Server</b> (Plan #218, E16).
 * Menge und Preis bleiben beim Wechsel extern -> intern erhalten, der Server liefert die
 * gespeicherte Summe weiter; eine 0 zu zeigen behauptete, es gaebe sie nicht. Der Halbgeviertstrich
 * steht in derselben Zelle mit denselben Formatangaben wie der Betrag: Eine leere Zelle liest sich
 * fuer den Screenreader wie eine fehlende Angabe, der Strich sagt „hier gibt es keinen Betrag".
 */

const SPALTEN: readonly TafelSpalte[] = [
  'Datum',
  'Status',
  { beschriftung: 'Summe', zahl: true },
];
const LEER = 'Noch kein Angebot an diese Firma.';

/** Was in „Summe" steht, wenn es keinen Betrag gibt (Halbgeviertstrich). */
const OHNE_BETRAG = '\u2013';

function Zeile({ angebot }: { readonly angebot: AngebotZeile }) {
  return (
    <Box component="tr">
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
        <Box
          component={RouterLink}
          to={`/angebote/${String(angebot.id)}`}
          className={ZAHLEN_KLASSE}
          sx={(theme) => ({
            color: 'inherit',
            textDecoration: 'none',
            '&:hover': { color: theme.vars.palette.kupferwolke.kupfer },
          })}
        >
          {tagWort(angebot.angebotDatum)}
        </Box>
        {angebot.intern ? <InternChip /> : null}
      </Box>
      <Box component="td">
        <AngebotsstatusChip status={angebot.status} />
      </Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={{ fontWeight: 600, whiteSpace: 'nowrap', textAlign: 'right' }}
      >
        {angebot.intern ? OHNE_BETRAG : euro(angebot.summeInCent)}
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
