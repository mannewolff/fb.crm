/**
 * Kontrastrechner nach WCAG 2.1 (relative Luminanz und Kontrastverhaeltnis).
 *
 * Er steht hier und nicht in einer Bibliothek, weil `theme.test.ts` mit ihm die
 * Kontrasttabelle beider Erscheinungsbilder nachrechnet: Die Schwelle aus
 * `CLAUDE-design.md` soll an einer Zahl haengen, die das Projekt selbst bildet.
 */

const HEX = /^(?:[0-9a-f]{3}|[0-9a-f]{6})$/i;

interface Kanaele {
  readonly r: number;
  readonly g: number;
  readonly b: number;
}

function kanaele(color: string): Kanaele {
  const roh = color.trim().replace(/^#/, '');
  if (!HEX.test(roh)) {
    throw new Error(`Kein Farbwert in Hex-Schreibweise: ${color}`);
  }
  const lang =
    roh.length === 3
      ? roh
          .split('')
          .map((ziffer) => ziffer + ziffer)
          .join('')
      : roh;
  return {
    r: Number.parseInt(lang.slice(0, 2), 16) / 255,
    g: Number.parseInt(lang.slice(2, 4), 16) / 255,
    b: Number.parseInt(lang.slice(4, 6), 16) / 255,
  };
}

/** Ein Kanal auf der linearen Skala: unterhalb der Schwelle flach, darueber potenziert. */
function linear(wert: number): number {
  return wert <= 0.03928 ? wert / 12.92 : ((wert + 0.055) / 1.055) ** 2.4;
}

/** Relative Luminanz einer Farbe: 0 fuer Schwarz, 1 fuer Weiss. */
export function relativeLuminance(color: string): number {
  const { r, g, b } = kanaele(color);
  return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b);
}

/** Kontrastverhaeltnis zweier Farben, immer >= 1 und unabhaengig von der Reihenfolge. */
export function contrastRatio(a: string, b: string): number {
  const eine = relativeLuminance(a);
  const andere = relativeLuminance(b);
  return (Math.max(eine, andere) + 0.05) / (Math.min(eine, andere) + 0.05);
}
