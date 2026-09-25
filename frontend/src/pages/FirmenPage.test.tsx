import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link, MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '../auth/AuthContext';
import AppShell from '../components/AppShell';
import { KopfAktionProvider } from '../components/KopfAktion';
import { fetchNachPfad, json, leer } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';
import FirmenPage from './FirmenPage';

/**
 * Die Tests laufen mit echter Uhr.
 *
 * Vitests Zeitdoppel waere die genauere Wahl, die Testing Library erkennt es aber nicht: Ihre
 * Weiche `jestFakeTimersAreEnabled` fragt zuerst nach einem globalen `jest`, das es hier nicht
 * gibt — `findBy`/`waitFor` warten dann auf einer angehaltenen Uhr und laufen in den Zeitrahmen.
 * Die Entprellung wird deshalb ueber `waitFor` abgewartet, und dass sie ueberhaupt greift, zeigt
 * die Zahl der Aufrufe: drei Tastendruecke, ein Aufruf.
 */

const BEISPIEL = {
  id: 7,
  name: 'Beispiel GmbH',
  ort: 'Bremen',
  aktiveAnsprechpartner: 2,
  aktiv: true,
};
const EINZEL = { id: 8, name: 'Einzel KG', ort: 'Oldenburg', aktiveAnsprechpartner: 1, aktiv: true };
const RUHEND = { id: 9, name: 'Ruhend AG', ort: null, aktiveAnsprechpartner: 0, aktiv: false };

const KONTO = { id: 1, displayName: 'Manfred Wolff', email: 'info@mwolff.org' };

/** Die Adresse, an der sich ablesen laesst, was in `useSearchParams` gelandet ist. */
function Adresse() {
  const ort = useLocation();
  return <p data-testid="adresse">{`${ort.pathname}${ort.search}`}</p>;
}

function adresse() {
  return screen.getByTestId('adresse').textContent;
}

/** Der Aufruf der Uebersicht, wie `firmenUebersicht` ihn zusammensetzt. */
function weg(suche: string, auchStillgelegte: boolean) {
  return `GET /api/firmen?suche=${suche}&auchStillgelegte=${String(auchStillgelegte)}`;
}

function suchfeld() {
  return screen.getByRole('searchbox', { name: 'Suche' });
}

function schalter() {
  return screen.getByRole('button', { name: 'auch stillgelegte' });
}

function renderSeite(start = '/firmen') {
  return renderMitTheme(
    <MemoryRouter initialEntries={[start]}>
      <KopfAktionProvider>
        <Routes>
          <Route path="/firmen" element={<FirmenPage />} />
          <Route path="/firmen/neu" element={<p>Maske</p>} />
          <Route path="/firmen/:id" element={<p>Detailansicht</p>} />
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

afterEach(() => {
  vi.restoreAllMocks();
});

describe('FirmenPage — die Liste', () => {
  it('zeigt die Zeilen in der Reihenfolge der Antwort, mit Ort und Zahl der Ansprechpartner', async () => {
    fetchNachPfad({ [weg('', false)]: json(200, { firmen: [BEISPIEL, EINZEL], gesamt: 2 }) });

    renderSeite();

    const zeilen = await screen.findAllByRole('listitem');
    expect(zeilen).toHaveLength(2);
    expect(within(zeilen[0]).getByText('Beispiel GmbH')).toBeInTheDocument();
    expect(within(zeilen[0]).getByText('Bremen')).toBeInTheDocument();
    expect(within(zeilen[0]).getByText('2 Ansprechpartner')).toBeInTheDocument();
    expect(within(zeilen[1]).getByText('Einzel KG')).toBeInTheDocument();
    expect(within(zeilen[1]).getByText('1 Ansprechpartner')).toBeInTheDocument();
  });

  it('fuehrt jede Zeile als Link auf die Detailansicht', async () => {
    fetchNachPfad({ [weg('', false)]: json(200, { firmen: [BEISPIEL], gesamt: 1 }) });

    renderSeite();

    expect(await screen.findByRole('link', { name: /Beispiel GmbH/ })).toHaveAttribute(
      'href',
      '/firmen/7',
    );
  });

  it('nennt den Stilllegungsstand als Text, nicht nur als Farbe', async () => {
    fetchNachPfad({ [weg('', true)]: json(200, { firmen: [BEISPIEL, RUHEND], gesamt: 2 }) });

    renderSeite('/firmen?auchStillgelegte=true');

    const zeilen = await screen.findAllByRole('listitem');
    expect(within(zeilen[1]).getByText('stillgelegt')).toBeInTheDocument();
    expect(within(zeilen[0]).queryByText('stillgelegt')).not.toBeInTheDocument();
    expect(within(zeilen[1]).getByText('0 Ansprechpartner')).toBeInTheDocument();
  });

  it('laesst den Ort weg, wo keiner hinterlegt ist — ohne Platzhalter', async () => {
    fetchNachPfad({ [weg('', true)]: json(200, { firmen: [RUHEND], gesamt: 1 }) });

    renderSeite('/firmen?auchStillgelegte=true');

    const zeile = await screen.findByRole('listitem');
    expect(within(zeile).getByText('Ruhend AG')).toBeInTheDocument();
    expect(within(zeile).queryByText('—')).not.toBeInTheDocument();
  });
});

describe('FirmenPage — Suche', () => {
  it('traegt die Eingabe erst nach der Entprellung in Adresse und Aufruf', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      [weg('', false)]: json(200, { firmen: [BEISPIEL], gesamt: 1 }),
      [weg('bei', false)]: json(200, { firmen: [BEISPIEL], gesamt: 1 }),
    });

    renderSeite();
    await screen.findByRole('listitem');
    await nutzer.type(suchfeld(), 'bei');

    expect(adresse()).toBe('/firmen');
    await waitFor(() => {
      expect(adresse()).toBe('/firmen?suche=bei');
    });
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen?suche=bei&auchStillgelegte=false',
      expect.objectContaining({ method: 'GET' }),
    );
    // Drei Tastendruecke, ein Aufruf — neben dem beim Aufbau. Ohne Entprellung waeren es vier.
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('nimmt den Suchtext wieder aus der Adresse, wenn das Feld geleert wird', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [weg('bei', false)]: json(200, { firmen: [BEISPIEL], gesamt: 1 }),
      [weg('', false)]: json(200, { firmen: [BEISPIEL], gesamt: 1 }),
    });

    renderSeite('/firmen?suche=bei');
    await screen.findByRole('listitem');
    await nutzer.clear(suchfeld());

    await waitFor(() => {
      expect(adresse()).toBe('/firmen');
    });
  });

  it('zeigt nur die Antwort der letzten Eingabe, auch wenn die aeltere spaeter eintrifft', async () => {
    const nutzer = userEvent.setup();
    // Kein Aufruf antwortet von selbst — die Reihenfolge der Antworten macht dieser Test.
    const offen: ((antwort: Response) => void)[] = [];
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(
      () =>
        new Promise<Response>((aufloesen) => {
          offen.push(aufloesen);
        }),
    );
    const antwort = (rumpf: unknown) =>
      new Response(JSON.stringify(rumpf), { headers: { 'Content-Type': 'application/json' } });

    renderSeite();
    await nutzer.type(suchfeld(), 'a');
    await waitFor(() => {
      expect(offen).toHaveLength(2);
    });
    await nutzer.type(suchfeld(), 'b');
    await waitFor(() => {
      expect(offen).toHaveLength(3);
    });
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/firmen?suche=a&auchStillgelegte=false',
      expect.anything(),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      3,
      '/api/firmen?suche=ab&auchStillgelegte=false',
      expect.anything(),
    );

    // Die juengere Antwort kommt zuerst, die veraltete danach — genau die Lage, in der ein
    // Aufruf ohne Abbruch die Liste zurueckdrehen wuerde.
    await act(async () => {
      offen[2](antwort({ firmen: [EINZEL], gesamt: 2 }));
      offen[1](antwort({ firmen: [BEISPIEL], gesamt: 2 }));
      await Promise.resolve();
    });

    expect(await screen.findByText('Einzel KG')).toBeInTheDocument();
    expect(screen.queryByText('Beispiel GmbH')).not.toBeInTheDocument();
  });
});

describe('FirmenPage — Schalter', () => {
  it('schaltet die stillgelegten in Adresse und Aufruf hinzu und wieder weg', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      [weg('', false)]: json(200, { firmen: [BEISPIEL], gesamt: 2 }),
      [weg('', true)]: json(200, { firmen: [BEISPIEL, RUHEND], gesamt: 2 }),
    });

    renderSeite();
    await screen.findByRole('listitem');
    expect(schalter()).toHaveAttribute('aria-pressed', 'false');

    await nutzer.click(schalter());

    expect(adresse()).toBe('/firmen?auchStillgelegte=true');
    expect(await screen.findByText('Ruhend AG')).toBeInTheDocument();
    expect(schalter()).toHaveAttribute('aria-pressed', 'true');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen?suche=&auchStillgelegte=true',
      expect.objectContaining({ method: 'GET' }),
    );

    await nutzer.click(schalter());

    expect(adresse()).toBe('/firmen');
  });

  it('stellt Suchtext und Schalter aus der Adresse wieder her', async () => {
    const fetchMock = fetchNachPfad({
      [weg('bei', true)]: json(200, { firmen: [BEISPIEL], gesamt: 3 }),
    });

    renderSeite('/firmen?suche=bei&auchStillgelegte=true');

    await screen.findByRole('listitem');
    expect(suchfeld()).toHaveValue('bei');
    expect(schalter()).toHaveAttribute('aria-pressed', 'true');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen?suche=bei&auchStillgelegte=true',
      expect.objectContaining({ method: 'GET' }),
    );
  });
});

describe('FirmenPage — leere Liste und Ausfall', () => {
  it('bietet bei noch keiner Firma den Weg zur ersten an', async () => {
    fetchNachPfad({ [weg('', false)]: json(200, { firmen: [], gesamt: 0 }) });

    renderSeite();

    expect(await screen.findByRole('status')).toHaveTextContent('Es ist noch keine Firma angelegt');
    expect(screen.getByRole('link', { name: 'Erste Firma anlegen' })).toHaveAttribute(
      'href',
      '/firmen/neu',
    );
  });

  it('sagt bei einem Suchtext ohne Treffer, dass nichts gefunden wurde', async () => {
    fetchNachPfad({ [weg('xyz', false)]: json(200, { firmen: [], gesamt: 4 }) });

    renderSeite('/firmen?suche=xyz');

    expect(await screen.findByRole('status')).toHaveTextContent('nichts gefunden');
    expect(screen.queryByRole('link', { name: 'Erste Firma anlegen' })).not.toBeInTheDocument();
  });

  it('weist ohne Suchtext auf den Schalter hin, wenn alle Firmen stillgelegt sind', async () => {
    fetchNachPfad({ [weg('', false)]: json(200, { firmen: [], gesamt: 4 }) });

    renderSeite();

    const hinweis = await screen.findByRole('status');
    expect(hinweis).toHaveTextContent('Alle Firmen sind stillgelegt');
    expect(hinweis).toHaveTextContent('auch stillgelegte');
  });

  it('zeigt beim Ausfall der Schnittstelle eine Meldung statt einer leeren Liste', async () => {
    fetchNachPfad({ [weg('', false)]: leer(500) });

    renderSeite();

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht zu erreichen');
    expect(screen.queryByRole('listitem')).not.toBeInTheDocument();
  });
});

describe('FirmenPage — Kopfaktion und Tastatur', () => {
  it('legt „Neue Firma" in den Kopf und raeumt den Platz beim Verlassen', async () => {
    const nutzer = userEvent.setup();
    fensterbreite(1440);
    fetchNachPfad({
      'GET /api/auth/me': json(200, KONTO),
      'GET /api/instance': json(200, { version: '0.1.3' }),
      [weg('', false)]: json(200, { firmen: [BEISPIEL], gesamt: 1 }),
    });

    renderMitTheme(
      <MemoryRouter initialEntries={['/firmen']}>
        <AuthProvider>
          <AppShell>
            <Routes>
              <Route path="/firmen" element={<FirmenPage />} />
              <Route path="/woanders" element={<p>Andere Seite</p>} />
            </Routes>
            <Link to="/woanders">Weiter</Link>
          </AppShell>
        </AuthProvider>
      </MemoryRouter>,
    );

    const kopf = within(screen.getByRole('banner'));
    expect(kopf.getByRole('link', { name: 'Neue Firma' })).toHaveAttribute('href', '/firmen/neu');
    await screen.findByRole('listitem');

    await nutzer.click(screen.getByRole('link', { name: 'Weiter' }));

    expect(screen.getByText('Andere Seite')).toBeInTheDocument();
    expect(screen.getByTestId('kopf-aktion')).toBeEmptyDOMElement();
  });

  it('fuehrt mit dem Tabulator ueber Suchfeld, Schalter und jede Zeile', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({ [weg('', false)]: json(200, { firmen: [BEISPIEL, EINZEL], gesamt: 2 }) });

    renderSeite();
    await screen.findAllByRole('listitem');

    await nutzer.tab();
    expect(suchfeld()).toHaveFocus();
    await nutzer.tab();
    expect(schalter()).toHaveFocus();
    await nutzer.tab();
    expect(screen.getByRole('link', { name: /Beispiel GmbH/ })).toHaveFocus();
    await nutzer.tab();
    expect(screen.getByRole('link', { name: /Einzel KG/ })).toHaveFocus();
  });
});
