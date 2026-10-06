import Box from '@mui/material/Box';
import { Fragment } from 'react';
import type { ReactNode } from 'react';

/** Eine Zeile der Liste: die Bezeichnung und ihr Wert. */
export interface Angabe {
  readonly name: string;
  readonly wert: ReactNode;
}

interface AngabenlisteProps {
  readonly zeilen: readonly Angabe[];
  /** Bezeichnung und Wert mittig zueinander — fuer Werte, die hoeher sind als eine Textzeile. */
  readonly mittig?: boolean;
  readonly testId?: string;
}

/**
 * Die Beschriftung–Wert-Liste (CLAUDE-design.md, „Bausteine"): `dl` im Raster, die Bezeichnung in
 * Text schwach, der Wert in Schriftstaerke 500. Auf schmalen Schirmen steht der Wert unter der
 * Bezeichnung, ab `sm` daneben.
 *
 * Angebotsansicht und Firmenansicht zeigen ihre Angaben so; ein Baustein statt zweier Abschriften.
 */
export default function Angabenliste({ zeilen, mittig = false, testId }: AngabenlisteProps) {
  return (
    <Box
      component="dl"
      data-testid={testId}
      sx={{
        display: 'grid',
        gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: 'auto minmax(0, 1fr)' },
        alignItems: mittig ? 'center' : undefined,
        gap: '10px 20px',
        margin: 0,
        fontSize: 13.5,
      }}
    >
      {zeilen.map((zeile) => (
        <Fragment key={zeile.name}>
          <Box
            component="dt"
            sx={(theme) => ({ color: theme.vars.palette.kupferwolke.textSchwach })}
          >
            {zeile.name}
          </Box>
          <Box component="dd" sx={{ margin: 0, fontWeight: 500 }}>
            {zeile.wert}
          </Box>
        </Fragment>
      ))}
    </Box>
  );
}
