import { describe, expect, it } from 'vitest';

import { initialen } from './initials';

describe('initialen (E12)', () => {
  it.each([
    ['ein Wort', 'Manfred', 'M'],
    ['zwei Woerter', 'Manfred Wolff', 'MW'],
    ['drei Woerter — erstes und letztes', 'Hans Peter Müller', 'HM'],
    ['Bindestrich im Vornamen', 'Anna-Lena Schmidt', 'AS'],
    ['Bindestrichname allein', 'Anna-Lena', 'AL'],
    ['Umlaut am Anfang', 'özlem ünal', 'ÖÜ'],
    ['Leerraum drumherum und doppelt', '  Manfred   Wolff ', 'MW'],
  ])('%s: %s ergibt %s', (_fall, name, kuerzel) => {
    expect(initialen(name)).toBe(kuerzel);
  });

  it.each([
    ['leerer Name', ''],
    ['nur Leerraum', '   '],
    ['nur Bindestriche', '--'],
  ])('%s ergibt ein Fragezeichen statt eines leeren Mals', (_fall, name) => {
    expect(initialen(name)).toBe('?');
  });

  it('zerlegt ein Zeichen jenseits der Grundebene nicht', () => {
    expect(initialen('𝔄nna 𝔅erg')).toBe('𝔄𝔅');
  });
});
