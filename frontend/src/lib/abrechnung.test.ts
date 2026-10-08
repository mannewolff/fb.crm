import { describe, expect, it } from 'vitest';

import { rechnungssummen, steuerBetrag, ueberschreitung } from './abrechnung';

/**
 * Die Rechnung der Entwurfsmaske (Issue #185).
 *
 * Geprueft wird gegen die Zahlen, mit denen das Backend rechnet: Betrag je Zeile auf den Cent,
 * Steuer aus der Netto-Summe. Wo beide Wege auseinanderlaufen koennen, steht der Unterschied im
 * Testfall — sonst waere nicht zu sehen, dass die Reihenfolge der Rundungen gemessen wird.
 */

describe('ueberschreitung — was mit dieser Menge zusammen zu viel waere (Kriterium 8)', () => {
  it('ist 0, solange die Menge im Offenen bleibt', () => {
    expect(ueberschreitung(0, 16000, 8000)).toBe(0);
  });

  it('ist 0, wenn die Menge das Offene genau ausschoepft', () => {
    expect(ueberschreitung(8000, 16000, 8000)).toBe(0);
  });

  it('nennt die Menge, die ueber das Angebot hinausgeht', () => {
    expect(ueberschreitung(0, 16000, 20000)).toBe(4000);
  });

  it('zaehlt die schon abgerechnete Menge mit', () => {
    expect(ueberschreitung(12000, 16000, 8000)).toBe(4000);
  });
});

describe('steuerBetrag — die Steuer auf die Netto-Summe', () => {
  it('rechnet 19 Prozent auf 9.600,00 zu 1.824,00', () => {
    expect(steuerBetrag(960000, 1900)).toBe(182400);
  });

  it('rundet kaufmaennisch auf den Cent', () => {
    // 19 Prozent von 499,96 sind 94,9924 — und damit 94,99.
    expect(steuerBetrag(49996, 1900)).toBe(9499);
  });

  it('rundet die halbe Einheit nach oben', () => {
    // 50 Prozent von 1,01 sind 0,505 — HALF_UP ergibt 0,51, nicht 0,50.
    expect(steuerBetrag(101, 5000)).toBe(51);
  });

  it('ergibt 0 ohne Netto-Summe', () => {
    expect(steuerBetrag(0, 1900)).toBe(0);
  });
});

describe('rechnungssummen — Netto, Steuer und Brutto der Maske (Kriterium 4)', () => {
  it('nennt fuer 80 Stunden zu 120,00 bei 19 Prozent 9.600,00, 1.824,00 und 11.424,00', () => {
    expect(rechnungssummen([{ mengeInHundertsteln: 8000, einzelpreisInCent: 12000 }], 1900)).toEqual(
      { nettoInCent: 960000, steuerInCent: 182400, bruttoInCent: 1142400 },
    );
  });

  it('rundet den Betrag einer Zeile auf den Cent — 2,5 mal 99,99 ergibt 249,98', () => {
    expect(rechnungssummen([{ mengeInHundertsteln: 250, einzelpreisInCent: 9999 }], 0)).toEqual({
      nettoInCent: 24998,
      steuerInCent: 0,
      bruttoInCent: 24998,
    });
  });

  it('rechnet die Steuer aus der Netto-Summe und nicht je Zeile', () => {
    // Je Zeile waeren 19 Prozent von 249,98 gleich 47,4962 und damit 47,50 — zweimal 95,00.
    // Aus der Summe 499,96 gerechnet sind es 94,99; genau dieser Betrag steht auf dem Beleg.
    const zeilen = [
      { mengeInHundertsteln: 250, einzelpreisInCent: 9999 },
      { mengeInHundertsteln: 250, einzelpreisInCent: 9999 },
    ];

    expect(rechnungssummen(zeilen, 1900)).toEqual({
      nettoInCent: 49996,
      steuerInCent: 9499,
      bruttoInCent: 59495,
    });
  });

  it('ergibt ohne Zeile dreimal 0', () => {
    expect(rechnungssummen([], 1900)).toEqual({
      nettoInCent: 0,
      steuerInCent: 0,
      bruttoInCent: 0,
    });
  });
});
