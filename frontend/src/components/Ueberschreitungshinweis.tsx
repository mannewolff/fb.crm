import Box from '@mui/material/Box';
import { IconAlertTriangle } from '@tabler/icons-react';

import { dezimal } from '../lib/geld';

/**
 * Der Hinweis an einer Zeile, die ueber das Angebot hinausgeht (#160, Kriterien 8 und 26).
 *
 * <b>Wort und Symbol, nicht Farbe allein</b> (CLAUDE-design.md, „Zustandsformen"): Die Toenung
 * stuetzt die Aussage, sie traegt sie nicht.
 *
 * Er steht hier und nicht in einer Ansicht, weil ihn zwei zeigen: die Entwurfsmaske der Rechnung
 * an der Menge, die gerade getippt wird ({@link RechnungPage}), und die Angebotsansicht an der
 * Position, deren Rechnungen zusammen zu viel ergeben ({@link AngebotPage}). Zweimal derselbe Satz
 * in zwei Dateien hiesse, ihn auch zweimal zu aendern.
 *
 * Die Kennung im `data-testid` ist die der <b>Angebotsposition</b> — an ihr haengt der Stand in
 * beiden Ansichten.
 */

/** Die Symbolgroesse im Hinweis an einer Zeile. */
const SYMBOL_HINWEIS = 14;

export interface UeberschreitungshinweisProps {
  /** Was mit dieser Menge zusammen zu viel waere, in Hundertsteln — stets groesser als 0. */
  readonly mengeInHundertsteln: number;
  /** Die Kennung der Angebotsposition, an der der Hinweis steht. */
  readonly angebotPositionId: number;
}

export default function Ueberschreitungshinweis({
  mengeInHundertsteln,
  angebotPositionId,
}: UeberschreitungshinweisProps) {
  return (
    <Box
      component="span"
      data-testid={`zeile-hinweis-${String(angebotPositionId)}`}
      sx={(theme) => ({
        display: 'inline-flex',
        alignItems: 'center',
        gap: '4px',
        color: theme.vars.palette.kupferwolke.toenung.bernstein.schrift,
      })}
    >
      <IconAlertTriangle size={SYMBOL_HINWEIS} stroke={1.8} aria-hidden />
      {`${dezimal(mengeInHundertsteln, ',')} über dem Angebot`}
    </Box>
  );
}
