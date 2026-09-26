import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useNavigate } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import App from './App';
import { fetchNachPfad, json } from './test/fetchNachPfad';
import { renderMitTheme } from './test/render';

const KONTO = { id: 1, displayName: 'Manfred Wolff', email: 'info@mwolff.org' };

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

  it('fuehrt „/vorgaenge" ohne Sitzung auf die Anmeldeseite', async () => {
    ohneSitzung();

    renderApp(['/vorgaenge'], 0);

    expect(await screen.findByLabelText(/^E-Mail-Adresse/)).toBeInTheDocument();
  });

  it('zeigt „/vorgaenge" mit Sitzung im Rahmen — nachgeladen, nicht im ersten Rutsch', async () => {
    fensterbreite(1440);
    fetchNachPfad({
      'GET /api/auth/me': json(200, KONTO),
      'GET /api/instance': json(200, { version: '0.1.3' }),
      'GET /api/vorgaenge?suche=&auchAbgeschlossene=false': json(200, {
        vorgaenge: [],
        gesamt: 0,
      }),
    });

    renderApp(['/vorgaenge'], 0);

    // Lazy und geschuetzt: Beim ersten Rendern steht erst die Sitzungspruefung da, die Ansicht
    // kommt nachgeladen mit ihrem Bündel.
    expect(screen.getByRole('status')).toHaveTextContent('Sitzung wird geprüft');
    expect(await screen.findByText(/Es ist noch kein Vorgang angelegt/)).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).toBeInTheDocument();
  });

  it('fuehrt „/vorgaenge/neu" ohne Sitzung auf die Anmeldeseite', async () => {
    ohneSitzung();

    renderApp(['/vorgaenge/neu'], 0);

    expect(await screen.findByLabelText(/^E-Mail-Adresse/)).toBeInTheDocument();
  });

  it('zeigt „/vorgaenge/neu" als Maske — statisch vor der dynamischen Kennung', async () => {
    fensterbreite(1440);
    fetchNachPfad({
      'GET /api/auth/me': json(200, KONTO),
      'GET /api/instance': json(200, { version: '0.1.3' }),
      'GET /api/firmen?suche=&auchStillgelegte=false': json(200, { firmen: [], gesamt: 0 }),
    });

    renderApp(['/vorgaenge/neu'], 0);

    // Lazy und geschuetzt: Beim ersten Rendern steht erst die Sitzungspruefung da.
    expect(screen.getByRole('status')).toHaveTextContent('Sitzung wird geprüft');
    // „neu" ist die Maske und nicht der Vorgang mit der Kennung „neu": Waere die dynamische
    // Route zuerst dran, ginge hier ein Aufruf auf /api/vorgaenge/neu hinaus.
    expect(await screen.findByRole('heading', { name: 'Neuer Vorgang' })).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).toBeInTheDocument();
  });

  it('fuehrt „/vorgaenge/5" ohne Sitzung auf die Anmeldeseite', async () => {
    ohneSitzung();

    renderApp(['/vorgaenge/5'], 0);

    expect(await screen.findByLabelText(/^E-Mail-Adresse/)).toBeInTheDocument();
  });

  it('zeigt „/vorgaenge/5" mit Sitzung im Rahmen — nachgeladen, nicht im ersten Rutsch', async () => {
    fensterbreite(1440);
    fetchNachPfad({
      'GET /api/auth/me': json(200, KONTO),
      'GET /api/instance': json(200, { version: '0.1.3' }),
      'GET /api/vorgaenge/5': json(200, {
        id: 5,
        nummer: 941,
        titel: 'Anteilsbalken je Vorgang',
        phase: 'ANBAHNUNG',
        abgeschlossen: false,
        firma: { id: 7, name: 'Beispiel GmbH', aktiv: true },
        ansprechpartner: null,
        historie: [],
      }),
    });

    renderApp(['/vorgaenge/5'], 0);

    expect(screen.getByRole('status')).toHaveTextContent('Sitzung wird geprüft');
    expect(
      // Die Kopfkarte traegt Nummer und Titel als die eine Ueberschrift der Ansicht.
      await screen.findByRole('heading', { level: 1, name: '#941 Anteilsbalken je Vorgang' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).toBeInTheDocument();
  });

  it('fuehrt „/vorgaenge/5/bearbeiten" ohne Sitzung auf die Anmeldeseite', async () => {
    ohneSitzung();

    renderApp(['/vorgaenge/5/bearbeiten'], 0);

    expect(await screen.findByLabelText(/^E-Mail-Adresse/)).toBeInTheDocument();
  });

  it('zeigt „/vorgaenge/5/bearbeiten" als Maske — nachgeladen, nicht im ersten Rutsch', async () => {
    fensterbreite(1440);
    fetchNachPfad({
      'GET /api/auth/me': json(200, KONTO),
      'GET /api/instance': json(200, { version: '0.1.3' }),
      'GET /api/vorgaenge/5': json(200, {
        id: 5,
        nummer: 941,
        titel: 'Anteilsbalken je Vorgang',
        phase: 'ANBAHNUNG',
        abgeschlossen: false,
        firma: { id: 7, name: 'Beispiel GmbH', aktiv: true },
        ansprechpartner: null,
        historie: [],
      }),
      'GET /api/firmen?suche=&auchStillgelegte=false': json(200, {
        firmen: [{ id: 7, name: 'Beispiel GmbH', ort: 'Bremen', aktiveAnsprechpartner: 0, aktiv: true }],
        gesamt: 1,
      }),
      'GET /api/firmen/7': json(200, {
        id: 7,
        name: 'Beispiel GmbH',
        strasse: null,
        plz: null,
        ort: 'Bremen',
        land: 'Deutschland',
        steuernummer: null,
        umsatzsteuerId: null,
        aktiv: true,
        ansprechpartner: [],
      }),
    });

    renderApp(['/vorgaenge/5/bearbeiten'], 0);

    expect(screen.getByRole('status')).toHaveTextContent('Sitzung wird geprüft');
    expect(
      await screen.findByRole('heading', { name: 'Vorgang bearbeiten' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).toBeInTheDocument();
  });
});
