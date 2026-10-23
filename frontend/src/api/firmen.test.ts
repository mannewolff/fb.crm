import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  ansprechpartnerAendern,
  ansprechpartnerAktivieren,
  ansprechpartnerAnlegen,
  ansprechpartnerStilllegen,
  firmaAendern,
  firmaAktivieren,
  firmaAnlegen,
  firmaLesen,
  firmaStilllegen,
  firmenUebersicht,
  parseAnsprechpartner,
  parseFirma,
  parseFirmenUebersicht,
} from './firmen';
import { fetchNachPfad, json } from '../test/fetchNachPfad';

const ZEILE = { id: 7, name: 'Beispiel GmbH', ort: 'Bremen', aktiveAnsprechpartner: 2, aktiv: true };

const PARTNER = {
  id: 3,
  vorname: 'Max',
  nachname: 'Mustermann',
  rolle: 'Einkauf',
  email: 'max@firma.de',
  telefonFestnetz: '0421/1234',
  telefonMobil: null,
  aktiv: true,
  version: 2,
};

const FIRMA = {
  id: 7,
  name: 'Beispiel GmbH',
  strasse: 'Hauptstraße 1',
  plz: '28195',
  ort: 'Bremen',
  land: 'Deutschland',
  steuernummer: null,
  umsatzsteuerId: null,
  aktiv: true,
  ansprechpartner: [PARTNER],
  version: 3,
};

/** Der Stand, den ein schreibender Schritt als starkes Etikett vorlegt (Plan #415, E2). */
const VORGELEGT = { 'If-Match': '"3"' };

const FIRMA_EINGABE = {
  name: 'Beispiel GmbH',
  strasse: 'Hauptstraße 1',
  plz: '28195',
  ort: 'Bremen',
  land: 'Deutschland',
  steuernummer: null,
  umsatzsteuerId: null,
};

const PARTNER_EINGABE = {
  vorname: 'Max',
  nachname: 'Mustermann',
  rolle: 'Einkauf',
  email: 'max@firma.de',
  telefonFestnetz: '0421/1234',
  telefonMobil: null,
};

afterEach(() => {
  vi.restoreAllMocks();
});

describe('parseFirmenUebersicht', () => {
  it('verengt eine vollstaendige Antwort', () => {
    expect(parseFirmenUebersicht({ firmen: [ZEILE], gesamt: 1 })).toEqual({
      firmen: [ZEILE],
      gesamt: 1,
    });
  });

  it.each([
    ['kein Objekt', 42],
    ['null', null],
    ['ohne firmen', { gesamt: 1 }],
    ['ohne gesamt', { firmen: [] }],
    ['Zeile ist kein Objekt', { firmen: ['x'], gesamt: 1 }],
    ['Zeile ohne id', { firmen: [{ ...ZEILE, id: undefined }], gesamt: 1 }],
    ['Zeile ohne name', { firmen: [{ ...ZEILE, name: 7 }], gesamt: 1 }],
    ['Zeile mit falschem ort', { firmen: [{ ...ZEILE, ort: 7 }], gesamt: 1 }],
    ['Zeile ohne Zahl der Ansprechpartner', { firmen: [{ ...ZEILE, aktiveAnsprechpartner: '2' }], gesamt: 1 }],
    ['Zeile ohne aktiv', { firmen: [{ ...ZEILE, aktiv: 'ja' }], gesamt: 1 }],
  ])('weist eine Antwort %s ab', (_fall, antwort) => {
    expect(() => parseFirmenUebersicht(antwort)).toThrow(TypeError);
  });
});

describe('parseFirma', () => {
  it('verengt eine vollstaendige Antwort samt Ansprechpartnern', () => {
    expect(parseFirma(FIRMA)).toEqual(FIRMA);
  });

  it('nimmt die leeren optionalen Angaben als null', () => {
    const leereAngaben = { ...FIRMA, strasse: null, plz: null, ort: null, land: null };
    expect(parseFirma(leereAngaben).strasse).toBeNull();
  });

  it.each([
    ['ohne id', { ...FIRMA, id: undefined }],
    ['ohne name', { ...FIRMA, name: null }],
    ['mit falscher strasse', { ...FIRMA, strasse: 7 }],
    ['ohne aktiv', { ...FIRMA, aktiv: undefined }],
    ['ohne Liste der Ansprechpartner', { ...FIRMA, ansprechpartner: undefined }],
    ['ohne version', { ...FIRMA, version: undefined }],
  ])('weist eine Antwort %s ab', (_fall, antwort) => {
    expect(() => parseFirma(antwort)).toThrow(TypeError);
  });
});

describe('parseAnsprechpartner', () => {
  it('verengt eine vollstaendige Antwort', () => {
    expect(parseAnsprechpartner(PARTNER)).toEqual(PARTNER);
  });

  it.each([
    ['ohne id', { ...PARTNER, id: undefined }],
    ['ohne nachname', { ...PARTNER, nachname: undefined }],
    ['mit falschem vorname', { ...PARTNER, vorname: 7 }],
    ['ohne aktiv', { ...PARTNER, aktiv: undefined }],
    ['ohne version', { ...PARTNER, version: undefined }],
  ])('weist eine Antwort %s ab', (_fall, antwort) => {
    expect(() => parseAnsprechpartner(antwort)).toThrow(TypeError);
  });
});

describe('firmenUebersicht', () => {
  it('traegt den Suchtext unverfaelscht in die Adresse', async () => {
    const adresse = '/api/firmen?suche=a%26b%25+c&auchStillgelegte=true';
    fetchNachPfad({ [`GET ${adresse}`]: json(200, { firmen: [ZEILE], gesamt: 1 }) });

    const uebersicht = await firmenUebersicht('a&b% c', true);

    expect(uebersicht.gesamt).toBe(1);
  });

  it('fragt ohne Schalter nur die aktiven Firmen', async () => {
    const adresse = '/api/firmen?suche=&auchStillgelegte=false';
    fetchNachPfad({ [`GET ${adresse}`]: json(200, { firmen: [], gesamt: 0 }) });

    expect(await firmenUebersicht('', false)).toEqual({ firmen: [], gesamt: 0 });
  });

  it('reicht das Abbruchsignal an den Aufruf durch', async () => {
    const adresse = '/api/firmen?suche=&auchStillgelegte=false';
    const fetchMock = fetchNachPfad({ [`GET ${adresse}`]: json(200, { firmen: [], gesamt: 0 }) });
    const steuerung = new AbortController();

    await firmenUebersicht('', false, steuerung.signal);

    expect(fetchMock).toHaveBeenCalledWith(
      adresse,
      expect.objectContaining({ signal: steuerung.signal }),
    );
  });
});

describe('die Wege der Firma', () => {
  it('legt an und bekommt die angelegte Firma zurueck', async () => {
    fetchNachPfad({ 'POST /api/firmen': json(201, FIRMA) });

    expect((await firmaAnlegen(FIRMA_EINGABE)).id).toBe(7);
  });

  it('liest die Detailansicht', async () => {
    fetchNachPfad({ 'GET /api/firmen/7': json(200, FIRMA) });

    expect((await firmaLesen(7)).ansprechpartner).toHaveLength(1);
  });

  it('schreibt die Angaben mit PUT, Rumpf und vorgelegtem Stand fort', async () => {
    const fetchMock = fetchNachPfad({ 'PUT /api/firmen/7': json(200, { ...FIRMA, version: 4 }) });

    const antwort = await firmaAendern(7, 3, FIRMA_EINGABE);

    expect(antwort.version).toBe(4);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen/7',
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify(FIRMA_EINGABE),
        headers: expect.objectContaining(VORGELEGT) as unknown,
      }),
    );
  });

  it('legt still und aktiviert wieder, jeweils auf dem vorgelegten Stand', async () => {
    const fetchMock = fetchNachPfad({
      'POST /api/firmen/7/stilllegen': json(200, { ...FIRMA, aktiv: false, version: 4 }),
      'POST /api/firmen/7/aktivieren': json(200, { ...FIRMA, version: 5 }),
    });

    expect((await firmaStilllegen(7, 3)).aktiv).toBe(false);
    expect((await firmaAktivieren(7, 3)).version).toBe(5);
    expect(fetchMock.mock.calls).toEqual([
      [
        '/api/firmen/7/stilllegen',
        expect.objectContaining({ headers: expect.objectContaining(VORGELEGT) as unknown }),
      ],
      [
        '/api/firmen/7/aktivieren',
        expect.objectContaining({ headers: expect.objectContaining(VORGELEGT) as unknown }),
      ],
    ]);
  });

  it('legt eine Firma ohne vorgelegten Stand an (#411, Kriterium 7)', async () => {
    const fetchMock = fetchNachPfad({ 'POST /api/firmen': json(201, FIRMA) });

    await firmaAnlegen(FIRMA_EINGABE);

    expect(fetchMock.mock.calls[0][1]?.headers).not.toHaveProperty('If-Match');
  });
});

describe('die Wege des Ansprechpartners', () => {
  it('legt unter der Firma an', async () => {
    const fetchMock = fetchNachPfad({
      'POST /api/firmen/7/ansprechpartner': json(201, PARTNER),
    });

    expect((await ansprechpartnerAnlegen(7, PARTNER_EINGABE)).id).toBe(3);
    expect(fetchMock.mock.calls[0][1]?.body).toBe(JSON.stringify(PARTNER_EINGABE));
  });

  it('schreibt die Angaben mit PUT und vorgelegtem Stand fort', async () => {
    const fetchMock = fetchNachPfad({
      'PUT /api/firmen/7/ansprechpartner/3': json(200, { ...PARTNER, version: 4 }),
    });

    const antwort = await ansprechpartnerAendern(7, 3, 3, PARTNER_EINGABE);

    expect(antwort.version).toBe(4);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen/7/ansprechpartner/3',
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify(PARTNER_EINGABE),
        headers: expect.objectContaining(VORGELEGT) as unknown,
      }),
    );
  });

  it('legt still und aktiviert wieder, jeweils auf dem vorgelegten Stand', async () => {
    const fetchMock = fetchNachPfad({
      'POST /api/firmen/7/ansprechpartner/3/stilllegen': json(200, { ...PARTNER, aktiv: false }),
      'POST /api/firmen/7/ansprechpartner/3/aktivieren': json(200, PARTNER),
    });

    expect((await ansprechpartnerStilllegen(7, 3, 3)).aktiv).toBe(false);
    expect((await ansprechpartnerAktivieren(7, 3, 3)).aktiv).toBe(true);
    expect(fetchMock.mock.calls.map((aufruf) => aufruf[1]?.headers)).toEqual([
      expect.objectContaining(VORGELEGT),
      expect.objectContaining(VORGELEGT),
    ]);
  });
});
