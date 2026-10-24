import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, test, vi } from 'vitest';

import { alsJson, fetchNachPfad, json, leer, problem } from '../test/fetchNachPfad';
import { alsRolle } from '../test/rechteDoppel';
import { renderMitTheme } from '../test/render';
import Kommentare from './Kommentare';

vi.mock('../auth/rechte', () => import('../test/rechteDoppel'));

/**
 * Der Bereich „Kommentare" in der Angebotsansicht (Issue #146, Kriterien 1, 2, 4 bis 7, 9 bis 12).
 *
 * Der Bereich laedt selbst — jede Probe legt darum den Leseweg mit fest. Die Zeitpunkte stehen als
 * Erwartung in deutscher Schreibweise; der Testlauf sieht `Europe/Berlin` (`test.env.TZ` in
 * `vite.config.ts`), UTC-Mittag ist dort 14 Uhr.
 */

const WEG = '/api/angebote/9/kommentare';

const NEUER = {
  id: 2,
  text: 'Kunde am Telefon: Start erst im November',
  createdAt: '2026-09-30T12:05:00Z',
  verfasser: 'Anna Berg',
  version: 0,
};

const ALTER = {
  id: 1,
  text: 'Erste Zeile\nZweite Zeile',
  createdAt: '2026-09-28T08:30:00Z',
  verfasser: 'Ben Krause',
  version: 4,
};

const NEUER_WORT = '30.09.2026, 14:05';
const ALTER_WORT = '28.09.2026, 10:30';

/** Der Text des aelteren Kommentars, wie Testing Library ihn normalisiert. */
const ALTER_TEXT = 'Erste Zeile Zweite Zeile';

const LAEDT = 'Die Kommentare werden geladen …';
const AUSFALL_LESEN = 'Die Kommentare sind gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_SPEICHERN = 'Der Kommentar wurde nicht gespeichert. Bitte später erneut versuchen.';
const AUSFALL_LOESCHEN = 'Der Kommentar wurde nicht gelöscht. Bitte später erneut versuchen.';
const TEXT_FEHLT = 'Bitte einen Text eingeben.';
const ZU_LANG = 'Ein Kommentar hat höchstens 2.000 Zeichen.';

type Nutzer = ReturnType<typeof userEvent.setup>;

function renderBereich() {
  // Im Router wie in der Angebotsansicht: „Zur Übersicht" ist ein Verweis (#411, Kriterium 6).
  return renderMitTheme(
    <MemoryRouter>
      <Kommentare angebotId={9} />
    </MemoryRouter>,
  );
}

/** Das Feld fuer den neuen Kommentar. */
function neuesFeld() {
  return screen.getByLabelText('Neuer Kommentar');
}

/** Das Feld, das beim Bearbeiten an der Stelle des Textes steht. */
function aenderungsfeld() {
  return screen.getByLabelText('Kommentar bearbeiten');
}

function speicherTaste() {
  return screen.getByRole('button', { name: 'Kommentar speichern' });
}

/** Die Zeitpunkte der Eintraege in ihrer Reihenfolge im Dokument. */
function zeitpunkte(): readonly (string | null)[] {
  return screen.getAllByTestId('kommentar-zeit').map((teil) => teil.textContent);
}

/**
 * Schreibt einen Kommentar ueber das Feld.
 *
 * Eingefuegt statt getippt: Ein Text von 2.001 Zeichen waere als Folge einzelner Tastendruecke
 * kein Test mehr, sondern eine Wartezeit.
 */
async function schreibe(nutzer: Nutzer, text: string) {
  await nutzer.click(neuesFeld());
  await nutzer.paste(text);
  await nutzer.click(speicherTaste());
}

/** Oeffnet das ⋯-Menue eines Kommentars und waehlt einen Eintrag. */
async function waehle(nutzer: Nutzer, zeitpunkt: string, eintrag: string) {
  await nutzer.click(screen.getByRole('button', { name: `Aktionen für Kommentar vom ${zeitpunkt}` }));
  await nutzer.click(await screen.findByRole('menuitem', { name: eintrag }));
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

afterEach(() => {
  vi.restoreAllMocks();
});

describe('Kommentare — was der Bereich zeigt (Kriterien 1, 4, 5, 11)', () => {
  it('zeigt Zeitpunkt und Text jedes Kommentars, die Anzahl im Kopf und die Reihenfolge des Servers', async () => {
    fetchNachPfad({ [`GET ${WEG}`]: json(200, { kommentare: [NEUER, ALTER] }) });

    renderBereich();

    expect(await screen.findByText(NEUER.text)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Kommentare' })).toBeInTheDocument();
    expect(screen.getByTestId('karte-anzahl')).toHaveTextContent('2');
    // Der Server sortiert (neuester zuoberst); der Bereich sortiert nicht nach.
    expect(zeitpunkte()).toEqual([NEUER_WORT, ALTER_WORT]);
  });

  it('behaelt die Zeilenumbrueche des Textes', async () => {
    fetchNachPfad({ [`GET ${WEG}`]: json(200, { kommentare: [ALTER] }) });

    renderBereich();

    const text = await screen.findByTestId('kommentar-text');
    expect(text).toHaveTextContent(ALTER_TEXT);
    expect(text).toHaveStyle({ whiteSpace: 'pre-wrap' });
  });

  it('sagt es, wenn das Angebot noch keinen Kommentar hat', async () => {
    fetchNachPfad({ [`GET ${WEG}`]: json(200, { kommentare: [] }) });

    renderBereich();

    expect(await screen.findByRole('status')).toHaveTextContent('Noch kein Kommentar.');
    expect(screen.queryByTestId('kommentar')).not.toBeInTheDocument();
    expect(screen.getByTestId('karte-anzahl')).toHaveTextContent('0');
  });

  it('zeigt waehrend des Ladens einen Hinweis und noch keine Anzahl', () => {
    fetchNachPfad({ [`GET ${WEG}`]: json(200, { kommentare: [] }) });

    renderBereich();

    expect(screen.getByText(LAEDT)).toBeInTheDocument();
    expect(screen.queryByTestId('karte-anzahl')).not.toBeInTheDocument();
  });

  it('meldet den Ausfall des Ladens im Bereich und sperrt das Speichern', async () => {
    fetchNachPfad({ [`GET ${WEG}`]: leer(503) });

    renderBereich();

    expect(await screen.findByText(AUSFALL_LESEN)).toBeInTheDocument();
    // Ohne den Bestand waere ein geschriebener Kommentar der einzige sichtbare — das waere ein
    // falsches Bild des Angebots.
    expect(speicherTaste()).toBeDisabled();
  });

  it('schreibt nach dem Ausbau keinen Zustand mehr, wenn die Antwort noch eintrifft', async () => {
    const fehler = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const spaet = angehalten(json(200, { kommentare: [ALTER] }));
    fetchNachPfad({ [`GET ${WEG}`]: spaet.weg });

    const { unmount } = renderBereich();
    unmount();
    spaet.loesen();
    await new Promise((weiter) => {
      setTimeout(weiter, 10);
    });

    expect(fehler).not.toHaveBeenCalled();
  });
});

describe('Kommentare — Verfasser und Rolle (Kriterien 10, 11 von #409)', () => {
  it('zeigt je Kommentar den Verfasser', async () => {
    fetchNachPfad({ [`GET ${WEG}`]: json(200, { kommentare: [NEUER, ALTER] }) });

    renderBereich();

    await screen.findByText(NEUER.text);
    const verfasser = screen.getAllByTestId('kommentar-verfasser').map((teil) => teil.textContent);
    expect(verfasser).toEqual(['Anna Berg', 'Ben Krause']);
    const [erster] = screen.getAllByTestId('kommentar');
    expect(within(erster).getByText('Anna Berg')).toBeInTheDocument();
  });

  test('Szenario: ReadOnly liest, ändert aber nichts', async () => {
    alsRolle('READ_ONLY');
    fetchNachPfad({ [`GET ${WEG}`]: json(200, { kommentare: [NEUER, ALTER] }) });

    renderBereich();

    expect(await screen.findByText(NEUER.text)).toBeInTheDocument();
    expect(screen.getByTestId('karte-anzahl')).toHaveTextContent('2');
    expect(screen.getAllByTestId('kommentar-verfasser')).toHaveLength(2);
    expect(screen.queryByLabelText('Neuer Kommentar')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Kommentar speichern' })).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: `Aktionen für Kommentar vom ${NEUER_WORT}` }),
    ).not.toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('zeigt einem Employee Formular und ⋯-Menue, denn er darf fachlich schreiben', async () => {
    alsRolle('EMPLOYEE');
    fetchNachPfad({ [`GET ${WEG}`]: json(200, { kommentare: [NEUER] }) });

    renderBereich();

    await screen.findByText(NEUER.text);
    expect(neuesFeld()).toBeInTheDocument();
    expect(speicherTaste()).toBeEnabled();
    expect(
      screen.getByRole('button', { name: `Aktionen für Kommentar vom ${NEUER_WORT}` }),
    ).toBeInTheDocument();
  });
});

describe('Kommentare — schreiben (Kriterien 2, 6, 7)', () => {
  it('stellt den neuen Kommentar ohne Neuladen zuoberst, schneidet den Rand ab und leert das Feld', async () => {
    const nutzer = userEvent.setup();
    let rumpf: unknown;
    const fetchMock = fetchNachPfad({
      [`GET ${WEG}`]: json(200, { kommentare: [ALTER] }),
      [`POST ${WEG}`]: (gesendet) => {
        rumpf = alsJson(gesendet);
        return json(201, NEUER)();
      },
    });

    renderBereich();
    await screen.findByText(ALTER_WORT);
    await schreibe(nutzer, `  ${NEUER.text}\n`);

    expect(await screen.findByText(NEUER_WORT)).toBeInTheDocument();
    expect(rumpf).toEqual({ text: NEUER.text });
    expect(zeitpunkte()).toEqual([NEUER_WORT, ALTER_WORT]);
    expect(neuesFeld()).toHaveValue('');
    expect(screen.getByTestId('karte-anzahl')).toHaveTextContent('2');
    // Gelesen wird einmal, geschrieben einmal — kein zweites Laden nach dem Speichern.
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('weist einen Kommentar aus Leerraum am Feld ab und behaelt den Text', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ [`GET ${WEG}`]: json(200, { kommentare: [] }) });

    renderBereich();
    await screen.findByRole('status');
    await schreibe(nutzer, '  \n  ');

    expect(neuesFeld()).toHaveAccessibleDescription(TEXT_FEHLT);
    expect(neuesFeld()).toHaveValue('  \n  ');
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('weist mehr als 2.000 Zeichen am Feld ab und behaelt den Text', async () => {
    const nutzer = userEvent.setup();
    const zuLang = 'x'.repeat(2001);
    const fetchMock = fetchNachPfad({ [`GET ${WEG}`]: json(200, { kommentare: [] }) });

    renderBereich();
    await screen.findByRole('status');
    await schreibe(nutzer, zuLang);

    expect(neuesFeld()).toHaveAccessibleDescription(ZU_LANG);
    expect(neuesFeld()).toHaveValue(zuLang);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('stellt einen Feldfehler des Servers ans Feld', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [`GET ${WEG}`]: json(200, { kommentare: [] }),
      [`POST ${WEG}`]: problem(400, 'Die Eingabe ist ungültig.', {
        text: ['Der Text ist zu lang.'],
      }),
    });

    renderBereich();
    await screen.findByRole('status');
    await schreibe(nutzer, 'Kurz notiert');

    expect(await screen.findByText('Der Text ist zu lang.')).toBeInTheDocument();
    expect(neuesFeld()).toHaveAccessibleDescription('Der Text ist zu lang.');
    expect(neuesFeld()).toHaveValue('Kurz notiert');
  });

  it('meldet einen Ausfall beim Schreiben im Bereich und behaelt den Text', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [`GET ${WEG}`]: json(200, { kommentare: [] }),
      [`POST ${WEG}`]: leer(503),
    });

    renderBereich();
    await screen.findByRole('status');
    await schreibe(nutzer, 'Kurz notiert');

    expect(await screen.findByText(AUSFALL_SPEICHERN)).toBeInTheDocument();
    expect(neuesFeld()).toHaveValue('Kurz notiert');
  });

  it('sperrt die Taste, solange das Speichern laeuft', async () => {
    const nutzer = userEvent.setup();
    const langsam = angehalten(json(201, NEUER));
    fetchNachPfad({
      [`GET ${WEG}`]: json(200, { kommentare: [] }),
      [`POST ${WEG}`]: langsam.weg,
    });

    renderBereich();
    await screen.findByRole('status');
    await schreibe(nutzer, NEUER.text);

    expect(speicherTaste()).toBeDisabled();

    langsam.loesen();

    expect(await screen.findByText(NEUER_WORT)).toBeInTheDocument();
    expect(speicherTaste()).toBeEnabled();
  });
});

describe('Kommentare — aendern und loeschen (Kriterien 9, 10)', () => {
  it('ersetzt den Text an Ort und Stelle, behaelt den Zeitpunkt und nennt kein „bearbeitet"', async () => {
    const nutzer = userEvent.setup();
    let rumpf: unknown;
    fetchNachPfad({
      [`GET ${WEG}`]: json(200, { kommentare: [NEUER, ALTER] }),
      [`PUT ${WEG}/1`]: (gesendet) => {
        rumpf = alsJson(gesendet);
        return json(200, { ...ALTER, text: 'Nachgetragen' })();
      },
    });

    renderBereich();
    await screen.findByText(ALTER_WORT);
    await waehle(nutzer, ALTER_WORT, 'Bearbeiten');
    expect(aenderungsfeld()).toHaveValue(ALTER.text);
    await nutzer.clear(aenderungsfeld());
    await nutzer.type(aenderungsfeld(), 'Nachgetragen');
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(await screen.findByText('Nachgetragen')).toBeInTheDocument();
    expect(rumpf).toEqual({ text: 'Nachgetragen' });
    expect(screen.getByText(ALTER_WORT)).toBeInTheDocument();
    expect(screen.queryByText('bearbeitet')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Kommentar bearbeiten')).not.toBeInTheDocument();
    // Der andere Kommentar bleibt, wie er war.
    expect(screen.getByText(NEUER.text)).toBeInTheDocument();
  });

  it('weist beim Aendern einen leeren Text am Feld ab', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ [`GET ${WEG}`]: json(200, { kommentare: [ALTER] }) });

    renderBereich();
    await screen.findByText(ALTER_WORT);
    await waehle(nutzer, ALTER_WORT, 'Bearbeiten');
    await nutzer.clear(aenderungsfeld());
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(aenderungsfeld()).toHaveAccessibleDescription(TEXT_FEHLT);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('stellt einen Feldfehler des Servers beim Aendern ans Feld und laesst die Aenderung offen', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [`GET ${WEG}`]: json(200, { kommentare: [ALTER] }),
      [`PUT ${WEG}/1`]: problem(400, 'Die Eingabe ist ungültig.', {
        text: ['Der Text ist zu lang.'],
      }),
    });

    renderBereich();
    await screen.findByText(ALTER_WORT);
    await waehle(nutzer, ALTER_WORT, 'Bearbeiten');
    await nutzer.type(aenderungsfeld(), ' und mehr');
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(await screen.findByText('Der Text ist zu lang.')).toBeInTheDocument();
    expect(aenderungsfeld()).toHaveAccessibleDescription('Der Text ist zu lang.');
  });

  it('nimmt das Bearbeiten mit „Abbrechen" zurueck, ohne die Schnittstelle zu rufen', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ [`GET ${WEG}`]: json(200, { kommentare: [ALTER] }) });

    renderBereich();
    await screen.findByText(ALTER_WORT);
    await waehle(nutzer, ALTER_WORT, 'Bearbeiten');
    await nutzer.clear(aenderungsfeld());
    await nutzer.type(aenderungsfeld(), 'Verworfen');
    await nutzer.click(screen.getByRole('button', { name: 'Abbrechen' }));

    expect(screen.queryByLabelText('Kommentar bearbeiten')).not.toBeInTheDocument();
    expect(screen.getByTestId('kommentar-text')).toHaveTextContent(ALTER_TEXT);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('loescht erst nach der Rueckfrage', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      [`GET ${WEG}`]: json(200, { kommentare: [ALTER] }),
      [`DELETE ${WEG}/1`]: leer(204),
    });

    renderBereich();
    await screen.findByText(ALTER_WORT);
    await waehle(nutzer, ALTER_WORT, 'Löschen');
    const frage = await screen.findByRole('dialog');
    expect(frage).toHaveAccessibleName(`Löschen: Kommentar vom ${ALTER_WORT}`);
    await nutzer.click(within(frage).getByRole('button', { name: 'Löschen' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Noch kein Kommentar.');
    expect(screen.getByTestId('karte-anzahl')).toHaveTextContent('0');
    expect(fetchMock).toHaveBeenCalledWith(
      `${WEG}/1`,
      expect.objectContaining({
        method: 'DELETE',
        headers: expect.objectContaining({ 'If-Match': '"4"' }) as unknown,
      }),
    );
  });

  it('loescht nicht, wenn die Rueckfrage abgebrochen wird', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ [`GET ${WEG}`]: json(200, { kommentare: [ALTER] }) });

    renderBereich();
    await screen.findByText(ALTER_WORT);
    await waehle(nutzer, ALTER_WORT, 'Löschen');
    const frage = await screen.findByRole('dialog');
    await nutzer.click(within(frage).getByRole('button', { name: 'Abbrechen' }));

    expect(screen.getByTestId('kommentar')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('meldet einen Ausfall beim Loeschen im Bereich und behaelt den Kommentar', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [`GET ${WEG}`]: json(200, { kommentare: [ALTER] }),
      [`DELETE ${WEG}/1`]: leer(503),
    });

    renderBereich();
    await screen.findByText(ALTER_WORT);
    await waehle(nutzer, ALTER_WORT, 'Löschen');
    const frage = await screen.findByRole('dialog');
    await nutzer.click(within(frage).getByRole('button', { name: 'Löschen' }));

    expect(await screen.findByText(AUSFALL_LOESCHEN)).toBeInTheDocument();
    expect(screen.getByTestId('kommentar')).toBeInTheDocument();
  });
});

/** Heute um die genannte Uhrzeit in der Ortszeit — die Meldung nennt dann nur die Uhrzeit. */
function heuteUm(stunde: number, minute: number): string {
  const zeitpunkt = new Date();
  zeitpunkt.setHours(stunde, minute, 0, 0);
  return zeitpunkt.toISOString();
}

/** Die Abweisung des Servers, wie `GlobalExceptionHandler` sie schreibt (Plan #415, E4, E16). */
function abweisung(konflikt: Readonly<Record<string, unknown>>): () => Response {
  return json(409, {
    title: 'Conflict',
    status: 409,
    detail: 'Dieser Eintrag wurde inzwischen geändert.',
    konflikt: { art: 'veraltet', geaendertAm: heuteUm(10, 42), eigenesFenster: false, ...konflikt },
  });
}

/** Annas Stand des aelteren Kommentars: ein neuer Text, Stand 5. */
const ANNAS_STAND = { ...ALTER, text: 'Annas Fassung', version: 5 };

/** Oeffnet den aelteren Kommentar zum Bearbeiten und setzt den eigenen Text ein. */
async function bearbeite(nutzer: Nutzer, text: string) {
  await screen.findByText(ALTER_WORT);
  await waehle(nutzer, ALTER_WORT, 'Bearbeiten');
  await nutzer.clear(aenderungsfeld());
  await nutzer.type(aenderungsfeld(), text);
  await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));
}

/** Loescht den aelteren Kommentar ueber das ⋯-Menue samt Rueckfrage. */
async function loesche(nutzer: Nutzer) {
  await screen.findByText(ALTER_WORT);
  await waehle(nutzer, ALTER_WORT, 'Löschen');
  const frage = await screen.findByRole('dialog');
  await nutzer.click(within(frage).getByRole('button', { name: 'Löschen' }));
}

describe('Kommentare — gleichzeitig arbeiten (#411, Kriterien 1 bis 3, 6 und 7)', () => {
  it('legt beim Aendern den Stand des Kommentars als If-Match vor', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      [`GET ${WEG}`]: json(200, { kommentare: [ALTER] }),
      [`PUT ${WEG}/1`]: json(200, { ...ALTER, text: 'Nachgetragen', version: 5 }),
    });

    renderBereich();
    await bearbeite(nutzer, 'Nachgetragen');

    expect(await screen.findByText('Nachgetragen')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      `${WEG}/1`,
      expect.objectContaining({
        method: 'PUT',
        headers: expect.objectContaining({ 'If-Match': '"4"' }) as unknown,
      }),
    );
  });

  it('schreibt einen neuen Kommentar ohne Stand (Kriterium 7)', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      [`GET ${WEG}`]: json(200, { kommentare: [] }),
      [`POST ${WEG}`]: json(201, NEUER),
    });

    renderBereich();
    await screen.findByRole('status');
    await schreibe(nutzer, NEUER.text);

    expect(await screen.findByText(NEUER_WORT)).toBeInTheDocument();
    expect(fetchMock.mock.calls[1][1]?.headers).not.toHaveProperty('If-Match');
  });

  it('zeigt bei einem inzwischen geaenderten Kommentar die Meldung und laesst den eigenen Text im Feld', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [`GET ${WEG}`]: json(200, { kommentare: [ALTER] }),
      [`PUT ${WEG}/1`]: abweisung({ geaendertVon: 'Anna' }),
    });

    renderBereich();
    await bearbeite(nutzer, 'Meine Fassung');

    expect(
      await screen.findByText('Anna hat diesen Eintrag inzwischen geändert (10:42).'),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Neu laden' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Zur Übersicht' })).not.toBeInTheDocument();
    expect(aenderungsfeld()).toHaveValue('Meine Fassung');
    // Die Abweisung ist kein Ausfall und keine Feldmeldung.
    expect(screen.queryByText(AUSFALL_SPEICHERN)).not.toBeInTheDocument();
    expect(aenderungsfeld()).not.toHaveAccessibleDescription();
  });

  it('holt mit „Neu laden" den Stand des Servers ins Feld, stellt den eigenen Text daneben und speichert auf dem neuen Stand', async () => {
    const nutzer = userEvent.setup();
    let gelesen = 0;
    let gespeichert = 0;
    const fetchMock = fetchNachPfad({
      [`GET ${WEG}`]: () => {
        gelesen += 1;
        return json(200, { kommentare: [gelesen === 1 ? ALTER : ANNAS_STAND] })();
      },
      [`PUT ${WEG}/1`]: () => {
        gespeichert += 1;
        return gespeichert === 1
          ? abweisung({ geaendertVon: 'Anna' })()
          : json(200, { ...ANNAS_STAND, text: 'Zusammengefuehrt', version: 6 })();
      },
    });

    renderBereich();
    await bearbeite(nutzer, 'Meine Fassung');
    await nutzer.click(await screen.findByRole('button', { name: 'Neu laden' }));

    const eigene = await screen.findByRole('region', { name: 'Deine Eingaben' });
    expect(within(eigene).getByText('Kommentar')).toBeInTheDocument();
    expect(within(eigene).getByText('Meine Fassung')).toBeInTheDocument();
    expect(aenderungsfeld()).toHaveValue('Annas Fassung');
    expect(screen.queryByRole('button', { name: 'Neu laden' })).not.toBeInTheDocument();

    await nutzer.clear(aenderungsfeld());
    await nutzer.type(aenderungsfeld(), 'Zusammengefuehrt');
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(await screen.findByText('Zusammengefuehrt')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenLastCalledWith(
      `${WEG}/1`,
      expect.objectContaining({
        headers: expect.objectContaining({ 'If-Match': '"5"' }) as unknown,
      }),
    );
  });

  it('nimmt den eigenen Text mit „Verwerfen" weg', async () => {
    const nutzer = userEvent.setup();
    let gelesen = 0;
    fetchNachPfad({
      [`GET ${WEG}`]: () => {
        gelesen += 1;
        return json(200, { kommentare: [gelesen === 1 ? ALTER : ANNAS_STAND] })();
      },
      [`PUT ${WEG}/1`]: abweisung({ geaendertVon: 'Anna' }),
    });

    renderBereich();
    await bearbeite(nutzer, 'Meine Fassung');
    await nutzer.click(await screen.findByRole('button', { name: 'Neu laden' }));
    await nutzer.click(await screen.findByRole('button', { name: 'Verwerfen' }));

    expect(screen.queryByRole('region', { name: 'Deine Eingaben' })).not.toBeInTheDocument();
  });

  it('meldet den Ausfall, wenn „Neu laden" scheitert, und laesst den eigenen Text im Feld', async () => {
    const nutzer = userEvent.setup();
    let gelesen = 0;
    fetchNachPfad({
      [`GET ${WEG}`]: () => {
        gelesen += 1;
        return gelesen === 1 ? json(200, { kommentare: [ALTER] })() : leer(503)();
      },
      [`PUT ${WEG}/1`]: abweisung({ geaendertVon: 'Anna' }),
    });

    renderBereich();
    await bearbeite(nutzer, 'Meine Fassung');
    await nutzer.click(await screen.findByRole('button', { name: 'Neu laden' }));

    expect(await screen.findByText(AUSFALL_LESEN)).toBeInTheDocument();
    expect(aenderungsfeld()).toHaveValue('Meine Fassung');
    expect(screen.queryByRole('region', { name: 'Deine Eingaben' })).not.toBeInTheDocument();
  });

  it('bietet beim inzwischen geloeschten Kommentar „Zur Übersicht" statt „Neu laden" und laesst den eigenen Text sichtbar (Kriterium 6)', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [`GET ${WEG}`]: json(200, { kommentare: [ALTER] }),
      [`PUT ${WEG}/1`]: abweisung({ art: 'gelöscht', geaendertVon: 'Anna' }),
    });

    renderBereich();
    await bearbeite(nutzer, 'Meine Fassung');

    expect(
      await screen.findByText('Anna hat diesen Eintrag inzwischen gelöscht (10:42).'),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Zur Übersicht' })).toHaveAttribute('href', '/angebote');
    expect(screen.queryByRole('button', { name: 'Neu laden' })).not.toBeInTheDocument();
    expect(aenderungsfeld()).toHaveValue('Meine Fassung');
  });

  it('weist das Loeschen eines inzwischen geaenderten Kommentars ab und behaelt ihn', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [`GET ${WEG}`]: json(200, { kommentare: [ALTER] }),
      [`DELETE ${WEG}/1`]: abweisung({ geaendertVon: 'Anna' }),
    });

    renderBereich();
    await loesche(nutzer);

    expect(
      await screen.findByText('Anna hat diesen Eintrag inzwischen geändert (10:42).'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('kommentar')).toBeInTheDocument();
    expect(screen.queryByText(AUSFALL_LOESCHEN)).not.toBeInTheDocument();
  });

  it('holt nach einem abgewiesenen Loeschen mit „Neu laden" den neuen Stand und loescht auf ihm', async () => {
    const nutzer = userEvent.setup();
    let gelesen = 0;
    let geloescht = 0;
    const fetchMock = fetchNachPfad({
      [`GET ${WEG}`]: () => {
        gelesen += 1;
        return json(200, { kommentare: [gelesen === 1 ? ALTER : ANNAS_STAND] })();
      },
      [`DELETE ${WEG}/1`]: () => {
        geloescht += 1;
        return geloescht === 1 ? abweisung({ geaendertVon: 'Anna' })() : leer(204)();
      },
    });

    renderBereich();
    await loesche(nutzer);
    await nutzer.click(await screen.findByRole('button', { name: 'Neu laden' }));

    // Ohne offenes Feld gibt es keinen eigenen Text, den es daneben zu stellen gaebe.
    expect(await screen.findByText('Annas Fassung')).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Deine Eingaben' })).not.toBeInTheDocument();
    await loesche(nutzer);

    expect(await screen.findByRole('status')).toHaveTextContent('Noch kein Kommentar.');
    expect(fetchMock).toHaveBeenLastCalledWith(
      `${WEG}/1`,
      expect.objectContaining({
        method: 'DELETE',
        headers: expect.objectContaining({ 'If-Match': '"5"' }) as unknown,
      }),
    );
  });

  it('schliesst beim „Neu laden" das Feld, wenn der Kommentar fort ist, und behaelt den eigenen Text daneben', async () => {
    const nutzer = userEvent.setup();
    let gelesen = 0;
    fetchNachPfad({
      [`GET ${WEG}`]: () => {
        gelesen += 1;
        return json(200, { kommentare: gelesen === 1 ? [ALTER] : [] })();
      },
      [`PUT ${WEG}/1`]: abweisung({ geaendertVon: 'Anna' }),
    });

    renderBereich();
    await bearbeite(nutzer, 'Meine Fassung');
    await nutzer.click(await screen.findByRole('button', { name: 'Neu laden' }));

    const eigene = await screen.findByRole('region', { name: 'Deine Eingaben' });
    expect(within(eigene).getByText('Meine Fassung')).toBeInTheDocument();
    expect(screen.queryByLabelText('Kommentar bearbeiten')).not.toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Noch kein Kommentar.');
  });

  it('meldet beim Loeschen die eigene Loeschung aus einem anderen Fenster mit „Zur Übersicht"', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [`GET ${WEG}`]: json(200, { kommentare: [ALTER] }),
      [`DELETE ${WEG}/1`]: abweisung({ art: 'gelöscht', eigenesFenster: true }),
    });

    renderBereich();
    await loesche(nutzer);

    expect(
      await screen.findByText(
        'Du hast diesen Eintrag inzwischen in einem anderen Fenster gelöscht (10:42).',
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Zur Übersicht' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Neu laden' })).not.toBeInTheDocument();
  });

  it('nimmt die Meldung beim naechsten Schritt weg', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [`GET ${WEG}`]: json(200, { kommentare: [ALTER] }),
      [`DELETE ${WEG}/1`]: abweisung({ geaendertVon: 'Anna' }),
      [`POST ${WEG}`]: json(201, NEUER),
    });

    renderBereich();
    await loesche(nutzer);
    await screen.findByText('Anna hat diesen Eintrag inzwischen geändert (10:42).');
    await schreibe(nutzer, NEUER.text);

    expect(await screen.findByText(NEUER_WORT)).toBeInTheDocument();
    expect(
      screen.queryByText('Anna hat diesen Eintrag inzwischen geändert (10:42).'),
    ).not.toBeInTheDocument();
  });
});
