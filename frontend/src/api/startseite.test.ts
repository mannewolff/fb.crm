import { afterEach, describe, expect, it, vi } from 'vitest';

import { parseStartseitenstand, startseite } from './startseite';
import { fetchNachPfad, json } from '../test/fetchNachPfad';

/** Der Stand, wie `StartseiteResponse` ihn schreibt — Monate als `JJJJ-MM`, Geld als Dezimalzahl. */
const STAND = {
  monat: '2026-10',
  monate: ['2026-10', '2026-09', '2026-08'],
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
    erfasstImMonat: 912.25,
    angebote: [
      {
        angebotId: 11,
        firmaName: 'IT Bildungshaus',
        angebotDatum: '2026-09-24',
        netto: 3368.25,
      },
    ],
  },
  abgerechnet: { netto: 1800, brutto: 2142, anzahl: 2 },
  interneStundenImMonat: 12.5,
};

afterEach(() => {
  vi.restoreAllMocks();
});

describe('parseStartseitenstand', () => {
  it('verengt Monat, Monate, Angebote in Arbeit und beide Kennzahlen', () => {
    const gelesen = parseStartseitenstand(STAND);

    expect(gelesen.monat).toBe('2026-10');
    expect(gelesen.monate).toEqual(['2026-10', '2026-09', '2026-08']);
    expect(gelesen.inArbeit).toEqual(STAND.inArbeit);
    expect(gelesen.abgerechnet).toEqual({
      nettoInCent: 180000,
      bruttoInCent: 214200,
      anzahl: 2,
    });
  });

  it('liest die Betraege als ganze Cent — nicht als Gleitkommazahl (E5)', () => {
    const gelesen = parseStartseitenstand(STAND);

    expect(gelesen.nichtAbgerechnet.nettoInCent).toBe(428050);
    expect(gelesen.nichtAbgerechnet.erfasstImMonatInCent).toBe(91225);
    expect(gelesen.nichtAbgerechnet.angebote).toEqual([
      {
        angebotId: 11,
        firmaName: 'IT Bildungshaus',
        angebotDatum: '2026-09-24',
        nettoInCent: 336825,
      },
    ]);
  });

  it('liest die internen Stunden des Monats als ganze Hundertstel (Kriterium 9)', () => {
    const gelesen = parseStartseitenstand(STAND);

    expect(gelesen.interneStundenInHundertsteln).toBe(1250);
  });

  it('scheitert, wo die internen Stunden keine Zahl sind', () => {
    expect(() => parseStartseitenstand({ ...STAND, interneStundenImMonat: '12.5' })).toThrow(
      TypeError,
    );
  });

  it('scheitert, wo der Monat fehlt — eine halb gelesene Antwort geht nicht weiter', () => {
    expect(() => parseStartseitenstand({ ...STAND, monat: null })).toThrow(TypeError);
  });

  it('scheitert, wo ein waehlbarer Monat keine Zeichenkette ist', () => {
    expect(() => parseStartseitenstand({ ...STAND, monate: [202610] })).toThrow(TypeError);
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

  it('scheitert, wo die Anzahl der Rechnungen keine Zahl ist', () => {
    const kaputt = { ...STAND, abgerechnet: { ...STAND.abgerechnet, anzahl: '2' } };

    expect(() => parseStartseitenstand(kaputt)).toThrow(TypeError);
  });

  it('scheitert, wo die Antwort kein Objekt ist', () => {
    expect(() => parseStartseitenstand(null)).toThrow(TypeError);
  });
});

describe('startseite', () => {
  it('fragt mit dem Monat als Parameter', async () => {
    const aufruf = fetchNachPfad({ 'GET /api/startseite?monat=2026-10': json(200, STAND) });

    await expect(startseite('2026-10')).resolves.toMatchObject({ monat: '2026-10' });
    expect(aufruf).toHaveBeenCalledTimes(1);
  });

  it('fragt ohne Parameter, wo kein Monat genannt ist — dann gilt der laufende (E8)', async () => {
    fetchNachPfad({ 'GET /api/startseite': json(200, STAND) });

    await expect(startseite()).resolves.toMatchObject({
      abgerechnet: { nettoInCent: 180000, bruttoInCent: 214200, anzahl: 2 },
    });
  });
});
