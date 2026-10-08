import { afterEach, describe, expect, it, vi } from 'vitest';

import { KEIN_ZEITRAUM, heute, tagWort, zeitraumWort } from './tag';

describe('tagWort', () => {
  it('setzt einen Tag aus dem Backend in deutscher Schreibweise', () => {
    expect(tagWort('2026-09-24')).toBe('24.09.2026');
  });

  it('laesst die fuehrende Null stehen', () => {
    expect(tagWort('2026-01-05')).toBe('05.01.2026');
  });

  it('gibt heraus, was hereinkam, wenn es kein Tag ist', () => {
    // Lieber der rohe Wert als eine erfundene Angabe: Was hier ankommt, hat der Parser schon
    // als Zeichenkette durchgelassen — die Ansicht soll ihn zeigen und nicht verschweigen.
    expect(tagWort('kein Tag')).toBe('kein Tag');
    expect(tagWort('2026-9-4')).toBe('2026-9-4');
  });
});

describe('zeitraumWort', () => {
  it('setzt Beginn und Ende mit Halbgeviertstrich', () => {
    expect(zeitraumWort('2026-10-01', '2026-12-31')).toBe('01.10.2026 – 31.12.2026');
  });

  it.each([
    ['ohne beide Tage', null, null],
    ['ohne Ende', '2026-10-01', null],
    ['ohne Beginn', null, '2026-12-31'],
  ])('sagt „nicht angegeben" %s', (_fall, ab, bis) => {
    expect(zeitraumWort(ab, bis)).toBe(KEIN_ZEITRAUM);
  });
});

describe('heute (A16: der Tag ist mit heute vorbelegt)', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('setzt den heutigen Tag in der Ortszeit des Betrachters', () => {
    vi.useFakeTimers();
    // UTC ist noch der 12., Europe/Berlin schon der 13. — gemeint ist die Zone des Betrachters.
    vi.setSystemTime(new Date('2026-11-12T23:30:00Z'));

    expect(heute()).toBe('2026-11-13');
  });

  it('fuellt Monat und Tag auf zwei Stellen auf', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-01-05T12:00:00Z'));

    expect(heute()).toBe('2026-01-05');
  });
});
