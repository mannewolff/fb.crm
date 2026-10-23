import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ZeiteintragMaske from './ZeiteintragMaske';
import { ALLE } from '../api/arbeitszeit';
import type { Personenauswahl, Zeitzeile } from '../api/arbeitszeit';
import { heute } from '../lib/tag';
import { alsJson, fetchNachPfad, json, problem } from '../test/fetchNachPfad';
import type { Routen } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';
import { alsRolle } from '../test/sitzungDoppel';

vi.mock('../auth/AuthContext', () => import('../test/sitzungDoppel'));

/**
 * Der Dialog zum Erfassen und Aendern einer Arbeitszeit (Issue #202; Plan #194, A14 bis A16, A19).
 *
 * Der Dialog holt seine Auswahllisten selbst — jede Probe legt darum `buchbare-positionen` mit fest,
 * und {@link antworten} legt fuer ein C-Level die Personen dazu (Plan #426, E11). Die Sitzung kommt
 * aus `sitzungDoppel`: angemeldet ist das Konto 1, ohne {@link alsRolle} als C-Level (E17).
 * Der heutige Tag kommt aus {@link heute}; dass die Funktion den richtigen Tag setzt, prueft
 * `lib/tag.test.ts` gegen eine feste Uhr. Hier geht es nur darum, dass der Dialog ihn vorbelegt.
 */

const POSITIONEN = 'GET /api/arbeitszeit/buchbare-positionen';
const ANLEGEN = 'POST /api/arbeitszeit';
const AENDERN = 'PUT /api/arbeitszeit/7';
const PERSONEN = 'GET /api/arbeitszeit/personen';

/** Der Tag, den der Dialog beim Erfassen vorbelegt (lokale Zeit, E4). */
const HEUTE = heute();

const KONZEPTION = {
  id: 101,
  bezeichnung: 'Konzeption',
  angebotId: 11,
  angebotDatum: '2026-09-24',
  angebotTitel: null,
  intern: false,
  firmaName: 'IT Bildungshaus',
};

const SCHULUNG = {
  id: 102,
  bezeichnung: 'Schulung',
  angebotId: 11,
  angebotDatum: '2026-09-24',
  angebotTitel: null,
  intern: false,
  firmaName: 'IT Bildungshaus',
};

const BETREUUNG = {
  id: 201,
  bezeichnung: 'Betreuung',
  angebotId: 21,
  angebotDatum: '2026-09-28',
  angebotTitel: null,
  intern: false,
  firmaName: 'Adler AG',
};

/** Eine Position, die in der Auswahlliste nicht mehr steht (A15). */
const ALTLAST = {
  id: 999,
  bezeichnung: 'Altlast',
  angebotId: 31,
  angebotDatum: '2026-05-04',
  angebotTitel: null,
  intern: false,
  firmaName: 'Vergangen GmbH',
};

/** Eine Position an einem internen Angebot (Issue #236, Kriterium 2). */
const EIGENE_WEBSEITE = {
  id: 301,
  bezeichnung: 'Eigene Webseite',
  angebotId: 41,
  angebotDatum: '2026-09-30',
  angebotTitel: null,
  intern: true,
  firmaName: 'Manfred Wolff',
};

/** Die Person der Zeile, wie `PersonResponse` sie schreibt (Plan #426, E14). */
const PERSON = { id: 1, name: 'Manfred Wolff', aktiv: true };

/** Eine zweite aktive Person — die, fuer die ein C-Level nachtraegt (Kriterium 3 von #410). */
const BEN = { id: 2, name: 'Ben Krause', aktiv: true };

/** Eine stillgelegte Person (Kriterium 9 von #410). */
const CLARA = { id: 3, name: 'Clara Stein', aktiv: false };

/** Die Zeile der Monatsliste, die geaendert wird. */
const ZEILE: Zeitzeile = {
  id: 7,
  tag: '2026-11-10',
  von: '09:00:00',
  bis: '10:45:00',
  stundenInHundertsteln: 175,
  position: KONZEPTION,
  person: PERSON,
};

/** Dieselbe Zeile, aber auf einer Position, die nicht mehr buchbar ist. */
const ZEILE_ALTLAST: Zeitzeile = { ...ZEILE, position: ALTLAST };

const ANGELEGT = {
  id: 12,
  accountId: 1,
  angebotPositionId: 101,
  tag: HEUTE,
  von: '09:00:00',
  bis: '10:45:00',
  stunden: 1.75,
};

type Nutzer = ReturnType<typeof userEvent.setup>;

/** Das fetch-Doppel mit den Personen, wie der Server sie einem C-Level nennt (E11). */
function antworten(routen: Routen) {
  return fetchNachPfad({ [PERSONEN]: json(200, [PERSON, BEN, CLARA]), ...routen });
}

/*
 * Die vier Felder sind Pflicht, und MUI setzt hinter ihre Beschriftung ein Sternchen. Darum der
 * Anker im Ausdruck statt des genauen Textes — und bei der Auswahl die Rolle, deren zugaenglicher
 * Name das Sternchen ohnehin nicht nennt (es traegt `aria-hidden`). Datums- und Zeitfelder haben
 * keine eigene Rolle; sie sind nur ueber ihre Beschriftung zu fassen.
 */
function tagFeld() {
  return screen.getByLabelText(/^Tag/);
}

function vonFeld() {
  return screen.getByLabelText(/^von/);
}

function bisFeld() {
  return screen.getByLabelText(/^bis/);
}

function personenFeld() {
  return screen.getByRole('combobox', { name: 'Person' });
}

function positionsFeld() {
  return screen.getByRole('combobox', { name: 'Position' });
}

function speicherTaste() {
  return screen.getByRole('button', { name: 'Speichern' });
}

/** Die Gruppen der Auswahlliste in ihrer Reihenfolge. */
function gruppen() {
  return within(positionsFeld()).getAllByRole('group');
}

/** Eine Antwort, die erst auf Abruf eintrifft — so laesst sich „waehrend des Speicherns" pruefen. */
function angehalten(antwort: () => Response) {
  let liefere!: (wert: Response) => void;
  const spaeter = new Promise<Response>((aufloesen) => {
    liefere = aufloesen;
  });
  return {
    weg: () => spaeter,
    loesen: () => {
      liefere(antwort());
    },
  };
}

function renderMaske(zeile: Zeitzeile | null = null, auswahl: Personenauswahl | null = null) {
  const gespeichert = vi.fn();
  const geschlossen = vi.fn();
  const view = renderMitTheme(
    <ZeiteintragMaske
      zeile={zeile}
      auswahl={auswahl}
      onGespeichert={gespeichert}
      onSchliessen={geschlossen}
    />,
  );
  return { ...view, gespeichert, geschlossen };
}

/** Fuellt die drei leeren Angaben einer neuen Buchung; der Tag steht schon. */
async function fuelle(nutzer: Nutzer) {
  await nutzer.type(await screen.findByLabelText(/^von/), '09:00');
  await nutzer.type(bisFeld(), '10:45');
  await nutzer.selectOptions(positionsFeld(), '101');
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('ZeiteintragMaske — je Rolle (Kriterium 10 von #409, Plan #414, A10)', () => {
  it.each([null, ZEILE])('oeffnet sich fuer ReadOnly nicht und fragt nichts ab (Zeile %#)', (zeile) => {
    alsRolle('READ_ONLY');
    const aufruf = antworten({ [POSITIONEN]: json(200, [KONZEPTION]) });

    renderMaske(zeile);

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Speichern' })).not.toBeInTheDocument();
    expect(aufruf).not.toHaveBeenCalled();
  });

  it('oeffnet sich fuer einen Employee, denn er darf fachlich schreiben', async () => {
    alsRolle('EMPLOYEE');
    antworten({ [POSITIONEN]: json(200, [KONZEPTION]) });

    renderMaske();

    expect(await screen.findByRole('dialog', { name: 'Zeit erfassen' })).toBeInTheDocument();
    expect(speicherTaste()).toBeInTheDocument();
  });
});

describe('ZeiteintragMaske — Erfassen (Kriterium 1, A16)', () => {
  it('nennt sich „Zeit erfassen", belegt den Tag mit heute und laesst Zeiten und Position leer', async () => {
    antworten({ [POSITIONEN]: json(200, [KONZEPTION]) });

    renderMaske();

    expect(await screen.findByRole('heading', { name: 'Zeit erfassen' })).toBeInTheDocument();
    expect(tagFeld()).toHaveValue(HEUTE);
    expect(vonFeld()).toHaveValue('');
    expect(bisFeld()).toHaveValue('');
    expect(positionsFeld()).toHaveValue('');
  });

  it('erfasst in Schritten einer Viertelstunde (A16)', async () => {
    antworten({ [POSITIONEN]: json(200, [KONZEPTION]) });

    renderMaske();

    expect(await screen.findByLabelText(/^von/)).toHaveAttribute('step', '900');
    expect(bisFeld()).toHaveAttribute('step', '900');
  });

  it('gruppiert die Auswahl nach Firma und Angebot (A15)', async () => {
    antworten({ [POSITIONEN]: json(200, [BETREUUNG, KONZEPTION, SCHULUNG]) });

    renderMaske();
    await screen.findByRole('combobox', { name: 'Position' });

    // Die Reihenfolge ist die der Antwort — sortiert wird am Server (BuchbarePositionenUseCase).
    expect(gruppen()).toHaveLength(2);
    expect(gruppen()[0]).toHaveAttribute('label', 'Adler AG — Angebot vom 28.09.2026');
    expect(gruppen()[1]).toHaveAttribute('label', 'IT Bildungshaus — Angebot vom 24.09.2026');
    expect(within(gruppen()[1]).getByRole('option', { name: 'Konzeption' })).toBeInTheDocument();
    expect(within(gruppen()[1]).getByRole('option', { name: 'Schulung' })).toBeInTheDocument();
  });

  it('schickt Tag, Uhrzeiten und Position und meldet den Erfolg nach oben', async () => {
    const nutzer = userEvent.setup();
    let rumpf: unknown;
    antworten({
      [POSITIONEN]: json(200, [KONZEPTION]),
      [ANLEGEN]: (gesendet) => {
        rumpf = alsJson(gesendet);
        return json(201, ANGELEGT)();
      },
    });

    const { gespeichert } = renderMaske();
    await fuelle(nutzer);
    await nutzer.click(speicherTaste());

    expect(gespeichert).toHaveBeenCalledTimes(1);
    expect(rumpf).toEqual({
      angebotPositionId: 101,
      tag: HEUTE,
      von: '09:00',
      bis: '10:45',
      accountId: 1,
    });
  });

  it('nimmt einen anderen Tag an und schickt ihn', async () => {
    const nutzer = userEvent.setup();
    let rumpf: unknown;
    antworten({
      [POSITIONEN]: json(200, [KONZEPTION]),
      [ANLEGEN]: (gesendet) => {
        rumpf = alsJson(gesendet);
        return json(201, ANGELEGT)();
      },
    });

    renderMaske();
    await nutzer.clear(await screen.findByLabelText(/^Tag/));
    await nutzer.type(tagFeld(), '2026-11-20');
    await fuelle(nutzer);
    await nutzer.click(speicherTaste());

    expect(rumpf).toEqual({
      angebotPositionId: 101,
      tag: '2026-11-20',
      von: '09:00',
      bis: '10:45',
      accountId: 1,
    });
  });

  it('sperrt Speichern und Abbrechen, solange die Anfrage laeuft', async () => {
    const nutzer = userEvent.setup();
    const spaet = angehalten(json(201, ANGELEGT));
    antworten({ [POSITIONEN]: json(200, [KONZEPTION]), [ANLEGEN]: spaet.weg });

    const { gespeichert } = renderMaske();
    await fuelle(nutzer);
    await nutzer.click(speicherTaste());

    expect(speicherTaste()).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Abbrechen' })).toBeDisabled();
    spaet.loesen();
    await vi.waitFor(() => {
      expect(gespeichert).toHaveBeenCalledTimes(1);
    });
  });

  it('nennt die fehlenden Angaben am Feld, statt eine halbe Buchung abzuschicken', async () => {
    const nutzer = userEvent.setup();
    const aufruf = antworten({ [POSITIONEN]: json(200, [KONZEPTION]) });

    renderMaske();
    await screen.findByRole('combobox', { name: 'Position' });
    await nutzer.click(speicherTaste());

    // Drei Meldungen: Der Tag ist vorbelegt, die drei anderen Angaben fehlen.
    expect(screen.getAllByText('Bitte ausfüllen.')).toHaveLength(3);
    // Die Lesewege der beiden Auswahllisten, und sonst nichts: Die Buchung ging nicht hinaus.
    expect(aufruf).toHaveBeenCalledTimes(2);
  });

  it('sagt es, wenn die Auswahlliste nicht zu erreichen ist', async () => {
    antworten({ [POSITIONEN]: problem(500, 'Kaputt.') });

    renderMaske();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Die buchbaren Positionen sind gerade nicht zu erreichen. Bitte später erneut versuchen.',
    );
  });

  it('sagt es, wenn keine Position buchbar ist', async () => {
    antworten({ [POSITIONEN]: json(200, []) });

    renderMaske();

    expect(await screen.findByRole('status')).toHaveTextContent('Keine Position ist buchbar.');
  });

  it('gibt „Abbrechen" nach oben weiter', async () => {
    const nutzer = userEvent.setup();
    antworten({ [POSITIONEN]: json(200, [KONZEPTION]) });

    const { geschlossen } = renderMaske();
    await nutzer.click(await screen.findByRole('button', { name: 'Abbrechen' }));

    expect(geschlossen).toHaveBeenCalledTimes(1);
  });
});

describe('ZeiteintragMaske — die Dauer (Kriterium 2)', () => {
  it('zeigt die Dauer erst, wenn beide Uhrzeiten gueltig sind', async () => {
    const nutzer = userEvent.setup();
    antworten({ [POSITIONEN]: json(200, [KONZEPTION]) });

    renderMaske();
    expect(screen.queryByTestId('zeit-dauer')).not.toBeInTheDocument();
    await nutzer.type(await screen.findByLabelText(/^von/), '09:00');
    // Nur der Beginn steht — daraus wird keine Dauer.
    expect(screen.queryByTestId('zeit-dauer')).not.toBeInTheDocument();
    await nutzer.type(bisFeld(), '10:45');

    expect(screen.getByTestId('zeit-dauer')).toHaveTextContent('1,75 Std.');
  });

  it('zeigt keine Dauer, wo das Ende nicht nach dem Beginn liegt', async () => {
    const nutzer = userEvent.setup();
    antworten({ [POSITIONEN]: json(200, [KONZEPTION]) });

    renderMaske();
    await nutzer.type(await screen.findByLabelText(/^von/), '11:00');
    await nutzer.type(bisFeld(), '09:00');

    expect(screen.queryByTestId('zeit-dauer')).not.toBeInTheDocument();
  });
});

describe('ZeiteintragMaske — Meldungen des Servers (A19, A8)', () => {
  it('haengt die Meldung zu 422 an das genannte Feld', async () => {
    const nutzer = userEvent.setup();
    const meldung = 'Arbeitszeit wird in Schritten einer Viertelstunde erfasst.';
    antworten({
      [POSITIONEN]: json(200, [KONZEPTION]),
      [ANLEGEN]: problem(422, meldung, { von: [meldung] }),
    });

    const { gespeichert } = renderMaske();
    await fuelle(nutzer);
    await nutzer.click(speicherTaste());

    expect(vonFeld()).toHaveAccessibleDescription(meldung);
    expect(gespeichert).not.toHaveBeenCalled();
    // Nach dem Fehlschlag darf wieder gespeichert werden.
    expect(speicherTaste()).toBeEnabled();
  });

  it('nennt die Ueberschneidung mit dem Text des Servers an beiden Zeitfeldern', async () => {
    const nutzer = userEvent.setup();
    const meldung = 'Überschneidet sich mit 9:00 bis 11:00, Schulung (Adler AG).';
    antworten({
      [POSITIONEN]: json(200, [KONZEPTION]),
      [ANLEGEN]: problem(422, meldung, { von: [meldung], bis: [meldung] }),
    });

    renderMaske();
    await fuelle(nutzer);
    await nutzer.click(speicherTaste());

    expect(vonFeld()).toHaveAccessibleDescription(meldung);
    expect(bisFeld()).toHaveAccessibleDescription(meldung);
  });

  it('haengt die Meldung zur Position an die Auswahl', async () => {
    const nutzer = userEvent.setup();
    const meldung = 'Auf diese Position kann keine Arbeitszeit gebucht werden.';
    antworten({
      [POSITIONEN]: json(200, [KONZEPTION]),
      [ANLEGEN]: problem(422, meldung, { angebotPositionId: [meldung] }),
    });

    renderMaske();
    await fuelle(nutzer);
    await nutzer.click(speicherTaste());

    expect(positionsFeld()).toHaveAccessibleDescription(meldung);
  });

  it('meldet den Ausfall ohne Feldmeldung als Satz im Dialog', async () => {
    const nutzer = userEvent.setup();
    antworten({
      [POSITIONEN]: json(200, [KONZEPTION]),
      [ANLEGEN]: problem(503, 'Kaputt.'),
    });

    renderMaske();
    await fuelle(nutzer);
    await nutzer.click(speicherTaste());

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Die Arbeitszeit wurde nicht gespeichert. Bitte später erneut versuchen.',
    );
  });
});

describe('ZeiteintragMaske — Aendern (Kriterium 6, A15)', () => {
  it('nennt sich „Arbeitszeit ändern" und belegt alle vier Angaben aus der Zeile', async () => {
    antworten({ [POSITIONEN]: json(200, [KONZEPTION, BETREUUNG]) });

    renderMaske(ZEILE);

    expect(await screen.findByRole('heading', { name: 'Arbeitszeit ändern' })).toBeInTheDocument();
    expect(tagFeld()).toHaveValue('2026-11-10');
    expect(vonFeld()).toHaveValue('09:00');
    expect(bisFeld()).toHaveValue('10:45');
    expect(positionsFeld()).toHaveValue('101');
  });

  it('schickt die Aenderung an den Weg des Eintrags', async () => {
    const nutzer = userEvent.setup();
    let rumpf: unknown;
    antworten({
      [POSITIONEN]: json(200, [KONZEPTION]),
      [AENDERN]: (gesendet) => {
        rumpf = alsJson(gesendet);
        return json(200, { ...ANGELEGT, id: 7 })();
      },
    });

    const { gespeichert } = renderMaske(ZEILE);
    await nutzer.clear(await screen.findByLabelText(/^bis/));
    await nutzer.type(bisFeld(), '11:00');
    await nutzer.click(speicherTaste());

    expect(gespeichert).toHaveBeenCalledTimes(1);
    expect(rumpf).toEqual({
      angebotPositionId: 101,
      tag: '2026-11-10',
      von: '09:00',
      bis: '11:00',
      accountId: 1,
    });
  });

  it('stellt die Position der Zeile voran, wenn sie nicht mehr buchbar ist (A15)', async () => {
    antworten({ [POSITIONEN]: json(200, [KONZEPTION]) });

    renderMaske(ZEILE_ALTLAST);
    await screen.findByRole('combobox', { name: 'Position' });

    expect(gruppen()[0]).toHaveAttribute('label', 'Vergangen GmbH — Angebot vom 04.05.2026');
    expect(within(gruppen()[0]).getByRole('option', { name: 'Altlast' })).toBeInTheDocument();
    expect(positionsFeld()).toHaveValue('999');
    // Die buchbaren Positionen stehen weiter dahinter — umhaengen bleibt moeglich.
    expect(within(positionsFeld()).getByRole('option', { name: 'Konzeption' })).toBeInTheDocument();
  });

  it('stellt die Position nicht doppelt voran, wenn sie noch buchbar ist', async () => {
    antworten({ [POSITIONEN]: json(200, [KONZEPTION]) });

    renderMaske(ZEILE);
    await screen.findByRole('combobox', { name: 'Position' });

    expect(within(positionsFeld()).getAllByRole('option', { name: 'Konzeption' })).toHaveLength(1);
  });
});

describe('ZeiteintragMaske — Kennzeichen intern in der Positionswahl (Issue #236, Kriterium 2)', () => {
  it('haengt „— intern" an den Gruppennamen eines internen Angebots, nicht an den eines externen', async () => {
    antworten({ [POSITIONEN]: json(200, [KONZEPTION, EIGENE_WEBSEITE]) });

    renderMaske();
    await screen.findByRole('combobox', { name: 'Position' });

    expect(gruppen()[0]).toHaveAttribute('label', 'IT Bildungshaus — Angebot vom 24.09.2026');
    expect(gruppen()[1]).toHaveAttribute(
      'label',
      'Manfred Wolff — Angebot vom 30.09.2026 — intern',
    );
  });

  it('bleibt ein natives Select: Gruppen sind `optgroup`, und die Ueberschrift traegt nur Text (E21)', async () => {
    antworten({ [POSITIONEN]: json(200, [EIGENE_WEBSEITE]) });

    renderMaske();
    await screen.findByRole('combobox', { name: 'Position' });

    const gruppe = gruppen()[0];
    expect(gruppe.tagName).toBe('OPTGROUP');
    expect(within(positionsFeld()).getByRole('option', { name: 'Eigene Webseite' }).tagName).toBe(
      'OPTION',
    );
    /*
     * Ein `optgroup`-Label nimmt nur Text: Das Wort „intern" steht im Attribut, und in der Gruppe
     * selbst steht nichts als ihre eine Option — kein Symbol, kein Chip, kein Element.
     */
    expect(within(gruppe).getAllByRole('option')).toHaveLength(1);
    expect(gruppe).toHaveTextContent('Eigene Webseite');
    expect(gruppe).not.toHaveTextContent('intern');
  });

  it('speichert eine Position beider Arten wie bisher', async () => {
    const nutzer = userEvent.setup();
    let rumpf: unknown;
    antworten({
      [POSITIONEN]: json(200, [KONZEPTION, EIGENE_WEBSEITE]),
      [ANLEGEN]: (gesendet) => {
        rumpf = alsJson(gesendet);
        return json(201, ANGELEGT)();
      },
    });

    const { gespeichert } = renderMaske();
    await nutzer.type(await screen.findByLabelText(/^von/), '09:00');
    await nutzer.type(bisFeld(), '10:45');
    await nutzer.selectOptions(positionsFeld(), '301');
    await nutzer.click(speicherTaste());

    expect(gespeichert).toHaveBeenCalledTimes(1);
    expect(rumpf).toEqual({
      angebotPositionId: 301,
      tag: HEUTE,
      von: '09:00',
      bis: '10:45',
      accountId: 1,
    });
  });
});

describe('ZeiteintragMaske — Titel am Angebot in der Positionswahl (Issue #328; Plan #323, E11)', () => {
  /** Zwei Angebote derselben Firma vom selben Tag, unterscheidbar nur am Titel. */
  const JAVA = {
    id: 501,
    bezeichnung: 'Durchfuehrung',
    angebotId: 51,
    angebotDatum: '2026-10-15',
    angebotTitel: 'Java-Grundkurs',
    intern: false,
    firmaName: 'IT-Bildungshaus',
  };
  const SPRING = { ...JAVA, id: 502, angebotId: 52, angebotTitel: 'Spring-Aufbaukurs' };

  it('Szenario: Zeit auf das richtige Angebot buchen', async () => {
    antworten({ [POSITIONEN]: json(200, [JAVA, SPRING]) });

    renderMaske();
    await screen.findByRole('combobox', { name: 'Position' });

    const namen = gruppen().map((gruppe) => gruppe.getAttribute('label'));
    expect(namen).toEqual(['IT-Bildungshaus — Java-Grundkurs', 'IT-Bildungshaus — Spring-Aufbaukurs']);
    expect(namen).not.toContain('IT-Bildungshaus — Angebot vom 15.10.2026');
  });

  it('haengt „— intern" auch hinter den Titel eines internen Angebots', async () => {
    antworten({
      [POSITIONEN]: json(200, [{ ...EIGENE_WEBSEITE, angebotTitel: 'Eigene Webseite' }]),
    });

    renderMaske();
    await screen.findByRole('combobox', { name: 'Position' });

    expect(gruppen()[0]).toHaveAttribute('label', 'Manfred Wolff — Eigene Webseite — intern');
  });

  it('nennt einen Titel von 200 Zeichen vollstaendig im Gruppennamen (Kriterium 8)', async () => {
    const titel = 'T'.repeat(200);
    antworten({ [POSITIONEN]: json(200, [{ ...JAVA, angebotTitel: titel }]) });

    renderMaske();
    await screen.findByRole('combobox', { name: 'Position' });

    expect(gruppen()[0]).toHaveAttribute('label', `IT-Bildungshaus — ${titel}`);
  });
});

describe('ZeiteintragMaske — die Person, fuer die erfasst wird (Plan #426, E12, E17; Kriterium 3 von #410)', () => {
  it('zeigt einem C-Level die Personenauswahl ueber der Position', async () => {
    antworten({ [POSITIONEN]: json(200, [KONZEPTION]) });

    renderMaske();
    const person = await screen.findByRole('combobox', { name: 'Person' });

    // Ueber der Position: Die Person kommt in der Reihenfolge des Dokuments zuerst.
    expect(person.compareDocumentPosition(positionsFeld())).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING,
    );
  });

  it('zeigt einem Employee keine Personenauswahl, fragt keine Personen und nennt keine', async () => {
    alsRolle('EMPLOYEE');
    const nutzer = userEvent.setup();
    let rumpf: unknown;
    const aufruf = antworten({
      [POSITIONEN]: json(200, [KONZEPTION]),
      [ANLEGEN]: (gesendet) => {
        rumpf = alsJson(gesendet);
        return json(201, ANGELEGT)();
      },
    });

    renderMaske(null, 2);
    await fuelle(nutzer);
    await nutzer.click(speicherTaste());

    expect(screen.queryByRole('combobox', { name: 'Person' })).not.toBeInTheDocument();
    expect(aufruf).not.toHaveBeenCalledWith('/api/arbeitszeit/personen', expect.anything());
    // Ohne Feld `accountId` gehoert der Eintrag dem angemeldeten Konto (E12).
    expect(rumpf).toEqual({ angebotPositionId: 101, tag: HEUTE, von: '09:00', bis: '10:45' });
  });

  it('Szenario: C-Level traegt Zeit fuer Ben nach — Ben ist vorausgewaehlt und geht mit', async () => {
    const nutzer = userEvent.setup();
    let rumpf: unknown;
    antworten({
      [POSITIONEN]: json(200, [KONZEPTION]),
      [ANLEGEN]: (gesendet) => {
        rumpf = alsJson(gesendet);
        return json(201, { ...ANGELEGT, accountId: 2 })();
      },
    });

    renderMaske(null, BEN.id);

    await vi.waitFor(() => {
      expect(personenFeld()).toHaveValue('2');
    });
    await fuelle(nutzer);
    await nutzer.click(speicherTaste());
    expect(rumpf).toEqual({
      angebotPositionId: 101,
      tag: HEUTE,
      von: '09:00',
      bis: '10:45',
      accountId: 2,
    });
  });

  it('belegt bei „Alle" das angemeldete Konto vor', async () => {
    antworten({ [POSITIONEN]: json(200, [KONZEPTION]) });

    renderMaske(null, ALLE);

    await vi.waitFor(() => {
      expect(personenFeld()).toHaveValue('1');
    });
  });

  it('belegt niemanden vor, wenn auf eine stillgelegte Person umgeschaltet ist', async () => {
    const nutzer = userEvent.setup();
    const aufruf = antworten({ [POSITIONEN]: json(200, [KONZEPTION]) });

    renderMaske(null, CLARA.id);
    await screen.findByRole('option', { name: BEN.name });

    expect(personenFeld()).toHaveValue('');
    // Ohne Person geht die Buchung nicht hinaus; die Meldung steht an der Auswahl.
    await fuelle(nutzer);
    await nutzer.click(speicherTaste());
    expect(personenFeld()).toHaveAccessibleDescription('Bitte ausfüllen.');
    expect(aufruf).toHaveBeenCalledTimes(2);
  });

  it('stellt beim Anlegen nur aktive Personen zur Wahl', async () => {
    antworten({ [POSITIONEN]: json(200, [KONZEPTION]) });

    renderMaske(null, ALLE);
    await screen.findByRole('option', { name: BEN.name });

    const namen = within(personenFeld())
      .getAllByRole('option')
      .map((option) => option.textContent);
    expect(namen).toEqual(['Bitte wählen', PERSON.name, BEN.name]);
  });

  it('belegt beim Aendern die Person der Zeile vor, auch eine stillgelegte, und schickt sie mit', async () => {
    const nutzer = userEvent.setup();
    let rumpf: unknown;
    antworten({
      [POSITIONEN]: json(200, [KONZEPTION]),
      [AENDERN]: (gesendet) => {
        rumpf = alsJson(gesendet);
        return json(200, { ...ANGELEGT, id: 7, accountId: 3 })();
      },
    });

    // Umgeschaltet ist auf Ben — beim Aendern zaehlt trotzdem die Person der Zeile.
    renderMaske({ ...ZEILE, person: CLARA }, BEN.id);
    await screen.findByRole('option', { name: BEN.name });

    expect(personenFeld()).toHaveValue('3');
    // Die stillgelegte Person der Zeile ist die einzige Ausnahme: Sie steht vorn und nur einmal.
    const namen = within(personenFeld())
      .getAllByRole('option')
      .map((option) => option.textContent);
    expect(namen).toEqual(['Bitte wählen', CLARA.name, PERSON.name, BEN.name]);
    await nutzer.click(speicherTaste());
    expect(rumpf).toEqual({
      angebotPositionId: 101,
      tag: '2026-11-10',
      von: '09:00',
      bis: '10:45',
      accountId: 3,
    });
  });

  it('stellt die aktive Person der Zeile beim Aendern nicht doppelt in die Auswahl', async () => {
    antworten({ [POSITIONEN]: json(200, [KONZEPTION]) });

    renderMaske({ ...ZEILE, person: BEN }, ALLE);
    await screen.findByRole('option', { name: PERSON.name });

    expect(personenFeld()).toHaveValue('2');
    expect(within(personenFeld()).getAllByRole('option', { name: BEN.name })).toHaveLength(1);
  });

  it('haengt die Meldung aus PersonNichtWaehlbar an die Personenauswahl', async () => {
    const nutzer = userEvent.setup();
    const meldung = 'Für diese Person kann hier keine Zeit erfasst werden.';
    antworten({
      [POSITIONEN]: json(200, [KONZEPTION]),
      [ANLEGEN]: problem(422, meldung, { accountId: [meldung] }),
    });

    const { gespeichert } = renderMaske(null, BEN.id);
    await vi.waitFor(() => {
      expect(personenFeld()).toHaveValue('2');
    });
    await fuelle(nutzer);
    await nutzer.click(speicherTaste());

    expect(personenFeld()).toHaveAccessibleDescription(meldung);
    expect(gespeichert).not.toHaveBeenCalled();
  });

  it('schickt die Person, die der C-Level waehlt', async () => {
    const nutzer = userEvent.setup();
    let rumpf: unknown;
    antworten({
      [POSITIONEN]: json(200, [KONZEPTION]),
      [ANLEGEN]: (gesendet) => {
        rumpf = alsJson(gesendet);
        return json(201, { ...ANGELEGT, accountId: 2 })();
      },
    });

    renderMaske(null, ALLE);
    await screen.findByRole('option', { name: BEN.name });
    await nutzer.selectOptions(personenFeld(), '2');
    await fuelle(nutzer);
    await nutzer.click(speicherTaste());

    expect(rumpf).toEqual({
      angebotPositionId: 101,
      tag: HEUTE,
      von: '09:00',
      bis: '10:45',
      accountId: 2,
    });
  });

  it('schreibt nichts mehr, wenn der Dialog vor der Antwort mit den Personen geschlossen wurde', async () => {
    const spaet = angehalten(json(200, [PERSON, BEN]));
    const aufruf = antworten({ [POSITIONEN]: json(200, [KONZEPTION]), [PERSONEN]: spaet.weg });

    const { unmount } = renderMaske(null, BEN.id);
    await screen.findByRole('combobox', { name: 'Position' });
    unmount();
    spaet.loesen();

    await vi.waitFor(() => {
      expect(aufruf).toHaveBeenCalledTimes(2);
    });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('sagt es, wenn die Personen nicht zu erreichen sind', async () => {
    antworten({ [POSITIONEN]: json(200, [KONZEPTION]), [PERSONEN]: problem(500, 'Kaputt.') });

    renderMaske(null, BEN.id);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Die Personen sind gerade nicht zu erreichen. Bitte später erneut versuchen.',
    );
    expect(personenFeld()).toHaveValue('');
  });
});
