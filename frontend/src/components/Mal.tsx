import Box from '@mui/material/Box';

import { initialen } from '../lib/initials';
import type { ToenungName } from '../theme';
import { RADIUS_MAL, RADIUS_RUND } from '../theme';

/**
 * Das Mal: das Kuerzel eines Namens auf einer Toenung (Vorlage `.avatar` Z. 43, `.firmenmal`
 * Z. 57).
 *
 * Zwei Formen, eine Bedeutung: **Personen** tragen den Kreis, **Firmen** das abgerundete Quadrat
 * (CLAUDE-design.md, „Radien": 26 px beim 84-px-Mal). Die Form unterscheidet damit ohne ein Wort,
 * ob ein Kuerzel fuer einen Menschen oder ein Haus steht.
 *
 * Das Kuerzel kommt aus `lib/initials.ts` — dieselbe Regel wie in der Nutzerkarte der Schiene, und
 * dieselbe Antwort „?" fuer einen Namen ohne Buchstaben.
 */
export interface MalProps {
  /** Der Name, aus dem das Kuerzel entsteht — und der zugaengliche Name, wo das Mal allein steht. */
  readonly name: string;
  /** Kantenlaenge in Pixeln: 84 in der Kopfkarte, 48 in einer Innenkarte, 36–42 in Zeilen. */
  readonly groesse: number;
  /** Die Toenung nach Bedeutung (CLAUDE-design.md, „Toenungen"). */
  readonly toenung: ToenungName;
  /** Kreis fuer Personen (Vorgabe), abgerundetes Quadrat fuer Firmen. */
  readonly form?: 'kreis' | 'quadrat';
  /**
   * Vorgabe: dekorativ. Wo der Name daneben steht, doppelte das vorgelesene Kuerzel ihn nur.
   * Steht das Mal **allein**, traegt es mit `dekorativ={false}` den Namen als Bild-Beschriftung.
   */
  readonly dekorativ?: boolean;
}

/** Das Kuerzel fuellt etwa ein Drittel der Kante (Vorlage: 28 px Schrift im 84-px-Mal). */
const SCHRIFT_ANTEIL = 3;

export default function Mal({
  name,
  groesse,
  toenung,
  form = 'kreis',
  dekorativ = true,
}: MalProps) {
  const quadrat = form === 'quadrat';
  return (
    <Box
      data-testid="mal"
      {...(dekorativ ? { 'aria-hidden': true } : { role: 'img', 'aria-label': name })}
      sx={(theme) => ({
        width: groesse,
        height: groesse,
        flex: 'none',
        borderRadius: `${String(quadrat ? RADIUS_MAL : RADIUS_RUND)}px`,
        display: 'grid',
        placeItems: 'center',
        fontSize: Math.round(groesse / SCHRIFT_ANTEIL),
        // Das Firmenmal traegt sein Kuerzel in 800 (CLAUDE-design.md, „Bausteine"), das
        // Personen-Mal in 700 (Vorlage `.avatar` Z. 43).
        fontWeight: quadrat ? 800 : 700,
        letterSpacing: quadrat ? '-.02em' : 'normal',
        color: theme.vars.palette.kupferwolke.toenung[toenung].schrift,
        backgroundColor: theme.vars.palette.kupferwolke.toenung[toenung].flaeche,
      })}
    >
      {initialen(name)}
    </Box>
  );
}
