import Box from '@mui/material/Box';
import { Link as RouterLink } from 'react-router-dom';

import { tagWort } from '../lib/tag';
import { ZAHLEN_KLASSE } from '../theme';

/** Der Weg zum Angebot, auf seinem Datum — die Gestalt der Zeilenverweise aller Tafeln. */
export default function Angebotsweg({
  angebotId,
  tag,
}: {
  readonly angebotId: number;
  readonly tag: string;
}) {
  return (
    <Box
      component={RouterLink}
      to={`/angebote/${String(angebotId)}`}
      className={ZAHLEN_KLASSE}
      sx={(theme) => ({
        color: 'inherit',
        textDecoration: 'none',
        '&:hover': { color: theme.vars.palette.kupferwolke.kupfer },
      })}
    >
      {tagWort(tag)}
    </Box>
  );
}
