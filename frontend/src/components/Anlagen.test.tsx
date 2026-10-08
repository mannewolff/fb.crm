import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterAll, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import Anlagen from './Anlagen';
import { MAX_UPLOAD_BYTE } from '../lib/dateigroesse';
import { fetchNachPfad, formularWeg, json, leer, problem } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';

/**
 * Der Bereich „Anlagen" in der Angebotsansicht (Issue #148, Kriterien 1 bis 14).
 *
 * Der Bereich laedt selbst — jede Probe legt darum den Leseweg mit fest. Die Zeitpunkte stehen als
 * Erwartung in deutscher Schreibweise; der Testlauf sieht `Europe/Berlin` (`test.env.TZ` in
 * `vite.config.ts`), UTC-Mittag ist dort 14 Uhr.
 */

const WEG = '/api/angebote/9/anlagen';

const NEUE = {
  id: 2,
  dateiName: 'Lastenheft.pdf',
  groesse: 2048,
  vorschauArt: 'PDF',
  createdAt: '2026-09-30T12:05:00Z',
};

const ALTE = {
  id: 1,
  dateiName: 'skizze.png',
  groesse: 500,
  vorschauArt: 'PNG',
  createdAt: '2026-09-28T08:30:00Z',
};

/** Eine Anlage ohne Vorschauart — zu ihr gibt es nur „Herunterladen" (Kriterium 15). */
const TABELLE = {
  id: 3,
  dateiName: 'Kalkulation.xlsx',
  groesse: 4096,
  vorschauArt: null,
  createdAt: '2026-09-29T09:00:00Z',
};

const NEUE_WORT = '30.09.2026, 14:05';
const ALTE_WORT = '28.09.2026, 10:30';

const LAEDT = 'Die Anlagen werden geladen …';
const LEER = 'Noch keine Anlage.';
const AUSFALL_LESEN = 'Die Anlagen sind gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_HOCHLADEN = 'Die Datei wurde nicht hochgeladen. Bitte später erneut versuchen.';
const AUSFALL_LOESCHEN = 'Die Anlage wurde nicht gelöscht. Bitte später erneut versuchen.';
const OHNE_INHALT = 'Die Datei ist leer.';
const ZU_GROSS = 'Die Datei darf höchstens 25 MB groß sein.';

type Nutzer = ReturnType<typeof userEvent.setup>;

function renderBereich() {
  return renderMitTheme(<Anlagen angebotId={9} />);
}

/** Das Dateifeld, das die Taste „Datei hochladen" oeffnet. */
function dateifeld() {
  return screen.getByLabelText('Datei auswählen');
}

function ladeTaste() {
  return screen.getByRole('button', { name: 'Datei hochladen' });
}

/** Die Dateinamen der Zeilen in ihrer Reihenfolge im Dokument. */
function namen(): readonly (string | null)[] {
  return screen.getAllByTestId('anlage-name').map((teil) => teil.textContent);
}

/**
 * Eine Datei mit vorgegebener Groesse.
 *
 * Die Groesse wird gesetzt und nicht aus Inhalt erzeugt: Ein `File` von 25 MB ist im Testlauf
 * eine Wartezeit und kein Erkenntnisgewinn — die Vorpruefung liest allein `size`.
 */
function dateiMitGroesse(name: string, groesse: number): File {
  const datei = new File(['x'], name, { type: 'application/pdf' });
  Object.defineProperty(datei, 'size', { value: groesse });
  return datei;
}

/** Eine Antwort, die erst auf Abruf eintrifft — so laesst sich „waehrend des Hochladens" pruefen. */
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

/**
 * Das Doppel fuer die Objekt-URL, die das Vorschaufenster anlegt (jsdom kennt beides nicht).
 *
 * Der Bereich selbst legt keine an; er oeffnet nur das Fenster. Ohne das Doppel scheiterte darum
 * jede Probe, die „Anzeigen" anstoesst, an einer Stelle, die mit ihrem Fall nichts zu tun hat.
 */
function objektUrlDoppel() {
  const erzeuge = vi.fn<(objekt: Blob) => string>(() => 'blob:fbcrm/vorschau');
  const gebeFrei = vi.fn<(url: string) => void>();
  Object.defineProperty(URL, 'createObjectURL', { value: erzeuge, configurable: true });
  Object.defineProperty(URL, 'revokeObjectURL', { value: gebeFrei, configurable: true });
  return { erzeuge, gebeFrei };
}

let objektUrl: ReturnType<typeof objektUrlDoppel>;

/** Oeffnet das ⋯-Menue einer Anlage und waehlt einen Eintrag. */
async function waehle(nutzer: Nutzer, dateiName: string, eintrag: string) {
  await nutzer.click(screen.getByRole('button', { name: `Aktionen für ${dateiName}` }));
  await nutzer.click(await screen.findByRole('menuitem', { name: eintrag }));
}

beforeEach(() => {
  objektUrl = objektUrlDoppel();
});

afterEach(() => {
  vi.restoreAllMocks();
});

/**
 * Abgeraeumt wird erst am Ende der Datei, nicht nach jeder Probe: Das `cleanup` aus dem
 * Vitest-Setup baut die Ansicht in seinem eigenen `afterEach` ab, und dabei gibt das Fenster seine
 * Objekt-URL frei. Ein vorher entfernter `revokeObjectURL` liesse genau diesen Abbau scheitern.
 */
afterAll(() => {
  Reflect.deleteProperty(URL, 'createObjectURL');
  Reflect.deleteProperty(URL, 'revokeObjectURL');
});

describe('Anlagen — was der Bereich zeigt (Kriterien 1, 4, 11)', () => {
  it('zeigt Name, Groesse und Zeitpunkt jeder Anlage, die Anzahl im Kopf und die Reihenfolge des Servers', async () => {
    fetchNachPfad({ [`GET ${WEG}`]: json(200, { anlagen: [NEUE, ALTE] }) });

    renderBereich();

    expect(await screen.findByText(NEUE.dateiName)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Anlagen' })).toBeInTheDocument();
    expect(screen.getByTestId('karte-anzahl')).toHaveTextContent('2');
    // Der Server sortiert (neueste zuoberst); der Bereich sortiert nicht nach.
    expect(namen()).toEqual([NEUE.dateiName, ALTE.dateiName]);
    const zeilen = screen.getAllByTestId('anlage');
    expect(within(zeilen[0]).getByTestId('anlage-groesse')).toHaveTextContent('2 KB');
    expect(within(zeilen[0]).getByTestId('anlage-zeit')).toHaveTextContent(NEUE_WORT);
    expect(within(zeilen[1]).getByTestId('anlage-groesse')).toHaveTextContent('500 B');
    expect(within(zeilen[1]).getByTestId('anlage-zeit')).toHaveTextContent(ALTE_WORT);
  });

  it('sagt es, wenn das Angebot noch keine Anlage hat', async () => {
    fetchNachPfad({ [`GET ${WEG}`]: json(200, { anlagen: [] }) });

    renderBereich();

    expect(await screen.findByRole('status')).toHaveTextContent(LEER);
    expect(screen.queryByTestId('anlage')).not.toBeInTheDocument();
    expect(screen.getByTestId('karte-anzahl')).toHaveTextContent('0');
  });

  it('zeigt waehrend des Ladens einen Hinweis und noch keine Anzahl', () => {
    fetchNachPfad({ [`GET ${WEG}`]: json(200, { anlagen: [] }) });

    renderBereich();

    expect(screen.getByText(LAEDT)).toBeInTheDocument();
    expect(screen.queryByTestId('karte-anzahl')).not.toBeInTheDocument();
  });

  it('meldet den Ausfall des Ladens im Bereich und sperrt das Hochladen', async () => {
    fetchNachPfad({ [`GET ${WEG}`]: leer(503) });

    renderBereich();

    expect(await screen.findByText(AUSFALL_LESEN)).toBeInTheDocument();
    // Ohne den Bestand waere die hochgeladene Datei die einzige sichtbare — das waere ein
    // falsches Bild des Angebots.
    expect(ladeTaste()).toBeDisabled();
  });

  it('fuehrt zwei gleichnamige Anlagen als zwei Zeilen mit je eigenem Weg', async () => {
    fetchNachPfad({
      [`GET ${WEG}`]: json(200, {
        anlagen: [NEUE, { ...ALTE, dateiName: NEUE.dateiName, groesse: 1024 }],
      }),
    });

    renderBereich();
    await screen.findAllByTestId('anlage');

    expect(namen()).toEqual([NEUE.dateiName, NEUE.dateiName]);
    expect(
      screen
        .getAllByRole('link', { name: `Herunterladen: ${NEUE.dateiName}` })
        .map((verweis) => verweis.getAttribute('href')),
    ).toEqual([`${WEG}/2/inhalt`, `${WEG}/1/inhalt`]);
  });

  it('verweist mit „Herunterladen" auf den Inhaltsweg der Anlage', async () => {
    fetchNachPfad({ [`GET ${WEG}`]: json(200, { anlagen: [ALTE] }) });

    renderBereich();

    const verweis = await screen.findByRole('link', {
      name: `Herunterladen: ${ALTE.dateiName}`,
    });
    expect(verweis).toHaveAttribute('href', `${WEG}/1/inhalt`);
    expect(verweis).toHaveAttribute('download');
  });

  it('schreibt nach dem Ausbau keinen Zustand mehr, wenn die Antwort noch eintrifft', async () => {
    const fehler = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const spaet = angehalten(json(200, { anlagen: [ALTE] }));
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

describe('Anlagen — hochladen (Kriterien 2, 5, 6, 7, 14)', () => {
  it('stellt die neue Anlage ohne Neuladen zuoberst und schickt sie als Teil „datei"', async () => {
    const nutzer = userEvent.setup();
    let formular: FormData | null = null;
    const fetchMock = fetchNachPfad({
      [`GET ${WEG}`]: json(200, { anlagen: [ALTE] }),
      [`POST ${WEG}`]: formularWeg(
        (gesendet) => {
          formular = gesendet;
        },
        json(201, NEUE),
      ),
    });

    renderBereich();
    await screen.findByText(ALTE.dateiName);
    await nutzer.upload(dateifeld(), dateiMitGroesse(NEUE.dateiName, NEUE.groesse));

    expect(await screen.findByText(NEUE.dateiName)).toBeInTheDocument();
    expect(namen()).toEqual([NEUE.dateiName, ALTE.dateiName]);
    expect(screen.getByTestId('karte-anzahl')).toHaveTextContent('2');
    expect(formular).not.toBeNull();
    // Gelesen wird einmal, hochgeladen einmal — kein zweites Laden nach dem Hochladen.
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('oeffnet mit der Taste das Dateifeld', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({ [`GET ${WEG}`]: json(200, { anlagen: [] }) });

    renderBereich();
    await screen.findByRole('status');
    // Der Dateidialog des Browsers laesst sich nicht oeffnen und nicht beobachten; beobachtbar
    // ist, dass die Taste das Feld anstoesst — mehr tut sie nicht.
    const oeffnen = vi.spyOn(dateifeld(), 'click');
    await nutzer.click(ladeTaste());

    expect(oeffnen).toHaveBeenCalled();
  });

  it('weist eine leere Datei vor dem Senden ab', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ [`GET ${WEG}`]: json(200, { anlagen: [] }) });

    renderBereich();
    await screen.findByRole('status');
    await nutzer.upload(dateifeld(), new File([], 'leer.txt'));

    expect(await screen.findByText(OHNE_INHALT)).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('weist eine zu grosse Datei vor dem Senden ab und nennt die Grenze', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ [`GET ${WEG}`]: json(200, { anlagen: [] }) });

    renderBereich();
    await screen.findByRole('status');
    await nutzer.upload(dateifeld(), dateiMitGroesse('gross.pdf', MAX_UPLOAD_BYTE + 1));

    expect(await screen.findByText(ZU_GROSS)).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('stellt einen Feldfehler des Servers als Meldung in den Bereich', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [`GET ${WEG}`]: json(200, { anlagen: [] }),
      [`POST ${WEG}`]: problem(400, 'Die Eingabe ist ungültig.', {
        datei: ['Die Datei braucht einen Namen.'],
      }),
    });

    renderBereich();
    await screen.findByRole('status');
    await nutzer.upload(dateifeld(), dateiMitGroesse('   .pdf', 12));

    expect(await screen.findByText('Die Datei braucht einen Namen.')).toBeInTheDocument();
    expect(screen.queryByTestId('anlage')).not.toBeInTheDocument();
  });

  it('meldet einen Ausfall beim Hochladen und legt keine halbe Zeile an', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [`GET ${WEG}`]: json(200, { anlagen: [] }),
      [`POST ${WEG}`]: leer(503),
    });

    renderBereich();
    await screen.findByRole('status');
    await nutzer.upload(dateifeld(), dateiMitGroesse('bericht.pdf', 12));

    expect(await screen.findByText(AUSFALL_HOCHLADEN)).toBeInTheDocument();
    expect(screen.queryByTestId('anlage')).not.toBeInTheDocument();
    expect(screen.getByTestId('karte-anzahl')).toHaveTextContent('0');
  });

  it('sperrt die Taste, solange das Hochladen laeuft', async () => {
    const nutzer = userEvent.setup();
    const langsam = angehalten(json(201, NEUE));
    fetchNachPfad({
      [`GET ${WEG}`]: json(200, { anlagen: [] }),
      [`POST ${WEG}`]: langsam.weg,
    });

    renderBereich();
    await screen.findByRole('status');
    await nutzer.upload(dateifeld(), dateiMitGroesse(NEUE.dateiName, NEUE.groesse));

    expect(ladeTaste()).toBeDisabled();

    langsam.loesen();

    expect(await screen.findByText(NEUE.dateiName)).toBeInTheDocument();
    expect(ladeTaste()).toBeEnabled();
  });

  it('sendet nichts, wenn die Wahl keine Datei ergibt', async () => {
    const fetchMock = fetchNachPfad({ [`GET ${WEG}`]: json(200, { anlagen: [] }) });

    renderBereich();
    await screen.findByRole('status');
    // So sieht der Wechsel aus, wenn der Dateidialog ohne Auswahl geschlossen wird.
    fireEvent.change(dateifeld(), { target: { files: null } });

    expect(screen.getByRole('status')).toHaveTextContent(LEER);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});

describe('Anlagen — loeschen (Kriterien 9, 10)', () => {
  it('loescht erst nach der Rueckfrage', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      [`GET ${WEG}`]: json(200, { anlagen: [ALTE] }),
      [`DELETE ${WEG}/1`]: leer(204),
    });

    renderBereich();
    await screen.findByText(ALTE.dateiName);
    await waehle(nutzer, ALTE.dateiName, 'Löschen');
    const frage = await screen.findByRole('dialog');
    expect(frage).toHaveAccessibleName(`Löschen: ${ALTE.dateiName}`);
    await nutzer.click(within(frage).getByRole('button', { name: 'Löschen' }));

    expect(await screen.findByRole('status')).toHaveTextContent(LEER);
    expect(screen.getByTestId('karte-anzahl')).toHaveTextContent('0');
    expect(fetchMock).toHaveBeenCalledWith(
      `${WEG}/1`,
      expect.objectContaining({ method: 'DELETE' }),
    );
  });

  it('loescht nicht, wenn die Rueckfrage abgebrochen wird', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ [`GET ${WEG}`]: json(200, { anlagen: [ALTE] }) });

    renderBereich();
    await screen.findByText(ALTE.dateiName);
    await waehle(nutzer, ALTE.dateiName, 'Löschen');
    const frage = await screen.findByRole('dialog');
    await nutzer.click(within(frage).getByRole('button', { name: 'Abbrechen' }));

    expect(screen.getByTestId('anlage')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('meldet einen Ausfall beim Loeschen im Bereich und behaelt die Anlage', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [`GET ${WEG}`]: json(200, { anlagen: [ALTE] }),
      [`DELETE ${WEG}/1`]: leer(503),
    });

    renderBereich();
    await screen.findByText(ALTE.dateiName);
    await waehle(nutzer, ALTE.dateiName, 'Löschen');
    const frage = await screen.findByRole('dialog');
    await nutzer.click(within(frage).getByRole('button', { name: 'Löschen' }));

    expect(await screen.findByText(AUSFALL_LOESCHEN)).toBeInTheDocument();
    expect(screen.getByTestId('anlage')).toBeInTheDocument();
  });
});

describe('Anlagen — anzeigen (Kriterium 15)', () => {
  it('bietet „Anzeigen" nur bei einer Anlage mit Vorschauart', async () => {
    fetchNachPfad({ [`GET ${WEG}`]: json(200, { anlagen: [ALTE, TABELLE] }) });

    renderBereich();
    await screen.findAllByTestId('anlage');

    expect(
      screen.getByRole('button', { name: `Anzeigen: ${ALTE.dateiName}` }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: `Anzeigen: ${TABELLE.dateiName}` }),
    ).not.toBeInTheDocument();
    // Herunterladen gibt es zu jeder Anlage.
    expect(
      screen.getByRole('link', { name: `Herunterladen: ${TABELLE.dateiName}` }),
    ).toBeInTheDocument();
  });

  it('oeffnet die Vorschau und gibt den Fokus danach an die Taste zurueck', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [`GET ${WEG}`]: json(200, { anlagen: [ALTE] }),
      [`GET ${WEG}/1/inhalt`]: () =>
        new Response('inhalt', { status: 200, headers: { 'Content-Type': 'image/png' } }),
    });

    renderBereich();
    await screen.findByText(ALTE.dateiName);
    const taste = screen.getByRole('button', { name: `Anzeigen: ${ALTE.dateiName}` });
    await nutzer.click(taste);

    const fenster = await screen.findByRole('dialog');
    expect(fenster).toHaveAccessibleName(ALTE.dateiName);

    await nutzer.click(within(fenster).getByRole('button', { name: 'Schließen' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(taste).toHaveFocus();
    expect(objektUrl.gebeFrei).toHaveBeenCalledWith('blob:fbcrm/vorschau');
  });
});
