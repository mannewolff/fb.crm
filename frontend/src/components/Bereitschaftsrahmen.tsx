import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';

import { ABSTAND_BUEHNE } from '../theme';
import Karte from './Karte';

/**
 * Was eine Ansicht zeigt, solange ihre Daten nicht bereitstehen (Issue #490): eine Karte mit dem
 * Ladetext oder mit dem Fehler, in der Spalte der Buehne.
 *
 * Genau eine der beiden Angaben steht — die Ansicht weiss, ob sie noch laedt oder ob es
 * fehlschlug, und nennt den Satz dazu selbst.
 */
export type BereitschaftsrahmenProps =
  | { /** Der Satz, solange geladen wird. */ readonly laedt: string }
  | { /** Der Satz zum Fehlschlag. */ readonly fehler: string };

export default function Bereitschaftsrahmen(props: BereitschaftsrahmenProps) {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: ABSTAND_BUEHNE }}>
      <Karte>
        {'laedt' in props ? (
          <Typography
            sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textSchwach })}
          >
            {props.laedt}
          </Typography>
        ) : (
          <Alert severity="error">{props.fehler}</Alert>
        )}
      </Karte>
    </Box>
  );
}
