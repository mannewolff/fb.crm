import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import KopfPfad, { KopfPfadProvider } from '../components/KopfPfad';
import { fetchNachPfad, json, leer } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';
import VorgaengePage from './VorgaengePage';

/**
 * Wie bei den Firmen laeuft die Entprellung gegen die echte Uhr und wird ueber `waitFor`
 * abgewartet — Vitests Zeitdoppel erkennt die Testing Library nicht (siehe `FirmenPage.test.tsx`).
 */

const WEBSITE = {
  id: 12,
  nummer: 12,
  titel: 'Neue Website',
  firma: 'Beispiel GmbH',
  phase: 'ANBAHNUNG',
  abgeschlossen: false,
  letzteAktivitaet: '2026-09-24T09:15:00Z',
};
const WARTUNG = {
  id: 13,
  nummer: 13,
  titel: 'Wartungsvertrag',
  firma: 'Einzel KG',
  phase: 'ANBAHNUNG',
  abgeschlossen: false,
  letzteAktivitaet: '2026-09-22T09:15:00Z',
};
const ERLEDIGT = {
  id: 9,
  nummer: 9,
  titel: 'Altes Angebot',
  firma: 'Ruhend AG',
  phase: 'ANBAHNUNG',
  abgeschlossen: true,
  letzteAktivitaet: '2026-08-01T09:15:00Z',
};

/** Die Adresse, an der sich ablesen laesst, was in `useSearchParams` gelandet ist. */
function Adresse() {
  const ort = useLocation();
  return <p data-testid="adresse">{`${ort.pathname}${ort.search}`}</p>;
}

function adresse() {
  return screen.getByTestId('adresse').textContent;
}

/** Der Aufruf der Uebersicht, wie `vorgaengeUebersicht` ihn zusammensetzt. */
function weg(suche: string, auchAbgeschlossene: boolean) {
  return `GET /api/vorgaenge?suche=${suche}&auchAbgeschlossene=${String(auchAbgeschlossene)}`;
}

function suchfeld() {
  return screen.getByRole('searchbox', { name: 'Suche' });
}

function schalter() {
  return screen.getByRole('button', { name: 'auch abgeschlossene' });
}

function renderSeite(start = '/vorgaenge') {
  return renderMitTheme(
    <MemoryRouter initialEntries={[start]}>
      <KopfPfadProvider>
        <Routes>
          <Route path="/vorgaenge" element={<VorgaengePage />} />
          <Route path="/vorgaenge/neu" element={<p>Maske</p>} />
          <Route path="/vorgaenge/:id" element={<p>Detailansicht</p>} />
        </Routes>
        <Adresse />
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

/** Dieselbe Ansicht, aber mit dem Kopf darueber — fuer den Pfad, den sie meldet. */
function renderMitKopf() {
  return renderMitTheme(
    <MemoryRouter initialEntries={['/vorgaenge']}>
      <KopfPfadProvider>
        <KopfPfad />
        <VorgaengePage />
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

/** Die Datenzeilen der Tafel — ohne die Kopfzeile. */
async function datenzeilen() {
  const zeilen = await screen.findAllByRole('row');
  return zeilen.slice(1);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('VorgaengePage — die Tafel', () => {
  it('zeigt die Zeilen in der Reihenfolge der Antwort mit Nummer, Titel, Firma, Phase und Tag', async () => {
    fetchNachPfad({ [weg('', false)]: json(200, { vorgaenge: [WEBSITE, WARTUNG], gesamt: 2 }) });

    renderSeite();

    const zeilen = await datenzeilen();
    expect(zeilen).toHaveLength(2);
    expect(within(zeilen[0]).getAllByRole('cell').map((zelle) => zelle.textContent)).toEqual([
      '#12',
      'Neue Website',
      'Beispiel GmbH',
      'Anbahnung',
      '24.09.2026',
    ]);
    expect(within(zeilen[1]).getAllByRole('cell').map((zelle) => zelle.textContent)).toEqual([
      '#13',
      'Wartungsvertrag',
      'Einzel KG',
      'Anbahnung',
      '22.09.2026',
    ]);
  });

  it('fuehrt jede Zeile als Link auf die Detailansicht, erreichbar mit dem Tabulator', async () => {
    fetchNachPfad({ [weg('', false)]: json(200, { vorgaenge: [WEBSITE, WARTUNG], gesamt: 2 }) });
    const nutzer = userEvent.setup();

    renderSeite();
    await datenzeilen();

    expect(screen.getByRole('link', { name: /Neue Website/ })).toHaveAttribute(
      'href',
      '/vorgaenge/12',
    );
    expect(screen.getByRole('link', { name: /Wartungsvertrag/ })).toHaveAttribute(
      'href',
      '/vorgaenge/13',
    );

    await nutzer.tab();
    expect(suchfeld()).toHaveFocus();
    await nutzer.tab();
    expect(schalter()).toHaveFocus();
    // Die Hauptaktion steht rechts im Kartenkopf und damit im Tabulatorweg vor der Tafel.
    await nutzer.tab();
    expect(screen.getByRole('link', { name: 'Neuer Vorgang' })).toHaveFocus();
    await nutzer.tab();
    expect(screen.getByRole('link', { name: /Neue Website/ })).toHaveFocus();
    await nutzer.tab();
    expect(screen.getByRole('link', { name: /Wartungsvertrag/ })).toHaveFocus();
  });

  it('nennt den Abschlussstand als Wort, nicht nur als Farbe', async () => {
    fetchNachPfad({ [weg('', true)]: json(200, { vorgaenge: [WEBSITE, ERLEDIGT], gesamt: 2 }) });

    renderSeite('/vorgaenge?auchAbgeschlossene=true');

    const zeilen = await datenzeilen();
    expect(within(zeilen[1]).getByText('abgeschlossen')).toBeInTheDocument();
    expect(within(zeilen[0]).queryByText('abgeschlossen')).not.toBeInTheDocument();
    // Der Stand steht im Namen des Links, damit der Screenreader ihn mit der Zeile vorliest.
    expect(screen.getByRole('link', { name: /Altes Angebot/ })).toHaveAccessibleName(
      /abgeschlossen/,
    );
  });
});

describe('VorgaengePage — Suche', () => {
  it('traegt die Eingabe erst nach der Entprellung in Adresse und Aufruf', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      [weg('', false)]: json(200, { vorgaenge: [WEBSITE], gesamt: 1 }),
      [weg('web', false)]: json(200, { vorgaenge: [WEBSITE], gesamt: 1 }),
    });

    renderSeite();
    await datenzeilen();
    await nutzer.type(suchfeld(), 'web');

    expect(adresse()).toBe('/vorgaenge');
    await waitFor(() => {
      expect(adresse()).toBe('/vorgaenge?suche=web');
    });
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/vorgaenge?suche=web&auchAbgeschlossene=false',
      expect.objectContaining({ method: 'GET' }),
    );
    // Drei Tastendruecke, ein Aufruf — neben dem beim Aufbau.
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('nimmt den Suchtext wieder aus der Adresse, wenn das Feld geleert wird', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [weg('web', false)]: json(200, { vorgaenge: [WEBSITE], gesamt: 1 }),
      [weg('', false)]: json(200, { vorgaenge: [WEBSITE], gesamt: 1 }),
    });

    renderSeite('/vorgaenge?suche=web');
    await datenzeilen();
    await nutzer.clear(suchfeld());

    await waitFor(() => {
      expect(adresse()).toBe('/vorgaenge');
    });
  });

  it('stellt Suchtext und Schalter aus der Adresse wieder her', async () => {
    const fetchMock = fetchNachPfad({
      [weg('web', true)]: json(200, { vorgaenge: [WEBSITE], gesamt: 3 }),
    });

    renderSeite('/vorgaenge?suche=web&auchAbgeschlossene=true');

    await datenzeilen();
    expect(suchfeld()).toHaveValue('web');
    expect(schalter()).toHaveAttribute('aria-pressed', 'true');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/vorgaenge?suche=web&auchAbgeschlossene=true',
      expect.objectContaining({ method: 'GET' }),
    );
  });
});

describe('VorgaengePage — Schalter', () => {
  it('schaltet die abgeschlossenen hinzu und wieder weg, mit Kennzeichnung am Schalter', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      [weg('', false)]: json(200, { vorgaenge: [WEBSITE], gesamt: 2 }),
      [weg('', true)]: json(200, { vorgaenge: [WEBSITE, ERLEDIGT], gesamt: 2 }),
    });

    renderSeite();
    await datenzeilen();
    expect(schalter()).toHaveAttribute('aria-pressed', 'false');

    await nutzer.click(schalter());

    expect(adresse()).toBe('/vorgaenge?auchAbgeschlossene=true');
    expect(await screen.findByText('Altes Angebot')).toBeInTheDocument();
    expect(schalter()).toHaveAttribute('aria-pressed', 'true');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/vorgaenge?suche=&auchAbgeschlossene=true',
      expect.objectContaining({ method: 'GET' }),
    );

    await nutzer.click(schalter());

    expect(adresse()).toBe('/vorgaenge');
  });
});

describe('VorgaengePage — die drei Leerfaelle und der Ausfall', () => {
  it('bietet beim noch leeren Bestand den Weg zum ersten Vorgang an', async () => {
    fetchNachPfad({ [weg('', false)]: json(200, { vorgaenge: [], gesamt: 0 }) });

    renderSeite();

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Es ist noch kein Vorgang angelegt',
    );
    expect(screen.getByRole('link', { name: 'Ersten Vorgang anlegen' })).toHaveAttribute(
      'href',
      '/vorgaenge/neu',
    );
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('sagt bei einem Suchtext ohne Treffer, dass nichts gefunden wurde', async () => {
    fetchNachPfad({ [weg('xyz', false)]: json(200, { vorgaenge: [], gesamt: 4 }) });

    renderSeite('/vorgaenge?suche=xyz');

    expect(await screen.findByRole('status')).toHaveTextContent('nichts gefunden');
    expect(screen.queryByRole('link', { name: 'Ersten Vorgang anlegen' })).not.toBeInTheDocument();
  });

  it('weist ohne Suchtext auf den Schalter hin, wenn alle Vorgaenge abgeschlossen sind', async () => {
    fetchNachPfad({ [weg('', false)]: json(200, { vorgaenge: [], gesamt: 4 }) });

    renderSeite();

    const hinweis = await screen.findByRole('status');
    expect(hinweis).toHaveTextContent('Alle Vorgänge sind abgeschlossen');
    expect(hinweis).toHaveTextContent('auch abgeschlossene');
  });

  it('zeigt beim Ausfall der Schnittstelle eine Meldung statt einer leeren Tafel', async () => {
    fetchNachPfad({ [weg('', false)]: leer(500) });

    renderSeite();

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht zu erreichen');
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('zeigt bis zur Antwort einen Ladehinweis', () => {
    fetchNachPfad({ [weg('', false)]: json(200, { vorgaenge: [WEBSITE], gesamt: 1 }) });

    renderSeite();

    expect(screen.getByText('Vorgänge werden geladen …')).toBeInTheDocument();
  });
});

describe('VorgaengePage — der Pfad im Kopf (E6)', () => {
  it('meldet „Vorgänge" als Pfad an den Kopf', async () => {
    fetchNachPfad({ [weg('', false)]: json(200, { vorgaenge: [], gesamt: 0 }) });

    renderMitKopf();

    const pfad = await screen.findByRole('navigation', { name: 'Pfad' });
    expect(within(pfad).getByText('Vorgänge')).toHaveAttribute('aria-current', 'page');
    expect(within(pfad).queryAllByRole('link')).toHaveLength(0);
  });
});

describe('VorgaengePage — die Hauptaktion im Kartenkopf', () => {
  it('stellt „Neuer Vorgang" rechts in den Kopf der Karte und fuehrt auf die Maske', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({ [weg('', false)]: json(200, { vorgaenge: [WEBSITE], gesamt: 1 }) });

    renderSeite();
    await datenzeilen();

    // Die Hauptaktion einer Liste steht im Kartenkopf, nicht mehr im Kopf der Anwendung
    // (CLAUDE-design.md, „Tasten"); der Kopf traegt nur noch den Pfad.
    const kopf = within(screen.getByTestId('karte-kopf'));
    expect(kopf.getByRole('heading', { level: 2, name: 'Vorgänge' })).toBeInTheDocument();
    const taste = kopf.getByRole('link', { name: 'Neuer Vorgang' });
    expect(taste).toHaveAttribute('href', '/vorgaenge/neu');

    await nutzer.click(taste);

    expect(screen.getByText('Maske')).toBeInTheDocument();
    expect(adresse()).toBe('/vorgaenge/neu');
  });

  it('haelt Suchfeld und Schalter im selben Kartenkopf', async () => {
    fetchNachPfad({ [weg('', false)]: json(200, { vorgaenge: [WEBSITE], gesamt: 1 }) });

    renderSeite();
    await datenzeilen();

    const kopf = within(screen.getByTestId('karte-kopf'));
    expect(kopf.getByRole('searchbox', { name: 'Suche' })).toBeInTheDocument();
    expect(kopf.getByRole('button', { name: 'auch abgeschlossene' })).toBeInTheDocument();
  });
});
