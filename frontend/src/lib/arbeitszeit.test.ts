import { describe, expect, it } from 'vitest';

import {
  dauerInHundertsteln,
  folgemonat,
  imRaster,
  monatWort,
  alsMonat,
  stundenWort,
  uhrzeitFeld,
  uhrzeitWort,
  vormonat,
  zeitspanneWort,
} from './arbeitszeit';

describe('uhrzeitWort (A8: „9:00" und nicht „09:00")', () => {
  it('laesst die fuehrende Null der Stunde weg und die Sekunden fallen', () => {
    expect(uhrzeitWort('09:00:00')).toBe('9:00');
  });

  it('nimmt die Uhrzeit auch ohne Sekunden an', () => {
    expect(uhrzeitWort('14:45')).toBe('14:45');
  });

  it('gibt zweistellige Stunden unveraendert weiter', () => {
    expect(uhrzeitWort('23:45:00')).toBe('23:45');
  });

  it('gibt heraus, was keine Uhrzeit ist — statt eine erfundene Angabe zu setzen', () => {
    expect(uhrzeitWort('spaeter')).toBe('spaeter');
  });
});

describe('zeitspanneWort', () => {
  it('setzt die Spanne wie die Meldung des Servers — „9:00 bis 11:00" (A8)', () => {
    expect(zeitspanneWort('09:00:00', '11:00:00')).toBe('9:00 bis 11:00');
  });
});

describe('imRaster (Antwort 6: Schritte einer Viertelstunde)', () => {
  it('nimmt die volle Stunde und jede Viertelstunde an', () => {
    expect(imRaster('09:00:00')).toBe(true);
    expect(imRaster('09:15')).toBe(true);
    expect(imRaster('09:30:00')).toBe(true);
    expect(imRaster('09:45:00')).toBe(true);
  });

  it('weist eine Minute ausserhalb des Rasters ab', () => {
    expect(imRaster('09:10')).toBe(false);
  });

  it('weist Sekunden ab — das Raster kennt keine', () => {
    expect(imRaster('09:00:30')).toBe(false);
  });

  it('weist ab, was keine Uhrzeit ist', () => {
    expect(imRaster('')).toBe(false);
  });

  it('weist eine Uhrzeit ab, die es nicht gibt', () => {
    expect(imRaster('24:00')).toBe(false);
    expect(imRaster('09:75')).toBe(false);
  });
});

describe('dauerInHundertsteln (Kriterium 2)', () => {
  it('rechnet die Viertelstunde exakt — 9:00 bis 10:45 sind 1,75 Std.', () => {
    expect(dauerInHundertsteln('09:00:00', '10:45:00')).toBe(175);
  });

  it('rundet kaufmaennisch wie das Backend, wo die Minuten kein Viertel sind', () => {
    // 10 Minuten sind 0,1666… Stunden; `Zeiteintrag#stundenAus` rundet HALF_UP auf 0,17.
    expect(dauerInHundertsteln('09:00', '09:10')).toBe(17);
  });

  it('gibt null, wenn das Ende nicht nach dem Beginn liegt', () => {
    expect(dauerInHundertsteln('11:00', '09:00')).toBeNull();
    expect(dauerInHundertsteln('09:00', '09:00')).toBeNull();
  });

  it('gibt null, wo eine der beiden Angaben keine Uhrzeit ist', () => {
    expect(dauerInHundertsteln('', '11:00')).toBeNull();
    expect(dauerInHundertsteln('09:00', 'spaeter')).toBeNull();
  });
});

describe('stundenWort', () => {
  it('setzt die Stunden mit zwei Stellen und der Einheit — „1,75 Std."', () => {
    expect(stundenWort(175)).toBe('1,75 Std.');
  });

  it('setzt auch die glatte Stunde mit beiden Stellen', () => {
    expect(stundenWort(200)).toBe('2,00 Std.');
  });

  it('setzt die Null als „0,00 Std."', () => {
    expect(stundenWort(0)).toBe('0,00 Std.');
  });
});

describe('alsMonat', () => {
  it('nimmt einen Monat in der Form JJJJ-MM an', () => {
    expect(alsMonat('2026-11')).toBe('2026-11');
  });

  it('weist ab, was kein Monat ist — der Parameter kommt aus der Adresse', () => {
    expect(alsMonat(null)).toBeNull();
    expect(alsMonat('')).toBeNull();
    expect(alsMonat('2026-13')).toBeNull();
    expect(alsMonat('2026-00')).toBeNull();
    expect(alsMonat('2026-1')).toBeNull();
    expect(alsMonat('November 2026')).toBeNull();
  });
});

describe('vormonat und folgemonat', () => {
  it('geht innerhalb des Jahres einen Monat zurueck und vor', () => {
    expect(vormonat('2026-11')).toBe('2026-10');
    expect(folgemonat('2026-11')).toBe('2026-12');
  });

  it('wechselt am Jahresanfang und am Jahresende das Jahr', () => {
    expect(vormonat('2026-01')).toBe('2025-12');
    expect(folgemonat('2026-12')).toBe('2027-01');
  });

  it('gibt heraus, was kein Monat ist — wie `monatWort`', () => {
    expect(vormonat('November 2026')).toBe('November 2026');
    expect(folgemonat('November 2026')).toBe('November 2026');
  });
});

describe('monatWort', () => {
  it('setzt den Monat als Namen mit Jahr — „November 2026"', () => {
    expect(monatWort('2026-11')).toBe('November 2026');
  });

  it('kennt jeden der zwoelf Monate', () => {
    expect(monatWort('2026-01')).toBe('Januar 2026');
    expect(monatWort('2026-12')).toBe('Dezember 2026');
  });

  it('gibt heraus, was kein Monat ist — wie `tagWort` bei einem Nicht-Tag', () => {
    // Zwei Formen von „kein Monat": die falsche Gestalt und eine Zahl ausserhalb der zwoelf.
    expect(monatWort('November 2026')).toBe('November 2026');
    expect(monatWort('2026-13')).toBe('2026-13');
  });
});

describe('uhrzeitFeld (A16: der Wert eines Zeitfeldes)', () => {
  it('schreibt die Uhrzeit zweistellig und ohne Sekunden', () => {
    expect(uhrzeitFeld('09:00:00')).toBe('09:00');
  });

  it('nimmt die Uhrzeit auch ohne Sekunden an', () => {
    expect(uhrzeitFeld('14:45')).toBe('14:45');
  });

  it('gibt ein leeres Feld heraus, wo keine Uhrzeit steht', () => {
    // Anders als {@link uhrzeitWort}: Ein Zeitfeld traegt eine Uhrzeit oder nichts — der rohe
    // Wert waere dort ein Wert, den der Browser ohnehin verwirft.
    expect(uhrzeitFeld('spaeter')).toBe('');
  });
});
