import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { IconChartBar } from '@tabler/icons-react';

import { RADIUS_KLEIN } from '../theme';

/**
 * Die Marke: Kupfer-Mal und Name (Vorlage `.marke` Z. 31–34).
 *
 * Das Mal ist 42 px gross, traegt Radius 14, den Verlauf Kupfer-Glanz → Kupfer und den farbigen
 * Kupfer-Schatten; das Symbol kommt aus der Symbolfamilie Tabler wie in der Vorlage (E3).
 *
 * Die Versionsnummer ist ein optionales Prop und bleibt auf den Auth-Seiten leer: Sie kommt
 * aus `GET /api/instance`, und der Pfad verlangt eine Sitzung (E10, Issue #26). Vor der
 * Anmeldung gibt es keine — die Karte darf dort also auch keine zeigen (K15).
 */

/** Kantenlaenge des Mals in Pixeln (CLAUDE-design.md, „Rahmen"). */
const MAL = 42;

export interface BrandMarkProps {
  /** Versionsstand der Instanz; ohne ihn bleibt die Zeile unter dem Namen leer. */
  readonly version?: string;
  /** Nur das Mal — fuer die eingeklappte Schiene, in der Name und Version keinen Platz haben. */
  readonly kompakt?: boolean;
}

export default function BrandMark({ version, kompakt = false }: BrandMarkProps) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: '12px', minWidth: 0 }}>
      <Box
        data-testid="marke-mal"
        aria-hidden
        sx={(theme) => ({
          width: MAL,
          height: MAL,
          borderRadius: `${RADIUS_KLEIN}px`,
          // Markenmal: Verlauf Kupfer-Glanz → Kupfer mit dem farbigen Kupfer-Schatten
          // (CLAUDE-design.md, „Rahmen"; Vorlage `.marke .mal` Z. 32).
          background: `linear-gradient(135deg, ${theme.vars.palette.kupferwolke.kupferGlanz}, ${theme.vars.palette.kupferwolke.kupfer})`,
          boxShadow: theme.vars.palette.kupferwolke.schatten.kupfer,
          color: theme.vars.palette.kupferwolke.kupferSchrift,
          display: 'grid',
          placeItems: 'center',
          flex: 'none',
        })}
      >
        <IconChartBar size={22} stroke={1.9} aria-hidden />
      </Box>
      {kompakt ? null : (
        <Box sx={{ lineHeight: 1.2, minWidth: 0 }}>
          <Typography
            component="span"
            variant="h3"
            sx={{ display: 'block', letterSpacing: '-.01em' }}
          >
            fb.crm
          </Typography>
          {version === undefined ? null : (
            <Typography
              component="span"
              data-testid="marke-zusatz"
              sx={(theme) => ({
                display: 'block',
                fontSize: 12,
                color: theme.vars.palette.kupferwolke.textSchwach,
              })}
            >
              {version}
            </Typography>
          )}
        </Box>
      )}
    </Box>
  );
}
