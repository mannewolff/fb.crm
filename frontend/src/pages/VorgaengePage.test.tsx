import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link, MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '../auth/AuthContext';
import AppShell from '../components/AppShell';
import { KopfAktionProvider } from '../components/KopfAktion';
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

const KONTO = { id: 1, displayName: 'Manfred Wolff', email: 'info@mwolff.org' };

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
      <KopfAktionProvider>
        <Routes>
          <Route path="/vorgaenge" element={<VorgaengePage />} />
          <Route path="/vorgaenge/neu" element={<p>Maske</p>} />
          <Route path="/vorgaenge/:id" element={<p>Detailansicht</p>} />
        </Routes>
        <Adresse />
      </KopfAktionProvider>
    </MemoryRouter>,
  );
}

/** Die Fensterbreite, gegen die `matchMedia` auswertet — nur der Rahmen fragt danach. */
function fensterbreite(breite: number) {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: (abfrage: string) => ({
      matches: breite >= Number.parseInt(abfrage.replace(/\D+/g, ' ').trim(), 10),
      media: abfrage,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
    }),
  });
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

describe('VorgaengePage — Kopfaktion', () => {
  it('legt „Neuer Vorgang" in den Kopf und raeumt den Platz beim Verlassen', async () => {
    const nutzer = userEvent.setup();
    fensterbreite(1440);
    fetchNachPfad({
      'GET /api/auth/me': json(200, KONTO),
      'GET /api/instance': json(200, { version: '0.1.3' }),
      [weg('', false)]: json(200, { vorgaenge: [WEBSITE], gesamt: 1 }),
    });

    renderMitTheme(
      <MemoryRouter initialEntries={['/vorgaenge']}>
        <AuthProvider>
          <AppShell>
            <Routes>
              <Route path="/vorgaenge" element={<VorgaengePage />} />
              <Route path="/woanders" element={<p>Andere Seite</p>} />
            </Routes>
            <Link to="/woanders">Weiter</Link>
          </AppShell>
        </AuthProvider>
      </MemoryRouter>,
    );

    const kopf = within(screen.getByRole('banner'));
    expect(kopf.getByRole('link', { name: 'Neuer Vorgang' })).toHaveAttribute(
      'href',
      '/vorgaenge/neu',
    );
    await screen.findByRole('table');

    await nutzer.click(screen.getByRole('link', { name: 'Weiter' }));

    expect(screen.getByText('Andere Seite')).toBeInTheDocument();
    expect(screen.getByTestId('kopf-aktion')).toBeEmptyDOMElement();
  });
});
