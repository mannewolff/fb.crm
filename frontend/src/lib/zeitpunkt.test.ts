import { describe, expect, it } from 'vitest';

import { alsEingabe, alsZeitstempel } from './zeitpunkt';

/**
 * Die Erwartungen stehen bewusst relativ zur Zeitzone der Maschine, nicht als feste Zeichenkette
 * mit `Z`: Sonst waere gruen, wer in UTC laeuft, und rot, wer in Europa/Berlin sitzt — und genau
 * die Umrechnung, um die es hier geht, waere nicht geprueft.
 */
const ORTSZEIT = new Date(2026, 8, 25, 14, 30, 0, 0);
const EINGABE = '2026-09-25T14:30';

describe('alsEingabe', () => {
  it('macht aus einem Zeitstempel die Ortszeit fuer datetime-local', () => {
    expect(alsEingabe(ORTSZEIT.toISOString())).toBe(EINGABE);
  });

  it('fuellt ein- auf zweistellige Teile auf', () => {
    expect(alsEingabe(new Date(2026, 0, 2, 3, 4).toISOString())).toBe('2026-01-02T03:04');
  });

  it('laesst das Feld leer, wenn der Zeitstempel keiner ist', () => {
    expect(alsEingabe('kein Zeitpunkt')).toBe('');
    expect(alsEingabe('')).toBe('');
  });
});

describe('alsZeitstempel', () => {
  it('liest die Eingabe als Ortszeit und gibt den Zeitpunkt in UTC zurueck', () => {
    expect(alsZeitstempel(EINGABE)).toBe(ORTSZEIT.toISOString());
  });

  it('meldet eine leere Eingabe als fehlend', () => {
    expect(alsZeitstempel('')).toBeNull();
  });

  it('meldet eine unlesbare Eingabe als fehlend', () => {
    expect(alsZeitstempel('gestern')).toBeNull();
  });
});

describe('hin und zurueck', () => {
  it('kommt bei derselben Ortszeit wieder an', () => {
    // `String` statt `?? ''`: Der Ersatzzweig eines `??` liefe hier nie, und eine Verzweigung
    // ohne Fall ist genau die Luecke, die die Schwelle von 100 % nicht durchlaesst. Ein `null`
    // wuerde zu '' und die Erwartung unten fiele laut um — was sie soll.
    expect(alsEingabe(String(alsZeitstempel(EINGABE)))).toBe(EINGABE);
  });

  it('kommt bei demselben Zeitstempel wieder an, auf die Minute genau', () => {
    const zeitstempel = ORTSZEIT.toISOString();

    expect(alsZeitstempel(alsEingabe(zeitstempel))).toBe(zeitstempel);
  });
});
