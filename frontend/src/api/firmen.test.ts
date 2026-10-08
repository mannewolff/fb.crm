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
import { fetchNachPfad, json, leer } from '../test/fetchNachPfad';

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
};

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

  it('schreibt die Angaben mit PUT und Rumpf fort', async () => {
    const fetchMock = fetchNachPfad({ 'PUT /api/firmen/7': leer(204) });

    await firmaAendern(7, FIRMA_EINGABE);

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen/7',
      expect.objectContaining({ method: 'PUT', body: JSON.stringify(FIRMA_EINGABE) }),
    );
  });

  it('legt still und aktiviert wieder', async () => {
    fetchNachPfad({
      'POST /api/firmen/7/stilllegen': leer(204),
      'POST /api/firmen/7/aktivieren': leer(204),
    });

    await expect(firmaStilllegen(7)).resolves.toBeUndefined();
    await expect(firmaAktivieren(7)).resolves.toBeUndefined();
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

  it('schreibt die Angaben mit PUT fort', async () => {
    const fetchMock = fetchNachPfad({ 'PUT /api/firmen/7/ansprechpartner/3': leer(204) });

    await ansprechpartnerAendern(7, 3, PARTNER_EINGABE);

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen/7/ansprechpartner/3',
      expect.objectContaining({ method: 'PUT', body: JSON.stringify(PARTNER_EINGABE) }),
    );
  });

  it('legt still und aktiviert wieder', async () => {
    fetchNachPfad({
      'POST /api/firmen/7/ansprechpartner/3/stilllegen': leer(204),
      'POST /api/firmen/7/ansprechpartner/3/aktivieren': leer(204),
    });

    await expect(ansprechpartnerStilllegen(7, 3)).resolves.toBeUndefined();
    await expect(ansprechpartnerAktivieren(7, 3)).resolves.toBeUndefined();
  });
});
