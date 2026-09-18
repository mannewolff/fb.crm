import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';

/**
 * Die Marke: Kupfer-Mal und Name (Vorlage Z. 216–241, Markup Z. 1103–1114).
 *
 * Die Versionsnummer ist ein optionales Prop und bleibt auf den Auth-Seiten leer: Sie kommt
 * aus `GET /api/instance`, und der Pfad verlangt eine Sitzung (E10, Issue #26). Vor der
 * Anmeldung gibt es keine — die Platte darf dort also auch keine zeigen (K15).
 */

/** Kantenlaenge des Mals in Pixeln (Vorlage Z. 223). */
const MAL = 30;

/**
 * Lichtkante und kurzer Schatten des Mals (Vorlage Z. 226–228).
 *
 * Steht hier und nicht im Theme: Die vier Tiefenstufen der Kupferwarte beschreiben Nut,
 * Platte, Abgehoben und Taste; das Mal ist keine davon, und die Vorlage schreibt seinen
 * Schatten ebenfalls am Bauteil fest.
 */
const MAL_SCHATTEN = '0 1px 0 rgba(255,255,255,.35) inset, 0 2px 6px rgba(0,0,0,.35)';

export interface BrandMarkProps {
  /** Versionsstand der Instanz; ohne ihn bleibt die Zeile unter dem Namen leer. */
  readonly version?: string;
}

export default function BrandMark({ version }: BrandMarkProps) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: '10px', paddingInline: '4px' }}>
      <Box
        data-testid="marke-mal"
        aria-hidden
        sx={(theme) => ({
          width: MAL,
          height: MAL,
          borderRadius: '9px',
          background: `linear-gradient(155deg, ${theme.vars.palette.kupferwarte.kupferHell}, ${theme.vars.palette.kupferwarte.kupfer} 62%, ${theme.vars.palette.kupferwarte.kupferTief})`,
          boxShadow: MAL_SCHATTEN,
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
      <Box sx={{ lineHeight: 1.15 }}>
        <Typography
          component="span"
          sx={(theme) => ({
            display: 'block',
            fontFamily: theme.typography.h1.fontFamily,
            fontStretch: '118%',
            fontWeight: 700,
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
              color: theme.vars.palette.kupferwarte.textSchwach,
            })}
          >
            {version}
          </Typography>
        )}
      </Box>
    </Box>
  );
}
