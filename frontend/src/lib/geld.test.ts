import { describe, expect, it } from 'vitest';

import { betrag, dezimal, euro, hundertstel } from './geld';

describe('hundertstel', () => {
  it.each<[string | number, number]>([
    ['2,5', 250],
    ['2.5', 250],
    ['1000,01', 100001],
    [1000.01, 100001],
    ['0', 0],
    [0, 0],
    ['7', 700],
    [' 3,40 ', 340],
  ])('liest %s als %i Hundertstel', (eingabe, erwartet) => {
    expect(hundertstel(eingabe)).toBe(erwartet);
  });

  it.each<[string, string]>([
    ['leer', ''],
    ['nur Leerraum', '   '],
    ['kein Zahlwort', 'viel'],
    ['drei Nachkommastellen', '1,234'],
    ['negativ', '-1,00'],
    ['mit Waehrungszeichen', '1,00 €'],
  ])('weist %s ab', (_fall, eingabe) => {
    expect(hundertstel(eingabe)).toBeNull();
  });
});

describe('betrag', () => {
  it('rechnet 2,5 x 1.000,01 EUR zu 2.500,03 EUR (Kriterium 5)', () => {
    // 250 Hundertstel mal 100.001 Cent = 25.000.250 Hundertstel-Cent = 250.002,5 Cent.
    expect(betrag(250, 100001)).toBe(250003);
  });

  it('rundet den halben Cent nach oben (HALF_UP)', () => {
    // 0,5 mal 1 Cent = 0,5 Cent — kaufmaennisch also ein ganzer.
    expect(betrag(50, 1)).toBe(1);
  });

  it('rundet unterhalb des halben Cents ab', () => {
    expect(betrag(49, 1)).toBe(0);
  });

  it.each<[string, number, number]>([
    ['Menge 0', 0, 100001],
    ['Preis 0', 250, 0],
  ])('ergibt bei %s den Betrag null', (_fall, menge, preis) => {
    expect(betrag(menge, preis)).toBe(0);
  });
});

describe('euro', () => {
  it.each<[number, string]>([
    [250003, '2.500,03 €'],
    [0, '0,00 €'],
    [5, '0,05 €'],
    [99, '0,99 €'],
    [100, '1,00 €'],
    [1234567890, '12.345.678,90 €'],
    // Die Grenzen der Dreiergruppe und eine sehr lange Ziffernfolge (Issue #249).
    [99999, '999,99 €'],
    [100000, '1.000,00 €'],
    [123456700, '1.234.567,00 €'],
    [123456789012345, '1.234.567.890.123,45 €'],
    // Das Vorzeichen bekommt keinen Tausenderpunkt hinter sich.
    [-12345, '-123,45 €'],
    [-123456789, '-1.234.567,89 €'],
  ])('schreibt %i Cent als %s', (cent, text) => {
    expect(euro(cent)).toBe(text);
  });

  it('traegt immer zwei Dezimalstellen', () => {
    expect(euro(700)).toMatch(/,\d{2} €$/u);
  });
});

describe('dezimal', () => {
  it('setzt Hundertstel mit dem gewuenschten Trenner und immer zwei Stellen', () => {
    expect(dezimal(250, ',')).toBe('2,50');
    expect(dezimal(100001, '.')).toBe('1000.01');
  });

  it('setzt kleine Werte mit fuehrender Null', () => {
    expect(dezimal(5, ',')).toBe('0,05');
    expect(dezimal(0, '.')).toBe('0.00');
  });

  it('setzt keinen Tausenderpunkt — der Rumpf einer Anfrage vertraegt keinen', () => {
    expect(dezimal(123456789, '.')).toBe('1234567.89');
  });
});
