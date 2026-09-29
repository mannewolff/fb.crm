import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useNavigate } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import App from './App';
import { fetchNachPfad, json } from './test/fetchNachPfad';
import { renderMitTheme } from './test/render';

const KONTO = { id: 1, displayName: 'Manfred Wolff', email: 'info@mwolff.org' };

/** Ein Angebot, wie das Backend es schreibt. */
const ANGEBOT = {
  id: 9,
  firmaId: 5,
  firmaName: 'Adler AG',
  ansprechpartnerId: null,
  ansprechpartnerName: null,
  status: 'ABGEGEBEN',
  angebotDatum: '2026-09-24',
  beschreibung: null,
  positionen: [],
  summe: 0,
};

/** Die Firma des Angebots, wie das Backend sie schreibt. */
const FIRMA = {
  id: 5,
  name: 'Adler AG',
  strasse: null,
  plz: null,
  ort: null,
  land: null,
  steuernummer: null,
  umsatzsteuerId: null,
  aktiv: true,
  ansprechpartner: [],
};

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

function ohneSitzung() {
  return vi
    .spyOn(globalThis, 'fetch')
    .mockImplementation(() => Promise.resolve(new Response(null, { status: 401 })));
}

/** Der Zurueck-Knopf des Browsers, nachgebildet ueber dieselbe Verlaufs-Schnittstelle. */
function Zurueck() {
  const navigate = useNavigate();
  return (
    <button
      type="button"
      onClick={() => {
        navigate(-1);
      }}
    >
      zurueck
    </button>
  );
}

function renderApp(verlauf: string[], stand: number) {
  return renderMitTheme(
    <MemoryRouter initialEntries={verlauf} initialIndex={stand}>
      <Zurueck />
      <App />
    </MemoryRouter>,
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('App', () => {
  it('zeigt ohne Sitzung die Anmeldeseite', async () => {
    ohneSitzung();

    renderApp(['/anmelden'], 0);

    expect(await screen.findByLabelText(/^E-Mail-Adresse/)).toBeInTheDocument();
  });

  it('fuehrt einen Schritt zurueck auf eine geschuetzte Adresse wieder auf die Anmeldeseite', async () => {
    // K9: Nach dem Abmelden liegt die Anwendung im Verlauf hinter der Anmeldeseite. Der
    // Zurueck-Knopf darf sie nicht wieder hervorholen — die Zugangsregel haengt an der
    // Sitzung, nicht am Verlauf.
    ohneSitzung();
    const nutzer = userEvent.setup();

    renderApp(['/', '/anmelden'], 1);
    await screen.findByLabelText(/^E-Mail-Adresse/);
    await nutzer.click(screen.getByRole('button', { name: 'zurueck' }));

    expect(await screen.findByLabelText(/^E-Mail-Adresse/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Anmelden' })).toBeInTheDocument();
  });

  it('fuehrt „/firmen/5/angebote/neu" ohne Sitzung auf die Anmeldeseite', async () => {
    ohneSitzung();

    renderApp(['/firmen/5/angebote/neu'], 0);

    expect(await screen.findByLabelText(/^E-Mail-Adresse/)).toBeInTheDocument();
  });

  it('zeigt „/firmen/5/angebote/neu" als Maske im Rahmen — nachgeladen', async () => {
    fensterbreite(1440);
    fetchNachPfad({
      'GET /api/auth/me': json(200, KONTO),
      'GET /api/instance': json(200, { version: '0.1.3' }),
      'GET /api/firmen/5': json(200, FIRMA),
    });

    renderApp(['/firmen/5/angebote/neu'], 0);

    // Lazy und geschuetzt: Beim ersten Rendern steht erst die Sitzungspruefung da.
    expect(screen.getByRole('status')).toHaveTextContent('Sitzung wird geprüft');
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Neues Angebot' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).toBeInTheDocument();
  });

  it('fuehrt „/angebote" ohne Sitzung auf die Anmeldeseite', async () => {
    ohneSitzung();

    renderApp(['/angebote'], 0);

    expect(await screen.findByLabelText(/^E-Mail-Adresse/)).toBeInTheDocument();
  });

  it('zeigt „/angebote" als Uebersicht im Rahmen und nicht als einzelnes Angebot', async () => {
    fensterbreite(1440);
    fetchNachPfad({
      'GET /api/auth/me': json(200, KONTO),
      'GET /api/instance': json(200, { version: '0.1.3' }),
      'GET /api/angebote': json(200, { angebote: [] }),
    });

    renderApp(['/angebote'], 0);

    expect(await screen.findByRole('heading', { level: 1, name: 'Angebote' })).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).toBeInTheDocument();
  });

  it('fuehrt „/angebote/9" ohne Sitzung auf die Anmeldeseite', async () => {
    ohneSitzung();

    renderApp(['/angebote/9'], 0);

    expect(await screen.findByLabelText(/^E-Mail-Adresse/)).toBeInTheDocument();
  });

  it('zeigt „/angebote/9" mit Sitzung im Rahmen', async () => {
    fensterbreite(1440);
    fetchNachPfad({
      'GET /api/auth/me': json(200, KONTO),
      'GET /api/instance': json(200, { version: '0.1.3' }),
      'GET /api/angebote/9': json(200, ANGEBOT),
    });

    renderApp(['/angebote/9'], 0);

    expect(screen.getByRole('status')).toHaveTextContent('Sitzung wird geprüft');
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Angebot vom 24.09.2026' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).toBeInTheDocument();
  });

  it('fuehrt „/angebote/9/bearbeiten" ohne Sitzung auf die Anmeldeseite', async () => {
    ohneSitzung();

    renderApp(['/angebote/9/bearbeiten'], 0);

    expect(await screen.findByLabelText(/^E-Mail-Adresse/)).toBeInTheDocument();
  });

  it('zeigt „/angebote/9/bearbeiten" als Maske des Angebots', async () => {
    fensterbreite(1440);
    fetchNachPfad({
      'GET /api/auth/me': json(200, KONTO),
      'GET /api/instance': json(200, { version: '0.1.3' }),
      'GET /api/angebote/9': json(200, ANGEBOT),
      'GET /api/firmen/5': json(200, FIRMA),
    });

    renderApp(['/angebote/9/bearbeiten'], 0);

    expect(screen.getByRole('status')).toHaveTextContent('Sitzung wird geprüft');
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Angebot bearbeiten' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).toBeInTheDocument();
  });

  it('fuehrt „/eigene-angaben" ohne Sitzung auf die Anmeldeseite', async () => {
    ohneSitzung();

    renderApp(['/eigene-angaben'], 0);

    expect(await screen.findByLabelText(/^E-Mail-Adresse/)).toBeInTheDocument();
  });

  it('zeigt „/eigene-angaben" mit Sitzung im Rahmen — nachgeladen, nicht im ersten Rutsch', async () => {
    fensterbreite(1440);
    fetchNachPfad({
      'GET /api/auth/me': json(200, KONTO),
      'GET /api/instance': json(200, { version: '0.1.3' }),
      'GET /api/eigene-angaben': json(200, {
        name: 'Manfred Wolff',
        strasse: null,
        plz: null,
        ort: 'Bremen',
        land: 'Deutschland',
        email: null,
        telefon: null,
        steuernummer: null,
        umsatzsteuerId: null,
        bankverbindung: null,
        zahlungsbedingungen: null,
      }),
    });

    renderApp(['/eigene-angaben'], 0);

    // Lazy und geschuetzt: Beim ersten Rendern steht erst die Sitzungspruefung da.
    expect(screen.getByRole('status')).toHaveTextContent('Sitzung wird geprüft');
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Eigene Angaben' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).toBeInTheDocument();
  });
});
