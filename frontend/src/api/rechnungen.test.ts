import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  abrechenbareAngebote,
  angebotAbrechnung,
  parseAbrechenbareAngebote,
  parseAngebotsabrechnung,
  parseRechnung,
  parseRechnungenUebersicht,
  rechnungAendern,
  rechnungAnlegen,
  rechnungDokumentPfad,
  rechnungLesen,
  rechnungLoeschen,
  rechnungStellen,
  rechnungenUebersicht,
} from './rechnungen';
import { fetchNachPfad, json, leer } from '../test/fetchNachPfad';

/** Eine Maskenzeile, wie Jackson sie schreibt: Mengen und Geld als Zahl mit zwei Stellen. */
const ZEILE = {
  angebotPositionId: 3,
  bezeichnung: 'Konzeption',
  einheit: 'STUNDE',
  einzelpreis: 120,
  angeboten: 160,
  abgerechnet: 0,
  offen: 160,
  menge: 80,
  ueberschreitung: 0,
};

/** Dieselbe Zeile nach dem Verengen: Mengen in Hundertsteln, Geld in Cent (E5). */
const ZEILE_VERENGT = {
  angebotPositionId: 3,
  bezeichnung: 'Konzeption',
  einheit: 'STUNDE',
  einzelpreisInCent: 12000,
  angebotenInHundertsteln: 16000,
  abgerechnetInHundertsteln: 0,
  offenInHundertsteln: 16000,
  mengeInHundertsteln: 8000,
  ueberschreitungInHundertsteln: 0,
};

const ENTWURF = {
  id: 4,
  angebotId: 9,
  firmaId: 5,
  firmaName: 'Adler AG',
  rechnungDatum: '2026-10-01',
  leistungszeitraum: 'Oktober 2026',
  zustand: 'ENTWURF',
  nummer: null,
  steuersatz: 19,
  netto: 9600,
  steuer: 1824,
  brutto: 11424,
  zahlungszielTage: null,
  empfaenger: null,
  absender: null,
  zeilen: [ZEILE],
};

const ENTWURF_VERENGT = {
  id: 4,
  angebotId: 9,
  firmaId: 5,
  firmaName: 'Adler AG',
  rechnungDatum: '2026-10-01',
  leistungszeitraum: 'Oktober 2026',
  zustand: 'ENTWURF',
  nummer: null,
  steuersatzInHundertsteln: 1900,
  nettoInCent: 960000,
  steuerInCent: 182400,
  bruttoInCent: 1142400,
  zahlungszielTage: null,
  empfaenger: null,
  absender: null,
  zeilen: [ZEILE_VERENGT],
};

/** Der Empfaenger, wie das Stellen ihn festschreibt. */
const EMPFAENGER = {
  firma: 'Adler AG',
  strasse: 'Hauptstraße 1',
  plz: '28195',
  ort: 'Bremen',
  land: 'Deutschland',
};

/** Die eigenen Angaben, wie das Stellen sie festschreibt. */
const ABSENDER = {
  name: 'Manfred Wolff',
  berufsbezeichnung: null,
  strasse: 'Am Wall 2',
  plz: '28195',
  ort: 'Bremen',
  land: 'Deutschland',
  email: 'info@mwolff.org',
  telefon: null,
  steuernummer: null,
  umsatzsteuerId: null,
  bankverbindung: null,
  webadresse: null,
};

const GESTELLT = {
  ...ENTWURF,
  zustand: 'GESTELLT',
  nummer: '0001-2026',
  zahlungszielTage: 14,
  empfaenger: EMPFAENGER,
  absender: ABSENDER,
};

const GESTELLT_VERENGT = {
  ...ENTWURF_VERENGT,
  zustand: 'GESTELLT',
  nummer: '0001-2026',
  zahlungszielTage: 14,
  empfaenger: EMPFAENGER,
  absender: ABSENDER,
};

/** Eine Zeile der Liste aller Rechnungen. */
const UEBERSICHT_ZEILE = {
  id: 4,
  nummer: '0001-2026',
  firmaId: 5,
  firmaName: 'Adler AG',
  rechnungDatum: '2026-10-01',
  brutto: 11424,
  zustand: 'GESTELLT',
};

const UEBERSICHT_ZEILE_VERENGT = {
  id: 4,
  nummer: '0001-2026',
  firmaId: 5,
  firmaName: 'Adler AG',
  rechnungDatum: '2026-10-01',
  bruttoInCent: 1142400,
  zustand: 'GESTELLT',
};

/** Eine Zeile der Wahl „Neue Rechnung". */
const WAHL_ZEILE = {
  angebotId: 9,
  firmaId: 5,
  firmaName: 'Adler AG',
  angebotDatum: '2026-09-24',
  offenerBetrag: 9600,
};

const WAHL_ZEILE_VERENGT = {
  angebotId: 9,
  firmaId: 5,
  firmaName: 'Adler AG',
  angebotDatum: '2026-09-24',
  offenerBetragInCent: 960000,
};

/** Eine Positionszeile des Abrechnungsstands am Angebot. */
const STAND_ZEILE = {
  angebotPositionId: 3,
  bezeichnung: 'Konzeption',
  einheit: 'STUNDE',
  angeboten: 160,
  abgerechnet: 80,
  offen: 80,
  ueberschreitung: 0,
  buchbar: true,
  angefallen: 22,
};

const STAND_ZEILE_VERENGT = {
  angebotPositionId: 3,
  bezeichnung: 'Konzeption',
  einheit: 'STUNDE',
  angebotenInHundertsteln: 16000,
  abgerechnetInHundertsteln: 8000,
  offenInHundertsteln: 8000,
  ueberschreitungInHundertsteln: 0,
  buchbar: true,
  angefallenInHundertsteln: 2200,
};

/** Eine Rechnung des Angebots, wie der Abrechnungsstand sie listet — ohne Firma. */
const STAND_RECHNUNG = {
  id: 4,
  nummer: '0001-2026',
  rechnungDatum: '2026-10-01',
  brutto: 11424,
  zustand: 'GESTELLT',
};

const STAND_RECHNUNG_VERENGT = {
  id: 4,
  nummer: '0001-2026',
  rechnungDatum: '2026-10-01',
  bruttoInCent: 1142400,
  zustand: 'GESTELLT',
};

/** Die Eingaben der Entwurfsmaske; die Menge geht als Dezimaltext hinaus (E5). */
const EINGABE = {
  rechnungDatum: '2026-10-01',
  leistungszeitraum: 'Oktober 2026',
  positionen: [{ angebotPositionId: 3, bezeichnung: 'Konzeption', menge: '80.00' }],
};

afterEach(() => {
  vi.restoreAllMocks();
});

describe('parseRechnung', () => {
  it('verengt einen Entwurf samt Maskenzeilen und rechnet Geld in ganze Cent', () => {
    expect(parseRechnung(ENTWURF)).toEqual(ENTWURF_VERENGT);
  });

  it('verengt eine gestellte Rechnung samt Nummer, Zahlungsziel und beiden Kopien', () => {
    expect(parseRechnung(GESTELLT)).toEqual(GESTELLT_VERENGT);
  });

  it('nimmt eine Rechnung ohne Leistungszeitraum an', () => {
    expect(parseRechnung({ ...ENTWURF, leistungszeitraum: null }).leistungszeitraum).toBeNull();
  });

  it('nimmt eine Kopie mit lauter leeren Anschriftsfeldern an', () => {
    const karg = { firma: 'Adler AG', strasse: null, plz: null, ort: null, land: null };

    expect(parseRechnung({ ...GESTELLT, empfaenger: karg }).empfaenger).toEqual(karg);
  });

  it.each([['STUNDE'], ['PERSONENTAG'], ['PAUSCHAL']])('nimmt die Einheit %s an', (einheit) => {
    expect(parseRechnung({ ...ENTWURF, zeilen: [{ ...ZEILE, einheit }] }).zeilen[0].einheit).toBe(
      einheit,
    );
  });

  it.each([
    ['kein Objekt', 42],
    ['null', null],
    ['ohne id', { ...ENTWURF, id: '4' }],
    ['ohne angebotId', { ...ENTWURF, angebotId: undefined }],
    ['ohne firmaId', { ...ENTWURF, firmaId: null }],
    ['ohne Firmenname', { ...ENTWURF, firmaName: 5 }],
    ['ohne Rechnungsdatum', { ...ENTWURF, rechnungDatum: null }],
    ['Leistungszeitraum als Zahl', { ...ENTWURF, leistungszeitraum: 10 }],
    // Ein Zustand, den die Oberflaeche nicht kennt, ist ein Formfehler und kein Ersatzwert.
    ['unbekannter Zustand', { ...ENTWURF, zustand: 'BEZAHLT' }],
    ['ohne Zustand', { ...ENTWURF, zustand: undefined }],
    ['Nummer als Zahl', { ...GESTELLT, nummer: 1 }],
    ['Steuersatz als Text', { ...ENTWURF, steuersatz: '19' }],
    ['Netto mit drei Nachkommastellen', { ...ENTWURF, netto: 9600.001 }],
    ['Steuer als Text', { ...ENTWURF, steuer: '1824' }],
    ['ohne Brutto', { ...ENTWURF, brutto: undefined }],
    ['Zahlungsziel als Text', { ...GESTELLT, zahlungszielTage: '14' }],
    ['Empfaenger ohne Firma', { ...GESTELLT, empfaenger: { ...EMPFAENGER, firma: null } }],
    ['Empfaenger mit Ort als Zahl', { ...GESTELLT, empfaenger: { ...EMPFAENGER, ort: 28195 } }],
    ['Absender ohne Name', { ...GESTELLT, absender: { ...ABSENDER, name: null } }],
    ['Absender mit Telefon als Zahl', { ...GESTELLT, absender: { ...ABSENDER, telefon: 421 } }],
    ['Zeilen als Objekt', { ...ENTWURF, zeilen: {} }],
    ['Zeile ist kein Objekt', { ...ENTWURF, zeilen: ['x'] }],
    ['Zeile ohne Kennung der Angebotsposition', { ...ENTWURF, zeilen: [{ ...ZEILE, angebotPositionId: null }] }],
    ['Zeile ohne Bezeichnung', { ...ENTWURF, zeilen: [{ ...ZEILE, bezeichnung: null }] }],
    ['Zeile mit unbekannter Einheit', { ...ENTWURF, zeilen: [{ ...ZEILE, einheit: 'TAG' }] }],
    ['Zeile mit negativem Einzelpreis', { ...ENTWURF, zeilen: [{ ...ZEILE, einzelpreis: -1 }] }],
    ['Zeile ohne angeboten', { ...ENTWURF, zeilen: [{ ...ZEILE, angeboten: undefined }] }],
    ['Zeile mit abgerechnet als Text', { ...ENTWURF, zeilen: [{ ...ZEILE, abgerechnet: '0' }] }],
    ['Zeile ohne offen', { ...ENTWURF, zeilen: [{ ...ZEILE, offen: null }] }],
    ['Zeile mit Menge als Text', { ...ENTWURF, zeilen: [{ ...ZEILE, menge: '80' }] }],
    ['Zeile ohne Ueberschreitung', { ...ENTWURF, zeilen: [{ ...ZEILE, ueberschreitung: undefined }] }],
  ])('weist eine Antwort ab: %s', (_fall, rumpf) => {
    expect(() => parseRechnung(rumpf)).toThrow(TypeError);
  });
});

describe('parseRechnungenUebersicht', () => {
  it('verengt die Liste aller Rechnungen', () => {
    expect(parseRechnungenUebersicht({ rechnungen: [UEBERSICHT_ZEILE] })).toEqual({
      rechnungen: [UEBERSICHT_ZEILE_VERENGT],
    });
  });

  it('verengt einen Entwurf ohne Nummer', () => {
    expect(
      parseRechnungenUebersicht({
        rechnungen: [{ ...UEBERSICHT_ZEILE, nummer: null, zustand: 'ENTWURF' }],
      }).rechnungen[0],
    ).toEqual({ ...UEBERSICHT_ZEILE_VERENGT, nummer: null, zustand: 'ENTWURF' });
  });

  it('verengt die leere Liste', () => {
    expect(parseRechnungenUebersicht({ rechnungen: [] })).toEqual({ rechnungen: [] });
  });

  it.each([
    ['kein Objekt', 'x'],
    ['ohne rechnungen', {}],
    ['rechnungen ist kein Array', { rechnungen: 3 }],
    ['Zeile ohne id', { rechnungen: [{ ...UEBERSICHT_ZEILE, id: null }] }],
    ['Zeile mit Nummer als Zahl', { rechnungen: [{ ...UEBERSICHT_ZEILE, nummer: 1 }] }],
    ['Zeile ohne firmaId', { rechnungen: [{ ...UEBERSICHT_ZEILE, firmaId: '5' }] }],
    ['Zeile ohne Firmenname', { rechnungen: [{ ...UEBERSICHT_ZEILE, firmaName: 5 }] }],
    ['Zeile ohne Rechnungsdatum', { rechnungen: [{ ...UEBERSICHT_ZEILE, rechnungDatum: 7 }] }],
    ['Zeile ohne Brutto', { rechnungen: [{ ...UEBERSICHT_ZEILE, brutto: null }] }],
    ['Zeile mit unbekanntem Zustand', { rechnungen: [{ ...UEBERSICHT_ZEILE, zustand: 'BEZAHLT' }] }],
  ])('weist eine Antwort ab: %s', (_fall, rumpf) => {
    expect(() => parseRechnungenUebersicht(rumpf)).toThrow(TypeError);
  });
});

describe('parseAbrechenbareAngebote', () => {
  it('verengt die Wahl samt offenem Betrag', () => {
    expect(parseAbrechenbareAngebote({ angebote: [WAHL_ZEILE] })).toEqual({
      angebote: [WAHL_ZEILE_VERENGT],
    });
  });

  it('verengt die leere Wahl', () => {
    expect(parseAbrechenbareAngebote({ angebote: [] })).toEqual({ angebote: [] });
  });

  it.each([
    ['kein Objekt', 7],
    ['angebote ist kein Array', { angebote: 3 }],
    ['Zeile ohne angebotId', { angebote: [{ ...WAHL_ZEILE, angebotId: null }] }],
    ['Zeile ohne firmaId', { angebote: [{ ...WAHL_ZEILE, firmaId: '5' }] }],
    ['Zeile ohne Firmenname', { angebote: [{ ...WAHL_ZEILE, firmaName: null }] }],
    ['Zeile ohne Angebotsdatum', { angebote: [{ ...WAHL_ZEILE, angebotDatum: 7 }] }],
    ['Zeile ohne offenen Betrag', { angebote: [{ ...WAHL_ZEILE, offenerBetrag: undefined }] }],
  ])('weist eine Antwort ab: %s', (_fall, rumpf) => {
    expect(() => parseAbrechenbareAngebote(rumpf)).toThrow(TypeError);
  });
});

describe('parseAngebotsabrechnung', () => {
  it('verengt Positionsstand und Rechnungen des Angebots', () => {
    expect(
      parseAngebotsabrechnung({ positionen: [STAND_ZEILE], rechnungen: [STAND_RECHNUNG] }),
    ).toEqual({ positionen: [STAND_ZEILE_VERENGT], rechnungen: [STAND_RECHNUNG_VERENGT] });
  });

  it('verengt den leeren Stand', () => {
    expect(parseAngebotsabrechnung({ positionen: [], rechnungen: [] })).toEqual({
      positionen: [],
      rechnungen: [],
    });
  });

  it.each([
    ['kein Objekt', 'x'],
    ['positionen ist kein Array', { positionen: 3, rechnungen: [] }],
    ['rechnungen ist kein Array', { positionen: [], rechnungen: 3 }],
    ['Position ohne Kennung', { positionen: [{ ...STAND_ZEILE, angebotPositionId: null }], rechnungen: [] }],
    ['Position ohne Bezeichnung', { positionen: [{ ...STAND_ZEILE, bezeichnung: 7 }], rechnungen: [] }],
    ['Position mit unbekannter Einheit', { positionen: [{ ...STAND_ZEILE, einheit: 'TAG' }], rechnungen: [] }],
    ['Position ohne angeboten', { positionen: [{ ...STAND_ZEILE, angeboten: null }], rechnungen: [] }],
    ['Position ohne abgerechnet', { positionen: [{ ...STAND_ZEILE, abgerechnet: '0' }], rechnungen: [] }],
    ['Position ohne offen', { positionen: [{ ...STAND_ZEILE, offen: undefined }], rechnungen: [] }],
    ['Position ohne Ueberschreitung', { positionen: [{ ...STAND_ZEILE, ueberschreitung: null }], rechnungen: [] }],
    ['Position mit buchbar als Wort', { positionen: [{ ...STAND_ZEILE, buchbar: 'ja' }], rechnungen: [] }],
    ['Position ohne angefallen', { positionen: [{ ...STAND_ZEILE, angefallen: null }], rechnungen: [] }],
    ['Rechnung ohne id', { positionen: [], rechnungen: [{ ...STAND_RECHNUNG, id: '4' }] }],
    ['Rechnung mit Nummer als Zahl', { positionen: [], rechnungen: [{ ...STAND_RECHNUNG, nummer: 1 }] }],
    ['Rechnung ohne Datum', { positionen: [], rechnungen: [{ ...STAND_RECHNUNG, rechnungDatum: null }] }],
    ['Rechnung ohne Brutto', { positionen: [], rechnungen: [{ ...STAND_RECHNUNG, brutto: undefined }] }],
    ['Rechnung mit unbekanntem Zustand', { positionen: [], rechnungen: [{ ...STAND_RECHNUNG, zustand: 'BEZAHLT' }] }],
  ])('weist eine Antwort ab: %s', (_fall, rumpf) => {
    expect(() => parseAngebotsabrechnung(rumpf)).toThrow(TypeError);
  });
});

describe('rechnungDokumentPfad', () => {
  it('nennt den Weg des archivierten Dokuments', () => {
    expect(rechnungDokumentPfad(4)).toBe('/api/rechnungen/4/dokument');
  });
});

describe('die Wege', () => {
  it('holt die Liste aller Rechnungen', async () => {
    const fetchMock = fetchNachPfad({
      'GET /api/rechnungen': json(200, { rechnungen: [UEBERSICHT_ZEILE] }),
    });

    expect((await rechnungenUebersicht()).rechnungen).toEqual([UEBERSICHT_ZEILE_VERENGT]);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/rechnungen',
      expect.objectContaining({ method: 'GET' }),
    );
  });

  it('holt die abrechenbaren Angebote', async () => {
    const fetchMock = fetchNachPfad({
      'GET /api/rechnungen/abrechenbare-angebote': json(200, { angebote: [WAHL_ZEILE] }),
    });

    expect((await abrechenbareAngebote()).angebote).toEqual([WAHL_ZEILE_VERENGT]);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/rechnungen/abrechenbare-angebote',
      expect.objectContaining({ method: 'GET' }),
    );
  });

  it('legt zum Angebot einen Entwurf ohne Rumpf an, wo kein Monat gewaehlt ist', async () => {
    const fetchMock = fetchNachPfad({ 'POST /api/angebote/9/rechnungen': json(201, ENTWURF) });

    expect(await rechnungAnlegen(9)).toEqual(ENTWURF_VERENGT);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/9/rechnungen',
      expect.objectContaining({ method: 'POST' }),
    );
    // Ohne Monat geht kein Rumpf hinaus — der Anwendungsfall belegt dann keine Menge vor.
    expect(fetchMock.mock.calls[0][1]).not.toHaveProperty('body');
  });

  it('schickt den gewaehlten Monat als `monat` mit', async () => {
    const fetchMock = fetchNachPfad({ 'POST /api/angebote/9/rechnungen': json(201, ENTWURF) });

    expect(await rechnungAnlegen(9, '2026-10')).toEqual(ENTWURF_VERENGT);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/9/rechnungen',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ monat: '2026-10' }) }),
    );
  });

  it('liest eine Rechnung', async () => {
    const fetchMock = fetchNachPfad({ 'GET /api/rechnungen/4': json(200, ENTWURF) });

    expect(await rechnungLesen(4)).toEqual(ENTWURF_VERENGT);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/rechnungen/4',
      expect.objectContaining({ method: 'GET' }),
    );
  });

  it('aendert den Entwurf als Ganzes; die Menge geht als Dezimaltext hinaus', async () => {
    const fetchMock = fetchNachPfad({ 'PUT /api/rechnungen/4': json(200, ENTWURF) });

    expect(await rechnungAendern(4, EINGABE)).toEqual(ENTWURF_VERENGT);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/rechnungen/4',
      expect.objectContaining({ method: 'PUT', body: JSON.stringify(EINGABE) }),
    );
  });

  it('loescht den Entwurf', async () => {
    const fetchMock = fetchNachPfad({ 'DELETE /api/rechnungen/4': leer(204) });

    await rechnungLoeschen(4);

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/rechnungen/4',
      expect.objectContaining({ method: 'DELETE' }),
    );
  });

  it('stellt die Rechnung und nimmt die gestellte aus der Antwort', async () => {
    const fetchMock = fetchNachPfad({ 'POST /api/rechnungen/4/stellen': json(200, GESTELLT) });

    expect(await rechnungStellen(4)).toEqual(GESTELLT_VERENGT);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/rechnungen/4/stellen',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('holt den Abrechnungsstand eines Angebots', async () => {
    const fetchMock = fetchNachPfad({
      'GET /api/angebote/9/abrechnung': json(200, {
        positionen: [STAND_ZEILE],
        rechnungen: [STAND_RECHNUNG],
      }),
    });

    expect((await angebotAbrechnung(9)).positionen).toEqual([STAND_ZEILE_VERENGT]);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/9/abrechnung',
      expect.objectContaining({ method: 'GET' }),
    );
  });
});
