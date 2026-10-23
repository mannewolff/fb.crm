import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  arbeitszeitMonat,
  buchbarePositionen,
  parseArbeitsmonat,
  parseBuchungspositionen,
  parsePersonen,
  parseZeiteintrag,
  personen,
  zeiteintragAendern,
  zeiteintragAnlegen,
  zeiteintragLoeschen,
} from './arbeitszeit';
import { ApiError } from './client';
import { alsJson, fetchNachPfad, json, leer, problem } from '../test/fetchNachPfad';

/** Eine Position, wie `BuchungspositionResponse` sie schreibt. */
const POSITION = {
  id: 101,
  bezeichnung: 'Konzeption',
  angebotId: 11,
  angebotDatum: '2026-09-24',
  angebotTitel: null,
  intern: false,
  firmaName: 'IT Bildungshaus',
};

/** Eine Person, wie `PersonResponse` sie schreibt. */
const PERSON = { id: 1, name: 'Manfred Wolff', aktiv: true };

/** Ein Monat mit einem Tag und zwei Eintraegen, wie `ArbeitszeitMonatResponse` ihn schreibt. */
const MONAT = {
  monat: '2026-11',
  auswahl: '1',
  tage: [
    {
      tag: '2026-11-12',
      eintraege: [
        {
          id: 7,
          tag: '2026-11-12',
          von: '09:00:00',
          bis: '10:45:00',
          stunden: 1.75,
          position: POSITION,
          person: PERSON,
        },
        {
          id: 8,
          tag: '2026-11-12',
          von: '13:00:00',
          bis: '15:00:00',
          stunden: 2,
          position: POSITION,
          person: PERSON,
        },
      ],
      stunden: 3.75,
    },
  ],
  stunden: 3.75,
  stundenFuerKunden: 1.75,
  stundenIntern: 2,
};

/** Ein Eintrag, wie `ZeiteintragResponse` ihn schreibt. */
const EINTRAG = {
  id: 7,
  accountId: 1,
  angebotPositionId: 101,
  tag: '2026-11-12',
  von: '09:00:00',
  bis: '10:45:00',
  stunden: 1.75,
};

const EINGABE = {
  angebotPositionId: 101,
  tag: '2026-11-12',
  von: '09:00',
  bis: '10:45',
};

afterEach(() => {
  vi.restoreAllMocks();
});

describe('parseArbeitsmonat (A20)', () => {
  it('verengt Monat, Tage, Zeilen und Summen — Stunden als ganze Hundertstel (E5)', () => {
    const gelesen = parseArbeitsmonat(MONAT);

    expect(gelesen.monat).toBe('2026-11');
    expect(gelesen.stundenInHundertsteln).toBe(375);
    // Die Monatssumme ist aufgeteilt in den Teil fuer Kunden und den internen (Issue #230).
    expect(gelesen.stundenFuerKundenInHundertsteln).toBe(175);
    expect(gelesen.stundenInternInHundertsteln).toBe(200);
    expect(gelesen.tage).toHaveLength(1);
    expect(gelesen.tage[0].tag).toBe('2026-11-12');
    expect(gelesen.tage[0].stundenInHundertsteln).toBe(375);
    expect(gelesen.tage[0].eintraege[1].stundenInHundertsteln).toBe(200);
    expect(gelesen.tage[0].eintraege[0]).toEqual({
      id: 7,
      tag: '2026-11-12',
      von: '09:00:00',
      bis: '10:45:00',
      stundenInHundertsteln: 175,
      position: POSITION,
      person: PERSON,
    });
  });

  it('liest die Auswahl einer Person als Kennung und das Wort alle als „Alle" (E10)', () => {
    expect(parseArbeitsmonat(MONAT).auswahl).toBe(1);
    expect(parseArbeitsmonat({ ...MONAT, auswahl: 'alle' }).auswahl).toBe('alle');
  });

  it.each([[undefined], [1], ['jemand'], ['']])(
    'scheitert, wo die Auswahl %j weder eine Kennung noch alle ist',
    (auswahl) => {
      expect(() => parseArbeitsmonat({ ...MONAT, auswahl })).toThrow(TypeError);
    },
  );

  it('liest die Person einer Zeile samt stillgelegtem Zustand (E14, Kriterium 9)', () => {
    const stillgelegt = { id: 2, name: 'Ben Berger', aktiv: false };
    const monat = {
      ...MONAT,
      tage: [{ ...MONAT.tage[0], eintraege: [{ ...MONAT.tage[0].eintraege[0], person: stillgelegt }] }],
    };

    expect(parseArbeitsmonat(monat).tage[0].eintraege[0].person).toEqual(stillgelegt);
  });

  it.each([
    ['fehlt', undefined],
    ['traegt keinen Namen', { id: 1, aktiv: true }],
    ['nennt ihren Zustand als Text', { id: 1, name: 'Manfred Wolff', aktiv: 'ja' }],
  ])('scheitert, wo die Person einer Zeile %s', (_fall, person) => {
    const kaputt = {
      ...MONAT,
      tage: [{ ...MONAT.tage[0], eintraege: [{ ...MONAT.tage[0].eintraege[0], person }] }],
    };

    expect(() => parseArbeitsmonat(kaputt)).toThrow(TypeError);
  });

  it('scheitert, wo ein Feld fehlt — eine halb gelesene Antwort geht nicht weiter', () => {
    expect(() => parseArbeitsmonat({ monat: '2026-11', tage: [], stunden: null })).toThrow(
      TypeError,
    );
  });

  it.each([['stundenFuerKunden'], ['stundenIntern']])(
    'scheitert, wo die Teilsumme %s fehlt',
    (feld) => {
      expect(() => parseArbeitsmonat({ ...MONAT, [feld]: undefined })).toThrow(TypeError);
    },
  );

  it('scheitert, wo die Position einer Zeile nicht die erwartete Form hat', () => {
    const kaputt = {
      ...MONAT,
      tage: [{ ...MONAT.tage[0], eintraege: [{ ...MONAT.tage[0].eintraege[0], position: 101 }] }],
    };

    expect(() => parseArbeitsmonat(kaputt)).toThrow(TypeError);
  });
});

describe('parseBuchungspositionen (A15)', () => {
  it('verengt die Liste — die Antwort ist ein Feld, kein Objekt um eines herum', () => {
    expect(parseBuchungspositionen([POSITION])).toEqual([POSITION]);
  });

  it.each([[false], [true]])('liest das Kennzeichen intern=%s der Position', (intern) => {
    expect(parseBuchungspositionen([{ ...POSITION, intern }])[0].intern).toBe(intern);
  });

  it('scheitert, wo das Kennzeichen der Position kein Wahrheitswert ist', () => {
    expect(() => parseBuchungspositionen([{ ...POSITION, intern: 'ja' }])).toThrow(TypeError);
  });

  it('liest den Titel des Angebots, wo er steht, und null, wo er fehlt (Issue #328)', () => {
    expect(parseBuchungspositionen([{ ...POSITION, angebotTitel: 'Java-Grundkurs' }])[0].angebotTitel).toBe(
      'Java-Grundkurs',
    );
    expect(parseBuchungspositionen([POSITION])[0].angebotTitel).toBeNull();
  });

  it('scheitert, wo der Titel des Angebots kein Text ist', () => {
    expect(() => parseBuchungspositionen([{ ...POSITION, angebotTitel: 7 }])).toThrow(TypeError);
  });

  it('scheitert, wo die Antwort keine Liste ist', () => {
    expect(() => parseBuchungspositionen({ positionen: [] })).toThrow(TypeError);
  });
});

describe('parseZeiteintrag', () => {
  it('verengt den Eintrag samt gerechneter Dauer', () => {
    expect(parseZeiteintrag(EINTRAG)).toEqual({
      id: 7,
      accountId: 1,
      angebotPositionId: 101,
      tag: '2026-11-12',
      von: '09:00:00',
      bis: '10:45:00',
      stundenInHundertsteln: 175,
    });
  });

  it('scheitert, wo die Kennung keine Zahl ist', () => {
    expect(() => parseZeiteintrag({ ...EINTRAG, id: '7' })).toThrow(TypeError);
  });

  it('scheitert, wo das Konto des Eintrags fehlt (E12)', () => {
    expect(() => parseZeiteintrag({ ...EINTRAG, accountId: undefined })).toThrow(TypeError);
  });
});

describe('parsePersonen (E11)', () => {
  it('verengt die Liste mit Kennung, Name und Zustand', () => {
    const stillgelegt = { id: 2, name: 'Ben Berger', aktiv: false };

    expect(parsePersonen([PERSON, stillgelegt])).toEqual([PERSON, stillgelegt]);
  });

  it('scheitert, wo die Kennung einer Person keine Zahl ist', () => {
    expect(() => parsePersonen([{ ...PERSON, id: '1' }])).toThrow(TypeError);
  });

  it('scheitert, wo die Antwort keine Liste ist', () => {
    expect(() => parsePersonen({ personen: [PERSON] })).toThrow(TypeError);
  });
});

describe('arbeitszeitMonat', () => {
  it('fragt mit dem Monat als Parameter', async () => {
    const aufruf = fetchNachPfad({ 'GET /api/arbeitszeit?monat=2026-11': json(200, MONAT) });

    await expect(arbeitszeitMonat('2026-11')).resolves.toMatchObject({ monat: '2026-11' });
    expect(aufruf).toHaveBeenCalledTimes(1);
  });

  it('fragt ohne Parameter, wo kein Monat genannt ist — dann gilt der laufende (E4)', async () => {
    fetchNachPfad({ 'GET /api/arbeitszeit': json(200, MONAT) });

    await expect(arbeitszeitMonat()).resolves.toMatchObject({ stundenInHundertsteln: 375 });
  });

  it('haengt die Auswahl einer Person als Parameter person an die Adresse (E10)', async () => {
    const aufruf = fetchNachPfad({ 'GET /api/arbeitszeit?person=2': json(200, MONAT) });

    await arbeitszeitMonat(undefined, 2);
    expect(aufruf).toHaveBeenCalledTimes(1);
  });

  it('haengt die Auswahl „Alle" als person=alle an die Adresse', async () => {
    const aufruf = fetchNachPfad({
      'GET /api/arbeitszeit?monat=2026-11&person=alle': json(200, { ...MONAT, auswahl: 'alle' }),
    });

    await expect(arbeitszeitMonat('2026-11', 'alle')).resolves.toMatchObject({ auswahl: 'alle' });
    expect(aufruf).toHaveBeenCalledTimes(1);
  });

  it('laesst den Parameter person ohne Auswahl weg — die Vorauswahl entscheidet der Server', async () => {
    // Das Doppel kennt nur diese eine Adresse; ein angehaengtes `person` scheiterte laut.
    const aufruf = fetchNachPfad({ 'GET /api/arbeitszeit?monat=2026-11': json(200, MONAT) });

    await arbeitszeitMonat('2026-11', undefined);
    expect(aufruf).toHaveBeenCalledTimes(1);
  });
});

describe('personen', () => {
  it('liest die waehlbaren Personen (E11)', async () => {
    fetchNachPfad({ 'GET /api/arbeitszeit/personen': json(200, [PERSON]) });

    await expect(personen()).resolves.toEqual([PERSON]);
  });
});

describe('buchbarePositionen', () => {
  it('liest die Auswahlliste des Dialogs', async () => {
    fetchNachPfad({ 'GET /api/arbeitszeit/buchbare-positionen': json(200, [POSITION]) });

    await expect(buchbarePositionen()).resolves.toEqual([POSITION]);
  });
});

describe('zeiteintragAnlegen', () => {
  it('schickt die vier Angaben als JSON und liest den angelegten Eintrag', async () => {
    let hinaus: unknown = null;
    fetchNachPfad({
      'POST /api/arbeitszeit': (rumpf) => {
        hinaus = alsJson(rumpf);
        return json(201, EINTRAG)();
      },
    });

    await expect(zeiteintragAnlegen(EINGABE)).resolves.toMatchObject({ id: 7 });
    expect(hinaus).toEqual(EINGABE);
    // Ohne Konto geht das Feld nicht hinaus: dann gehoert der Eintrag dem angemeldeten (E12).
    expect(hinaus).not.toHaveProperty('accountId');
  });

  it('schickt das genannte Konto als accountId mit (E12)', async () => {
    let hinaus: unknown = null;
    fetchNachPfad({
      'POST /api/arbeitszeit': (rumpf) => {
        hinaus = alsJson(rumpf);
        return json(201, { ...EINTRAG, accountId: 2 })();
      },
    });

    await expect(zeiteintragAnlegen({ ...EINGABE, accountId: 2 })).resolves.toMatchObject({
      accountId: 2,
    });
    expect(hinaus).toEqual({ ...EINGABE, accountId: 2 });
  });

  it('reicht die Feldmeldung des Servers als ApiError heraus (A19)', async () => {
    fetchNachPfad({
      'POST /api/arbeitszeit': problem(422, 'Überschneidet sich mit 9:00 bis 11:00.', {
        von: ['Überschneidet sich mit 9:00 bis 11:00.'],
      }),
    });

    await expect(zeiteintragAnlegen(EINGABE)).rejects.toBeInstanceOf(ApiError);
  });
});

describe('zeiteintragAendern', () => {
  it('schickt denselben Rumpf an den Weg des Eintrags', async () => {
    let hinaus: unknown = null;
    fetchNachPfad({
      'PUT /api/arbeitszeit/7': (rumpf) => {
        hinaus = alsJson(rumpf);
        return json(200, EINTRAG)();
      },
    });

    await expect(zeiteintragAendern(7, EINGABE)).resolves.toMatchObject({ id: 7 });
    expect(hinaus).toEqual(EINGABE);
  });
});

describe('zeiteintragLoeschen', () => {
  it('loescht und erwartet keine Antwort mit Inhalt', async () => {
    const aufruf = fetchNachPfad({ 'DELETE /api/arbeitszeit/7': leer(204) });

    await expect(zeiteintragLoeschen(7)).resolves.toBeUndefined();
    expect(aufruf).toHaveBeenCalledTimes(1);
  });
});
