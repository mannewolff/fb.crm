import { afterEach, describe, expect, it, vi } from 'vitest';

import { auftragsbestand, parseAuftragsbestand } from './auftragsbestand';
import { fetchNachPfad, json } from '../test/fetchNachPfad';

const ZEILE = {
  vorgangId: 5,
  vorgangNummer: 12,
  vorgangTitel: 'Website-Relaunch',
  firma: 'Beispiel GmbH',
  auftragId: 3,
  nummer: 'AU-2026-001',
  status: 'IN_ARBEIT',
  auftragssumme: 3000.03,
  abgerechnet: 0,
  offenerRest: 3000.03,
};

const ZEILE_VERENGT = {
  vorgangId: 5,
  vorgangNummer: 12,
  vorgangTitel: 'Website-Relaunch',
  firma: 'Beispiel GmbH',
  auftragId: 3,
  nummer: 'AU-2026-001',
  status: 'IN_ARBEIT',
  auftragssummeInCent: 300003,
  abgerechnetInCent: 0,
  offenerRestInCent: 300003,
};

afterEach(() => {
  vi.restoreAllMocks();
});

describe('parseAuftragsbestand', () => {
  it('verengt Zeilen und Summen und rechnet Geld in ganze Cent', () => {
    expect(
      parseAuftragsbestand({ zeilen: [ZEILE], beauftragt: 3000.03, nochOffen: 3000.03 }),
    ).toEqual({ zeilen: [ZEILE_VERENGT], beauftragtInCent: 300003, nochOffenInCent: 300003 });
  });

  it('nimmt den leeren Bestand mit zweimal null an', () => {
    expect(parseAuftragsbestand({ zeilen: [], beauftragt: 0.0, nochOffen: 0.0 })).toEqual({
      zeilen: [],
      beauftragtInCent: 0,
      nochOffenInCent: 0,
    });
  });

  it.each([['OFFEN'], ['IN_ARBEIT'], ['ABGESCHLOSSEN']])('nimmt den Status %s an', (status) => {
    const antwort = { zeilen: [{ ...ZEILE, status }], beauftragt: 0, nochOffen: 0 };

    expect(parseAuftragsbestand(antwort).zeilen[0].status).toBe(status);
  });

  it.each([
    ['kein Objekt', 'x'],
    ['Zeilen fehlen', { beauftragt: 0, nochOffen: 0 }],
    ['beauftragt fehlt', { zeilen: [], nochOffen: 0 }],
    ['nochOffen als Text', { zeilen: [], beauftragt: 0, nochOffen: '0.00' }],
    ['Zeile ist kein Objekt', { zeilen: [1], beauftragt: 0, nochOffen: 0 }],
    ['Zeile ohne Vorgangsnummer', { zeilen: [{ ...ZEILE, vorgangNummer: '12' }], beauftragt: 0, nochOffen: 0 }],
    ['Zeile ohne Firma', { zeilen: [{ ...ZEILE, firma: null }], beauftragt: 0, nochOffen: 0 }],
    ['Zeile ohne Auftragskennung', { zeilen: [{ ...ZEILE, auftragId: undefined }], beauftragt: 0, nochOffen: 0 }],
    ['Zeile mit unbekanntem Status', { zeilen: [{ ...ZEILE, status: 'X' }], beauftragt: 0, nochOffen: 0 }],
    ['Zeile ohne abgerechnet', { zeilen: [{ ...ZEILE, abgerechnet: undefined }], beauftragt: 0, nochOffen: 0 }],
    ['Zeile mit Rest als Text', { zeilen: [{ ...ZEILE, offenerRest: '1' }], beauftragt: 0, nochOffen: 0 }],
  ])('weist eine Antwort ab: %s', (_fall, rumpf) => {
    expect(() => parseAuftragsbestand(rumpf)).toThrow(TypeError);
  });
});

describe('auftragsbestand', () => {
  it('fragt mit GET und reicht das Abbruchsignal durch', async () => {
    const fetchMock = fetchNachPfad({
      'GET /api/auftragsbestand': json(200, { zeilen: [], beauftragt: 0, nochOffen: 0 }),
    });
    const steuerung = new AbortController();

    await expect(auftragsbestand(steuerung.signal)).resolves.toEqual({
      zeilen: [],
      beauftragtInCent: 0,
      nochOffenInCent: 0,
    });
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/auftragsbestand',
      expect.objectContaining({ signal: steuerung.signal }),
    );
  });
});
