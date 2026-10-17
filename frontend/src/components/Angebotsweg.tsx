import Box from '@mui/material/Box';
import { Link as RouterLink } from 'react-router-dom';

import { tagWort } from '../lib/tag';
import { ZAHLEN_KLASSE } from '../theme';

/**
 * Der Weg zum Angebot — die Gestalt der Zeilenverweise aller Tafeln.
 *
 * Mit Titel traegt der Titel den Weg, sonst wie vor #316 das Datum (Issue #324, E2). Ein langer
 * Titel bricht um, statt gekuerzt zu werden: Sonst verschwaende gerade der unterscheidende Teil, und
 * `overflowWrap: 'anywhere'` faengt auch den Titel ohne ein einziges Leerzeichen (E10).
 */
export default function Angebotsweg({
  angebotId,
  tag,
  titel,
}: {
  readonly angebotId: number;
  readonly tag: string;
  readonly titel: string | null;
}) {
  return (
    <Box
      component={RouterLink}
      to={`/angebote/${String(angebotId)}`}
      className={titel === null ? ZAHLEN_KLASSE : undefined}
      sx={(theme) => ({
        color: 'inherit',
        textDecoration: 'none',
        ...(titel === null ? {} : { overflowWrap: 'anywhere' }),
        '&:hover': { color: theme.vars.palette.kupferwolke.kupfer },
      })}
    >
      {titel ?? tagWort(tag)}
    </Box>
  );
}
