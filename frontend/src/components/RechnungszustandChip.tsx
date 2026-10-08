import { rechnungszustandBild } from '../lib/rechnungszustand';
import type { Rechnungszustand } from '../lib/rechnungszustand';
import ZustandsChip from './ZustandsChip';

/**
 * Der Zustand einer Rechnung als Chip — in der Liste, in der Ansicht der einzelnen Rechnung und am
 * Angebot dieselbe Form (#160, Kriterium 15).
 *
 * Dasselbe Muster wie {@link AngebotsstatusChip}: Die Zuordnung Zustand → Wort, Toenung und Symbol
 * liegt in `lib/rechnungszustand.ts`, die Groesse des Symbols hier.
 */

const SYMBOL_CHIP = 13;

export interface RechnungszustandChipProps {
  readonly zustand: Rechnungszustand;
}

export default function RechnungszustandChip({ zustand }: RechnungszustandChipProps) {
  const bild = rechnungszustandBild(zustand);
  const Zeichen = bild.symbol;
  return (
    <ZustandsChip
      wort={bild.wort}
      toenung={bild.toenung}
      symbol={<Zeichen size={SYMBOL_CHIP} stroke={1.8} />}
    />
  );
}
