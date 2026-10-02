import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  angebotAendern,
  angebotAnlegen,
  angebotLesen,
  angebotStatusWeiter,
  angebotStatusZurueck,
  angeboteDerFirma,
  angeboteUebersicht,
  parseAngebot,
  parseAngeboteUebersicht,
  parseFirmaAngebote,
} from './angebote';
import { alsJson, fetchNachPfad, json } from '../test/fetchNachPfad';

/** Eine Position, wie Jackson sie schreibt: Menge und Preis als Zahl mit zwei Stellen. */
const POSITION = {
  id: 3,
  bezeichnung: 'Konzeption',
  abrechnungsmodus: 'AUFWAND',
  menge: 2.5,
  einheit: 'PERSONENTAG',
  einzelpreis: 1000.01,
  betrag: 2500.03,
};

const ANGEBOT = {
  id: 9,
  firmaId: 5,
  firmaName: 'Adler AG',
  ansprechpartnerId: 8,
  ansprechpartnerName: 'Eva Adler',
  status: 'ANGELEGT',
  angebotDatum: '2026-09-24',
  beschreibung: null,
  intern: false,
  positionen: [POSITION],
  summe: 2500.03,
};

/** Dieselbe Position nach dem Verengen: Menge in Hundertsteln, Geld in Cent (E5). */
const POSITION_VERENGT = {
  id: 3,
  bezeichnung: 'Konzeption',
  abrechnungsmodus: 'AUFWAND',
  mengeInHundertsteln: 250,
  einheit: 'PERSONENTAG',
  einzelpreisInCent: 100001,
  betragInCent: 250003,
};

const ANGEBOT_VERENGT = {
  id: 9,
  firmaId: 5,
  firmaName: 'Adler AG',
  ansprechpartnerId: 8,
  ansprechpartnerName: 'Eva Adler',
  status: 'ANGELEGT',
  angebotDatum: '2026-09-24',
  beschreibung: null,
  intern: false,
  positionen: [POSITION_VERENGT],
  summeInCent: 250003,
};

const ZEILE = {
  id: 9,
  angebotDatum: '2026-09-24',
  status: 'BESTELLT',
  intern: false,
  summe: 2500.03,
};

const EINGABE = {
  angebotDatum: '2026-09-24',
  ansprechpartnerId: 8,
  beschreibung: 'Neue Website',
  intern: false,
  positionen: [
    {
      id: 3,
      bezeichnung: 'Konzeption',
      abrechnungsmodus: 'AUFWAND' as const,
      menge: '2.50',
      einheit: 'PERSONENTAG' as const,
      einzelpreis: '1000.01',
    },
  ],
};

/** Eine hinzugefuegte Position geht ohne Kennung hinaus (Plan #169, E2). */
const EINGABE_MIT_NEUER_POSITION = {
  ...EINGABE,
  positionen: [
    ...EINGABE.positionen,
    {
      id: null,
      bezeichnung: 'Betreuung',
      abrechnungsmodus: 'FESTPREIS' as const,
      menge: '1.00',
      einheit: 'PAUSCHAL' as const,
      einzelpreis: '0.00',
    },
  ],
};

afterEach(() => {
  vi.restoreAllMocks();
});

describe('parseAngebot', () => {
  it('verengt ein Angebot samt Positionen und rechnet Geld in ganze Cent', () => {
    expect(parseAngebot(ANGEBOT)).toEqual(ANGEBOT_VERENGT);
  });

  it('nimmt eine Beschreibung an', () => {
    expect(parseAngebot({ ...ANGEBOT, beschreibung: 'Neue Website' }).beschreibung).toBe(
      'Neue Website',
    );
  });

  it('nimmt ein Angebot ohne Position mit der Summe null an', () => {
    expect(parseAngebot({ ...ANGEBOT, positionen: [], summe: 0 })).toEqual({
      ...ANGEBOT_VERENGT,
      positionen: [],
      summeInCent: 0,
    });
  });

  it('verengt ein Angebot ohne Ansprechpartner', () => {
    expect(
      parseAngebot({ ...ANGEBOT, ansprechpartnerId: null, ansprechpartnerName: null }),
    ).toEqual({ ...ANGEBOT_VERENGT, ansprechpartnerId: null, ansprechpartnerName: null });
  });

  it.each([[false], [true]])('liest das Kennzeichen intern=%s', (intern) => {
    expect(parseAngebot({ ...ANGEBOT, intern }).intern).toBe(intern);
  });

  it.each([
    ['ANGELEGT'],
    ['ABGEGEBEN'],
    ['BESTELLT'],
    ['ERLEDIGT'],
    ['ABGERECHNET'],
    ['LAEUFT'],
    ['ABGESCHLOSSEN'],
  ])(
    'nimmt den Status %s an',
    (status) => {
      expect(parseAngebot({ ...ANGEBOT, status }).status).toBe(status);
    },
  );

  it.each([['STUNDE'], ['PERSONENTAG'], ['PAUSCHAL']])('nimmt die Einheit %s an', (einheit) => {
    expect(
      parseAngebot({ ...ANGEBOT, positionen: [{ ...POSITION, einheit }] }).positionen[0].einheit,
    ).toBe(einheit);
  });

  it('nimmt den Abrechnungsmodus FESTPREIS an', () => {
    const festpreis = { ...POSITION, abrechnungsmodus: 'FESTPREIS' };

    expect(
      parseAngebot({ ...ANGEBOT, positionen: [festpreis] }).positionen[0].abrechnungsmodus,
    ).toBe('FESTPREIS');
  });

  it.each([
    ['kein Objekt', 42],
    ['null', null],
    ['ohne id', { ...ANGEBOT, id: '9' }],
    ['ohne firmaId', { ...ANGEBOT, firmaId: undefined }],
    ['ohne Firmenname', { ...ANGEBOT, firmaName: null }],
    ['Ansprechpartner als Text', { ...ANGEBOT, ansprechpartnerId: '8' }],
    ['Name des Ansprechpartners als Zahl', { ...ANGEBOT, ansprechpartnerName: 8 }],
    ['unbekannter Status', { ...ANGEBOT, status: 'VERHANDELT' }],
    ['ohne Status', { ...ANGEBOT, status: undefined }],
    ['ohne Angebotsdatum', { ...ANGEBOT, angebotDatum: null }],
    ['Beschreibung als Zahl', { ...ANGEBOT, beschreibung: 7 }],
    ['Positionen als Objekt', { ...ANGEBOT, positionen: {} }],
    ['Kennzeichen als Text', { ...ANGEBOT, intern: 'true' }],
    ['ohne Kennzeichen', { ...ANGEBOT, intern: undefined }],
    ['Summe als Zeichenkette', { ...ANGEBOT, summe: '2500.03' }],
    ['Summe mit drei Nachkommastellen', { ...ANGEBOT, summe: 2500.031 }],
    ['Position ist kein Objekt', { ...ANGEBOT, positionen: ['x'] }],
    // Ohne Kennung waere die Position beim naechsten Speichern eine neue (Plan #169, E2).
    ['Position ohne Kennung', { ...ANGEBOT, positionen: [{ ...POSITION, id: null }] }],
    ['Position mit Kennung als Text', { ...ANGEBOT, positionen: [{ ...POSITION, id: '3' }] }],
    ['Position ohne Bezeichnung', { ...ANGEBOT, positionen: [{ ...POSITION, bezeichnung: null }] }],
    [
      'Position mit unbekanntem Modus',
      { ...ANGEBOT, positionen: [{ ...POSITION, abrechnungsmodus: 'SCHAETZUNG' }] },
    ],
    ['Position mit Menge als Text', { ...ANGEBOT, positionen: [{ ...POSITION, menge: '2,5' }] }],
    [
      'Position mit unbekannter Einheit',
      { ...ANGEBOT, positionen: [{ ...POSITION, einheit: 'TAG' }] },
    ],
    [
      'Position mit negativem Einzelpreis',
      { ...ANGEBOT, positionen: [{ ...POSITION, einzelpreis: -1 }] },
    ],
    ['Position ohne Betrag', { ...ANGEBOT, positionen: [{ ...POSITION, betrag: undefined }] }],
  ])('weist eine Antwort ab: %s', (_fall, rumpf) => {
    expect(() => parseAngebot(rumpf)).toThrow(TypeError);
  });
});

describe('parseFirmaAngebote', () => {
  it('verengt die Liste an der Firma', () => {
    expect(parseFirmaAngebote({ angebote: [ZEILE] })).toEqual({
      angebote: [
        {
          id: 9,
          angebotDatum: '2026-09-24',
          status: 'BESTELLT',
          intern: false,
          summeInCent: 250003,
        },
      ],
    });
  });

  it('verengt die leere Liste', () => {
    expect(parseFirmaAngebote({ angebote: [] })).toEqual({ angebote: [] });
  });

  it.each([
    ['kein Objekt', 'x'],
    ['ohne angebote', {}],
    ['angebote ist kein Array', { angebote: 3 }],
    ['Zeile ohne id', { angebote: [{ ...ZEILE, id: null }] }],
    ['Zeile mit unbekanntem Status', { angebote: [{ ...ZEILE, status: 'OFFEN' }] }],
    ['Zeile ohne Angebotsdatum', { angebote: [{ ...ZEILE, angebotDatum: 7 }] }],
    ['Zeile ohne Kennzeichen', { angebote: [{ ...ZEILE, intern: null }] }],
    ['Zeile ohne Summe', { angebote: [{ ...ZEILE, summe: null }] }],
  ])('weist eine Antwort ab: %s', (_fall, rumpf) => {
    expect(() => parseFirmaAngebote(rumpf)).toThrow(TypeError);
  });
});

const UEBERSICHT_ZEILE = {
  id: 9,
  firmaId: 5,
  firmaName: 'Adler AG',
  angebotDatum: '2026-09-24',
  status: 'BESTELLT',
  intern: false,
  summe: 2500.03,
};

describe('parseAngeboteUebersicht', () => {
  it('verengt die Uebersicht samt Firmenname', () => {
    expect(parseAngeboteUebersicht({ angebote: [UEBERSICHT_ZEILE] })).toEqual({
      angebote: [
        {
          id: 9,
          firmaId: 5,
          firmaName: 'Adler AG',
          angebotDatum: '2026-09-24',
          status: 'BESTELLT',
          intern: false,
          summeInCent: 250003,
        },
      ],
    });
  });

  it.each([
    ['kein Objekt', 'x'],
    ['angebote ist kein Array', { angebote: 3 }],
    ['Zeile ohne id', { angebote: [{ ...UEBERSICHT_ZEILE, id: null }] }],
    ['Zeile ohne firmaId', { angebote: [{ ...UEBERSICHT_ZEILE, firmaId: '5' }] }],
    ['Zeile ohne Firmenname', { angebote: [{ ...UEBERSICHT_ZEILE, firmaName: 5 }] }],
    ['Zeile ohne Angebotsdatum', { angebote: [{ ...UEBERSICHT_ZEILE, angebotDatum: null }] }],
    ['Zeile mit unbekanntem Status', { angebote: [{ ...UEBERSICHT_ZEILE, status: 'OFFEN' }] }],
    ['Zeile ohne Kennzeichen', { angebote: [{ ...UEBERSICHT_ZEILE, intern: 1 }] }],
    ['Zeile ohne Summe', { angebote: [{ ...UEBERSICHT_ZEILE, summe: undefined }] }],
  ])('weist eine Antwort ab: %s', (_fall, rumpf) => {
    expect(() => parseAngeboteUebersicht(rumpf)).toThrow(TypeError);
  });
});

describe('die Wege', () => {
  it('holt die Uebersicht aller Angebote ohne Filter', async () => {
    const fetchMock = fetchNachPfad({
      'GET /api/angebote': json(200, { angebote: [UEBERSICHT_ZEILE] }),
    });

    expect((await angeboteUebersicht(null)).angebote).toHaveLength(1);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote',
      expect.objectContaining({ method: 'GET' }),
    );
  });

  it('holt die Uebersicht nach Status gefiltert', async () => {
    const fetchMock = fetchNachPfad({
      'GET /api/angebote?status=BESTELLT': json(200, { angebote: [] }),
    });

    expect((await angeboteUebersicht('BESTELLT')).angebote).toEqual([]);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote?status=BESTELLT',
      expect.objectContaining({ method: 'GET' }),
    );
  });

  it('holt die Angebote einer Firma', async () => {
    const fetchMock = fetchNachPfad({
      'GET /api/firmen/5/angebote': json(200, { angebote: [ZEILE] }),
    });

    const antwort = await angeboteDerFirma(5);

    expect(antwort.angebote).toHaveLength(1);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen/5/angebote',
      expect.objectContaining({ method: 'GET' }),
    );
  });

  it('legt ein Angebot ohne Ansprechpartner an', async () => {
    const fetchMock = fetchNachPfad({ 'POST /api/firmen/5/angebote': json(201, ANGEBOT) });

    expect(await angebotAnlegen(5, null, false)).toEqual(ANGEBOT_VERENGT);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen/5/angebote',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ ansprechpartnerId: null, intern: false }),
      }),
    );
  });

  it('legt ein internes Angebot an und schickt das Kennzeichen mit', async () => {
    let rumpf: unknown = null;
    fetchNachPfad({
      'POST /api/firmen/1/angebote': (gesendet) => {
        rumpf = alsJson(gesendet);
        return json(201, ANGEBOT)();
      },
    });

    await angebotAnlegen(1, null, true);

    expect(rumpf).toEqual({ ansprechpartnerId: null, intern: true });
  });

  it('legt ein Angebot mit einem Ansprechpartner an', async () => {
    const fetchMock = fetchNachPfad({ 'POST /api/firmen/5/angebote': json(201, ANGEBOT) });

    await angebotAnlegen(5, 8, false);

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen/5/angebote',
      expect.objectContaining({ body: JSON.stringify({ ansprechpartnerId: 8, intern: false }) }),
    );
  });

  it('liest ein Angebot', async () => {
    fetchNachPfad({ 'GET /api/angebote/9': json(200, ANGEBOT) });

    expect(await angebotLesen(9)).toEqual(ANGEBOT_VERENGT);
  });

  it('aendert das Angebot als Ganzes und nimmt die Antwort (E8)', async () => {
    const fetchMock = fetchNachPfad({ 'PUT /api/angebote/9': json(200, ANGEBOT) });

    expect(await angebotAendern(9, EINGABE)).toEqual(ANGEBOT_VERENGT);
    // Menge und Preis gehen als Dezimaltext hinaus und nicht als Gleitkommazahl (E5, geld.ts).
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/9',
      expect.objectContaining({ method: 'PUT', body: JSON.stringify(EINGABE) }),
    );
  });

  it('schickt die Kennung einer bestehenden Position mit und laesst sie an einer neuen leer', async () => {
    const fetchMock = fetchNachPfad({ 'PUT /api/angebote/9': json(200, ANGEBOT) });

    await angebotAendern(9, EINGABE_MIT_NEUER_POSITION);

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/9',
      expect.objectContaining({ body: JSON.stringify(EINGABE_MIT_NEUER_POSITION) }),
    );
  });

  it('laesst beim internen Angebot die vier Positionsangaben weg', async () => {
    let rumpf: unknown = null;
    fetchNachPfad({
      'PUT /api/angebote/9': (gesendet) => {
        rumpf = alsJson(gesendet);
        return json(200, ANGEBOT)();
      },
    });

    await angebotAendern(9, { ...EINGABE, intern: true });

    // Die interne Arbeit hat keine Menge, keine Einheit, keinen Preis und keine Abrechnungsart
    // (Plan #218, E7) — ein mitgeschickter Wert waere eine Angabe, die es nicht gibt.
    expect(rumpf).toEqual({
      angebotDatum: '2026-09-24',
      ansprechpartnerId: 8,
      beschreibung: 'Neue Website',
      intern: true,
      positionen: [{ id: 3, bezeichnung: 'Konzeption' }],
    });
  });

  it.each([
    ['weiter', angebotStatusWeiter, 'POST /api/angebote/9/status/weiter'],
    ['zurueck', angebotStatusZurueck, 'POST /api/angebote/9/status/zurueck'],
  ])('schaltet den Status %s und nimmt die Antwort', async (_name, weg, schluessel) => {
    const fetchMock = fetchNachPfad({ [schluessel]: json(200, { ...ANGEBOT, status: 'ABGEGEBEN' }) });

    expect((await weg(9)).status).toBe('ABGEGEBEN');
    expect(fetchMock).toHaveBeenCalledWith(
      schluessel.slice('POST '.length),
      expect.objectContaining({ method: 'POST' }),
    );
  });
});
