import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  angebotAendern,
  angebotAnlegen,
  angebotLesen,
  angebotStatusWeiter,
  angebotStornieren,
  angebotStornoZuruecknehmen,
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
  leistungsjahr: '2026',
};

const ANGEBOT = {
  id: 9,
  firmaId: 5,
  firmaName: 'Adler AG',
  ansprechpartnerId: 8,
  ansprechpartnerName: 'Eva Adler',
  status: 'ANGELEGT',
  angebotDatum: '2026-09-24',
  titel: 'Neue Website',
  beschreibung: null,
  intern: false,
  storniert: false,
  stornoGrund: null,
  positionen: [POSITION],
  summe: 2500.03,
  version: 4,
  geaendertVon: 30,
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
  leistungsjahr: '2026',
};

const ANGEBOT_VERENGT = {
  id: 9,
  firmaId: 5,
  firmaName: 'Adler AG',
  ansprechpartnerId: 8,
  ansprechpartnerName: 'Eva Adler',
  status: 'ANGELEGT',
  angebotDatum: '2026-09-24',
  titel: 'Neue Website',
  beschreibung: null,
  intern: false,
  storniert: false,
  stornoGrund: null,
  positionen: [POSITION_VERENGT],
  summeInCent: 250003,
  version: 4,
  geaendertVon: 30,
};

const ZEILE = {
  id: 9,
  angebotDatum: '2026-09-24',
  titel: 'Neue Website',
  status: 'BESTELLT',
  intern: false,
  storniert: false,
  summe: 2500.03,
};

const EINGABE = {
  angebotDatum: '2026-09-24',
  titel: 'Neue Website',
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
      leistungsjahr: '2026',
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
      // Kein Jahr heisst „keine Angabe": Der Server belegt es vor (Plan #303, E2).
      leistungsjahr: null,
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

  it('liest Storno und Grund am stornierten Angebot (Plan #332)', () => {
    const angebot = parseAngebot({
      ...ANGEBOT,
      storniert: true,
      stornoGrund: 'vom Kunden abgelehnt',
    });

    expect(angebot.storniert).toBe(true);
    expect(angebot.stornoGrund).toBe('vom Kunden abgelehnt');
  });

  it('liest den fehlenden Grund als null (Plan #332)', () => {
    const angebot = parseAngebot({ ...ANGEBOT, storniert: true, stornoGrund: null });

    expect(angebot.storniert).toBe(true);
    expect(angebot.stornoGrund).toBeNull();
  });

  it('liest den Titel (Issue #318)', () => {
    expect(parseAngebot({ ...ANGEBOT, titel: 'Schulung Java' }).titel).toBe('Schulung Java');
  });

  it('liest ein Angebot ohne Titel als null — ein Angebot von vor dem Titel (Issue #318)', () => {
    expect(parseAngebot({ ...ANGEBOT, titel: null })).toEqual({ ...ANGEBOT_VERENGT, titel: null });
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

  it('liest das Leistungsjahr als Text (Plan #303, E9)', () => {
    expect(
      parseAngebot({ ...ANGEBOT, positionen: [{ ...POSITION, leistungsjahr: '2027' }] })
        .positionen[0].leistungsjahr,
    ).toBe('2027');
  });

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
    ['Titel als Zahl', { ...ANGEBOT, titel: 7 }],
    ['ohne Titel', { ...ANGEBOT, titel: undefined }],
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
    [
      'Position ohne Leistungsjahr',
      { ...ANGEBOT, positionen: [{ ...POSITION, leistungsjahr: undefined }] },
    ],
    [
      'Position mit Leistungsjahr als Zahl',
      { ...ANGEBOT, positionen: [{ ...POSITION, leistungsjahr: 2026 }] },
    ],
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
          titel: 'Neue Website',
          status: 'BESTELLT',
          intern: false,
          storniert: false,
          summeInCent: 250003,
        },
      ],
    });
  });

  it('liest das Storno an der Zeile (Plan #332)', () => {
    expect(
      parseFirmaAngebote({ angebote: [{ ...ZEILE, storniert: true }] }).angebote[0].storniert,
    ).toBe(true);
  });

  it('liest eine Zeile ohne Titel als null (Issue #318)', () => {
    expect(parseFirmaAngebote({ angebote: [{ ...ZEILE, titel: null }] }).angebote[0].titel).toBe(
      null,
    );
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
    ['Zeile mit Titel als Zahl', { angebote: [{ ...ZEILE, titel: 7 }] }],
    ['Zeile ohne Titel', { angebote: [{ ...ZEILE, titel: undefined }] }],
    ['Zeile ohne Kennzeichen', { angebote: [{ ...ZEILE, intern: null }] }],
    ['Zeile ohne Storno', { angebote: [{ ...ZEILE, storniert: undefined }] }],
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
  titel: 'Neue Website',
  status: 'BESTELLT',
  intern: false,
  storniert: false,
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
          titel: 'Neue Website',
          status: 'BESTELLT',
          intern: false,
          storniert: false,
          summeInCent: 250003,
        },
      ],
    });
  });

  it('liest das Storno an der Zeile (Plan #332)', () => {
    expect(
      parseAngeboteUebersicht({ angebote: [{ ...UEBERSICHT_ZEILE, storniert: true }] }).angebote[0]
        .storniert,
    ).toBe(true);
  });

  it('laesst den fehlenden Titel als null durch (Issue #325)', () => {
    expect(
      parseAngeboteUebersicht({ angebote: [{ ...UEBERSICHT_ZEILE, titel: null }] }).angebote[0]
        .titel,
    ).toBeNull();
  });

  it.each([
    ['kein Objekt', 'x'],
    ['angebote ist kein Array', { angebote: 3 }],
    ['Zeile ohne id', { angebote: [{ ...UEBERSICHT_ZEILE, id: null }] }],
    ['Zeile ohne firmaId', { angebote: [{ ...UEBERSICHT_ZEILE, firmaId: '5' }] }],
    ['Zeile ohne Firmenname', { angebote: [{ ...UEBERSICHT_ZEILE, firmaName: 5 }] }],
    ['Zeile ohne Angebotsdatum', { angebote: [{ ...UEBERSICHT_ZEILE, angebotDatum: null }] }],
    ['Zeile mit Titel als Zahl', { angebote: [{ ...UEBERSICHT_ZEILE, titel: 7 }] }],
    ['Zeile ohne Titel', { angebote: [{ ...UEBERSICHT_ZEILE, titel: undefined }] }],
    ['Zeile mit unbekanntem Status', { angebote: [{ ...UEBERSICHT_ZEILE, status: 'OFFEN' }] }],
    ['Zeile ohne Kennzeichen', { angebote: [{ ...UEBERSICHT_ZEILE, intern: 1 }] }],
    ['Zeile ohne Storno', { angebote: [{ ...UEBERSICHT_ZEILE, storniert: 'ja' }] }],
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

  it('holt unter einem Status nur die nicht stornierten Angebote darin (E6, E7)', async () => {
    const fetchMock = fetchNachPfad({
      'GET /api/angebote?status=ABGEGEBEN&storniert=false': json(200, { angebote: [] }),
    });

    expect((await angeboteUebersicht('ABGEGEBEN')).angebote).toEqual([]);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote?status=ABGEGEBEN&storniert=false',
      expect.objectContaining({ method: 'GET' }),
    );
  });

  it('holt unter „Storniert" jedes stornierte Angebot, ohne Status (E20)', async () => {
    const fetchMock = fetchNachPfad({
      'GET /api/angebote?storniert=true': json(200, {
        angebote: [{ ...UEBERSICHT_ZEILE, storniert: true }],
      }),
    });

    expect((await angeboteUebersicht('STORNIERT')).angebote[0].storniert).toBe(true);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote?storniert=true',
      expect.objectContaining({ method: 'GET' }),
    );
  });

  it('storniert ein Angebot mit Grund (Plan #332)', async () => {
    const fetchMock = fetchNachPfad({
      'POST /api/angebote/7/storno': json(200, { ...ANGEBOT, id: 7, storniert: true }),
    });

    expect((await angebotStornieren(7, 'vom Kunden abgelehnt')).storniert).toBe(true);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/7/storno',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ grund: 'vom Kunden abgelehnt' }),
      }),
    );
  });

  it('storniert ein Angebot ohne Grund und schickt null', async () => {
    const fetchMock = fetchNachPfad({
      'POST /api/angebote/7/storno': json(200, { ...ANGEBOT, id: 7, storniert: true }),
    });

    await angebotStornieren(7, null);

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/7/storno',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ grund: null }) }),
    );
  });

  it('nimmt das Storno zurueck (Plan #332)', async () => {
    const fetchMock = fetchNachPfad({
      'DELETE /api/angebote/7/storno': json(200, { ...ANGEBOT, id: 7 }),
    });

    expect((await angebotStornoZuruecknehmen(7)).storniert).toBe(false);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/7/storno',
      expect.objectContaining({ method: 'DELETE' }),
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

    expect(
      await angebotAnlegen(5, { titel: 'Neue Website', ansprechpartnerId: null, intern: false }),
    ).toEqual(ANGEBOT_VERENGT);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen/5/angebote',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ titel: 'Neue Website', ansprechpartnerId: null, intern: false }),
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

    await angebotAnlegen(1, { titel: 'Eigene Arbeit', ansprechpartnerId: null, intern: true });

    expect(rumpf).toEqual({ titel: 'Eigene Arbeit', ansprechpartnerId: null, intern: true });
  });

  it('legt ein Angebot mit einem Ansprechpartner an', async () => {
    const fetchMock = fetchNachPfad({ 'POST /api/firmen/5/angebote': json(201, ANGEBOT) });

    await angebotAnlegen(5, { titel: 'Schulung Java', ansprechpartnerId: 8, intern: false });

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen/5/angebote',
      expect.objectContaining({
        body: JSON.stringify({ titel: 'Schulung Java', ansprechpartnerId: 8, intern: false }),
      }),
    );
  });

  it('liest ein Angebot', async () => {
    fetchNachPfad({ 'GET /api/angebote/9': json(200, ANGEBOT) });

    expect(await angebotLesen(9)).toEqual(ANGEBOT_VERENGT);
  });

  it('aendert das Angebot als Ganzes und nimmt die Antwort (E8)', async () => {
    const fetchMock = fetchNachPfad({ 'PUT /api/angebote/9': json(200, ANGEBOT) });

    expect(await angebotAendern(9, 4, EINGABE)).toEqual(ANGEBOT_VERENGT);
    // Menge und Preis gehen als Dezimaltext hinaus und nicht als Gleitkommazahl (E5, geld.ts).
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/9',
      expect.objectContaining({ method: 'PUT', body: JSON.stringify(EINGABE) }),
    );
  });

  it('legt den Stand, auf dem die Aenderung beruht, als If-Match vor (Plan #415, E2)', async () => {
    const fetchMock = fetchNachPfad({ 'PUT /api/angebote/9': json(200, ANGEBOT) });

    await angebotAendern(9, 4, EINGABE);

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/9',
      expect.objectContaining({
        headers: expect.objectContaining({ 'If-Match': '"4"' }) as unknown,
      }),
    );
  });

  it('liest den Stand und den Urheber des Angebots (Plan #415, E2)', () => {
    expect(parseAngebot({ ...ANGEBOT, version: 7, geaendertVon: null })).toMatchObject({
      version: 7,
      geaendertVon: null,
    });
  });

  it('weist ein Angebot ohne Stand ab — ohne ihn liesse es sich nicht speichern', () => {
    expect(() => parseAngebot({ ...ANGEBOT, version: undefined })).toThrow(TypeError);
  });

  it('schickt die Kennung einer bestehenden Position mit und laesst sie an einer neuen leer', async () => {
    const fetchMock = fetchNachPfad({ 'PUT /api/angebote/9': json(200, ANGEBOT) });

    await angebotAendern(9, 4, EINGABE_MIT_NEUER_POSITION);

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

    await angebotAendern(9, 4, { ...EINGABE, intern: true });

    // Die interne Arbeit hat keine Menge, keine Einheit, keinen Preis und keine Abrechnungsart
    // (Plan #218, E7) — ein mitgeschickter Wert waere eine Angabe, die es nicht gibt. Auch das
    // Leistungsjahr bleibt draussen: Der Server belegt es vor (Plan #303, E2). Der Titel steht auf
    // der obersten Ebene und bleibt (Issue #318).
    expect(rumpf).toEqual({
      angebotDatum: '2026-09-24',
      titel: 'Neue Website',
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
