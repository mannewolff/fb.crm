import { afterEach, describe, expect, it, vi } from 'vitest';

import { parseStartseitenstand, startseite } from './startseite';
import { fetchNachPfad, json } from '../test/fetchNachPfad';

/**
 * Ein Stand bei Monatswahl, wie `StartseiteResponse` ihn schreibt — Monate als `JJJJ-MM`, Jahre als
 * `JJJJ`, Geld als Dezimalzahl. Die Monatsliste unter „Abgerechnet" ist dann leer. „Offene
 * Rechnungen" traegt zwei Zeilen, eine je Art (Issue #285).
 */
const STAND = {
  zeitraum: { art: 'MONAT', wert: '2026-10' },
  waehlbar: { jahre: ['2026', '2025'], monate: ['2026-10', '2026-09', '2026-08'] },
  inArbeit: [
    {
      angebotId: 11,
      firmaName: 'IT Bildungshaus',
      angebotDatum: '2026-09-24',
      status: 'BESTELLT',
    },
    {
      angebotId: 9,
      firmaName: 'Werkstatt Nord',
      angebotDatum: '2026-08-03',
      status: 'ERLEDIGT',
    },
  ],
  nichtAbgerechnet: {
    netto: 4280.5,
    erfasstImZeitraum: 912.25,
    angebote: [
      {
        angebotId: 11,
        firmaName: 'IT Bildungshaus',
        angebotDatum: '2026-09-24',
        netto: 3368.25,
      },
    ],
  },
  abgerechnet: {
    netto: 1800,
    brutto: 2142,
    anzahl: 2,
    offenNetto: 360,
    offenAnzahl: 1,
    monate: [],
  },
  offeneRechnungen: {
    netto: 500,
    anzahl: 2,
    rechnungen: [
      {
        nachgetragen: false,
        id: 7,
        nummer: 'R26-0007',
        firmaName: 'IT Bildungshaus',
        rechnungDatum: '2026-09-30',
        netto: 360,
      },
      {
        nachgetragen: true,
        id: 3,
        nummer: 'AR-1',
        firmaName: 'Werkstatt Nord',
        rechnungDatum: '2026-10-01',
        netto: 140,
      },
    ],
  },
  interneStundenImZeitraum: 12.5,
};

/** Derselbe Stand bei Jahreswahl: Die Monate mit gestellten Rechnungen stehen unter „Abgerechnet". */
const JAHRESSTAND = {
  ...STAND,
  zeitraum: { art: 'JAHR', wert: '2026' },
  abgerechnet: {
    netto: 5400,
    brutto: 6426,
    anzahl: 5,
    offenNetto: 1800,
    offenAnzahl: 2,
    monate: [
      { monat: '2026-03', anzahl: 3, netto: 3600, brutto: 4284, offenNetto: 0, offenAnzahl: 0 },
      { monat: '2026-09', anzahl: 2, netto: 1800, brutto: 2142, offenNetto: 1800, offenAnzahl: 2 },
    ],
  },
};

afterEach(() => {
  vi.restoreAllMocks();
});

describe('parseStartseitenstand', () => {
  it('verengt Zeitraum, waehlbare Zeitraeume, Angebote in Arbeit und beide Kennzahlen', () => {
    const gelesen = parseStartseitenstand(STAND);

    expect(gelesen.zeitraum).toEqual({ art: 'MONAT', wert: '2026-10' });
    expect(gelesen.waehlbar).toEqual({
      jahre: ['2026', '2025'],
      monate: ['2026-10', '2026-09', '2026-08'],
    });
    expect(gelesen.inArbeit).toEqual(STAND.inArbeit);
    expect(gelesen.abgerechnet).toEqual({
      nettoInCent: 180000,
      bruttoInCent: 214200,
      anzahl: 2,
      offenNettoInCent: 36000,
      offenAnzahl: 1,
      monate: [],
    });
  });

  it('verengt ein Jahr samt seinen Monatszeilen in ganzen Cent (#273, Kriterium 7)', () => {
    const gelesen = parseStartseitenstand(JAHRESSTAND);

    expect(gelesen.zeitraum).toEqual({ art: 'JAHR', wert: '2026' });
    expect(gelesen.abgerechnet).toEqual({
      nettoInCent: 540000,
      bruttoInCent: 642600,
      anzahl: 5,
      offenNettoInCent: 180000,
      offenAnzahl: 2,
      monate: [
        {
          monat: '2026-03',
          anzahl: 3,
          nettoInCent: 360000,
          bruttoInCent: 428400,
          offenNettoInCent: 0,
          offenAnzahl: 0,
        },
        {
          monat: '2026-09',
          anzahl: 2,
          nettoInCent: 180000,
          bruttoInCent: 214200,
          offenNettoInCent: 180000,
          offenAnzahl: 2,
        },
      ],
    });
  });

  it('liest die Betraege als ganze Cent — nicht als Gleitkommazahl (E5)', () => {
    const gelesen = parseStartseitenstand(STAND);

    expect(gelesen.nichtAbgerechnet.nettoInCent).toBe(428050);
    expect(gelesen.nichtAbgerechnet.erfasstImZeitraumInCent).toBe(91225);
    expect(gelesen.nichtAbgerechnet.angebote).toEqual([
      {
        angebotId: 11,
        firmaName: 'IT Bildungshaus',
        angebotDatum: '2026-09-24',
        nettoInCent: 336825,
      },
    ]);
  });

  it('liest die internen Stunden des Zeitraums als ganze Hundertstel (Kriterium 9)', () => {
    const gelesen = parseStartseitenstand(STAND);

    expect(gelesen.interneStundenInHundertsteln).toBe(1250);
  });

  it('scheitert, wo die internen Stunden keine Zahl sind', () => {
    expect(() => parseStartseitenstand({ ...STAND, interneStundenImZeitraum: '12.5' })).toThrow(
      TypeError,
    );
  });

  it('scheitert, wo der Zeitraum fehlt — eine halb gelesene Antwort geht nicht weiter', () => {
    expect(() => parseStartseitenstand({ ...STAND, zeitraum: null })).toThrow(TypeError);
  });

  it('scheitert, wo die Art des Zeitraums unbekannt ist (E10)', () => {
    const kaputt = { ...STAND, zeitraum: { art: 'WOCHE', wert: '2026-10' } };

    expect(() => parseStartseitenstand(kaputt)).toThrow(TypeError);
  });

  it('scheitert, wo der Wert des Zeitraums keine Zeichenkette ist', () => {
    const kaputt = { ...STAND, zeitraum: { art: 'JAHR', wert: 2026 } };

    expect(() => parseStartseitenstand(kaputt)).toThrow(TypeError);
  });

  it('scheitert, wo die waehlbaren Zeitraeume fehlen', () => {
    expect(() => parseStartseitenstand({ ...STAND, waehlbar: undefined })).toThrow(TypeError);
  });

  it('scheitert, wo ein waehlbares Jahr als Zahl kommt — Jahre stehen als Text (E10)', () => {
    const kaputt = { ...STAND, waehlbar: { ...STAND.waehlbar, jahre: [2026] } };

    expect(() => parseStartseitenstand(kaputt)).toThrow(TypeError);
  });

  it('scheitert, wo ein waehlbarer Monat keine Zeichenkette ist', () => {
    const kaputt = { ...STAND, waehlbar: { ...STAND.waehlbar, monate: [202610] } };

    expect(() => parseStartseitenstand(kaputt)).toThrow(TypeError);
  });

  it('scheitert, wo die Monatsliste unter „Abgerechnet" fehlt', () => {
    const ohneMonate = { ...STAND.abgerechnet, monate: undefined };

    expect(() => parseStartseitenstand({ ...STAND, abgerechnet: ohneMonate })).toThrow(TypeError);
  });

  it('scheitert, wo eine Monatszeile einen Betrag mit drei Nachkommastellen traegt', () => {
    const kaputt = {
      ...JAHRESSTAND,
      abgerechnet: {
        ...JAHRESSTAND.abgerechnet,
        monate: [{ ...JAHRESSTAND.abgerechnet.monate[0], brutto: 1.005 }],
      },
    };

    expect(() => parseStartseitenstand(kaputt)).toThrow(TypeError);
  });

  it('scheitert, wo der Status eines Angebots in Arbeit keiner ist', () => {
    const kaputt = {
      ...STAND,
      inArbeit: [{ ...STAND.inArbeit[0], status: 'UNTERWEGS' }],
    };

    expect(() => parseStartseitenstand(kaputt)).toThrow(TypeError);
  });

  it('scheitert, wo ein Anteil einen Betrag mit drei Nachkommastellen traegt', () => {
    const kaputt = {
      ...STAND,
      nichtAbgerechnet: {
        ...STAND.nichtAbgerechnet,
        angebote: [{ ...STAND.nichtAbgerechnet.angebote[0], netto: 1.005 }],
      },
    };

    expect(() => parseStartseitenstand(kaputt)).toThrow(TypeError);
  });

  it('liest den offenen Anteil als ganze Cent und die Zahl der offenen Rechnungen (#284)', () => {
    const gelesen = parseStartseitenstand(STAND);

    expect(gelesen.abgerechnet.offenNettoInCent).toBe(36000);
    expect(gelesen.abgerechnet.offenAnzahl).toBe(1);
  });

  it('liest „nichts offen" als 0 und nicht als fehlendes Feld (#284)', () => {
    const bezahlt = { ...STAND, abgerechnet: { ...STAND.abgerechnet, offenNetto: 0, offenAnzahl: 0 } };

    const gelesen = parseStartseitenstand(bezahlt);

    expect(gelesen.abgerechnet.offenNettoInCent).toBe(0);
    expect(gelesen.abgerechnet.offenAnzahl).toBe(0);
  });

  it('scheitert, wo der offene Betrag fehlt — eine halb gelesene Kennzahl geht nicht weiter', () => {
    const kaputt = { ...STAND, abgerechnet: { ...STAND.abgerechnet, offenNetto: undefined } };

    expect(() => parseStartseitenstand(kaputt)).toThrow(TypeError);
  });

  it('scheitert, wo die Zahl der offenen Rechnungen keine Zahl ist', () => {
    const kaputt = { ...STAND, abgerechnet: { ...STAND.abgerechnet, offenAnzahl: '1' } };

    expect(() => parseStartseitenstand(kaputt)).toThrow(TypeError);
  });

  it('scheitert, wo eine Monatszeile den offenen Betrag mit drei Nachkommastellen traegt', () => {
    const kaputt = {
      ...JAHRESSTAND,
      abgerechnet: {
        ...JAHRESSTAND.abgerechnet,
        monate: [{ ...JAHRESSTAND.abgerechnet.monate[0], offenNetto: 1.005 }],
      },
    };

    expect(() => parseStartseitenstand(kaputt)).toThrow(TypeError);
  });

  it('scheitert, wo die Zahl der offenen Rechnungen einer Monatszeile fehlt', () => {
    const kaputt = {
      ...JAHRESSTAND,
      abgerechnet: {
        ...JAHRESSTAND.abgerechnet,
        monate: [{ ...JAHRESSTAND.abgerechnet.monate[0], offenAnzahl: null }],
      },
    };

    expect(() => parseStartseitenstand(kaputt)).toThrow(TypeError);
  });

  it('verengt die offenen Rechnungen samt Art, Nummer und Betrag in ganzen Cent (#285)', () => {
    const gelesen = parseStartseitenstand(STAND);

    expect(gelesen.offeneRechnungen).toEqual({
      nettoInCent: 50000,
      anzahl: 2,
      rechnungen: [
        {
          nachgetragen: false,
          id: 7,
          nummer: 'R26-0007',
          firmaName: 'IT Bildungshaus',
          rechnungDatum: '2026-09-30',
          nettoInCent: 36000,
        },
        {
          nachgetragen: true,
          id: 3,
          nummer: 'AR-1',
          firmaName: 'Werkstatt Nord',
          rechnungDatum: '2026-10-01',
          nettoInCent: 14000,
        },
      ],
    });
  });

  it('liest „nichts offen" als 0 und leere Liste, nicht als fehlendes Feld (#285)', () => {
    const leer = { ...STAND, offeneRechnungen: { netto: 0, anzahl: 0, rechnungen: [] } };

    const gelesen = parseStartseitenstand(leer);

    expect(gelesen.offeneRechnungen.nettoInCent).toBe(0);
    expect(gelesen.offeneRechnungen.anzahl).toBe(0);
    expect(gelesen.offeneRechnungen.rechnungen).toEqual([]);
  });

  it('scheitert, wo die Kennzahl „Offene Rechnungen" fehlt (#285)', () => {
    expect(() => parseStartseitenstand({ ...STAND, offeneRechnungen: undefined })).toThrow(
      TypeError,
    );
  });

  it('scheitert, wo die Liste der offenen Rechnungen keine Liste ist (#285)', () => {
    const kaputt = { ...STAND, offeneRechnungen: { ...STAND.offeneRechnungen, rechnungen: null } };

    expect(() => parseStartseitenstand(kaputt)).toThrow(TypeError);
  });

  it('scheitert, wo die Art einer offenen Rechnung kein Wahrheitswert ist (#285)', () => {
    const kaputt = {
      ...STAND,
      offeneRechnungen: {
        ...STAND.offeneRechnungen,
        rechnungen: [{ ...STAND.offeneRechnungen.rechnungen[0], nachgetragen: 'nein' }],
      },
    };

    expect(() => parseStartseitenstand(kaputt)).toThrow(TypeError);
  });

  it('scheitert, wo die Nummer einer offenen Rechnung fehlt — sie traegt den Weg (#285)', () => {
    const kaputt = {
      ...STAND,
      offeneRechnungen: {
        ...STAND.offeneRechnungen,
        rechnungen: [{ ...STAND.offeneRechnungen.rechnungen[0], nummer: null }],
      },
    };

    expect(() => parseStartseitenstand(kaputt)).toThrow(TypeError);
  });

  it('scheitert, wo eine offene Rechnung einen Betrag mit drei Nachkommastellen traegt (#285)', () => {
    const kaputt = {
      ...STAND,
      offeneRechnungen: {
        ...STAND.offeneRechnungen,
        rechnungen: [{ ...STAND.offeneRechnungen.rechnungen[0], netto: 1.005 }],
      },
    };

    expect(() => parseStartseitenstand(kaputt)).toThrow(TypeError);
  });

  it('scheitert, wo die Zahl der offenen Rechnungen keine Zahl ist (#285)', () => {
    const kaputt = { ...STAND, offeneRechnungen: { ...STAND.offeneRechnungen, anzahl: '2' } };

    expect(() => parseStartseitenstand(kaputt)).toThrow(TypeError);
  });

  it('scheitert, wo die Anzahl der Rechnungen keine Zahl ist', () => {
    const kaputt = { ...STAND, abgerechnet: { ...STAND.abgerechnet, anzahl: '2' } };

    expect(() => parseStartseitenstand(kaputt)).toThrow(TypeError);
  });

  it('scheitert, wo die Antwort kein Objekt ist', () => {
    expect(() => parseStartseitenstand(null)).toThrow(TypeError);
  });
});

describe('startseite', () => {
  it('fragt mit einem Monat als Zeitraum (E1)', async () => {
    const aufruf = fetchNachPfad({ 'GET /api/startseite?zeitraum=2026-10': json(200, STAND) });

    await expect(startseite('2026-10')).resolves.toMatchObject({
      zeitraum: { art: 'MONAT', wert: '2026-10' },
    });
    expect(aufruf.mock.calls.map(([ziel]) => ziel)).toEqual(['/api/startseite?zeitraum=2026-10']);
  });

  it('fragt mit einem Jahr als Zeitraum (E1)', async () => {
    const aufruf = fetchNachPfad({ 'GET /api/startseite?zeitraum=2026': json(200, JAHRESSTAND) });

    await expect(startseite('2026')).resolves.toMatchObject({
      zeitraum: { art: 'JAHR', wert: '2026' },
    });
    expect(aufruf.mock.calls.map(([ziel]) => ziel)).toEqual(['/api/startseite?zeitraum=2026']);
  });

  it('fragt ohne Parameter, wo kein Zeitraum genannt ist — dann gilt der laufende (E8)', async () => {
    const aufruf = fetchNachPfad({ 'GET /api/startseite': json(200, STAND) });

    await expect(startseite()).resolves.toMatchObject({
      abgerechnet: { nettoInCent: 180000, bruttoInCent: 214200, anzahl: 2 },
    });
    expect(aufruf.mock.calls.map(([ziel]) => ziel)).toEqual(['/api/startseite']);
  });
});
