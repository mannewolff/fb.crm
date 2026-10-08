import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';

import type { ToenungName } from '../theme';
import { RADIUS_KACHEL, RADIUS_SYMBOLFELD } from '../theme';

/**
 * Die Kennzahl-Kachel: eine Zahl auf einer Toenung (Vorlage `.zahl-karte` Z. 71–74, HTML Z. 152–154;
 * CLAUDE-design.md, „Bausteine").
 *
 * Sie ist <b>reine Darstellung</b>: Beschriftung, Zahl und die Zweitzeile kommen fertig formatiert
 * herein. Die Kachel rechnet nichts und formatiert nichts — wer eine Kennzahl zeigt, weiss, in
 * welcher Einheit sie steht (Arbeitspaket 215). <b>Ob die Zweitzeile ueberhaupt dasteht,
 * entscheidet der Aufrufer</b>, indem er sie uebergibt oder weglaesst; die Kachel kennt keine
 * Bedingung dafuer.
 *
 * <b>Die Toenung kommt als Name</b>, nicht als Farbpaar: So bleibt ihre Bedeutung im Aufruf lesbar,
 * und kein Aufrufer paart eine Flaeche mit der Schrift einer anderen Toenung (wie `Mal`).
 *
 * <b>Die Beschriftung steht in der vollen Schrift der Toenung, nicht in `opacity: .85`</b> wie in der
 * Vorlage: Gegen vier der sechs Toenungen faellt sie damit unter 4,5:1, und AA gilt in 13 px ohne
 * Ausnahme. CLAUDE-design.md — die bindende Quelle, wo sie von der Vorlage abweicht — verlangt fuer
 * die Beschriftung nur 13 px / 600 und keine Deckkraft; Accessibility steht ueber der visuellen
 * Praeferenz (CLAUDE.md, Prioritaeten).
 *
 * <b>Die Zahl traegt keine Tabellenziffern</b> (`ZAHLEN_KLASSE`): „Eine grosse Einzelzahl
 * (Kachel) braucht keine Tabellenziffern" (CLAUDE-design.md, Typografie) — anders als die Vorlage,
 * und auch hier gilt die bindende Datei.
 */

/** Kantenlaenge des Symbolfelds (CLAUDE-design.md, „Bausteine": 48 px). */
const SYMBOLFELD = 48;

export interface KennzahlkachelProps {
  /** Die Toenung nach Bedeutung (CLAUDE-design.md, „Toenungen"). */
  readonly toenung: ToenungName;
  /** Das Symbol im Feld links. Es stuetzt die Beschriftung und wird nie vorgelesen. */
  readonly symbol: ReactNode;
  /** Was die Zahl bedeutet — etwa „Abgerechnet". */
  readonly beschriftung: string;
  /** Die Zahl, fertig formatiert — etwa „1.800,00 €" oder „3". */
  readonly zahl: string;
  /** Eine zweite Zeile unter der Zahl, etwa der Bruttobetrag oder der Wert des Monats. */
  readonly zweitzeile?: string;
}

export default function Kennzahlkachel({
  toenung,
  symbol,
  beschriftung,
  zahl,
  zweitzeile,
}: KennzahlkachelProps) {
  return (
    <Box
      data-testid="kennzahlkachel"
      sx={(theme) => ({
        display: 'flex',
        alignItems: 'center',
        gap: '16px',
        padding: '16px 20px',
        borderRadius: `${RADIUS_KACHEL}px`,
        backgroundColor: theme.vars.palette.kupferwolke.toenung[toenung].flaeche,
        color: theme.vars.palette.kupferwolke.toenung[toenung].schrift,
      })}
    >
      <Box
        data-testid="kennzahlkachel-symbol"
        aria-hidden
        sx={(theme) => ({
          width: SYMBOLFELD,
          height: SYMBOLFELD,
          flex: 'none',
          display: 'grid',
          placeItems: 'center',
          fontSize: 22,
          borderRadius: `${RADIUS_SYMBOLFELD}px`,
          backgroundColor: theme.vars.palette.kupferwolke.schleier,
        })}
      >
        {symbol}
      </Box>
      <Box sx={{ minWidth: 0 }}>
        <Typography component="div" sx={{ fontSize: 13, fontWeight: 600 }}>
          {beschriftung}
        </Typography>
        <Typography
          component="div"
          sx={{ fontSize: 26, fontWeight: 800, letterSpacing: '-.02em' }}
        >
          {zahl}
        </Typography>
        {zweitzeile === undefined ? null : (
          <Typography
            data-testid="kennzahlkachel-zweitzeile"
            component="div"
            sx={{ fontSize: 13, fontWeight: 500 }}
          >
            {zweitzeile}
          </Typography>
        )}
      </Box>
    </Box>
  );
}
