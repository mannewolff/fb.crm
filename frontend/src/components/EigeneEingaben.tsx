import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { useId } from 'react';

import { RADIUS_KLEIN } from '../theme';
import WeicheTaste from './WeicheTaste';

/**
 * Die eigenen Eingaben nach einer Abweisung, schreibgeschuetzt und zum Kopieren (#411,
 * Kriterien 3 und 6).
 *
 * Die Regel fuer alle Ansichten (Plan #415, E12): „Neu laden" ersetzt den Inhalt der Maske durch
 * den Stand des Servers samt neuer Version, und die eigenen Werte stehen hier daneben, bis
 * „Verwerfen" gewaehlt wird. So traegt man die eigene Aenderung auf dem neuen Stand erneut ein,
 * ohne dass etwas still verloren geht.
 *
 * Die Werte stehen als <b>Text und nicht als Eingabefeld</b>: Ein zweites Feld mit derselben
 * Beschriftung neben der Maske machte jede Beschriftung doppelt, und ein Feld laedt zum Tippen ein,
 * wo nur kopiert werden soll. Ausgeblendet wird die Komponente von der Ansicht, die die Werte haelt
 * — sie selbst meldet nur „Verwerfen".
 */

/** Eine eigene Eingabe: die Beschriftung des Feldes und was darin stand. */
export interface EigeneEingabe {
  readonly bezeichnung: string;
  readonly wert: string;
}

export interface EigeneEingabenProps {
  readonly werte: readonly EigeneEingabe[];
  /** Die eigenen Werte werden nicht mehr gebraucht. */
  readonly onVerwerfen: () => void;
}

/** Was fuer ein leeres Feld steht — eine fehlende Zeile laese sich wie ein vergessenes Feld. */
const LEER = '(leer)';

export default function EigeneEingaben({ werte, onVerwerfen }: EigeneEingabenProps) {
  const titelId = useId();
  return (
    <Box
      component="section"
      aria-labelledby={titelId}
      sx={(theme) => ({
        display: 'grid',
        gap: '10px',
        padding: '14px 16px',
        borderRadius: `${String(RADIUS_KLEIN)}px`,
        background: theme.vars.palette.kupferwolke.flaecheWeich,
        boxShadow: `inset 0 0 0 1px ${theme.vars.palette.kupferwolke.linie}`,
      })}
    >
      <Typography id={titelId} component="h2" variant="subtitle2">
        Deine Eingaben
      </Typography>
      <Box
        component="dl"
        sx={{
          display: 'grid',
          gridTemplateColumns: 'max-content 1fr',
          columnGap: '16px',
          rowGap: '6px',
          margin: 0,
          userSelect: 'text',
        }}
      >
        {werte.map((eingabe) => (
          <Box key={eingabe.bezeichnung} sx={{ display: 'contents' }}>
            <Box
              component="dt"
              sx={(theme) => ({ color: theme.vars.palette.kupferwolke.textMatt })}
            >
              {eingabe.bezeichnung}
            </Box>
            <Box
              component="dd"
              sx={{ margin: 0, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}
            >
              {eingabe.wert === '' ? LEER : eingabe.wert}
            </Box>
          </Box>
        ))}
      </Box>
      <Box>
        <WeicheTaste onClick={onVerwerfen}>Verwerfen</WeicheTaste>
      </Box>
    </Box>
  );
}
