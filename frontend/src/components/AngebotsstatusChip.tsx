import type { Angebotsstatus } from '../lib/angebotsstatus';
import { angebotsstatusBild } from '../lib/angebotsstatus';
import ZustandsChip from './ZustandsChip';

/**
 * Der Status eines Angebots als Chip — in der Ansicht, in der Liste an der Firma und in der
 * Uebersicht aller Angebote dieselbe Form (Issue #127).
 */

const SYMBOL_CHIP = 13;

export interface AngebotsstatusChipProps {
  readonly status: Angebotsstatus;
}

export default function AngebotsstatusChip({ status }: AngebotsstatusChipProps) {
  const bild = angebotsstatusBild(status);
  const Zeichen = bild.symbol;
  return (
    <ZustandsChip
      wort={bild.wort}
      toenung={bild.toenung}
      symbol={<Zeichen size={SYMBOL_CHIP} stroke={1.8} />}
    />
  );
}
