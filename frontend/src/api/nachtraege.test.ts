import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  nachtragAendern,
  nachtragAnlegen,
  nachtragDokumentAblegen,
  nachtragDokumentEntfernen,
  nachtragDokumentPfad,
  nachtragLesen,
  nachtragLoeschen,
  parseNachtrag,
  setzeNachtragszustand,
} from './nachtraege';
import type { NachtragEingabe } from './nachtraege';
import { FORMFEHLER } from './verengen';
import { fetchNachPfad, formularWeg, json, leer } from '../test/fetchNachPfad';

/** Eine nachgetragene Rechnung, wie Jackson sie schreibt: Geld als Zahl mit zwei Stellen. */
const NACHTRAG = {
  id: 7,
  firmaId: 5,
  firmaName: 'Adler AG',
  nummer: 'RE-2026-014',
  rechnungDatum: '2026-03-12',
  netto: 1000.5,
  brutto: 1190.6,
  zustand: 'GESTELLT',
  dokument: true,
};

/** Dieselbe Rechnung nach dem Verengen: Geld in ganzen Cent. */
const NACHTRAG_VERENGT = {
  id: 7,
  firmaId: 5,
  firmaName: 'Adler AG',
  nummer: 'RE-2026-014',
  rechnungDatum: '2026-03-12',
  nettoInCent: 100050,
  bruttoInCent: 119060,
  zustand: 'GESTELLT',
  dokument: true,
};

/** Die Angaben der Maske — die Betraege als Dezimaltext mit Punkt. */
const EINGABE: NachtragEingabe = {
  firmaId: 5,
  nummer: 'RE-2026-014',
  rechnungDatum: '2026-03-12',
  netto: '1000.50',
  brutto: '1190.60',
};

afterEach(() => {
  vi.restoreAllMocks();
});

describe('parseNachtrag', () => {
  it('verengt eine nachgetragene Rechnung; Geld wird zu ganzen Cent', () => {
    expect(parseNachtrag(NACHTRAG)).toEqual(NACHTRAG_VERENGT);
  });

  it('verengt eine Rechnung ohne hinterlegtes Original', () => {
    expect(parseNachtrag({ ...NACHTRAG, dokument: false }).dokument).toBe(false);
  });

  it('weist einen unbekannten Zustand ab', () => {
    expect(() => parseNachtrag({ ...NACHTRAG, zustand: 'OFFEN' })).toThrow(FORMFEHLER);
  });

  it('weist einen Betrag mit drei Nachkommastellen ab', () => {
    expect(() => parseNachtrag({ ...NACHTRAG, brutto: 1.005 })).toThrow(FORMFEHLER);
  });

  it('weist ein fehlendes Kennzeichen des Originals ab', () => {
    expect(() => parseNachtrag({ ...NACHTRAG, dokument: 'ja' })).toThrow(FORMFEHLER);
  });
});

describe('nachtragDokumentPfad', () => {
  it('nennt den Weg des hinterlegten Originals, ohne es abzurufen', () => {
    expect(nachtragDokumentPfad(7)).toBe('/api/nachgetragene-rechnungen/7/dokument');
  });
});

describe('die Wege', () => {
  it('traegt eine Rechnung nach; die Betraege gehen als Dezimaltext hinaus', async () => {
    const fetchMock = fetchNachPfad({
      'POST /api/nachgetragene-rechnungen': json(201, NACHTRAG),
    });

    expect(await nachtragAnlegen(EINGABE)).toEqual(NACHTRAG_VERENGT);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/nachgetragene-rechnungen',
      expect.objectContaining({ method: 'POST', body: JSON.stringify(EINGABE) }),
    );
  });

  it('liest eine nachgetragene Rechnung', async () => {
    const fetchMock = fetchNachPfad({
      'GET /api/nachgetragene-rechnungen/7': json(200, NACHTRAG),
    });

    expect(await nachtragLesen(7)).toEqual(NACHTRAG_VERENGT);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/nachgetragene-rechnungen/7',
      expect.objectContaining({ method: 'GET' }),
    );
  });

  it('aendert die Eckdaten als Ganzes', async () => {
    const fetchMock = fetchNachPfad({
      'PUT /api/nachgetragene-rechnungen/7': json(200, NACHTRAG),
    });

    expect(await nachtragAendern(7, EINGABE)).toEqual(NACHTRAG_VERENGT);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/nachgetragene-rechnungen/7',
      expect.objectContaining({ method: 'PUT', body: JSON.stringify(EINGABE) }),
    );
  });

  it('loescht die nachgetragene Rechnung', async () => {
    const fetchMock = fetchNachPfad({ 'DELETE /api/nachgetragene-rechnungen/7': leer(204) });

    await nachtragLoeschen(7);

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/nachgetragene-rechnungen/7',
      expect.objectContaining({ method: 'DELETE' }),
    );
  });

  it('stellt den Zustand um; das Ziel geht im Rumpf hinaus', async () => {
    const fetchMock = fetchNachPfad({
      'PUT /api/nachgetragene-rechnungen/7/zustand': json(200, {
        ...NACHTRAG,
        zustand: 'BEZAHLT',
      }),
    });

    expect((await setzeNachtragszustand(7, 'BEZAHLT')).zustand).toBe('BEZAHLT');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/nachgetragene-rechnungen/7/zustand',
      expect.objectContaining({ method: 'PUT', body: JSON.stringify({ zustand: 'BEZAHLT' }) }),
    );
  });

  it('legt das Original im Teil „datei" ab und gibt den neuen Stand heraus', async () => {
    const gesendet: FormData[] = [];
    fetchNachPfad({
      'POST /api/nachgetragene-rechnungen/7/dokument': formularWeg((formular) => {
        gesendet.push(formular);
      }, json(200, NACHTRAG)),
    });
    const datei = new File(['%PDF-1.7'], 'rechnung.pdf', { type: 'application/pdf' });

    await expect(nachtragDokumentAblegen(7, datei)).resolves.toEqual(NACHTRAG_VERENGT);
    expect(gesendet).toHaveLength(1);
    expect([...gesendet[0].keys()]).toEqual(['datei']);
    expect(gesendet[0].get('datei')).toBe(datei);
  });

  it('entfernt das Original', async () => {
    const fetchMock = fetchNachPfad({
      'DELETE /api/nachgetragene-rechnungen/7/dokument': leer(204),
    });

    await nachtragDokumentEntfernen(7);

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/nachgetragene-rechnungen/7/dokument',
      expect.objectContaining({ method: 'DELETE' }),
    );
  });
});
