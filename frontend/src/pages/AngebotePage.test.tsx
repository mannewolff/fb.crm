import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import AngebotePage from './AngebotePage';
import { KopfPfadProvider } from '../components/KopfPfad';
import { fetchNachPfad, json, leer } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';

const JUENGER = {
  id: 12,
  firmaId: 5,
  firmaName: 'Adler AG',
  angebotDatum: '2026-09-26',
  status: 'BESTELLT',
  intern: false,
  summe: 1200,
};

const AELTER = {
  id: 9,
  firmaId: 6,
  firmaName: 'Biber GmbH',
  angebotDatum: '2026-09-24',
  status: 'ANGELEGT',
  intern: false,
  summe: 2500.03,
};

const INTERN = {
  id: 14,
  firmaId: 7,
  firmaName: 'Caesar KG',
  angebotDatum: '2026-09-28',
  status: 'LAEUFT',
  intern: true,
  summe: 0,
};

/** Die Adresse samt Suchteil — dort steht der Filter. */
function Adresse() {
  const ort = useLocation();
  return <p data-testid="adresse">{`${ort.pathname}${ort.search}`}</p>;
}

function renderSeite(start = '/angebote') {
  return renderMitTheme(
    <MemoryRouter initialEntries={[start]}>
      <KopfPfadProvider>
        <Routes>
          <Route path="/angebote" element={<AngebotePage />} />
        </Routes>
        <Adresse />
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('AngebotePage — die Uebersicht (Issue #127, Kriterium 8)', () => {
  it('traegt „Angebote" als die eine Ueberschrift', async () => {
    fetchNachPfad({ 'GET /api/angebote': json(200, { angebote: [JUENGER] }) });

    renderSeite();

    expect(await screen.findByRole('heading', { level: 1, name: 'Angebote' })).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
  });

  it('zeigt die Tafel mit Datum, Firma, Status und Summe in der Reihenfolge der Antwort', async () => {
    fetchNachPfad({ 'GET /api/angebote': json(200, { angebote: [JUENGER, AELTER] }) });

    renderSeite();

    const tafel = within(await screen.findByRole('table', { name: 'Angebote' }));
    expect(tafel.getAllByRole('columnheader').map((kopf) => kopf.textContent)).toEqual([
      'Datum',
      'Firma',
      'Status',
      'Summe',
    ]);
    const zeilen = tafel.getAllByRole('row').slice(1);
    expect(zeilen.map((zeile) => within(zeile).getAllByRole('link')[0].textContent)).toEqual([
      '26.09.2026',
      '24.09.2026',
    ]);
    const erste = within(zeilen[0]);
    expect(erste.getByRole('link', { name: 'Adler AG' })).toHaveAttribute('href', '/firmen/5');
    expect(erste.getByText('Bestellt')).toBeInTheDocument();
    expect(erste.getByText('1.200,00 €')).toBeInTheDocument();
  });

  it('stellt den Kopf einer Zahlenspalte rechtsbuendig und die uebrigen links (Issue #286)', async () => {
    fetchNachPfad({ 'GET /api/angebote': json(200, { angebote: [JUENGER] }) });

    renderSeite();

    const tafel = within(await screen.findByRole('table', { name: 'Angebote' }));
    expect(tafel.getByRole('columnheader', { name: 'Summe' })).toHaveStyle({ textAlign: 'right' });
    for (const name of ['Datum', 'Firma', 'Status']) {
      expect(tafel.getByRole('columnheader', { name })).toHaveStyle({ textAlign: 'left' });
    }
  });

  it('fuehrt ueber das Datum auf das Angebot', async () => {
    fetchNachPfad({ 'GET /api/angebote': json(200, { angebote: [JUENGER, AELTER] }) });

    renderSeite();

    expect(await screen.findByRole('link', { name: '26.09.2026' })).toHaveAttribute(
      'href',
      '/angebote/12',
    );
    expect(screen.getByRole('link', { name: '24.09.2026' })).toHaveAttribute(
      'href',
      '/angebote/9',
    );
  });

  it('bietet im Filter „alle" und die sieben Status an', async () => {
    fetchNachPfad({ 'GET /api/angebote': json(200, { angebote: [] }) });

    renderSeite();

    const filter = await screen.findByRole('combobox', { name: 'Status' });
    expect(filter).toHaveValue('');
    expect(within(filter).getAllByRole('option').map((eintrag) => eintrag.textContent)).toEqual([
      'alle',
      'Angelegt',
      'Abgegeben',
      'Bestellt',
      'Erledigt',
      'Abgerechnet',
      'Läuft',
      'Abgeschlossen',
    ]);
  });

  it('ruft mit dem Filter „bestellt" nur die bestellten ab und haelt ihn in der Adresse', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/angebote': json(200, { angebote: [JUENGER, AELTER] }),
      'GET /api/angebote?status=BESTELLT': json(200, { angebote: [JUENGER] }),
    });

    renderSeite();
    await screen.findByRole('link', { name: '24.09.2026' });
    await nutzer.selectOptions(screen.getByRole('combobox', { name: 'Status' }), 'BESTELLT');

    expect(await screen.findByTestId('adresse')).toHaveTextContent('/angebote?status=BESTELLT');
    await vi.waitFor(() => {
      expect(screen.queryByRole('link', { name: '24.09.2026' })).not.toBeInTheDocument();
    });
    expect(screen.getByRole('link', { name: '26.09.2026' })).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote?status=BESTELLT',
      expect.objectContaining({ method: 'GET' }),
    );
  });

  it('nimmt den Filter aus der Adresse und kehrt mit „alle" zur ganzen Liste zurueck', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'GET /api/angebote?status=BESTELLT': json(200, { angebote: [JUENGER] }),
      'GET /api/angebote': json(200, { angebote: [JUENGER, AELTER] }),
    });

    renderSeite('/angebote?status=BESTELLT');

    expect(await screen.findByRole('combobox', { name: 'Status' })).toHaveValue('BESTELLT');
    await screen.findByRole('link', { name: '26.09.2026' });
    await nutzer.selectOptions(screen.getByRole('combobox', { name: 'Status' }), '');

    expect(await screen.findByRole('link', { name: '24.09.2026' })).toBeInTheDocument();
    expect(screen.getByTestId('adresse')).toHaveTextContent(/^\/angebote$/);
  });

  it('liest einen unbekannten Status in der Adresse als „alle"', async () => {
    const fetchMock = fetchNachPfad({ 'GET /api/angebote': json(200, { angebote: [JUENGER] }) });

    renderSeite('/angebote?status=VERHANDELT');

    expect(await screen.findByRole('link', { name: '26.09.2026' })).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/angebote', expect.anything());
  });

  it('verwirft eine veraltete Antwort, wenn der Filter inzwischen gewechselt hat', async () => {
    const nutzer = userEvent.setup();
    let liefereAlle!: (antwort: Response) => void;
    const alleSpaeter = new Promise<Response>((aufloesen) => {
      liefereAlle = aufloesen;
    });
    fetchNachPfad({
      'GET /api/angebote': () => alleSpaeter,
      'GET /api/angebote?status=BESTELLT': json(200, { angebote: [JUENGER] }),
    });

    renderSeite();
    await nutzer.selectOptions(await screen.findByRole('combobox', { name: 'Status' }), 'BESTELLT');
    await screen.findByRole('link', { name: '26.09.2026' });
    liefereAlle(json(200, { angebote: [JUENGER, AELTER] })());

    // Die spaete Antwort ohne Filter darf die gefilterte Liste nicht ueberschreiben.
    await new Promise((weiter) => setTimeout(weiter, 20));
    expect(screen.queryByRole('link', { name: '24.09.2026' })).not.toBeInTheDocument();
  });
});

describe('AngebotePage — leer, Ausfall, Laden', () => {
  it('sagt ohne jedes Angebot einen Satz statt einer leeren Tafel', async () => {
    fetchNachPfad({ 'GET /api/angebote': json(200, { angebote: [] }) });

    renderSeite();

    expect(await screen.findByRole('status')).toHaveTextContent('noch kein Angebot');
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('sagt es eigens, wenn im gewaehlten Status keines steht', async () => {
    fetchNachPfad({ 'GET /api/angebote?status=ERLEDIGT': json(200, { angebote: [] }) });

    renderSeite('/angebote?status=ERLEDIGT');

    expect(await screen.findByRole('status')).toHaveTextContent(
      'In diesem Status gibt es kein Angebot.',
    );
  });

  it('meldet den Ausfall der Schnittstelle', async () => {
    fetchNachPfad({ 'GET /api/angebote': leer(503) });

    renderSeite();

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht zu erreichen');
  });

  it('zeigt waehrend des Ladens einen Hinweis', () => {
    fetchNachPfad({ 'GET /api/angebote': json(200, { angebote: [] }) });

    renderSeite();

    expect(screen.getByText('Angebote werden geladen …')).toBeInTheDocument();
  });
});

describe('AngebotePage — Kennzeichen intern und Strich in der Summe (Issue #233)', () => {
  it('zeigt am internen Angebot den Chip „Intern" und in der Spalte „Summe" den Strich', async () => {
    fetchNachPfad({ 'GET /api/angebote': json(200, { angebote: [INTERN] }) });

    renderSeite();

    const tafel = within(await screen.findByRole('table', { name: 'Angebote' }));
    const zeile = within(tafel.getAllByRole('row')[1]);
    expect(zeile.getByText('Intern')).toBeInTheDocument();
    expect(zeile.getByText('\u2013')).toBeInTheDocument();
    expect(zeile.queryByText(/€/)).not.toBeInTheDocument();
  });

  it('zeigt am externen Angebot keinen Chip „Intern", sondern seinen Betrag', async () => {
    fetchNachPfad({ 'GET /api/angebote': json(200, { angebote: [JUENGER] }) });

    renderSeite();

    const tafel = within(await screen.findByRole('table', { name: 'Angebote' }));
    const zeile = within(tafel.getAllByRole('row')[1]);
    expect(zeile.queryByText('Intern')).not.toBeInTheDocument();
    expect(zeile.getByText('1.200,00 €')).toBeInTheDocument();
  });

  it('zeigt den Strich auch bei gespeicherter Summe ueber 0 (E16)', async () => {
    // Menge und Preis bleiben beim Wechsel extern -> intern erhalten; der Strich haengt am
    // Kennzeichen, nicht an einer 0 vom Server.
    fetchNachPfad({ 'GET /api/angebote': json(200, { angebote: [{ ...INTERN, summe: 4500 }] }) });

    renderSeite();

    const tafel = within(await screen.findByRole('table', { name: 'Angebote' }));
    const zeile = within(tafel.getAllByRole('row')[1]);
    expect(zeile.getByText('\u2013')).toBeInTheDocument();
    expect(zeile.queryByText('4.500,00 €')).not.toBeInTheDocument();
  });

  it('ruft mit dem Filter „Laeuft" nur die laufenden ab und haelt ihn in der Adresse', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/angebote': json(200, { angebote: [JUENGER, INTERN] }),
      'GET /api/angebote?status=LAEUFT': json(200, { angebote: [INTERN] }),
    });

    renderSeite();
    await screen.findByRole('link', { name: '26.09.2026' });
    await nutzer.selectOptions(screen.getByRole('combobox', { name: 'Status' }), 'LAEUFT');

    expect(await screen.findByTestId('adresse')).toHaveTextContent('/angebote?status=LAEUFT');
    await vi.waitFor(() => {
      expect(screen.queryByRole('link', { name: '26.09.2026' })).not.toBeInTheDocument();
    });
    expect(screen.getByRole('link', { name: '28.09.2026' })).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote?status=LAEUFT',
      expect.objectContaining({ method: 'GET' }),
    );
  });
});
