import Box from '@mui/material/Box';
import type { ReactNode } from 'react';

import type { ToenungName } from '../theme';
import { KENNZAHL_TYPOGRAFIE, RADIUS_KACHEL, RADIUS_SYMBOLFELD, ZAHLEN_KLASSE } from '../theme';

/**
 * Die Kennzahl-Kachel: eine Zahl mit ihrer Beschriftung auf einer Toenung, links ein Symbolfeld
 * (Vorlage `.zahl-karte` Z. 71–74 und ihr HTML Z. 152–154; CLAUDE-design.md, „Bausteine":
 * Raster aus zwei bis vier Kacheln).
 *
 * Sie ist der Baustein, auf den `KENNZAHL_TYPOGRAFIE` in `theme.ts` wartet — die Typografie stand
 * dort schon, die Kachel entsteht mit der ersten Auswertung, die sie braucht.
 *
 * **Die Kachel zeigt an, sie fuehrt nicht.** Sie ist kein Tastaturziel: kein `tabIndex`, keine
 * Rolle, kein Weg. Eine Flaeche, die sich anfassen laesst, ohne irgendwohin zu fuehren, ist eine
 * leere Verheissung im Tabulatorweg. Wer von einer Zahl aus weiterwill, tut es ueber die Zeilen
 * darunter.
 *
 * **Die Zahl kommt fertig gesetzt herein.** Die Kachel rechnet und formatiert nicht: Betraege
 * entstehen mit `lib/geld.ts`, Stueckzahlen stehen als Text da. Eine Kachel, die selbst
 * formatierte, waere eine zweite Regel neben der einen fuer Geld.
 *
 * Die Toenung kommt als **Name** herein wie bei {@link Mal} und {@link ZustandsChip}: Jede Toenung
 * traegt eine feste Bedeutung, und kein Aufrufer kann eine Flaeche mit der Schrift einer anderen
 * paaren.
 */
export interface KennzahlProps {
  /** Was die Zahl bedeutet — „Pipeline netto", „Umsatz 2026". */
  readonly beschriftung: string;
  /** Die Zahl, fertig gesetzt — „18.450,00 €", „3". */
  readonly zahl: string;
  /** Die Toenung nach Bedeutung (CLAUDE-design.md, „Toenungen"). */
  readonly toenung: ToenungName;
  /** Das stuetzende Symbol im Feld links; es traegt keine eigene Aussage. */
  readonly symbol: ReactNode;
}

/** Kantenlaenge des Symbolfeldes (Vorlage `.zahl-karte .ikon` Z. 73). */
const SYMBOLFELD = 48;

export default function Kennzahl({ beschriftung, zahl, toenung, symbol }: KennzahlProps) {
  return (
    <Box
      data-testid="kennzahl"
      sx={(theme) => ({
        display: 'flex',
        alignItems: 'center',
        gap: '16px',
        borderRadius: `${RADIUS_KACHEL}px`,
        padding: '22px 24px',
        color: theme.vars.palette.kupferwolke.toenung[toenung].schrift,
        backgroundColor: theme.vars.palette.kupferwolke.toenung[toenung].flaeche,
      })}
    >
      <Box
        data-testid="kennzahl-symbol"
        aria-hidden
        sx={(theme) => ({
          width: SYMBOLFELD,
          height: SYMBOLFELD,
          flex: 'none',
          display: 'grid',
          placeItems: 'center',
          borderRadius: `${RADIUS_SYMBOLFELD}px`,
          // Halbtransparentes Weiss (Vorlage Z. 73), abgeleitet aus der Flaeche des Themes:
          // ein zweiter Farbwert im Baustein waere ein Wert neben der einen Wertequelle.
          background: `color-mix(in srgb, ${theme.vars.palette.kupferwolke.flaeche} 70%, transparent)`,
        })}
      >
        {symbol}
      </Box>
      <Box>
        <Box
          component="small"
          sx={{ display: 'block', fontSize: 13, fontWeight: 600, opacity: 0.85 }}
        >
          {beschriftung}
        </Box>
        <Box
          component="b"
          data-testid="kennzahl-zahl"
          // Tabellenziffern: Die Kacheln stehen als Raster nebeneinander, ihre Zahlen damit
          // untereinander (Vorlage `.zahl-karte b` Z. 74).
          className={ZAHLEN_KLASSE}
          sx={{ display: 'block', ...KENNZAHL_TYPOGRAFIE }}
        >
          {zahl}
        </Box>
      </Box>
    </Box>
  );
}
