import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { FUSS_EINTRAEGE, NAV_BLOECKE } from '../layout/navItems';
import { SCHIENE_SCHLUESSEL } from '../lib/railState';
import { fetchNachPfad, json, leer } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';
import NavRail from './NavRail';

/** Ein Speicher, der ein Neuladen ueberlebt, weil er nicht an der Komponente haengt. */
function speicher() {
  const inhalt = new Map<string, string>();
  Object.defineProperty(globalThis, 'localStorage', {
    value: {
      getItem: (schluessel: string) => inhalt.get(schluessel) ?? null,
      setItem: (schluessel: string, wert: string) => {
        inhalt.set(schluessel, wert);
      },
    },
    configurable: true,
  });
  return inhalt;
}

function renderSchiene(adresse = '/') {
  return renderMitTheme(
    <MemoryRouter initialEntries={[adresse]}>
      <NavRail />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  speicher();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('navItems (E15, E18)', () => {
  it('fuehrt genau die drei Fuss-Eintraege und sonst nichts', () => {
    expect(FUSS_EINTRAEGE.map((eintrag) => eintrag.beschriftung)).toEqual([
      'Administration',
      'Dokumentation',
      'Einklappen',
    ]);
  });

  it('fuehrt genau den Block „Stammdaten" mit dem Eintrag „Firmen"', () => {
    expect(
      NAV_BLOECKE.map((block) => [block.etikett, block.eintraege.map((e) => [e.beschriftung, e.ziel])]),
    ).toEqual([['Stammdaten', [['Firmen', '/firmen']]]]);
  });
});

describe('NavRail', () => {
  it('ist als Hauptnavigation benannt', () => {
    fetchNachPfad({ 'GET /api/instance': json(200, { version: '0.1.1' }) });

    renderSchiene();

    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).toBeInTheDocument();
  });

  it('hat nach der Anmeldung keinen aktiven Eintrag (K10)', () => {
    fetchNachPfad({ 'GET /api/instance': json(200, { version: '0.1.1' }) });

    renderSchiene('/');

    expect(screen.queryAllByRole('link', { current: 'page' })).toHaveLength(0);
    expect(screen.queryAllByRole('link', { current: true })).toHaveLength(0);
  });

  it('traegt zwischen Marke und Fuss den Block „Stammdaten" mit „Firmen" (K11, E18)', () => {
    fetchNachPfad({ 'GET /api/instance': json(200, { version: '0.1.1' }) });

    renderSchiene();

    // Die Marke bleibt fuer sich: Das Etikett gehoert zum Block, nicht zum Kopf.
    const kopf = within(screen.getByTestId('schiene-kopf'));
    expect(kopf.queryAllByRole('link')).toHaveLength(0);
    expect(screen.getByTestId('schiene-kopf')).toHaveTextContent(/^fb\.crm$/);

    const bloecke = within(screen.getByTestId('schiene-bloecke'));
    expect(bloecke.getByText('Stammdaten')).toBeInTheDocument();
    expect(bloecke.getAllByRole('link').map((link) => link.getAttribute('href'))).toEqual(['/firmen']);
    expect(bloecke.getByRole('link', { name: 'Firmen' })).toBeInTheDocument();
    // Kein Umschalter in den Bloecken — Tasten stehen allein im Fuss.
    expect(bloecke.queryAllByRole('button')).toHaveLength(0);
    // Die Bloecke stehen im Baum oberhalb des Fusses.
    expect(
      screen.getByTestId('schiene-bloecke').compareDocumentPosition(screen.getByTestId('schiene-fuss')),
    ).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
  });

  it('ruft beim Waehlen eines Eintrags den Rueckruf (Schaltflaechen-Schiene)', async () => {
    fetchNachPfad({ 'GET /api/instance': json(200, { version: '0.1.1' }) });
    const nutzer = userEvent.setup();
    const gewaehlt = vi.fn();

    renderMitTheme(
      <MemoryRouter initialEntries={['/']}>
        <NavRail onWahl={gewaehlt} />
      </MemoryRouter>,
    );
    await nutzer.click(screen.getByRole('link', { name: 'Firmen' }));

    expect(gewaehlt).toHaveBeenCalledTimes(1);
  });

  it('traegt im Fuss genau die drei Eintraege (K12)', () => {
    fetchNachPfad({ 'GET /api/instance': json(200, { version: '0.1.1' }) });

    renderSchiene();

    const fuss = within(screen.getByTestId('schiene-fuss'));
    expect(fuss.getAllByRole('link').map((link) => link.getAttribute('href'))).toEqual([
      '/administration',
      '/dokumentation',
    ]);
    expect(fuss.getAllByRole('button').map((taste) => taste.textContent)).toEqual(['Einklappen']);
  });

  it.each([
    ['/administration', 'Administration'],
    ['/dokumentation', 'Dokumentation'],
    ['/firmen', 'Firmen'],
    // Auch die Detailansicht einer Firma laesst „Firmen" aktiv stehen (Plan-Review Fund 3).
    ['/firmen/7', 'Firmen'],
  ])('setzt auf %s aria-current="page" an „%s" und nur dort (K12)', (adresse, beschriftung) => {
    fetchNachPfad({ 'GET /api/instance': json(200, { version: '0.1.1' }) });

    renderSchiene(adresse);

    const aktiv = screen.getAllByRole('link', { current: 'page' });
    expect(aktiv).toHaveLength(1);
    expect(aktiv[0]).toHaveAccessibleName(beschriftung);
  });

  it('zeigt die Version der Instanz an der Marke (K11)', async () => {
    fetchNachPfad({ 'GET /api/instance': json(200, { version: '0.1.1' }) });

    renderSchiene();

    expect(await screen.findByTestId('marke-zusatz')).toHaveTextContent('v0.1.1');
  });

  it('bleibt ohne Version bedienbar, wenn der Versionsstand nicht zu erfahren ist', async () => {
    const fetchMock = fetchNachPfad({ 'GET /api/instance': leer(503) });

    renderSchiene();
    await vi.waitFor(() => {
      expect(fetchMock).toHaveBeenCalled();
    });

    expect(screen.queryByTestId('marke-zusatz')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Einklappen' })).toBeEnabled();
  });

  it('klappt ein, behaelt die Beschriftung als Namen und uebersteht das Neuladen (K12, E13)', async () => {
    fetchNachPfad({ 'GET /api/instance': json(200, { version: '0.1.1' }) });
    const nutzer = userEvent.setup();

    const { unmount } = renderSchiene();
    const taste = screen.getByRole('button', { name: 'Einklappen' });
    expect(taste).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByRole('navigation')).toHaveAttribute('data-eingeklappt', 'false');
    await nutzer.click(taste);

    expect(screen.getByRole('button', { name: 'Einklappen' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('navigation')).toHaveAttribute('data-eingeklappt', 'true');
    // Eingeklappt steht keine sichtbare Beschriftung mehr — der Name bleibt als aria-label.
    expect(screen.queryByText('Administration')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Administration' })).toBeInTheDocument();
    // Ebenso im Block: das Etikett entfaellt, der Link behaelt seinen Namen.
    expect(screen.queryByText('Stammdaten')).not.toBeInTheDocument();
    expect(screen.queryByText('Firmen')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Firmen' })).toBeInTheDocument();

    // Neuladen: die Komponente geht, der Speicher bleibt.
    unmount();
    renderSchiene();

    expect(screen.getByRole('navigation')).toHaveAttribute('data-eingeklappt', 'true');
    expect(localStorage.getItem(SCHIENE_SCHLUESSEL)).toBe('true');
  });

  it('wechselt die Breite zwischen 224 und 64 px', async () => {
    fetchNachPfad({ 'GET /api/instance': json(200, { version: '0.1.1' }) });
    const nutzer = userEvent.setup();

    renderSchiene();
    const schiene = screen.getByRole('navigation');
    expect(getComputedStyle(schiene).width).toBe('224px');

    await nutzer.click(screen.getByRole('button', { name: 'Einklappen' }));

    expect(getComputedStyle(screen.getByRole('navigation')).width).toBe('64px');
  });

  it('klappt wieder aus', async () => {
    fetchNachPfad({ 'GET /api/instance': json(200, { version: '0.1.1' }) });
    const nutzer = userEvent.setup();

    renderSchiene();
    await nutzer.click(screen.getByRole('button', { name: 'Einklappen' }));
    await nutzer.click(screen.getByRole('button', { name: 'Einklappen' }));

    expect(screen.getByRole('navigation')).toHaveAttribute('data-eingeklappt', 'false');
    expect(screen.getByText('Administration')).toBeInTheDocument();
  });
});
