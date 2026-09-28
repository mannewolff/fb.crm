import { screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import AuftragsbestandPage from './AuftragsbestandPage';
import { KopfPfadProvider } from '../components/KopfPfad';
import { fetchNachPfad, json, leer } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';

const GROSS = {
  vorgangId: 5,
  vorgangNummer: 12,
  vorgangTitel: 'Website-Relaunch',
  firma: 'Beispiel GmbH',
  auftragId: 3,
  nummer: 'AU-2026-001',
  status: 'IN_ARBEIT',
  auftragssumme: 3000.03,
  abgerechnet: 0,
  offenerRest: 3000.03,
};

const KLEIN = {
  vorgangId: 7,
  vorgangNummer: 14,
  vorgangTitel: 'Schulung',
  firma: 'Muster AG',
  auftragId: 4,
  nummer: 'AU-2026-002',
  status: 'OFFEN',
  auftragssumme: 500,
  abgerechnet: 0,
  offenerRest: 500,
};

/**
 * Die Summen der Antwort sind absichtlich **nicht** die Summe der Zeilen: So zeigt der Test, dass
 * die Kacheln die Werte des Servers tragen und nicht nachrechnen.
 */
const GEFUELLT = { zeilen: [GROSS, KLEIN], beauftragt: 9999.99, nochOffen: 1234.56 };
const LEER = { zeilen: [], beauftragt: 0, nochOffen: 0 };

function renderSeite(antwort: () => Response | Promise<Response>) {
  const fetchMock = fetchNachPfad({ 'GET /api/auftragsbestand': antwort });
  const { unmount } = renderMitTheme(
    <MemoryRouter initialEntries={['/auftragsbestand']}>
      <KopfPfadProvider>
        <AuftragsbestandPage />
      </KopfPfadProvider>
    </MemoryRouter>,
  );
  return { fetchMock, unmount };
}

/** Die Datenzeilen der Tafel in der Reihenfolge der Antwort — ohne die Kopfzeile. */
function datenzeilen(): readonly HTMLElement[] {
  return screen.getAllByRole('row').slice(1);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('AuftragsbestandPage (Kriterien 12 bis 14)', () => {
  it('traegt genau eine Ueberschrift der ersten Ebene', async () => {
    renderSeite(json(200, GEFUELLT));

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Auftragsbestand' }),
    ).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
  });

  it('zeigt ueber der Tafel zwei Kacheln „Beauftragt" und „Noch offen" mit den Werten der Antwort', async () => {
    renderSeite(json(200, GEFUELLT));
    const kacheln = await screen.findAllByTestId('kennzahl');

    expect(kacheln).toHaveLength(2);
    expect(kacheln[0]).toHaveTextContent('Beauftragt');
    expect(kacheln[0]).toHaveTextContent('9.999,99 €');
    expect(kacheln[1]).toHaveTextContent('Noch offen');
    expect(kacheln[1]).toHaveTextContent('1.234,56 €');
    // Nicht die Summe der Zeilen (3.500,03 €): Die Rundungsregel steht im Backend.
    expect(kacheln[0]).not.toHaveTextContent('3.500,03 €');
    expect(kacheln[1]).not.toHaveTextContent('3.500,03 €');
    expect(kacheln[0].compareDocumentPosition(screen.getByRole('table'))).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING,
    );
  });

  it('fuehrt je Zeile Vorgang, Firma, Nummer, Status, Auftragssumme, Abgerechnet und offenen Rest', async () => {
    renderSeite(json(200, GEFUELLT));
    await screen.findByRole('table');

    expect(screen.getAllByRole('columnheader').map((kopf) => kopf.textContent)).toEqual([
      'Vorgang',
      'Firma',
      'Auftrag',
      'Status',
      'Auftragssumme',
      'Abgerechnet',
      'Offener Rest',
    ]);
    expect(
      within(datenzeilen()[0])
        .getAllByRole('cell')
        .map((zelle) => zelle.textContent),
    ).toEqual([
      '#12 Website-Relaunch',
      'Beispiel GmbH',
      'AU-2026-001',
      'In Arbeit',
      '3.000,03 €',
      '0,00 €',
      '3.000,03 €',
    ]);
  });

  it('stellt den Status als Wort dar', async () => {
    renderSeite(json(200, GEFUELLT));
    await screen.findByRole('table');

    expect(screen.getAllByTestId('chip').map((chip) => chip.textContent)).toEqual([
      'In Arbeit',
      'Offen',
    ]);
  });

  it('legt den Weg auf die Auftragsnummer und fuehrt auf den Auftrag unter seinem Vorgang', async () => {
    renderSeite(json(200, GEFUELLT));
    await screen.findByRole('table');

    expect(screen.getByRole('link', { name: 'AU-2026-001' })).toHaveAttribute(
      'href',
      '/vorgaenge/5/auftraege/3',
    );
    expect(screen.getByRole('link', { name: 'AU-2026-002' })).toHaveAttribute(
      'href',
      '/vorgaenge/7/auftraege/4',
    );
    // Die Zeile selbst ist kein Weg: genau ein Link je Zeile, auf der Nummer.
    for (const zeile of datenzeilen()) {
      expect(within(zeile).getAllByRole('link')).toHaveLength(1);
    }
  });

  it('uebernimmt die Reihenfolge der Antwort und sortiert nicht nach', async () => {
    renderSeite(json(200, { ...GEFUELLT, zeilen: [KLEIN, GROSS] }));
    await screen.findByRole('table');

    expect(datenzeilen().map((zeile) => within(zeile).getByRole('link').textContent)).toEqual([
      'AU-2026-002',
      'AU-2026-001',
    ]);
  });

  it('zeigt den leeren Bestand mit zweimal 0,00 € und einem Hinweis statt einer leeren Tafel', async () => {
    renderSeite(json(200, LEER));

    expect(await screen.findByRole('status')).toHaveTextContent('Kein Auftrag liegt vor Ihnen');
    const kacheln = screen.getAllByTestId('kennzahl');
    expect(kacheln.map((kachel) => within(kachel).getByTestId('kennzahl-zahl').textContent)).toEqual([
      '0,00 €',
      '0,00 €',
    ]);
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('sagt, dass die Auswertung geladen wird, und zeigt noch keine Kachel', () => {
    renderSeite(() => new Promise<Response>(() => undefined));

    expect(screen.getByText('Der Auftragsbestand wird geladen …')).toBeInTheDocument();
    expect(screen.queryAllByTestId('kennzahl')).toHaveLength(0);
  });

  it('meldet einen Ausfall ohne Kacheln', async () => {
    renderSeite(leer(503));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Der Auftragsbestand ist gerade nicht zu erreichen.',
    );
    expect(screen.queryAllByTestId('kennzahl')).toHaveLength(0);
  });

  it('bricht den Aufruf ab, wenn die Ansicht verlassen wird', () => {
    const { fetchMock, unmount } = renderSeite(() => new Promise<Response>(() => undefined));
    const aufruf = fetchMock.mock.calls[0][1];

    unmount();

    expect(aufruf).toHaveProperty('signal.aborted', true);
  });

  it('uebernimmt keine Antwort mehr, die nach dem Verlassen ankommt', async () => {
    const fehler = vi.spyOn(console, 'error');
    // Das Doppel antwortet erst nach dem Verlassen — und ohne auf den Abbruch zu hoeren.
    const { unmount } = renderSeite(
      () =>
        new Promise<Response>((aufloesen) => {
          setTimeout(() => {
            aufloesen(json(200, GEFUELLT)());
          }, 0);
        }),
    );

    unmount();
    await new Promise((weiter) => {
      setTimeout(weiter, 20);
    });

    expect(fehler).not.toHaveBeenCalled();
  });
});
