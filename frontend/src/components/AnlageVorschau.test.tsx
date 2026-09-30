import { fireEvent, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterAll, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import AnlageVorschau from './AnlageVorschau';
import type { AnlageMitVorschau } from './AnlageVorschau';
import { fetchNachPfad, leer } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';

/**
 * Das Vorschaufenster einer Anlage (Issue #148, Kriterium 15; Plan #150, E6, E7, E11).
 *
 * Die Proben stellen `URL.createObjectURL` und `URL.revokeObjectURL` selbst bereit: jsdom kennt
 * beide nicht — ohne das Doppel scheiterte jede Probe an einer Stelle, die mit ihrem Fall nichts
 * zu tun hat. Das Doppel ist zugleich das Fenster in die Zusage: Welche Art der Inhalt bekommt und
 * ob die URL wieder freigegeben wird, ist nur an ihm zu sehen.
 *
 * Der Inhalt kommt ueber den Inhaltsweg der Anlage; welche Art die Antwort dabei nennt, steht je
 * Probe — genau darum geht es im PDF-Fall.
 */

const WEG = '/api/angebote/9/anlagen/1/inhalt';
const URL_WERT = 'blob:fbcrm/vorschau';

const BILD: AnlageMitVorschau = {
  id: 1,
  dateiName: 'skizze.png',
  groesse: 500,
  vorschauArt: 'PNG',
  createdAt: '2026-09-28T08:30:00Z',
};

const PDF: AnlageMitVorschau = { ...BILD, dateiName: 'Lastenheft.pdf', vorschauArt: 'PDF' };

const LAEDT = 'Die Datei wird geladen …';
const AUSFALL = 'Die Datei ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const BILD_AUSFALL = 'Das Bild lässt sich nicht anzeigen.';

/** Eine Antwort mit Bytes und der Art, die der Server nennt. */
function inhalt(art: string): () => Response {
  return () => new Response('inhalt', { status: 200, headers: { 'Content-Type': art } });
}

/** Eine Antwort, die erst auf Abruf eintrifft — so laesst sich „waehrend des Ladens" pruefen. */
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
 * Das Doppel fuer die Objekt-URL.
 *
 * `vi.spyOn` faellt aus: Es braucht eine vorhandene Eigenschaft, und jsdom bringt keine mit.
 * Gesetzt wird darum am `URL`-Objekt selbst, wie die Speicher-Doppel es an `globalThis` tun.
 *
 * Der Eingang heisst `Blob` und nicht `Blob | MediaSource`: Diese Anwendung legt nur Blobs als
 * Objekt-URL an. Die weitere Schreibweise erzwaenge beim Nachlesen eine Verengung, deren zweiter
 * Zweig nie eintritt — eine Zeile, die kein Fall erreicht.
 */
function objektUrlDoppel() {
  const erzeuge = vi.fn<(objekt: Blob) => string>(() => URL_WERT);
  const gebeFrei = vi.fn<(url: string) => void>();
  Object.defineProperty(URL, 'createObjectURL', { value: erzeuge, configurable: true });
  Object.defineProperty(URL, 'revokeObjectURL', { value: gebeFrei, configurable: true });
  return { erzeuge, gebeFrei };
}

let objektUrl: ReturnType<typeof objektUrlDoppel>;

/** Der Blob, der als Objekt-URL angelegt wurde. */
function angelegterBlob(): Blob {
  return objektUrl.erzeuge.mock.calls[0][0];
}

function renderVorschau(anlage: AnlageMitVorschau) {
  const schliessen = vi.fn();
  const utils = renderMitTheme(
    <AnlageVorschau angebotId={9} anlage={anlage} onSchliessen={schliessen} />,
  );
  return { ...utils, schliessen, nutzer: userEvent.setup() };
}

function schliessenTaste() {
  return screen.getByRole('button', { name: 'Schließen' });
}

function ladeVerweis() {
  return screen.getByRole('link', { name: 'Herunterladen' });
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

describe('AnlageVorschau — das Fenster selbst', () => {
  it('traegt den Dateinamen als Titel', async () => {
    fetchNachPfad({ [`GET ${WEG}`]: inhalt('application/pdf') });

    renderVorschau(PDF);

    expect(await screen.findByRole('dialog')).toHaveAccessibleName(PDF.dateiName);
    expect(screen.getByRole('heading', { name: PDF.dateiName })).toBeInTheDocument();
  });

  it('verweist mit „Herunterladen" auf den Inhaltsweg der Anlage', async () => {
    fetchNachPfad({ [`GET ${WEG}`]: inhalt('image/png') });

    renderVorschau(BILD);
    await screen.findByRole('img', { name: BILD.dateiName });

    expect(ladeVerweis()).toHaveAttribute('href', WEG);
    expect(ladeVerweis()).toHaveAttribute('download');
  });

  it('zeigt waehrend des Ladens einen Hinweis und laesst „Herunterladen" bedienbar', async () => {
    const langsam = angehalten(inhalt('image/png'));
    fetchNachPfad({ [`GET ${WEG}`]: langsam.weg });

    renderVorschau(BILD);

    expect(await screen.findByText(LAEDT)).toBeInTheDocument();
    expect(ladeVerweis()).toBeInTheDocument();
    expect(objektUrl.erzeuge).not.toHaveBeenCalled();
  });

  it('meldet den Ausfall des Abrufs und laesst „Herunterladen" bedienbar', async () => {
    fetchNachPfad({ [`GET ${WEG}`]: leer(503) });

    renderVorschau(BILD);

    expect(await screen.findByText(AUSFALL)).toBeInTheDocument();
    expect(ladeVerweis()).toBeInTheDocument();
    expect(screen.queryByRole('img', { name: BILD.dateiName })).not.toBeInTheDocument();
  });

  it('schliesst mit der Schliessen-Taste', async () => {
    fetchNachPfad({ [`GET ${WEG}`]: inhalt('image/png') });

    const { nutzer, schliessen } = renderVorschau(BILD);
    await screen.findByRole('img', { name: BILD.dateiName });
    await nutzer.click(schliessenTaste());

    expect(schliessen).toHaveBeenCalledTimes(1);
  });

  it('schliesst mit Escape', async () => {
    fetchNachPfad({ [`GET ${WEG}`]: inhalt('image/png') });

    const { nutzer, schliessen } = renderVorschau(BILD);
    await screen.findByRole('img', { name: BILD.dateiName });
    await nutzer.keyboard('{Escape}');

    expect(schliessen).toHaveBeenCalledTimes(1);
  });

  it('enthaelt keine Einbettung des Inhaltswegs und kein gesetztes HTML', async () => {
    fetchNachPfad({ [`GET ${WEG}`]: inhalt('application/pdf') });

    const { container } = renderVorschau(PDF);
    const rahmen = await screen.findByTitle(PDF.dateiName);

    // Gezeigt wird die Objekt-URL; der Weg mit seinen Sicherheits-Kopfzeilen wird nie eingebettet.
    expect(rahmen).toHaveAttribute('src', URL_WERT);
    expect(container.innerHTML).not.toContain('/api/angebote/9/anlagen/1/inhalt"');
  });
});

describe('AnlageVorschau — Bild (Kriterium 15)', () => {
  it('zeigt ein Bild als <img> mit dem Dateinamen als alt und passt es ein', async () => {
    fetchNachPfad({ [`GET ${WEG}`]: inhalt('image/png') });

    renderVorschau(BILD);

    const bild = await screen.findByRole('img', { name: BILD.dateiName });
    expect(bild).toHaveAttribute('src', URL_WERT);
    expect(bild).toHaveStyle({ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' });
    expect(screen.queryByTitle(BILD.dateiName)).not.toBeInTheDocument();
  });

  it('gibt dem Inhalt die Art der Vorschauart, nicht die der Antwort', async () => {
    fetchNachPfad({ [`GET ${WEG}`]: inhalt('text/html') });

    renderVorschau(BILD);
    await screen.findByRole('img', { name: BILD.dateiName });

    expect(angelegterBlob().type).toBe('image/png');
  });

  it('meldet es, wenn das Bild nicht laedt', async () => {
    fetchNachPfad({ [`GET ${WEG}`]: inhalt('image/png') });

    renderVorschau(BILD);
    const bild = await screen.findByRole('img', { name: BILD.dateiName });
    fireEvent.error(bild);

    expect(await screen.findByText(BILD_AUSFALL)).toBeInTheDocument();
    expect(screen.queryByRole('img', { name: BILD.dateiName })).not.toBeInTheDocument();
    expect(ladeVerweis()).toBeInTheDocument();
  });
});

describe('AnlageVorschau — PDF (Kriterium 15, E7)', () => {
  it('zeigt ein PDF in einem Rahmen mit dem Dateinamen als Titel', async () => {
    fetchNachPfad({ [`GET ${WEG}`]: inhalt('application/pdf') });

    renderVorschau(PDF);

    const rahmen = await screen.findByTitle(PDF.dateiName);
    expect(rahmen.tagName).toBe('IFRAME');
    expect(rahmen).toHaveAttribute('src', URL_WERT);
    expect(screen.queryByRole('img', { name: PDF.dateiName })).not.toBeInTheDocument();
  });

  it('setzt die Art des Inhalts fest auf application/pdf, auch wenn die Antwort eine andere nennt', async () => {
    fetchNachPfad({ [`GET ${WEG}`]: inhalt('text/html') });

    renderVorschau(PDF);
    await screen.findByTitle(PDF.dateiName);

    // Der Betrachter des Browsers folgt der Art der Objekt-URL; sie kommt von hier, nicht von dort.
    expect(angelegterBlob().type).toBe('application/pdf');
  });
});

describe('AnlageVorschau — die Objekt-URL', () => {
  it('gibt die Objekt-URL frei, wenn die Vorschau abgebaut wird', async () => {
    fetchNachPfad({ [`GET ${WEG}`]: inhalt('image/png') });

    const { nutzer, schliessen, unmount } = renderVorschau(BILD);
    await screen.findByRole('img', { name: BILD.dateiName });
    await nutzer.click(schliessenTaste());
    expect(schliessen).toHaveBeenCalledTimes(1);
    // So schliesst der Aufrufer: Er baut die Vorschau ab (siehe `Anlagen.tsx`).
    unmount();

    expect(objektUrl.gebeFrei).toHaveBeenCalledWith(URL_WERT);
  });

  it('legt keine Objekt-URL mehr an, wenn die Antwort nach dem Schliessen kommt', async () => {
    const fehler = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const spaet = angehalten(inhalt('image/png'));
    fetchNachPfad({ [`GET ${WEG}`]: spaet.weg });

    const { unmount } = renderVorschau(BILD);
    unmount();
    spaet.loesen();
    await new Promise((weiter) => {
      setTimeout(weiter, 10);
    });

    expect(objektUrl.erzeuge).not.toHaveBeenCalled();
    expect(objektUrl.gebeFrei).not.toHaveBeenCalled();
    expect(fehler).not.toHaveBeenCalled();
  });
});
