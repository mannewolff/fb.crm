import { screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import PipelinePage from './PipelinePage';
import { KopfPfadProvider } from '../components/KopfPfad';
import { fetchNachPfad, json, leer } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';

const ZEILE = {
  angebotId: 9,
  nummer: 'A-2026-001',
  vorgangId: 12,
  vorgangNummer: 2026001,
  vorgangTitel: 'Neue Website',
  firma: 'Beispiel GmbH',
  summe: 2500,
  wahrscheinlichkeit: 60,
  gewichteteSumme: 1500,
  entscheidungErwartetAm: '2026-10-15',
};

/** Eine zweite Zeile mit Einschaetzung — die Auswertung zeigt mehr als einen Fall. */
const ZWEITE = {
  ...ZEILE,
  angebotId: 10,
  nummer: 'A-2026-002',
  vorgangId: 13,
  vorgangNummer: 2026002,
  vorgangTitel: 'Wartungsvertrag',
  firma: 'Muster AG',
  summe: 1000,
  wahrscheinlichkeit: 30,
  gewichteteSumme: 300,
  entscheidungErwartetAm: '2026-11-02',
};

/** Ein Vorgang ohne Einschaetzung: `wahrscheinlichkeit == null` **ist** die Kennzeichnung (F2). */
const OHNE_EINSCHAETZUNG = {
  ...ZEILE,
  angebotId: 11,
  nummer: 'A-2026-003',
  vorgangId: 14,
  vorgangNummer: 2026003,
  vorgangTitel: 'Schulungstag',
  firma: 'Dritte KG',
  summe: 800,
  wahrscheinlichkeit: null,
  gewichteteSumme: 0,
  entscheidungErwartetAm: null,
};

const GEFUELLT = {
  zeilen: [ZEILE, ZWEITE, OHNE_EINSCHAETZUNG],
  summe: 4300,
  gewichteteSumme: 1800,
};

const LEER = { zeilen: [], summe: 0, gewichteteSumme: 0 };

function renderSeite(antwort: () => Response) {
  const fetchMock = fetchNachPfad({ 'GET /api/pipeline': antwort });
  renderMitTheme(
    <MemoryRouter initialEntries={['/pipeline']}>
      <KopfPfadProvider>
        <PipelinePage />
      </KopfPfadProvider>
    </MemoryRouter>,
  );
  return fetchMock;
}

/**
 * Die Datenzeilen der Tafel in der Reihenfolge der Antwort — ohne die Kopfzeile.
 *
 * Gesucht wird nach Position und nicht nach Inhalt: Die Reihenfolge ist die der Auswertung, und
 * eine Suche nach dem Titel liesse genau das ungeprueft.
 */
function datenzeilen(): readonly HTMLElement[] {
  return screen.getAllByRole('row').slice(1);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('PipelinePage', () => {
  it('traegt genau eine Ueberschrift der ersten Ebene', async () => {
    renderSeite(json(200, GEFUELLT));

    expect(await screen.findByRole('heading', { level: 1, name: 'Pipeline' })).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
  });

  it('zeigt ueber der Tabelle zwei Kacheln: ungewichtete und gewichtete Summe', async () => {
    renderSeite(json(200, GEFUELLT));
    const kacheln = await screen.findAllByTestId('kennzahl');

    expect(kacheln).toHaveLength(2);
    expect(kacheln[0]).toHaveTextContent('Pipeline netto');
    expect(kacheln[0]).toHaveTextContent('4.300,00 €');
    expect(kacheln[1]).toHaveTextContent('Gewichtete Pipeline');
    expect(kacheln[1]).toHaveTextContent('1.800,00 €');
    // Die Kacheln stehen ueber der Tafel (Vorlage `.zahlen` Z. 152–154 ueber `.raster`).
    expect(kacheln[0].compareDocumentPosition(screen.getByRole('table'))).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING,
    );
  });

  it('fuehrt je Zeile Vorgang, Firma, Summe, Wahrscheinlichkeit, gewichtete Summe und Entscheidung', async () => {
    renderSeite(json(200, GEFUELLT));
    await screen.findByRole('table');

    expect(
      screen.getAllByRole('columnheader').map((kopf) => kopf.textContent),
    ).toEqual([
      'Vorgang',
      'Firma',
      'Summe',
      'Wahrscheinlichkeit',
      'Gewichtete Summe',
      'Entscheidung erwartet',
    ]);
    expect(
      within(datenzeilen()[0])
        .getAllByRole('cell')
        .map((zelle) => zelle.textContent),
    ).toEqual([
      '#2026001 Neue Website',
      'Beispiel GmbH',
      '2.500,00 €',
      '60 %',
      '1.500,00 €',
      '15.10.2026',
    ]);
  });

  it('zeigt bei einem Vorgang ohne Einschaetzung „nicht eingeschätzt" als Wort', async () => {
    renderSeite(json(200, GEFUELLT));
    await screen.findByRole('table');

    // Kriterium 23: die Kennzeichnung steht in der Zeile — als Wort, nicht als Farbe
    // (CLAUDE-react.md, Accessibility).
    // Die dritte Zeile der Antwort ist die ohne Einschaetzung.
    const zeile = within(datenzeilen()[2]);
    expect(zeile.getByText('nicht eingeschätzt')).toBeInTheDocument();
    expect(zeile.getAllByRole('cell')[4]).toHaveTextContent('0,00 €');
    expect(zeile.getAllByRole('cell')[5]).toHaveTextContent('nicht angegeben');
  });

  it('fuehrt aus jeder Zeile zum Vorgang', async () => {
    renderSeite(json(200, GEFUELLT));
    await screen.findByRole('table');

    expect(
      within(screen.getByRole('table'))
        .getAllByRole('link')
        .map((link) => link.getAttribute('href')),
    ).toEqual(['/vorgaenge/12', '/vorgaenge/13', '/vorgaenge/14']);
  });

  it('zeigt die leere Pipeline mit zwei Nullsummen und einem Hinweis statt einer leeren Tabelle', async () => {
    renderSeite(json(200, LEER));

    expect(await screen.findByRole('status')).toHaveTextContent(/Es ist kein Angebot offen/);
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    const kacheln = screen.getAllByTestId('kennzahl');
    expect(kacheln).toHaveLength(2);
    expect(kacheln[0]).toHaveTextContent('0,00 €');
    expect(kacheln[1]).toHaveTextContent('0,00 €');
  });

  it('zeigt beim Laden einen Hinweis und noch keine Kacheln', () => {
    renderSeite(json(200, GEFUELLT));

    expect(screen.getByText(/Pipeline wird geladen/)).toBeInTheDocument();
    expect(screen.queryAllByTestId('kennzahl')).toHaveLength(0);
  });

  it('meldet den Ausfall der Schnittstelle ohne technische Einzelheiten', async () => {
    renderSeite(leer(503));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Die Pipeline ist gerade nicht zu erreichen. Bitte später erneut versuchen.',
    );
    expect(screen.queryAllByTestId('kennzahl')).toHaveLength(0);
  });
});
