import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ZeiteintragMaske from './ZeiteintragMaske';
import type { Zeitzeile } from '../api/arbeitszeit';
import { heute } from '../lib/tag';
import { alsJson, fetchNachPfad, json, problem } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';

/**
 * Der Dialog zum Erfassen und Aendern einer Arbeitszeit (Issue #202; Plan #194, A14 bis A16, A19).
 *
 * Der Dialog holt seine Auswahlliste selbst — jede Probe legt darum `buchbare-positionen` mit fest.
 * Der heutige Tag kommt aus {@link heute}; dass die Funktion den richtigen Tag setzt, prueft
 * `lib/tag.test.ts` gegen eine feste Uhr. Hier geht es nur darum, dass der Dialog ihn vorbelegt.
 */

const POSITIONEN = 'GET /api/arbeitszeit/buchbare-positionen';
const ANLEGEN = 'POST /api/arbeitszeit';
const AENDERN = 'PUT /api/arbeitszeit/7';

/** Der Tag, den der Dialog beim Erfassen vorbelegt (lokale Zeit, E4). */
const HEUTE = heute();

const KONZEPTION = {
  id: 101,
  bezeichnung: 'Konzeption',
  angebotId: 11,
  angebotDatum: '2026-09-24',
  intern: false,
  firmaName: 'IT Bildungshaus',
};

const SCHULUNG = {
  id: 102,
  bezeichnung: 'Schulung',
  angebotId: 11,
  angebotDatum: '2026-09-24',
  intern: false,
  firmaName: 'IT Bildungshaus',
};

const BETREUUNG = {
  id: 201,
  bezeichnung: 'Betreuung',
  angebotId: 21,
  angebotDatum: '2026-09-28',
  intern: false,
  firmaName: 'Adler AG',
};

/** Eine Position, die in der Auswahlliste nicht mehr steht (A15). */
const ALTLAST = {
  id: 999,
  bezeichnung: 'Altlast',
  angebotId: 31,
  angebotDatum: '2026-05-04',
  intern: false,
  firmaName: 'Vergangen GmbH',
};

/** Die Zeile der Monatsliste, die geaendert wird. */
const ZEILE: Zeitzeile = {
  id: 7,
  tag: '2026-11-10',
  von: '09:00:00',
  bis: '10:45:00',
  stundenInHundertsteln: 175,
  position: KONZEPTION,
};

/** Dieselbe Zeile, aber auf einer Position, die nicht mehr buchbar ist. */
const ZEILE_ALTLAST: Zeitzeile = { ...ZEILE, position: ALTLAST };

const ANGELEGT = {
  id: 12,
  angebotPositionId: 101,
  tag: HEUTE,
  von: '09:00:00',
  bis: '10:45:00',
  stunden: 1.75,
};

type Nutzer = ReturnType<typeof userEvent.setup>;

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

function renderMaske(zeile: Zeitzeile | null = null) {
  const gespeichert = vi.fn();
  const geschlossen = vi.fn();
  const view = renderMitTheme(
    <ZeiteintragMaske zeile={zeile} onGespeichert={gespeichert} onSchliessen={geschlossen} />,
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

describe('ZeiteintragMaske — Erfassen (Kriterium 1, A16)', () => {
  it('nennt sich „Zeit erfassen", belegt den Tag mit heute und laesst Zeiten und Position leer', async () => {
    fetchNachPfad({ [POSITIONEN]: json(200, [KONZEPTION]) });

    renderMaske();

    expect(await screen.findByRole('heading', { name: 'Zeit erfassen' })).toBeInTheDocument();
    expect(tagFeld()).toHaveValue(HEUTE);
    expect(vonFeld()).toHaveValue('');
    expect(bisFeld()).toHaveValue('');
    expect(positionsFeld()).toHaveValue('');
  });

  it('erfasst in Schritten einer Viertelstunde (A16)', async () => {
    fetchNachPfad({ [POSITIONEN]: json(200, [KONZEPTION]) });

    renderMaske();

    expect(await screen.findByLabelText(/^von/)).toHaveAttribute('step', '900');
    expect(bisFeld()).toHaveAttribute('step', '900');
  });

  it('gruppiert die Auswahl nach Firma und Angebot (A15)', async () => {
    fetchNachPfad({ [POSITIONEN]: json(200, [BETREUUNG, KONZEPTION, SCHULUNG]) });

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
    fetchNachPfad({
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
    expect(rumpf).toEqual({ angebotPositionId: 101, tag: HEUTE, von: '09:00', bis: '10:45' });
  });

  it('nimmt einen anderen Tag an und schickt ihn', async () => {
    const nutzer = userEvent.setup();
    let rumpf: unknown;
    fetchNachPfad({
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
    });
  });

  it('sperrt Speichern und Abbrechen, solange die Anfrage laeuft', async () => {
    const nutzer = userEvent.setup();
    const spaet = angehalten(json(201, ANGELEGT));
    fetchNachPfad({ [POSITIONEN]: json(200, [KONZEPTION]), [ANLEGEN]: spaet.weg });

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
    const aufruf = fetchNachPfad({ [POSITIONEN]: json(200, [KONZEPTION]) });

    renderMaske();
    await screen.findByRole('combobox', { name: 'Position' });
    await nutzer.click(speicherTaste());

    // Drei Meldungen: Der Tag ist vorbelegt, die drei anderen Angaben fehlen.
    expect(screen.getAllByText('Bitte ausfüllen.')).toHaveLength(3);
    // Der Leseweg der Auswahlliste, und sonst nichts: Die Buchung ging nicht hinaus.
    expect(aufruf).toHaveBeenCalledTimes(1);
  });

  it('sagt es, wenn die Auswahlliste nicht zu erreichen ist', async () => {
    fetchNachPfad({ [POSITIONEN]: problem(500, 'Kaputt.') });

    renderMaske();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Die buchbaren Positionen sind gerade nicht zu erreichen. Bitte später erneut versuchen.',
    );
  });

  it('sagt es, wenn keine Position buchbar ist', async () => {
    fetchNachPfad({ [POSITIONEN]: json(200, []) });

    renderMaske();

    expect(await screen.findByRole('status')).toHaveTextContent('Keine Position ist buchbar.');
  });

  it('gibt „Abbrechen" nach oben weiter', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({ [POSITIONEN]: json(200, [KONZEPTION]) });

    const { geschlossen } = renderMaske();
    await nutzer.click(await screen.findByRole('button', { name: 'Abbrechen' }));

    expect(geschlossen).toHaveBeenCalledTimes(1);
  });
});

describe('ZeiteintragMaske — die Dauer (Kriterium 2)', () => {
  it('zeigt die Dauer erst, wenn beide Uhrzeiten gueltig sind', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({ [POSITIONEN]: json(200, [KONZEPTION]) });

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
    fetchNachPfad({ [POSITIONEN]: json(200, [KONZEPTION]) });

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
    fetchNachPfad({
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
    fetchNachPfad({
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
    fetchNachPfad({
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
    fetchNachPfad({
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
    fetchNachPfad({ [POSITIONEN]: json(200, [KONZEPTION, BETREUUNG]) });

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
    fetchNachPfad({
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
    });
  });

  it('stellt die Position der Zeile voran, wenn sie nicht mehr buchbar ist (A15)', async () => {
    fetchNachPfad({ [POSITIONEN]: json(200, [KONZEPTION]) });

    renderMaske(ZEILE_ALTLAST);
    await screen.findByRole('combobox', { name: 'Position' });

    expect(gruppen()[0]).toHaveAttribute('label', 'Vergangen GmbH — Angebot vom 04.05.2026');
    expect(within(gruppen()[0]).getByRole('option', { name: 'Altlast' })).toBeInTheDocument();
    expect(positionsFeld()).toHaveValue('999');
    // Die buchbaren Positionen stehen weiter dahinter — umhaengen bleibt moeglich.
    expect(within(positionsFeld()).getByRole('option', { name: 'Konzeption' })).toBeInTheDocument();
  });

  it('stellt die Position nicht doppelt voran, wenn sie noch buchbar ist', async () => {
    fetchNachPfad({ [POSITIONEN]: json(200, [KONZEPTION]) });

    renderMaske(ZEILE);
    await screen.findByRole('combobox', { name: 'Position' });

    expect(within(positionsFeld()).getAllByRole('option', { name: 'Konzeption' })).toHaveLength(1);
  });
});
