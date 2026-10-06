import { describe, expect, it } from 'vitest';

import { alsZeitraum, alsZeitraumart, zeitraumWort } from './zeitraum';

describe('alsZeitraum', () => {
  it('nimmt einen Monat als JJJJ-MM', () => {
    expect(alsZeitraum('2026-10')).toBe('2026-10');
  });

  it('nimmt Januar und Dezember — die Raender der Monate', () => {
    expect(alsZeitraum('2026-01')).toBe('2026-01');
    expect(alsZeitraum('2026-12')).toBe('2026-12');
  });

  it('nimmt ein Jahr als JJJJ', () => {
    expect(alsZeitraum('2026')).toBe('2026');
  });

  it('verwirft Monat 13 und Monat 0', () => {
    expect(alsZeitraum('2026-13')).toBeNull();
    expect(alsZeitraum('2026-00')).toBeNull();
  });

  it('verwirft eine zu kurze Ziffernfolge', () => {
    expect(alsZeitraum('26')).toBeNull();
    expect(alsZeitraum('202')).toBeNull();
  });

  it('verwirft eine zu lange Ziffernfolge', () => {
    expect(alsZeitraum('20261')).toBeNull();
  });

  it('verwirft einen einstelligen Monat', () => {
    expect(alsZeitraum('2026-1')).toBeNull();
  });

  it('verwirft Unsinn', () => {
    expect(alsZeitraum('uebermorgen')).toBeNull();
    expect(alsZeitraum(' 2026')).toBeNull();
    expect(alsZeitraum('2026 ')).toBeNull();
  });

  it('verwirft die leere Zeichenkette', () => {
    expect(alsZeitraum('')).toBeNull();
  });

  it('verwirft null — kein Parameter, kein Zeitraum', () => {
    expect(alsZeitraum(null)).toBeNull();
  });
});

describe('alsZeitraumart', () => {
  it('kennt MONAT und JAHR', () => {
    expect(alsZeitraumart('MONAT')).toBe('MONAT');
    expect(alsZeitraumart('JAHR')).toBe('JAHR');
  });

  it('verwirft eine unbekannte Art', () => {
    expect(alsZeitraumart('WOCHE')).toBeNull();
    expect(alsZeitraumart('monat')).toBeNull();
    expect(alsZeitraumart(null)).toBeNull();
    expect(alsZeitraumart(1)).toBeNull();
  });
});

describe('zeitraumWort', () => {
  it('schreibt einen Monat als Name mit Jahr', () => {
    expect(zeitraumWort('2026-10')).toBe('Oktober 2026');
  });

  it('schreibt ein Jahr als seine Ziffern', () => {
    expect(zeitraumWort('2026')).toBe('2026');
  });
});
