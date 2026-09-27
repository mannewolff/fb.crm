import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  anhangPfad,
  eintragAendern,
  eintragHinzufuegen,
  parseEintrag,
  parseVorgaengeDerFirma,
  parseVorgaengeUebersicht,
  parseVorgang,
  parseVorgangAngelegt,
  vorgaengeDerFirma,
  vorgaengeUebersicht,
  vorgangAbschliessen,
  vorgangAendern,
  vorgangAnlegen,
  vorgangLesen,
  vorgangWiederEroeffnen,
} from './vorgaenge';
import { fetchNachPfad, json, leer } from '../test/fetchNachPfad';

const ZEILE = {
  id: 12,
  nummer: 2026001,
  titel: 'Neue Website',
  firma: 'Beispiel GmbH',
  phase: 'ANBAHNUNG',
  abgeschlossen: false,
  letzteAktivitaet: '2026-09-24T09:15:00Z',
};

const KOMMENTAR = {
  id: 5,
  art: 'KOMMENTAR',
  text: 'Angerufen',
  geschehenAm: '2026-09-24T09:15:00Z',
  herkunft: 'VON_HAND',
  dateiName: null,
  dateiGroesse: null,
  geaendertAm: null,
};

const ANHANG = {
  id: 6,
  art: 'ANHANG',
  text: null,
  geschehenAm: '2026-09-24T10:00:00Z',
  herkunft: 'VON_HAND',
  dateiName: 'Angebot.pdf',
  dateiGroesse: 20480,
  geaendertAm: '2026-09-24T11:00:00Z',
};

const ZUORDNUNG = { id: 7, name: 'Beispiel GmbH', aktiv: true };

const VORGANG = {
  id: 12,
  nummer: 2026001,
  titel: 'Neue Website',
  phase: 'ANBAHNUNG',
  abgeschlossen: false,
  abschlusswahrscheinlichkeit: 60,
  entscheidungErwartetAm: '2026-10-15',
  firma: ZUORDNUNG,
  ansprechpartner: { id: 3, name: 'Max Mustermann', aktiv: false },
  historie: [ANHANG, KOMMENTAR],
};

const EINGABE = {
  titel: 'Neue Website',
  firmaId: 7,
  ansprechpartnerId: 3,
  abschlusswahrscheinlichkeit: 60,
  entscheidungErwartetAm: '2026-10-15',
};

/** Ein Ereignis der Historie: von der Anwendung vermerkt, ohne Datei (Kriterium 19). */
const EREIGNIS = {
  id: 7,
  art: 'EREIGNIS',
  text: 'Angebot A-2026-009 versendet',
  geschehenAm: '2026-09-25T08:00:00Z',
  herkunft: 'AUTOMATISCH',
  dateiName: null,
  dateiGroesse: null,
  geaendertAm: null,
};

afterEach(() => {
  vi.restoreAllMocks();
});

describe('parseVorgaengeUebersicht', () => {
  it('verengt eine vollstaendige Antwort', () => {
    expect(parseVorgaengeUebersicht({ vorgaenge: [ZEILE], gesamt: 1 })).toEqual({
      vorgaenge: [ZEILE],
      gesamt: 1,
    });
  });

  it('nimmt eine Zeile in der Phase ANGEBOT an (Issue #101)', () => {
    const angebot = { ...ZEILE, phase: 'ANGEBOT' };

    expect(parseVorgaengeUebersicht({ vorgaenge: [angebot], gesamt: 1 }).vorgaenge[0].phase).toBe(
      'ANGEBOT',
    );
  });

  it.each([
    ['kein Objekt', 42],
    ['null', null],
    ['ohne vorgaenge', { gesamt: 1 }],
    ['ohne gesamt', { vorgaenge: [] }],
    ['Zeile ist kein Objekt', { vorgaenge: ['x'], gesamt: 1 }],
    ['Zeile ohne id', { vorgaenge: [{ ...ZEILE, id: undefined }], gesamt: 1 }],
    ['Zeile ohne nummer', { vorgaenge: [{ ...ZEILE, nummer: '2026001' }], gesamt: 1 }],
    ['Zeile ohne titel', { vorgaenge: [{ ...ZEILE, titel: 7 }], gesamt: 1 }],
    ['Zeile ohne firma', { vorgaenge: [{ ...ZEILE, firma: null }], gesamt: 1 }],
    ['Zeile mit unbekannter phase', { vorgaenge: [{ ...ZEILE, phase: 'RECHNUNG' }], gesamt: 1 }],
    ['Zeile ohne abgeschlossen', { vorgaenge: [{ ...ZEILE, abgeschlossen: 'nein' }], gesamt: 1 }],
    [
      'Zeile ohne letzteAktivitaet',
      { vorgaenge: [{ ...ZEILE, letzteAktivitaet: undefined }], gesamt: 1 },
    ],
  ])('weist eine Antwort %s ab', (_fall, antwort) => {
    expect(() => parseVorgaengeUebersicht(antwort)).toThrow(TypeError);
  });
});

describe('parseVorgaengeDerFirma', () => {
  it('verengt offene und abgeschlossene getrennt', () => {
    const antwort = { offene: [ZEILE], abgeschlossene: [{ ...ZEILE, id: 13, abgeschlossen: true }] };

    expect(parseVorgaengeDerFirma(antwort)).toEqual(antwort);
  });

  it.each([
    ['ohne offene', { abgeschlossene: [] }],
    ['ohne abgeschlossene', { offene: [] }],
  ])('weist eine Antwort %s ab', (_fall, antwort) => {
    expect(() => parseVorgaengeDerFirma(antwort)).toThrow(TypeError);
  });
});

describe('parseEintrag', () => {
  it('verengt einen Kommentar ohne Datei und ohne Aenderung', () => {
    expect(parseEintrag(KOMMENTAR)).toEqual(KOMMENTAR);
  });

  it('verengt einen Anhang samt Dateiangaben und Aenderungsvermerk', () => {
    expect(parseEintrag(ANHANG)).toEqual(ANHANG);
  });

  it('verengt ein Ereignis mit automatischer Herkunft (Issue #93)', () => {
    expect(parseEintrag(EREIGNIS)).toEqual(EREIGNIS);
  });

  it.each([
    ['ohne id', { ...KOMMENTAR, id: undefined }],
    ['mit unbekannter art', { ...KOMMENTAR, art: 'NOTIZ' }],
    ['mit falschem text', { ...KOMMENTAR, text: 7 }],
    ['ohne geschehenAm', { ...KOMMENTAR, geschehenAm: undefined }],
    ['mit unbekannter herkunft', { ...KOMMENTAR, herkunft: 'IMPORT' }],
    ['mit falschem dateiName', { ...ANHANG, dateiName: 7 }],
    ['mit falscher dateiGroesse', { ...ANHANG, dateiGroesse: '20480' }],
    ['mit falschem geaendertAm', { ...ANHANG, geaendertAm: 7 }],
  ])('weist einen Eintrag %s ab', (_fall, antwort) => {
    expect(() => parseEintrag(antwort)).toThrow(TypeError);
  });
});

describe('parseVorgang', () => {
  it('verengt einen Vorgang samt Zuordnungen und Historie', () => {
    expect(parseVorgang(VORGANG)).toEqual(VORGANG);
  });

  it('nimmt einen Vorgang ohne Ansprechpartner', () => {
    expect(parseVorgang({ ...VORGANG, ansprechpartner: null }).ansprechpartner).toBeNull();
  });

  it('nimmt einen Vorgang in der Phase ANGEBOT an (Issue #101)', () => {
    expect(parseVorgang({ ...VORGANG, phase: 'ANGEBOT' }).phase).toBe('ANGEBOT');
  });

  it('nimmt die beiden Pipeline-Felder auch als null (Kriterium 21)', () => {
    const ohne = parseVorgang({
      ...VORGANG,
      abschlusswahrscheinlichkeit: null,
      entscheidungErwartetAm: null,
    });

    expect(ohne.abschlusswahrscheinlichkeit).toBeNull();
    expect(ohne.entscheidungErwartetAm).toBeNull();
  });

  it.each([
    ['ohne id', { ...VORGANG, id: undefined }],
    ['ohne titel', { ...VORGANG, titel: null }],
    ['mit unbekannter phase', { ...VORGANG, phase: 'RECHNUNG' }],
    ['ohne firma', { ...VORGANG, firma: undefined }],
    ['mit Firma ohne name', { ...VORGANG, firma: { ...ZUORDNUNG, name: undefined } }],
    ['mit Firma ohne aktiv', { ...VORGANG, firma: { ...ZUORDNUNG, aktiv: 'ja' } }],
    ['ohne historie', { ...VORGANG, historie: undefined }],
    [
      'mit falscher abschlusswahrscheinlichkeit',
      { ...VORGANG, abschlusswahrscheinlichkeit: '60' },
    ],
    ['ohne abschlusswahrscheinlichkeit', { ...VORGANG, abschlusswahrscheinlichkeit: undefined }],
    ['mit falschem entscheidungErwartetAm', { ...VORGANG, entscheidungErwartetAm: 20261015 }],
    ['ohne entscheidungErwartetAm', { ...VORGANG, entscheidungErwartetAm: undefined }],
  ])('weist eine Antwort %s ab', (_fall, antwort) => {
    expect(() => parseVorgang(antwort)).toThrow(TypeError);
  });
});

describe('parseVorgangAngelegt', () => {
  it('verengt Kennung und Nummer', () => {
    expect(parseVorgangAngelegt({ id: 12, nummer: 2026001 })).toEqual({ id: 12, nummer: 2026001 });
  });

  it('weist eine Antwort ohne Nummer ab', () => {
    expect(() => parseVorgangAngelegt({ id: 12 })).toThrow(TypeError);
  });
});

describe('vorgaengeUebersicht', () => {
  it('traegt den Suchtext unverfaelscht in die Adresse', async () => {
    const adresse = '/api/vorgaenge?suche=a%26b%25+c&auchAbgeschlossene=true';
    fetchNachPfad({ [`GET ${adresse}`]: json(200, { vorgaenge: [ZEILE], gesamt: 1 }) });

    expect((await vorgaengeUebersicht('a&b% c', true)).gesamt).toBe(1);
  });

  it('fragt ohne Schalter nur die offenen Vorgaenge', async () => {
    const adresse = '/api/vorgaenge?suche=&auchAbgeschlossene=false';
    fetchNachPfad({ [`GET ${adresse}`]: json(200, { vorgaenge: [], gesamt: 0 }) });

    expect(await vorgaengeUebersicht('', false)).toEqual({ vorgaenge: [], gesamt: 0 });
  });

  it('reicht das Abbruchsignal an den Aufruf durch', async () => {
    const adresse = '/api/vorgaenge?suche=&auchAbgeschlossene=false';
    const fetchMock = fetchNachPfad({ [`GET ${adresse}`]: json(200, { vorgaenge: [], gesamt: 0 }) });
    const steuerung = new AbortController();

    await vorgaengeUebersicht('', false, steuerung.signal);

    expect(fetchMock).toHaveBeenCalledWith(
      adresse,
      expect.objectContaining({ signal: steuerung.signal }),
    );
  });
});

describe('die Wege des Vorgangs', () => {
  it('legt an und bekommt Kennung und Nummer zurueck', async () => {
    const fetchMock = fetchNachPfad({
      'POST /api/vorgaenge': json(201, { id: 12, nummer: 2026001 }),
    });

    expect(await vorgangAnlegen(EINGABE)).toEqual({ id: 12, nummer: 2026001 });
    expect(fetchMock.mock.calls[0][1]?.body).toBe(JSON.stringify(EINGABE));
  });

  it('liest den Vorgang samt Historie', async () => {
    fetchNachPfad({ 'GET /api/vorgaenge/12': json(200, VORGANG) });

    expect((await vorgangLesen(12)).historie).toHaveLength(2);
  });

  it('schreibt die Angaben mit PUT und Rumpf fort', async () => {
    const fetchMock = fetchNachPfad({ 'PUT /api/vorgaenge/12': leer(204) });

    await vorgangAendern(12, { ...EINGABE, ansprechpartnerId: null });

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/vorgaenge/12',
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({ ...EINGABE, ansprechpartnerId: null }),
      }),
    );
  });

  it('schliesst ab und eroeffnet wieder', async () => {
    fetchNachPfad({
      'POST /api/vorgaenge/12/abschliessen': leer(204),
      'POST /api/vorgaenge/12/wiedereroeffnen': leer(204),
    });

    await expect(vorgangAbschliessen(12)).resolves.toBeUndefined();
    await expect(vorgangWiederEroeffnen(12)).resolves.toBeUndefined();
  });

  it('liest die Vorgaenge einer Firma ueber deren eigenen Weg', async () => {
    fetchNachPfad({
      'GET /api/firmen/7/vorgaenge': json(200, { offene: [ZEILE], abgeschlossene: [] }),
    });

    expect((await vorgaengeDerFirma(7)).offene).toHaveLength(1);
  });
});

describe('die Wege des Eintrags', () => {
  it('schickt einen neuen Eintrag als Formular, damit die Datei mitgeht', async () => {
    const fetchMock = fetchNachPfad({ 'POST /api/vorgaenge/12/eintraege': leer(201) });
    const formular = new FormData();
    formular.append('art', 'KOMMENTAR');

    await eintragHinzufuegen(12, formular);

    const optionen = fetchMock.mock.calls[0][1];
    expect(optionen?.body).toBe(formular);
    expect(optionen?.headers).toBeUndefined();
  });

  it('schreibt Text und Zeitpunkt eines Eintrags als JSON fort', async () => {
    const aenderung = { text: 'Angerufen, Rueckruf zugesagt', geschehenAm: '2026-09-24T09:15:00Z' };
    const fetchMock = fetchNachPfad({ 'PUT /api/vorgaenge/12/eintraege/5': leer(204) });

    await eintragAendern(12, 5, aenderung);

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/vorgaenge/12/eintraege/5',
      expect.objectContaining({
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(aenderung),
      }),
    );
  });
});

describe('anhangPfad', () => {
  it('nennt den Weg zur Datei, ohne ihn aufzurufen', () => {
    expect(anhangPfad(3, 7)).toBe('/api/vorgaenge/3/eintraege/7/datei');
  });
});
