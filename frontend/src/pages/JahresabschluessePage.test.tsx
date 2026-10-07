import { screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import JahresabschluessePage from './JahresabschluessePage';
import { KopfPfadProvider } from '../components/KopfPfad';
import { fetchNachPfad, json, problem } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';

const WEG = 'GET /api/jahresabschluesse';

/** Das laufende Jahr, wie das Backend es schreibt — ohne abgegebenes Angebot. */
const LAUFEND = { jahr: '2026', laeuftNoch: true, netto: 12500.5, anzahl: 4, annahmequote: null };

/** Ein vergangenes Jahr mit Annahmequote. */
const VERGANGEN = { jahr: '2025', laeuftNoch: false, netto: 40000, anzahl: 12, annahmequote: 66.7 };

function renderSeite() {
  return renderMitTheme(
    <MemoryRouter initialEntries={['/jahresabschluesse']}>
      <KopfPfadProvider>
        <Routes>
          <Route path="/jahresabschluesse" element={<JahresabschluessePage />} />
        </Routes>
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

/** Die Datenzeilen der Tafel, ohne die Kopfzeile. */
async function datenzeilen(): Promise<HTMLElement[]> {
  const tafel = within(await screen.findByRole('table', { name: 'Jahresabschlüsse' }));
  return tafel.getAllByRole('row').slice(1);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('JahresabschluessePage — die Uebersicht der Jahre (#287, Kriterien 1 bis 3)', () => {
  it('zeigt waehrend des Ladens einen Hinweis und danach „Jahresabschlüsse" als die eine Ueberschrift', async () => {
    fetchNachPfad({ [WEG]: json(200, [VERGANGEN]) });

    renderSeite();

    expect(screen.getByText('Jahresabschlüsse werden geladen …')).toBeInTheDocument();
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Jahresabschlüsse' }),
    ).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
  });

  it('traegt die vier Spalten und stellt den Kopf der drei Zahlenspalten rechts', async () => {
    fetchNachPfad({ [WEG]: json(200, [VERGANGEN]) });

    renderSeite();

    const tafel = within(await screen.findByRole('table', { name: 'Jahresabschlüsse' }));
    expect(tafel.getAllByRole('columnheader').map((kopf) => kopf.textContent)).toEqual([
      'Jahr',
      'Einnahmen netto',
      'Rechnungen',
      'Annahmequote',
    ]);
    expect(tafel.getByRole('columnheader', { name: 'Jahr' })).toHaveStyle({ textAlign: 'left' });
    for (const name of ['Einnahmen netto', 'Rechnungen', 'Annahmequote']) {
      expect(tafel.getByRole('columnheader', { name })).toHaveStyle({ textAlign: 'right' });
    }
  });

  it('stellt zwei Jahre juengstes zuerst und legt den Weg auf das Jahr, nicht auf die Zeile', async () => {
    fetchNachPfad({ [WEG]: json(200, [LAUFEND, VERGANGEN]) });

    renderSeite();

    const zeilen = await datenzeilen();
    expect(zeilen).toHaveLength(2);
    expect(
      zeilen.map((zeile) => within(zeile).getByRole('link').getAttribute('href')),
    ).toEqual(['/jahresabschluesse/2026', '/jahresabschluesse/2025']);
    // Genau ein Weg je Zeile, und er traegt das Jahr als Namen — nicht die Zahlen der Zeile.
    expect(within(zeilen[1]).getAllByRole('link')).toHaveLength(1);
    expect(within(zeilen[1]).getByRole('link')).toHaveAccessibleName('2025');
  });

  it('setzt Einnahmen netto in Euro, die Zahl der Rechnungen und die Annahmequote in Prozent', async () => {
    fetchNachPfad({ [WEG]: json(200, [VERGANGEN]) });

    renderSeite();

    const zellen = within((await datenzeilen())[0]).getAllByRole('cell');
    expect(zellen.map((zelle) => zelle.textContent)).toEqual([
      '2025',
      '40.000,00 €',
      '12',
      '66,7 %',
    ]);
  });

  it('kennzeichnet das laufende Jahr mit „läuft noch", das vergangene nicht', async () => {
    fetchNachPfad({ [WEG]: json(200, [LAUFEND, VERGANGEN]) });

    renderSeite();

    const [laufend, vergangen] = await datenzeilen();
    expect(within(laufend).getByText('läuft noch')).toBeInTheDocument();
    expect(within(vergangen).queryByText('läuft noch')).not.toBeInTheDocument();
  });

  it('zeigt eine fehlende Annahmequote als „—" mit dem Grund (Kriterium 11)', async () => {
    fetchNachPfad({ [WEG]: json(200, [LAUFEND]) });

    renderSeite();

    const quote = within((await datenzeilen())[0]).getAllByRole('cell')[3];
    expect(quote).toHaveTextContent('—');
    expect(within(quote).getByText('keine abgegebenen Angebote')).toBeInTheDocument();
    expect(quote).not.toHaveTextContent('%');
  });

  it('zeigt eine Quote 0 bei abgegebenen Angeboten als „0,0 %" und ohne Strich', async () => {
    fetchNachPfad({ [WEG]: json(200, [{ ...VERGANGEN, annahmequote: 0 }]) });

    renderSeite();

    const quote = within((await datenzeilen())[0]).getAllByRole('cell')[3];
    expect(quote).toHaveTextContent('0,0 %');
    expect(quote).not.toHaveTextContent('—');
    expect(quote).not.toHaveTextContent('keine abgegebenen Angebote');
  });

  it('sagt im leeren Zustand, wann hier ein Jahr erscheint, und zeigt keine Tafel (Kriterium 2)', async () => {
    fetchNachPfad({ [WEG]: json(200, []) });

    renderSeite();

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Hier erscheint ein Jahr, sobald es eine gestellte Rechnung oder ein abgegebenes Angebot hat.',
    );
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('meldet den Ausfall des Weges', async () => {
    fetchNachPfad({ [WEG]: problem(500, 'kaputt') });

    renderSeite();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Die Jahresabschlüsse sind gerade nicht zu erreichen.',
    );
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
});
