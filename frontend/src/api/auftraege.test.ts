import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  auftragAmAngebot,
  auftragAnlegen,
  auftragLesen,
  auftragLoeschen,
  auftragPflegen,
  auftraegeDesVorgangs,
  parseAngebotAuftrag,
  parseAuftrag,
  parseVorgangAuftraege,
} from './auftraege';
import { alsJson, fetchNachPfad, json, leer } from '../test/fetchNachPfad';

/** Eine Aufwandsposition, wie Jackson sie schreibt: Zahlen mit zwei Stellen. */
const AUFWAND = {
  bezeichnung: 'Konzeption',
  abrechnungsmodus: 'AUFWAND',
  menge: 2.5,
  einheit: 'PERSONENTAG',
  einzelpreis: 1000.01,
  stundenJePersonentag: 8,
  betrag: 2500.03,
};

/** Eine Festpreisposition traegt keine Stunden je Personentag (Kriterium 4). */
const FESTPREIS = {
  bezeichnung: 'Einrichtung',
  abrechnungsmodus: 'FESTPREIS',
  menge: 1,
  einheit: 'PAUSCHAL',
  einzelpreis: 500,
  stundenJePersonentag: null,
  betrag: 500,
};

const AUFTRAG = {
  id: 3,
  vorgangId: 5,
  angebotId: 9,
  angebotNummer: 'A-2026-001',
  nummer: 'AU-2026-001',
  status: 'OFFEN',
  auftragDatum: '2026-09-28',
  kundenbestellnummer: null,
  leistungAb: null,
  leistungBis: null,
  positionen: [AUFWAND, FESTPREIS],
  summe: 3000.03,
};

const AUFTRAG_VERENGT = {
  id: 3,
  vorgangId: 5,
  angebotId: 9,
  angebotNummer: 'A-2026-001',
  nummer: 'AU-2026-001',
  status: 'OFFEN',
  auftragDatum: '2026-09-28',
  kundenbestellnummer: null,
  leistungAb: null,
  leistungBis: null,
  positionen: [
    {
      bezeichnung: 'Konzeption',
      abrechnungsmodus: 'AUFWAND',
      mengeInHundertsteln: 250,
      einheit: 'PERSONENTAG',
      einzelpreisInCent: 100001,
      stundenJePersonentagInHundertsteln: 800,
      betragInCent: 250003,
    },
    {
      bezeichnung: 'Einrichtung',
      abrechnungsmodus: 'FESTPREIS',
      mengeInHundertsteln: 100,
      einheit: 'PAUSCHAL',
      einzelpreisInCent: 50000,
      stundenJePersonentagInHundertsteln: null,
      betragInCent: 50000,
    },
  ],
  summeInCent: 300003,
};

const ZEILE = {
  id: 3,
  nummer: 'AU-2026-001',
  status: 'IN_ARBEIT',
  auftragDatum: '2026-09-28',
  leistungAb: '2026-10-01',
  leistungBis: '2026-12-31',
  summe: 3000.03,
};

const PFLEGE = {
  auftragDatum: '2026-09-28',
  kundenbestellnummer: 'PO-4711',
  leistungAb: null,
  leistungBis: null,
  status: 'IN_ARBEIT' as const,
};

afterEach(() => {
  vi.restoreAllMocks();
});

describe('parseAuftrag', () => {
  it('verengt einen Auftrag samt Positionen und rechnet Geld in ganze Cent', () => {
    expect(parseAuftrag(AUFTRAG)).toEqual(AUFTRAG_VERENGT);
  });

  it('nimmt Bestellnummer und Leistungszeitraum an, wenn sie gesetzt sind', () => {
    const gesetzt = {
      ...AUFTRAG,
      kundenbestellnummer: 'PO-4711',
      leistungAb: '2026-10-01',
      leistungBis: '2026-12-31',
    };

    expect(parseAuftrag(gesetzt)).toEqual({
      ...AUFTRAG_VERENGT,
      kundenbestellnummer: 'PO-4711',
      leistungAb: '2026-10-01',
      leistungBis: '2026-12-31',
    });
  });

  it('nimmt einen Auftrag an, dessen Angebot keine Nummer traegt', () => {
    expect(parseAuftrag({ ...AUFTRAG, angebotNummer: null }).angebotNummer).toBeNull();
  });

  it.each([['OFFEN'], ['IN_ARBEIT'], ['ABGESCHLOSSEN']])('nimmt den Status %s an', (status) => {
    expect(parseAuftrag({ ...AUFTRAG, status }).status).toBe(status);
  });

  it.each([
    ['kein Objekt', 42],
    ['null', null],
    ['id als Text', { ...AUFTRAG, id: '3' }],
    ['ohne vorgangId', { ...AUFTRAG, vorgangId: undefined }],
    ['ohne angebotId', { ...AUFTRAG, angebotId: undefined }],
    ['Angebotsnummer als Zahl', { ...AUFTRAG, angebotNummer: 1 }],
    ['ohne Nummer', { ...AUFTRAG, nummer: null }],
    ['unbekannter Status', { ...AUFTRAG, status: 'STORNIERT' }],
    ['ohne Auftragsdatum', { ...AUFTRAG, auftragDatum: undefined }],
    ['Bestellnummer als Zahl', { ...AUFTRAG, kundenbestellnummer: 4711 }],
    ['fehlende Bestellnummer', { ...AUFTRAG, kundenbestellnummer: undefined }],
    ['Leistungsbeginn als Zahl', { ...AUFTRAG, leistungAb: 1 }],
    ['Leistungsende als Zahl', { ...AUFTRAG, leistungBis: 1 }],
    ['Positionen als Objekt', { ...AUFTRAG, positionen: {} }],
    ['Summe als Text', { ...AUFTRAG, summe: '3000.03' }],
    ['Position ohne Bezeichnung', { ...AUFTRAG, positionen: [{ ...AUFWAND, bezeichnung: null }] }],
    [
      'Position mit unbekanntem Modus',
      { ...AUFTRAG, positionen: [{ ...AUFWAND, abrechnungsmodus: 'SCHAETZUNG' }] },
    ],
    ['Position mit unbekannter Einheit', { ...AUFTRAG, positionen: [{ ...AUFWAND, einheit: 'TAG' }] }],
    ['Position mit Menge als Text', { ...AUFTRAG, positionen: [{ ...AUFWAND, menge: '2.5' }] }],
    [
      'Position mit Stunden als Text',
      { ...AUFTRAG, positionen: [{ ...AUFWAND, stundenJePersonentag: '8' }] },
    ],
    [
      'Position ohne Feld fuer Stunden',
      { ...AUFTRAG, positionen: [{ ...AUFWAND, stundenJePersonentag: undefined }] },
    ],
    ['Position ohne Betrag', { ...AUFTRAG, positionen: [{ ...AUFWAND, betrag: undefined }] }],
  ])('weist eine Antwort ab: %s', (_fall, rumpf) => {
    expect(() => parseAuftrag(rumpf)).toThrow(TypeError);
  });
});

describe('parseVorgangAuftraege', () => {
  it('verengt die Liste am Vorgang', () => {
    expect(parseVorgangAuftraege({ auftraege: [ZEILE] })).toEqual({
      auftraege: [
        {
          id: 3,
          nummer: 'AU-2026-001',
          status: 'IN_ARBEIT',
          auftragDatum: '2026-09-28',
          leistungAb: '2026-10-01',
          leistungBis: '2026-12-31',
          summeInCent: 300003,
        },
      ],
    });
  });

  it('nimmt eine Zeile ohne Leistungszeitraum an', () => {
    const ohne = { ...ZEILE, leistungAb: null, leistungBis: null };

    expect(parseVorgangAuftraege({ auftraege: [ohne] }).auftraege[0]).toMatchObject({
      leistungAb: null,
      leistungBis: null,
    });
  });

  it.each([
    ['kein Objekt', 'x'],
    ['Liste fehlt', {}],
    ['Zeile ist kein Objekt', { auftraege: [1] }],
    ['Zeile ohne Nummer', { auftraege: [{ ...ZEILE, nummer: undefined }] }],
    ['Zeile mit unbekanntem Status', { auftraege: [{ ...ZEILE, status: 'X' }] }],
    ['Zeile ohne Summe', { auftraege: [{ ...ZEILE, summe: undefined }] }],
  ])('weist eine Antwort ab: %s', (_fall, rumpf) => {
    expect(() => parseVorgangAuftraege(rumpf)).toThrow(TypeError);
  });
});

describe('parseAngebotAuftrag', () => {
  it('nimmt „kein Auftrag, nicht anlegbar" an', () => {
    expect(parseAngebotAuftrag({ auftrag: null, anlegbar: false })).toEqual({
      auftrag: null,
      anlegbar: false,
    });
  });

  it('nimmt einen bestehenden Auftrag an', () => {
    expect(parseAngebotAuftrag({ auftrag: AUFTRAG, anlegbar: false })).toEqual({
      auftrag: AUFTRAG_VERENGT,
      anlegbar: false,
    });
  });

  it.each([
    ['anlegbar fehlt', { auftrag: null }],
    ['anlegbar als Text', { auftrag: null, anlegbar: 'ja' }],
    ['Auftrag fehlt', { anlegbar: true }],
    ['Auftrag in falscher Form', { auftrag: { id: 3 }, anlegbar: false }],
  ])('weist eine Antwort ab: %s', (_fall, rumpf) => {
    expect(() => parseAngebotAuftrag(rumpf)).toThrow(TypeError);
  });
});

describe('die sechs Wege zum Auftrag', () => {
  it('fragt am Angebot mit GET nach dem Auftrag (Plan E3)', async () => {
    const fetch = fetchNachPfad({
      'GET /api/angebote/9/auftrag': json(200, { auftrag: null, anlegbar: true }),
    });

    await expect(auftragAmAngebot(9)).resolves.toEqual({ auftrag: null, anlegbar: true });
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it('legt am Angebot mit POST an und schickt die Eingabe als Rumpf', async () => {
    let gesendet: unknown = null;
    fetchNachPfad({
      'POST /api/angebote/9/auftrag': (rumpf) => {
        gesendet = alsJson(rumpf);
        return json(201, AUFTRAG)();
      },
    });

    const angelegt = await auftragAnlegen(9, {
      kundenbestellnummer: null,
      leistungAb: null,
      leistungBis: null,
      positionen: [{ platz: 1, menge: '2.50', stundenJePersonentag: '8.00' }],
    });

    expect(angelegt).toEqual(AUFTRAG_VERENGT);
    // Ohne Datum fehlt das Feld ganz — das Backend setzt den heutigen Tag (Plan E7).
    expect(gesendet).toEqual({
      kundenbestellnummer: null,
      leistungAb: null,
      leistungBis: null,
      positionen: [{ platz: 1, menge: '2.50', stundenJePersonentag: '8.00' }],
    });
  });

  it('liest einen Auftrag mit GET', async () => {
    fetchNachPfad({ 'GET /api/auftraege/3': json(200, AUFTRAG) });

    await expect(auftragLesen(3)).resolves.toEqual(AUFTRAG_VERENGT);
  });

  it('pflegt einen Auftrag mit PUT und schickt die vier Angaben samt Status', async () => {
    let gesendet: unknown = null;
    fetchNachPfad({
      'PUT /api/auftraege/3': (rumpf) => {
        gesendet = alsJson(rumpf);
        return json(200, { ...AUFTRAG, status: 'IN_ARBEIT' })();
      },
    });

    const gepflegt = await auftragPflegen(3, PFLEGE);

    expect(gepflegt.status).toBe('IN_ARBEIT');
    expect(gesendet).toEqual(PFLEGE);
  });

  it('loescht einen Auftrag mit DELETE', async () => {
    const fetch = fetchNachPfad({ 'DELETE /api/auftraege/3': leer(204) });

    await expect(auftragLoeschen(3)).resolves.toBeUndefined();
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it('liest die Auftraege eines Vorgangs mit GET', async () => {
    fetchNachPfad({ 'GET /api/vorgaenge/5/auftraege': json(200, { auftraege: [] }) });

    await expect(auftraegeDesVorgangs(5)).resolves.toEqual({ auftraege: [] });
  });
});
