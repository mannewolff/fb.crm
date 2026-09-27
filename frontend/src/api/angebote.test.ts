import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  angebotAblehnen,
  angebotAendern,
  angebotAnlegen,
  angebotAnnehmen,
  angebotLesen,
  angebotPdfPfad,
  angebotVersenden,
  angebotVerwerfen,
  angeboteDesVorgangs,
  parseAngebot,
  parseVorgangAngebote,
} from './angebote';
import { fetchNachPfad, json, leer } from '../test/fetchNachPfad';

/** Eine Position, wie Jackson sie schreibt: Menge und Preis als Zahl mit zwei Stellen. */
const POSITION = {
  bezeichnung: 'Konzeption',
  abrechnungsmodus: 'AUFWAND',
  menge: 2.5,
  einheit: 'PERSONENTAG',
  einzelpreis: 1000.01,
  betrag: 2500.03,
};

const ENTWURF = {
  id: 9,
  vorgangId: 5,
  nummer: null,
  stand: 'ENTWURF',
  angebotDatum: '2026-09-24',
  gueltigBis: '2026-10-24',
  leistungsbeschreibung: null,
  zahlungsbedingungen: 'Zahlbar in 14 Tagen',
  versendetAm: null,
  reaktionAm: null,
  positionen: [POSITION],
  summe: 2500.03,
};

/** Dieselbe Position nach dem Verengen: Menge in Hundertsteln, Geld in Cent (E5). */
const POSITION_VERENGT = {
  bezeichnung: 'Konzeption',
  abrechnungsmodus: 'AUFWAND',
  mengeInHundertsteln: 250,
  einheit: 'PERSONENTAG',
  einzelpreisInCent: 100001,
  betragInCent: 250003,
};

const ENTWURF_VERENGT = {
  id: 9,
  vorgangId: 5,
  nummer: null,
  stand: 'ENTWURF',
  angebotDatum: '2026-09-24',
  gueltigBis: '2026-10-24',
  leistungsbeschreibung: null,
  zahlungsbedingungen: 'Zahlbar in 14 Tagen',
  versendetAm: null,
  reaktionAm: null,
  positionen: [POSITION_VERENGT],
  summeInCent: 250003,
};

const ZEILE = {
  id: 9,
  nummer: 'A-2026-001',
  stand: 'VERSENDET',
  angebotDatum: '2026-09-24',
  gueltigBis: '2026-10-24',
  summe: 2500.03,
};

const EINGABE = {
  gueltigBis: '2026-10-24',
  leistungsbeschreibung: 'Neue Website',
  zahlungsbedingungen: 'Zahlbar in 14 Tagen',
  positionen: [
    {
      bezeichnung: 'Konzeption',
      abrechnungsmodus: 'AUFWAND' as const,
      menge: '2.50',
      einheit: 'PERSONENTAG' as const,
      einzelpreis: '1000.01',
    },
  ],
};

afterEach(() => {
  vi.restoreAllMocks();
});

describe('parseAngebot', () => {
  it('verengt einen Entwurf samt Positionen und rechnet Geld in ganze Cent', () => {
    expect(parseAngebot(ENTWURF)).toEqual(ENTWURF_VERENGT);
  });

  it('nimmt ein versendetes Angebot mit Nummer und Zeitpunkten an', () => {
    const versendet = {
      ...ENTWURF,
      nummer: 'A-2026-001',
      stand: 'ANGENOMMEN',
      leistungsbeschreibung: 'Neue Website',
      versendetAm: '2026-09-24T08:00:00Z',
      reaktionAm: '2026-09-26T09:30:00Z',
    };

    expect(parseAngebot(versendet)).toEqual({
      ...ENTWURF_VERENGT,
      nummer: 'A-2026-001',
      stand: 'ANGENOMMEN',
      leistungsbeschreibung: 'Neue Website',
      versendetAm: '2026-09-24T08:00:00Z',
      reaktionAm: '2026-09-26T09:30:00Z',
    });
  });

  it('nimmt ein Angebot ohne Position mit der Summe null an', () => {
    expect(parseAngebot({ ...ENTWURF, positionen: [], summe: 0 })).toEqual({
      ...ENTWURF_VERENGT,
      positionen: [],
      summeInCent: 0,
    });
  });

  it.each([
    ['ENTWURF'],
    ['VERSENDET'],
    ['ABGELAUFEN'],
    ['ANGENOMMEN'],
    ['ABGELEHNT'],
    ['ABGELOEST'],
  ])('nimmt den Stand %s an', (stand) => {
    expect(parseAngebot({ ...ENTWURF, stand }).stand).toBe(stand);
  });

  it.each([
    ['STUNDE'],
    ['PERSONENTAG'],
    ['PAUSCHAL'],
  ])('nimmt die Einheit %s an', (einheit) => {
    expect(parseAngebot({ ...ENTWURF, positionen: [{ ...POSITION, einheit }] }).positionen[0].einheit)
      .toBe(einheit);
  });

  it('nimmt den Abrechnungsmodus FESTPREIS an', () => {
    const festpreis = { ...POSITION, abrechnungsmodus: 'FESTPREIS' };

    expect(parseAngebot({ ...ENTWURF, positionen: [festpreis] }).positionen[0].abrechnungsmodus)
      .toBe('FESTPREIS');
  });

  it.each([
    ['kein Objekt', 42],
    ['null', null],
    ['ohne id', { ...ENTWURF, id: '9' }],
    ['ohne vorgangId', { ...ENTWURF, vorgangId: undefined }],
    ['Nummer als Zahl', { ...ENTWURF, nummer: 2026001 }],
    ['unbekannter Stand', { ...ENTWURF, stand: 'STORNIERT' }],
    ['ohne Angebotsdatum', { ...ENTWURF, angebotDatum: null }],
    ['ohne Gueltigkeit', { ...ENTWURF, gueltigBis: undefined }],
    ['Leistungsbeschreibung als Zahl', { ...ENTWURF, leistungsbeschreibung: 7 }],
    ['Zahlungsbedingungen als Zahl', { ...ENTWURF, zahlungsbedingungen: 7 }],
    ['versendetAm als Zahl', { ...ENTWURF, versendetAm: 17 }],
    ['reaktionAm als Zahl', { ...ENTWURF, reaktionAm: 17 }],
    ['Positionen als Objekt', { ...ENTWURF, positionen: {} }],
    ['Summe als Zeichenkette', { ...ENTWURF, summe: '2500.03' }],
    ['Summe mit drei Nachkommastellen', { ...ENTWURF, summe: 2500.031 }],
    ['Position ist kein Objekt', { ...ENTWURF, positionen: ['x'] }],
    ['Position ohne Bezeichnung', { ...ENTWURF, positionen: [{ ...POSITION, bezeichnung: null }] }],
    [
      'Position mit unbekanntem Modus',
      { ...ENTWURF, positionen: [{ ...POSITION, abrechnungsmodus: 'SCHAETZUNG' }] },
    ],
    ['Position mit Menge als Text', { ...ENTWURF, positionen: [{ ...POSITION, menge: '2,5' }] }],
    ['Position mit unbekannter Einheit', { ...ENTWURF, positionen: [{ ...POSITION, einheit: 'TAG' }] }],
    [
      'Position mit negativem Einzelpreis',
      { ...ENTWURF, positionen: [{ ...POSITION, einzelpreis: -1 }] },
    ],
    ['Position ohne Betrag', { ...ENTWURF, positionen: [{ ...POSITION, betrag: undefined }] }],
  ])('weist eine Antwort ab: %s', (_fall, rumpf) => {
    expect(() => parseAngebot(rumpf)).toThrow(TypeError);
  });
});

describe('parseVorgangAngebote', () => {
  it('verengt die Liste am Vorgang', () => {
    expect(parseVorgangAngebote({ angebote: [ZEILE] })).toEqual({
      angebote: [
        {
          id: 9,
          nummer: 'A-2026-001',
          stand: 'VERSENDET',
          angebotDatum: '2026-09-24',
          gueltigBis: '2026-10-24',
          summeInCent: 250003,
        },
      ],
    });
  });

  it('verengt die leere Liste', () => {
    expect(parseVorgangAngebote({ angebote: [] })).toEqual({ angebote: [] });
  });

  it.each([
    ['kein Objekt', 'x'],
    ['ohne angebote', {}],
    ['angebote ist kein Array', { angebote: 3 }],
    ['Zeile ohne id', { angebote: [{ ...ZEILE, id: null }] }],
    ['Zeile mit Nummer als Zahl', { angebote: [{ ...ZEILE, nummer: 1 }] }],
    ['Zeile mit unbekanntem Stand', { angebote: [{ ...ZEILE, stand: 'OFFEN' }] }],
    ['Zeile ohne Angebotsdatum', { angebote: [{ ...ZEILE, angebotDatum: 7 }] }],
    ['Zeile ohne Gueltigkeit', { angebote: [{ ...ZEILE, gueltigBis: 7 }] }],
    ['Zeile ohne Summe', { angebote: [{ ...ZEILE, summe: null }] }],
  ])('weist eine Antwort ab: %s', (_fall, rumpf) => {
    expect(() => parseVorgangAngebote(rumpf)).toThrow(TypeError);
  });
});

describe('die Wege', () => {
  it('holt die Angebote eines Vorgangs', async () => {
    const fetchMock = fetchNachPfad({
      'GET /api/vorgaenge/5/angebote': json(200, { angebote: [ZEILE] }),
    });

    const antwort = await angeboteDesVorgangs(5);

    expect(antwort.angebote).toHaveLength(1);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/vorgaenge/5/angebote',
      expect.objectContaining({ method: 'GET' }),
    );
  });

  it('legt ein Angebot ohne Vorlage an', async () => {
    const fetchMock = fetchNachPfad({ 'POST /api/vorgaenge/5/angebote': json(201, ENTWURF) });

    expect(await angebotAnlegen(5, null)).toEqual(ENTWURF_VERENGT);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/vorgaenge/5/angebote',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ vorlageAngebotId: null }) }),
    );
  });

  it('legt ein Angebot mit einer Vorlage an (Kriterium 8)', async () => {
    const fetchMock = fetchNachPfad({ 'POST /api/vorgaenge/5/angebote': json(201, ENTWURF) });

    await angebotAnlegen(5, 7);

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/vorgaenge/5/angebote',
      expect.objectContaining({ body: JSON.stringify({ vorlageAngebotId: 7 }) }),
    );
  });

  it('liest ein Angebot', async () => {
    fetchNachPfad({ 'GET /api/angebote/9': json(200, ENTWURF) });

    expect(await angebotLesen(9)).toEqual(ENTWURF_VERENGT);
  });

  it('schreibt den Entwurf als Ganzes fort und nimmt die Antwort (E8)', async () => {
    const fetchMock = fetchNachPfad({ 'PUT /api/angebote/9': json(200, ENTWURF) });

    expect(await angebotAendern(9, EINGABE)).toEqual(ENTWURF_VERENGT);
    // Menge und Preis gehen als Dezimaltext hinaus und nicht als Gleitkommazahl (E5, geld.ts).
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/9',
      expect.objectContaining({ method: 'PUT', body: JSON.stringify(EINGABE) }),
    );
  });

  it('verwirft einen Entwurf ohne Antwortrumpf (E19)', async () => {
    const fetchMock = fetchNachPfad({ 'DELETE /api/angebote/9': leer(204) });

    await expect(angebotVerwerfen(9)).resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/9',
      expect.objectContaining({ method: 'DELETE' }),
    );
  });

  it.each([
    ['versenden', angebotVersenden, 'POST /api/angebote/9/versenden'],
    ['annehmen', angebotAnnehmen, 'POST /api/angebote/9/annehmen'],
    ['ablehnen', angebotAblehnen, 'POST /api/angebote/9/ablehnen'],
  ])('schaltet den Zustand ueber den Pfad: %s', async (_name, weg, schluessel) => {
    const fetchMock = fetchNachPfad({ [schluessel]: json(200, ENTWURF) });

    expect(await weg(9)).toEqual(ENTWURF_VERENGT);
    expect(fetchMock).toHaveBeenCalledWith(
      schluessel.slice('POST '.length),
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('nennt den Weg zum Beleg als Pfad und nicht als Aufruf (E17)', () => {
    const fetchMock = fetchNachPfad({});

    expect(angebotPdfPfad(9)).toBe('/api/angebote/9/pdf');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
