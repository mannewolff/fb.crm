import { describe, expect, it } from 'vitest';

import { emailZiel, telefonZiel } from './telefonlink';

describe('telefonZiel', () => {
  it('laesst Leerzeichen, Klammern und Striche einer gespeicherten Nummer weg', () => {
    expect(telefonZiel('+49 (0)421 12-34')).toBe('tel:+4904211234');
  });

  it('nimmt den Schraegstrich zwischen Vorwahl und Anschluss', () => {
    expect(telefonZiel('0421/1234')).toBe('tel:04211234');
  });

  it('behaelt das Plus nur, wenn es vorne steht', () => {
    expect(telefonZiel('0421+1234')).toBe('tel:04211234');
  });

  it('macht aus einem Wert ohne Ziffer keinen Link', () => {
    expect(telefonZiel('  -- ')).toBeNull();
  });

  it('macht aus einer fremden Zeichenfolge keinen Link', () => {
    expect(telefonZiel('javascript:alert(1)')).toBeNull();
  });

  it('macht aus dem leeren Wert keinen Link', () => {
    expect(telefonZiel('')).toBeNull();
  });
});

describe('emailZiel', () => {
  it('kodiert die Adresse und setzt das Schema fest davor', () => {
    expect(emailZiel('max@firma.de')).toBe('mailto:max%40firma.de');
  });

  it('kodiert ein untergeschobenes Kopffeld mit, statt es wirken zu lassen', () => {
    expect(emailZiel('max@firma.de?subject=x&bcc=dieb@fremd.de')).toBe(
      'mailto:max%40firma.de%3Fsubject%3Dx%26bcc%3Ddieb%40fremd.de',
    );
  });

  it('macht aus dem leeren Wert keinen Link', () => {
    expect(emailZiel('   ')).toBeNull();
  });
});
