import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  briefbogenLesen,
  briefbogenPflegen,
  briefpapierEntfernen,
  briefpapierHochladen,
  parseBriefbogen,
  probedruckHolen,
} from './briefbogen';
import type { BriefbogenEingabe } from './briefbogen';
import { ApiError } from './client';
import { FORMFEHLER } from './verengen';
import { blobText } from '../test/blobText';
import { alsJson, fetchNachPfad, formularWeg, json, leer, problem } from '../test/fetchNachPfad';

/** Der Stand mit hinterlegtem Briefpapier, wie `BriefbogenResponse` ihn schreibt. */
const MIT_PAPIER = {
  briefpapier: true,
  dateiname: 'Briefbogen.pdf',
  groesse: 48213,
  seiten: 2,
  absenderzeile: true,
  steuerBankZeile: true,
  links: 250,
  rechts: 200,
  inhaltOben: 985,
  unten: 200,
  folgeOben: 200,
  folgeUnten: 200,
  version: 3,
};

/** Der Stand einer frischen Instanz: die Vorgaben der Migration, kein Briefpapier. */
const OHNE_PAPIER = {
  ...MIT_PAPIER,
  briefpapier: false,
  dateiname: null,
  groesse: null,
  seiten: null,
};

const EINGABE: BriefbogenEingabe = {
  absenderzeile: false,
  steuerBankZeile: true,
  links: 250,
  rechts: 200,
  inhaltOben: 985,
  unten: 200,
  folgeOben: 200,
  folgeUnten: 200,
};

afterEach(() => {
  vi.restoreAllMocks();
});

/** Schalter und Raender, wie sie in jedem verengten Stand stehen. */
const EINSTELLUNGEN = {
  absenderzeile: true,
  steuerBankZeile: true,
  links: 250,
  rechts: 200,
  inhaltOben: 985,
  unten: 200,
  folgeOben: 200,
  folgeUnten: 200,
};

const MIT_PAPIER_VERENGT = {
  papier: { dateiname: 'Briefbogen.pdf', groesse: 48213, seiten: 2 },
  ...EINSTELLUNGEN,
  version: 3,
};

const OHNE_PAPIER_VERENGT = { papier: null, ...EINSTELLUNGEN, version: 3 };

/** Der Bedingungskopf, mit dem ein geschuetzter Weg den Stand 3 vorlegt (Plan #415, E2). */
const VORGELEGT = { 'If-Match': '"3"' };

describe('parseBriefbogen', () => {
  it('verengt den Stand mit Briefpapier; Name, Groesse und Seiten stehen beisammen', () => {
    expect(parseBriefbogen(MIT_PAPIER)).toEqual(MIT_PAPIER_VERENGT);
  });

  it('verengt den Stand ohne Briefpapier zu papier: null', () => {
    expect(parseBriefbogen(OHNE_PAPIER)).toEqual(OHNE_PAPIER_VERENGT);
  });

  it.each([
    ['kein Objekt', 'Briefbogen'],
    ['ein Rand als Text', { ...MIT_PAPIER, links: '250' }],
    ['ein Schalter als Text', { ...MIT_PAPIER, absenderzeile: 'ja' }],
    ['die Groesse als Text', { ...MIT_PAPIER, groesse: '48213' }],
    ['ein fehlender Rand', { ...MIT_PAPIER, folgeUnten: undefined }],
    ['ein Briefpapier ohne Namen', { ...MIT_PAPIER, dateiname: null }],
    ['ein Briefpapier ohne Seitenzahl', { ...MIT_PAPIER, seiten: null }],
    ['ohne version', { ...MIT_PAPIER, version: undefined }],
  ])('scheitert an einer unerwarteten Antwort: %s', (_fall, wert) => {
    expect(() => parseBriefbogen(wert)).toThrow(FORMFEHLER);
  });
});

describe('briefbogenLesen', () => {
  it('liest den Stand ueber GET', async () => {
    const fetchMock = fetchNachPfad({ 'GET /api/briefpapier': json(200, MIT_PAPIER) });

    await expect(briefbogenLesen()).resolves.toEqual(MIT_PAPIER_VERENGT);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('scheitert an einer Antwort der falschen Form', async () => {
    fetchNachPfad({ 'GET /api/briefpapier': json(200, { briefpapier: 'ja' }) });

    await expect(briefbogenLesen()).rejects.toThrow(TypeError);
  });
});

describe('briefbogenPflegen', () => {
  it('schickt Schalter und Raender als JSON per PUT und liest den neuen Stand', async () => {
    let gesendet: unknown = null;
    const fetchMock = fetchNachPfad({
      'PUT /api/briefpapier': (rumpf) => {
        gesendet = alsJson(rumpf);
        return json(200, { ...MIT_PAPIER, absenderzeile: false, version: 4 })();
      },
    });

    const neu = await briefbogenPflegen(3, EINGABE);

    expect(gesendet).toEqual(EINGABE);
    expect(neu.absenderzeile).toBe(false);
    expect(neu.version).toBe(4);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/briefpapier',
      expect.objectContaining({ headers: expect.objectContaining(VORGELEGT) as unknown }),
    );
  });

  it('reicht die Feldfehler einer 422-Antwort als ApiError weiter', async () => {
    fetchNachPfad({
      'PUT /api/briefpapier': problem(422, 'Ungültige Eingabe', {
        inhaltOben: ['Bei den übrigen eingestellten Rändern darf dieser Rand höchstens 120,0 mm betragen.'],
      }),
    });

    const fehler: unknown = await briefbogenPflegen(3, EINGABE).catch((ursache: unknown) => ursache);

    expect(fehler).toBeInstanceOf(ApiError);
    expect(fehler).toMatchObject({
      status: 422,
      fieldErrors: {
        inhaltOben: [
          'Bei den übrigen eingestellten Rändern darf dieser Rand höchstens 120,0 mm betragen.',
        ],
      },
    });
  });
});

describe('briefpapierHochladen', () => {
  it('schickt die Datei im Teil „datei" per POST und liest den neuen Stand', async () => {
    const formulare: FormData[] = [];
    const fetchMock = fetchNachPfad({
      'POST /api/briefpapier/datei': formularWeg(
        (gesendet) => {
          formulare.push(gesendet);
        },
        json(200, MIT_PAPIER),
      ),
    });
    const datei = new File(['%PDF'], 'Briefbogen.pdf', { type: 'application/pdf' });

    await expect(briefpapierHochladen(3, datei)).resolves.toEqual(MIT_PAPIER_VERENGT);
    expect(formulare).toHaveLength(1);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/briefpapier/datei',
      expect.objectContaining({ headers: expect.objectContaining(VORGELEGT) as unknown }),
    );
    const teil = formulare[0].get('datei');
    expect(teil).toBeInstanceOf(File);
    expect(teil).toHaveProperty('name', 'Briefbogen.pdf');
  });

  it('reicht die Feldmeldung zu „datei" aus einer 422-Antwort weiter', async () => {
    fetchNachPfad({
      'POST /api/briefpapier/datei': problem(422, 'Ungültige Eingabe', {
        datei: ['Das Briefpapier muss A4 hochkant sein.'],
      }),
    });

    await expect(briefpapierHochladen(3, new File(['x'], 'a.pdf'))).rejects.toMatchObject({
      status: 422,
      fieldErrors: { datei: ['Das Briefpapier muss A4 hochkant sein.'] },
    });
  });
});

describe('briefpapierEntfernen', () => {
  it('entfernt das Briefpapier per DELETE und liest den Stand ohne Papier', async () => {
    const fetchMock = fetchNachPfad({ 'DELETE /api/briefpapier/datei': json(200, OHNE_PAPIER) });

    await expect(briefpapierEntfernen(3)).resolves.toEqual(OHNE_PAPIER_VERENGT);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/briefpapier/datei',
      expect.objectContaining({
        method: 'DELETE',
        headers: expect.objectContaining(VORGELEGT) as unknown,
      }),
    );
  });
});

describe('probedruckHolen', () => {
  it('holt den Probedruck als Blob', async () => {
    const fetchMock = fetchNachPfad({
      'GET /api/briefpapier/probedruck': () => new Response('%PDF-probe', { status: 200 }),
    });

    const blob = await probedruckHolen();

    await expect(blobText(blob)).resolves.toBe('%PDF-probe');
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: 'GET' });
  });

  it('scheitert ohne Briefpapier mit 404', async () => {
    fetchNachPfad({ 'GET /api/briefpapier/probedruck': leer(404) });

    await expect(probedruckHolen()).rejects.toMatchObject({ status: 404 });
  });
});
