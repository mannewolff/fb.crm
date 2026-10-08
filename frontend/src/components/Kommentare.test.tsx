import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { alsJson, fetchNachPfad, json, leer, problem } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';
import Kommentare from './Kommentare';

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
};

const ALTER = {
  id: 1,
  text: 'Erste Zeile\nZweite Zeile',
  createdAt: '2026-09-28T08:30:00Z',
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
  return renderMitTheme(<Kommentare angebotId={9} />);
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
      expect.objectContaining({ method: 'DELETE' }),
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
