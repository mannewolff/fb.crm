import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '../auth/AuthContext';
import { FUSS_EINTRAEGE, NAV_BLOECKE } from '../layout/navItems';
import { SCHIENE_SCHLUESSEL } from '../lib/railState';
import { fetchNachPfad, json, leer } from '../test/fetchNachPfad';
import type { Routen } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';
import NavRail from './NavRail';

const KONTO = { id: 1, displayName: 'Manfred Wolff', email: 'info@mwolff.org' };

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

/** Sitzung und Versionsstand — beides braucht die Schiene fuer Marke und Nutzerkarte. */
function angemeldet(weitere: Routen = {}) {
  return fetchNachPfad({
    'GET /api/auth/me': json(200, KONTO),
    'GET /api/instance': json(200, { version: '0.1.1' }),
    ...weitere,
  });
}

function renderSchiene(adresse = '/', onWahl?: () => void) {
  return renderMitTheme(
    <MemoryRouter initialEntries={[adresse]}>
      <AuthProvider>
        <NavRail onWahl={onWahl} />
      </AuthProvider>
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
  it('fuehrt im Fuss nur noch die beiden Ziele — „Einklappen" steht an der Marke (E7)', () => {
    expect(FUSS_EINTRAEGE.map((eintrag) => eintrag.beschriftung)).toEqual([
      'Administration',
      'Dokumentation',
    ]);
  });

  it('fuehrt „Stammdaten" mit seinen Eintraegen', () => {
    expect(
      NAV_BLOECKE.map((block) => [block.titel, block.eintraege.map((e) => [e.beschriftung, e.ziel])]),
    ).toEqual([
      // „Eigene Angaben" ist ein Stammdatum wie die Firma und steht neben ihr — nicht hinter
      // „Administration" und nicht in einem eigenen Block mit einem Eintrag (E14).
      [
        'Stammdaten',
        [
          ['Firmen', '/firmen'],
          ['Eigene Angaben', '/eigene-angaben'],
        ],
      ],
    ]);
  });
});

describe('NavRail', () => {
  it('ist als Hauptnavigation benannt', () => {
    angemeldet();

    renderSchiene();

    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).toBeInTheDocument();
  });

  it('hat nach der Anmeldung keinen aktiven Eintrag (K10)', () => {
    angemeldet();

    renderSchiene('/');

    expect(screen.queryAllByRole('link', { current: 'page' })).toHaveLength(0);
    expect(screen.queryAllByRole('link', { current: true })).toHaveLength(0);
  });

  it('traegt zwischen Marke und Fuss die zwei Bloecke in ihrer Reihenfolge (K11, E18, E24)', () => {
    angemeldet();

    renderSchiene();

    // Die Marke bleibt fuer sich: Der Gruppentitel gehoert zum Block, nicht zum Kopf.
    const kopf = within(screen.getByTestId('schiene-kopf'));
    expect(kopf.queryAllByRole('link')).toHaveLength(0);
    expect(kopf.getByText('fb.crm')).toBeInTheDocument();

    const bloecke = within(screen.getByTestId('schiene-bloecke'));
    // Die Reihenfolge der Titel und der Links haelt die Aussage der Bloecke fest (E24).
    expect(bloecke.getAllByText(/^Stammdaten$/).map((e) => e.textContent)).toEqual([
      'Stammdaten',
    ]);
    expect(bloecke.getAllByRole('link').map((link) => link.getAttribute('href'))).toEqual([
      '/firmen',
      '/eigene-angaben',
    ]);
    expect(bloecke.getByRole('link', { name: 'Firmen' })).toBeInTheDocument();
    expect(bloecke.getByRole('link', { name: 'Eigene Angaben' })).toBeInTheDocument();
    // Kein Umschalter in den Bloecken — Tasten stehen an der Marke und im Fuss.
    expect(bloecke.queryAllByRole('button')).toHaveLength(0);
    // Die Bloecke stehen im Baum oberhalb des Fusses.
    expect(
      screen.getByTestId('schiene-bloecke').compareDocumentPosition(screen.getByTestId('schiene-fuss')),
    ).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
  });

  it('schreibt die Gruppentitel in Satzschreibung, ohne Versalien (Typografie)', () => {
    angemeldet();

    renderSchiene();

    const titel = within(screen.getByTestId('schiene-bloecke')).getByText('Stammdaten');
    expect(getComputedStyle(titel).textTransform).toBe('none');
  });

  it('ruft beim Waehlen eines Eintrags den Rueckruf (Schaltflaechen-Schiene)', async () => {
    angemeldet();
    const nutzer = userEvent.setup();
    const gewaehlt = vi.fn();

    renderSchiene('/', gewaehlt);
    await nutzer.click(screen.getByRole('link', { name: 'Firmen' }));

    expect(gewaehlt).toHaveBeenCalledTimes(1);
  });

  it('traegt im Fuss Administration, Dokumentation und die Nutzerkarte (K12, E7)', async () => {
    angemeldet();

    renderSchiene();

    const fuss = within(screen.getByTestId('schiene-fuss'));
    expect(fuss.getAllByRole('link').map((link) => link.getAttribute('href'))).toEqual([
      '/administration',
      '/dokumentation',
    ]);
    const karte = await fuss.findByRole('button', { name: 'Nutzermenü: Manfred Wolff' });
    expect(karte).toHaveTextContent('Manfred Wolff');
    expect(karte).toHaveTextContent('info@mwolff.org');
    expect(karte).toHaveTextContent('MW');
  });

  it('oeffnet aus der Nutzerkarte das Menue mit „Abmelden" (K14, E7)', async () => {
    angemeldet();
    const nutzer = userEvent.setup();

    renderSchiene();
    await nutzer.click(await screen.findByRole('button', { name: /Nutzermenü/ }));

    const eintraege = within(screen.getByRole('menu')).getAllByRole('menuitem');
    expect(eintraege.map((eintrag) => eintrag.textContent)).toEqual(['Abmelden']);
  });

  it.each([
    ['/administration', 'Administration'],
    ['/dokumentation', 'Dokumentation'],
    ['/firmen', 'Firmen'],
    // Auch die Detailansicht einer Firma laesst „Firmen" aktiv stehen (Plan-Review Fund 3).
    ['/firmen/7', 'Firmen'],
    ['/eigene-angaben', 'Eigene Angaben'],
  ])('setzt auf %s aria-current="page" an „%s" und nur dort (K12)', (adresse, beschriftung) => {
    angemeldet();

    renderSchiene(adresse);

    const aktiv = screen.getAllByRole('link', { current: 'page' });
    expect(aktiv).toHaveLength(1);
    expect(aktiv[0]).toHaveAccessibleName(beschriftung);
  });

  it('zeigt die Version der Instanz an der Marke (K11)', async () => {
    angemeldet();

    renderSchiene();

    expect(await screen.findByTestId('marke-zusatz')).toHaveTextContent('v0.1.1');
  });

  it('bleibt ohne Version bedienbar, wenn der Versionsstand nicht zu erfahren ist', async () => {
    const fetchMock = angemeldet({ 'GET /api/instance': leer(503) });

    renderSchiene();
    await vi.waitFor(() => {
      expect(fetchMock).toHaveBeenCalled();
    });

    expect(screen.queryByTestId('marke-zusatz')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Einklappen' })).toBeEnabled();
  });

  it('klappt ein, behaelt jeden Namen und uebersteht das Neuladen (K12, E13)', async () => {
    angemeldet();
    const nutzer = userEvent.setup();

    const { unmount } = renderSchiene();
    const taste = screen.getByRole('button', { name: 'Einklappen' });
    // Die Taste sagt, ob die Schiene offen steht — nicht, ob sie gedrueckt ist.
    expect(taste).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('navigation')).toHaveAttribute('data-eingeklappt', 'false');
    await nutzer.click(taste);

    expect(screen.getByRole('button', { name: 'Einklappen' })).toHaveAttribute(
      'aria-expanded',
      'false',
    );
    expect(screen.getByRole('navigation')).toHaveAttribute('data-eingeklappt', 'true');
    // Eingeklappt steht keine sichtbare Beschriftung mehr — der Name bleibt als aria-label.
    expect(screen.queryByText('Administration')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Administration' })).toBeInTheDocument();
    // Ebenso im Block: der Gruppentitel entfaellt, der Link behaelt seinen Namen.
    expect(screen.queryByText('Stammdaten')).not.toBeInTheDocument();
    expect(screen.queryByText('Firmen')).not.toBeInTheDocument();
    expect(screen.queryByText('Eigene Angaben')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Firmen' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Eigene Angaben' })).toBeInTheDocument();
    // Marke und Kuerzel bleiben; Name und E-Mail der Nutzerkarte entfallen.
    expect(screen.getByTestId('marke-mal')).toBeInTheDocument();
    expect(screen.getByTestId('nutzer-mal')).toHaveTextContent('MW');
    expect(screen.queryByText('info@mwolff.org')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Nutzermenü/ })).toBeInTheDocument();

    // Neuladen: die Komponente geht, der Speicher bleibt.
    unmount();
    renderSchiene();

    expect(screen.getByRole('navigation')).toHaveAttribute('data-eingeklappt', 'true');
    expect(localStorage.getItem(SCHIENE_SCHLUESSEL)).toBe('true');
  });

  it('zeichnet jeden Eintrag der Bloecke mit einem eigenen Symbol, keines ist das Markenmal', async () => {
    angemeldet();
    const nutzer = userEvent.setup();

    renderSchiene();
    await nutzer.click(screen.getByRole('button', { name: 'Einklappen' }));

    const namen = NAV_BLOECKE.flatMap((block) => block.eintraege.map((e) => e.symbol));
    const striche = namen.map((name) => screen.getByTestId(`nav-symbol-${name}`).innerHTML);
    expect(new Set(namen).size).toBe(namen.length);
    expect(new Set(striche).size).toBe(striche.length);
    expect(namen).not.toContain('chart-bar');
  });

  it('zeichnet „Firmen" und „Eigene Angaben" mit verschiedenen Symbolen (E24)', async () => {
    // Eingeklappt steht nur noch das Symbol da. Zwei Eintraege mit demselben Strich waeren dort
    // nicht mehr zu unterscheiden.
    angemeldet();
    const nutzer = userEvent.setup();

    renderSchiene();
    await nutzer.click(screen.getByRole('button', { name: 'Einklappen' }));

    // Eingeklappt steht keine Beschriftung mehr da — nur noch der Strich.
    expect(screen.queryByText('Firmen')).not.toBeInTheDocument();
    const strich = (name: string) => screen.getByTestId(`nav-symbol-${name}`).innerHTML;
    expect(strich('id')).toBeTruthy();
    expect(strich('id')).not.toBe(strich('building-community'));
  });

  it('wechselt die Breite zwischen 260 und 76 px (Rahmen)', async () => {
    angemeldet();
    const nutzer = userEvent.setup();

    renderSchiene();
    const schiene = screen.getByRole('navigation');
    expect(getComputedStyle(schiene).width).toBe('260px');

    await nutzer.click(screen.getByRole('button', { name: 'Einklappen' }));

    expect(getComputedStyle(screen.getByRole('navigation')).width).toBe('76px');
  });

  it('klappt wieder aus', async () => {
    angemeldet();
    const nutzer = userEvent.setup();

    renderSchiene();
    await nutzer.click(screen.getByRole('button', { name: 'Einklappen' }));
    await nutzer.click(screen.getByRole('button', { name: 'Einklappen' }));

    expect(screen.getByRole('navigation')).toHaveAttribute('data-eingeklappt', 'false');
    expect(screen.getByText('Administration')).toBeInTheDocument();
  });
});
