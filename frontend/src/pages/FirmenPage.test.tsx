import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import KopfPfad, { KopfPfadProvider } from '../components/KopfPfad';
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

/** Die Datenzeilen der Tafel — ohne die Kopfzeile mit den Spaltennamen. */
function zeilen() {
  return screen.getAllByRole('row').slice(1);
}

function renderSeite(start = '/firmen') {
  return renderMitTheme(
    <MemoryRouter initialEntries={[start]}>
      <KopfPfadProvider>
        <Routes>
          <Route path="/firmen" element={<FirmenPage />} />
          <Route path="/firmen/neu" element={<p>Maske</p>} />
          <Route path="/firmen/:id" element={<p>Detailansicht</p>} />
        </Routes>
        <Adresse />
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

/** Dieselbe Ansicht, aber mit dem Kopf darueber — fuer den Pfad, den sie meldet. */
function renderMitKopf() {
  return renderMitTheme(
    <MemoryRouter initialEntries={['/firmen']}>
      <KopfPfadProvider>
        <KopfPfad />
        <FirmenPage />
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('FirmenPage — die Tafel', () => {
  it('zeigt die Zeilen in der Reihenfolge der Antwort, mit Ort und Zahl der Ansprechpartner', async () => {
    fetchNachPfad({ [weg('', false)]: json(200, { firmen: [BEISPIEL, EINZEL], gesamt: 2 }) });

    renderSeite();

    await screen.findByRole('table', { name: 'Firmen' });
    const reihen = zeilen();
    expect(reihen).toHaveLength(2);
    expect(within(reihen[0]).getByText('Beispiel GmbH')).toBeInTheDocument();
    expect(within(reihen[0]).getByText('Bremen')).toBeInTheDocument();
    expect(within(reihen[0]).getByText('2')).toBeInTheDocument();
    expect(within(reihen[1]).getByText('Einzel KG')).toBeInTheDocument();
    expect(within(reihen[1]).getByText('1')).toBeInTheDocument();
  });

  it('nennt jede Spalte in der Kopfzeile', async () => {
    fetchNachPfad({ [weg('', false)]: json(200, { firmen: [BEISPIEL], gesamt: 1 }) });

    renderSeite();

    await screen.findByRole('table', { name: 'Firmen' });
    expect(
      screen.getAllByRole('columnheader').map((spalte) => spalte.textContent),
    ).toEqual(['Firma', 'Ort', 'Ansprechpartner']);
  });

  it('fuehrt jede Zeile als Link auf die Detailansicht', async () => {
    fetchNachPfad({ [weg('', false)]: json(200, { firmen: [BEISPIEL], gesamt: 1 }) });

    renderSeite();

    expect(await screen.findByRole('link', { name: 'Beispiel GmbH' })).toHaveAttribute(
      'href',
      '/firmen/7',
    );
  });

  it('nennt den Stilllegungsstand als Chip mit Wort, nicht nur als Farbe', async () => {
    fetchNachPfad({ [weg('', true)]: json(200, { firmen: [BEISPIEL, RUHEND], gesamt: 2 }) });

    renderSeite('/firmen?auchStillgelegte=true');

    await screen.findByRole('table', { name: 'Firmen' });
    const reihen = zeilen();
    expect(within(reihen[1]).getByText('Stillgelegt')).toBeInTheDocument();
    expect(within(reihen[0]).queryByTestId('chip')).not.toBeInTheDocument();
    expect(within(reihen[1]).getByText('0')).toBeInTheDocument();
  });

  it('laesst den Ort weg, wo keiner hinterlegt ist — ohne Platzhalter', async () => {
    fetchNachPfad({ [weg('', true)]: json(200, { firmen: [RUHEND], gesamt: 1 }) });

    renderSeite('/firmen?auchStillgelegte=true');

    await screen.findByRole('table', { name: 'Firmen' });
    const reihe = zeilen()[0];
    expect(within(reihe).getByText('Ruhend AG')).toBeInTheDocument();
    expect(within(reihe).queryByText('—')).not.toBeInTheDocument();
  });
});

describe('FirmenPage — die Hauptaktion im Kartenkopf', () => {
  it('traegt die Kupfertaste „Neue Firma" im Kopf der Karte, nicht im Kopf des Rahmens', () => {
    fetchNachPfad({ [weg('', false)]: json(200, { firmen: [BEISPIEL], gesamt: 1 }) });

    renderSeite();

    const kopf = within(screen.getByTestId('karte-kopf'));
    expect(kopf.getByRole('heading', { name: 'Firmen' })).toBeInTheDocument();
    expect(kopf.getByRole('link', { name: 'Neue Firma' })).toHaveAttribute('href', '/firmen/neu');
    // Filter und Schalter stehen links daneben, in derselben Leiste (Plan E5).
    expect(kopf.getByRole('searchbox', { name: 'Suche' })).toBeInTheDocument();
    expect(kopf.getByRole('button', { name: 'auch stillgelegte' })).toBeInTheDocument();
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
    await screen.findByRole('table', { name: 'Firmen' });
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
    await screen.findByRole('table', { name: 'Firmen' });
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
    await screen.findByRole('table', { name: 'Firmen' });
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

    await screen.findByRole('table', { name: 'Firmen' });
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
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
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
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('sagt es, solange die Firmen noch geladen werden', () => {
    fetchNachPfad({ [weg('', false)]: () => new Promise<Response>(() => {}) });

    renderSeite();

    expect(screen.getByText('Firmen werden geladen …')).toBeInTheDocument();
  });
});

describe('FirmenPage — Pfad und Tastatur', () => {
  it('meldet „Firmen" als Pfad an den Kopf (E6)', async () => {
    fetchNachPfad({ [weg('', false)]: json(200, { firmen: [], gesamt: 0 }) });

    renderMitKopf();

    const pfad = await screen.findByRole('navigation', { name: 'Pfad' });
    expect(within(pfad).getByText('Firmen')).toHaveAttribute('aria-current', 'page');
    expect(within(pfad).queryAllByRole('link')).toHaveLength(0);
  });

  it('fuehrt mit dem Tabulator ueber Suchfeld, Schalter, Hauptaktion und jede Zeile', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({ [weg('', false)]: json(200, { firmen: [BEISPIEL, EINZEL], gesamt: 2 }) });

    renderSeite();
    await screen.findByRole('table', { name: 'Firmen' });

    const reihe = [
      suchfeld(),
      schalter(),
      screen.getByRole('link', { name: 'Neue Firma' }),
      screen.getByRole('link', { name: 'Beispiel GmbH' }),
      screen.getByRole('link', { name: 'Einzel KG' }),
    ];
    for (const element of reihe) {
      await nutzer.tab();
      expect(element).toHaveFocus();
    }
  });
});
