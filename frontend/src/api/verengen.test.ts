import { describe, expect, it } from 'vitest';

import {
  inHundertsteln,
  jaNein,
  liste,
  objekt,
  text,
  textOderNull,
  zahl,
  zahlOderNull,
} from './verengen';

describe('objekt', () => {
  it('nimmt ein Objekt', () => {
    expect(objekt({ a: 1 })).toEqual({ a: 1 });
  });

  it.each([
    ['null', null],
    ['eine Zahl', 42],
    ['eine Zeichenkette', 'x'],
    ['nichts', undefined],
  ])('weist %s ab', (_fall, wert) => {
    expect(() => objekt(wert)).toThrow(TypeError);
  });
});

describe('liste', () => {
  it('nimmt eine Liste', () => {
    expect(liste([1, 2])).toEqual([1, 2]);
  });

  it('weist ab, was keine Liste ist', () => {
    expect(() => liste({ laenge: 2 })).toThrow(TypeError);
  });
});

describe('zahl', () => {
  it('nimmt eine Zahl', () => {
    expect(zahl(7)).toBe(7);
  });

  it('weist eine Zahl in Anfuehrungszeichen ab', () => {
    expect(() => zahl('7')).toThrow(TypeError);
  });
});

describe('zahlOderNull', () => {
  it('nimmt eine Zahl und die fehlende Angabe', () => {
    expect(zahlOderNull(7)).toBe(7);
    expect(zahlOderNull(null)).toBeNull();
  });

  it('weist eine Zahl in Anfuehrungszeichen ab', () => {
    expect(() => zahlOderNull('7')).toThrow(TypeError);
  });
});

describe('text', () => {
  it('nimmt eine Zeichenkette', () => {
    expect(text('a')).toBe('a');
  });

  it('weist eine Zahl ab', () => {
    expect(() => text(7)).toThrow(TypeError);
  });
});

describe('textOderNull', () => {
  it('nimmt eine Zeichenkette und die fehlende Angabe', () => {
    expect(textOderNull('a')).toBe('a');
    expect(textOderNull(null)).toBeNull();
  });

  it('weist eine Zahl ab', () => {
    expect(() => textOderNull(7)).toThrow(TypeError);
  });
});

describe('jaNein', () => {
  it('nimmt einen Wahrheitswert', () => {
    expect(jaNein(true)).toBe(true);
    expect(jaNein(false)).toBe(false);
  });

  it('weist ein Wort ab', () => {
    expect(() => jaNein('ja')).toThrow(TypeError);
  });
});

describe('inHundertsteln', () => {
  it('rechnet eine Dezimalzahl in ganze Hundertstel', () => {
    expect(inHundertsteln(2500.03)).toBe(250003);
    expect(inHundertsteln(2.5)).toBe(250);
    expect(inHundertsteln(0)).toBe(0);
  });

  it.each([
    ['eine Zeichenkette', '2500.03'],
    ['drei Nachkommastellen', 2500.031],
    ['einen negativen Wert', -1],
    ['null', null],
  ])('weist %s ab', (_fall, wert) => {
    expect(() => inHundertsteln(wert)).toThrow(TypeError);
  });
});
