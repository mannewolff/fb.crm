import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';

import { RADIUS_KLEIN } from '../theme';

/**
 * Die Marke: Kupfer-Mal und Name (Vorlage `.marke` Z. 31–34).
 *
 * Die Versionsnummer ist ein optionales Prop und bleibt auf den Auth-Seiten leer: Sie kommt
 * aus `GET /api/instance`, und der Pfad verlangt eine Sitzung (E10, Issue #26). Vor der
 * Anmeldung gibt es keine — die Platte darf dort also auch keine zeigen (K15).
 */

/** Kantenlaenge des Mals in Pixeln. */
const MAL = 30;


export interface BrandMarkProps {
  /** Versionsstand der Instanz; ohne ihn bleibt die Zeile unter dem Namen leer. */
  readonly version?: string;
  /** Nur das Mal — fuer die eingeklappte Schiene, in der Name und Version keinen Platz haben. */
  readonly kompakt?: boolean;
}

export default function BrandMark({ version, kompakt = false }: BrandMarkProps) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: '10px', paddingInline: '4px' }}>
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
          display: 'grid',
          placeItems: 'center',
          flex: 'none',
        })}
      >
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
          <rect x="1.5" y="2" width="3.6" height="12" rx="1.2" fill="#fff" fillOpacity=".92" />
          <rect x="6.2" y="2" width="3.6" height="8" rx="1.2" fill="#fff" fillOpacity=".72" />
          <rect x="10.9" y="2" width="3.6" height="5" rx="1.2" fill="#fff" fillOpacity=".5" />
        </svg>
      </Box>
      {kompakt ? null : (
      <Box sx={{ lineHeight: 1.15 }}>
        <Typography
          component="span"
          sx={(theme) => ({
            display: 'block',
            fontFamily: theme.typography.h3.fontFamily,
            fontWeight: 800,
            fontSize: 14,
            letterSpacing: '-.01em',
          })}
        >
          fb.crm
        </Typography>
        {version === undefined ? null : (
          <Typography
            component="span"
            data-testid="marke-zusatz"
            sx={(theme) => ({
              display: 'block',
              fontSize: 10.5,
              letterSpacing: '.04em',
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
