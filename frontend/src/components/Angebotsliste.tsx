import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { Link as RouterLink } from 'react-router-dom';

import type { AngebotZeile } from '../api/angebote';
import { euro } from '../lib/geld';
import { tagWort } from '../lib/tag';
import { ZAHLEN_KLASSE } from '../theme';
import AngebotsstatusChip from './AngebotsstatusChip';
import Tafel from './Tafel';

/**
 * Die Angebote einer Firma als Tafel (Kriterium 7).
 *
 * <b>Nicht sortiert.</b> Die Reihenfolge kommt vom Server — neueste zuerst. Eine zweite Sortierung
 * hier waere eine zweite Wahrheit.
 *
 * <b>Der Weg liegt auf dem Datum, nicht auf der Zeile.</b> Eine ganze Tabellenzeile klickbar zu
 * machen hiesse, ein `tr` mit `onClick` zu versehen — kein Ziel des Tabulators und fuer den
 * Screenreader kein Weg. Ein Angebot hat keine Nummer; das Datum ist, was es in der Liste benennt.
 */

const SPALTEN = ['Datum', 'Status', 'Summe'] as const;
const LEER = 'Noch kein Angebot an diese Firma.';

function Zeile({ angebot }: { readonly angebot: AngebotZeile }) {
  return (
    <Box component="tr">
      <Box component="td" sx={{ fontWeight: 500, whiteSpace: 'nowrap' }}>
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
      </Box>
      <Box component="td">
        <AngebotsstatusChip status={angebot.status} />
      </Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={{ fontWeight: 600, whiteSpace: 'nowrap', textAlign: 'right' }}
      >
        {euro(angebot.summeInCent)}
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
    <Tafel beschriftung="Angebote" spalten={[...SPALTEN]}>
      {angebote.map((angebot) => (
        <Zeile key={angebot.id} angebot={angebot} />
      ))}
    </Tafel>
  );
}
