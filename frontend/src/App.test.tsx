import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useNavigate } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import App from './App';
import { fetchNachPfad, json } from './test/fetchNachPfad';
import { renderMitTheme } from './test/render';

const KONTO = { id: 1, displayName: 'Manfred Wolff', email: 'info@mwolff.org' };

/** Ein versendetes Angebot, wie das Backend es schreibt. */
const ANGEBOT = {
  id: 9,
  vorgangId: 5,
  nummer: 'A-2026-001',
  stand: 'VERSENDET',
  angebotDatum: '2026-09-24',
  gueltigBis: '2026-10-24',
  leistungsbeschreibung: null,
  zahlungsbedingungen: null,
  versendetAm: '2026-09-24T08:00:00Z',
  reaktionAm: null,
  positionen: [],
  summe: 0,
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
        abschlusswahrscheinlichkeit: null,
        entscheidungErwartetAm: null,
        firma: { id: 7, name: 'Beispiel GmbH', aktiv: true },
        ansprechpartner: null,
        historie: [],
      }),
      'GET /api/vorgaenge/5/angebote': json(200, { angebote: [] }),
    });

    renderApp(['/vorgaenge/5'], 0);

    expect(screen.getByRole('status')).toHaveTextContent('Sitzung wird geprüft');
    expect(
      // Die Kopfkarte traegt Nummer und Titel als die eine Ueberschrift der Ansicht.
      await screen.findByRole('heading', { level: 1, name: '#941 Anteilsbalken je Vorgang' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).toBeInTheDocument();
  });

  it('fuehrt „/vorgaenge/5/angebote/neu" ohne Sitzung auf die Anmeldeseite', async () => {
    ohneSitzung();

    renderApp(['/vorgaenge/5/angebote/neu'], 0);

    expect(await screen.findByLabelText(/^E-Mail-Adresse/)).toBeInTheDocument();
  });

  it('zeigt „/vorgaenge/5/angebote/neu" als Maske — statisch vor der dynamischen Kennung', async () => {
    fensterbreite(1440);
    fetchNachPfad({
      'GET /api/auth/me': json(200, KONTO),
      'GET /api/instance': json(200, { version: '0.1.3' }),
      'GET /api/vorgaenge/5/angebote': json(200, { angebote: [] }),
    });

    renderApp(['/vorgaenge/5/angebote/neu'], 0);

    // „neu" ist die Maske und nicht das Angebot mit der Kennung „neu": Waere die dynamische
    // Route zuerst dran, ginge hier ein Aufruf auf /api/angebote/neu hinaus.
    expect(screen.getByRole('status')).toHaveTextContent('Sitzung wird geprüft');
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Neues Angebot' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).toBeInTheDocument();
  });

  it('fuehrt „/vorgaenge/5/angebote/9" ohne Sitzung auf die Anmeldeseite', async () => {
    ohneSitzung();

    renderApp(['/vorgaenge/5/angebote/9'], 0);

    expect(await screen.findByLabelText(/^E-Mail-Adresse/)).toBeInTheDocument();
  });

  it('zeigt „/vorgaenge/5/angebote/9" mit Sitzung im Rahmen', async () => {
    fensterbreite(1440);
    fetchNachPfad({
      'GET /api/auth/me': json(200, KONTO),
      'GET /api/instance': json(200, { version: '0.1.3' }),
      'GET /api/angebote/9': json(200, ANGEBOT),
    });

    renderApp(['/vorgaenge/5/angebote/9'], 0);

    expect(screen.getByRole('status')).toHaveTextContent('Sitzung wird geprüft');
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Angebot A-2026-001' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).toBeInTheDocument();
  });

  it('fuehrt „/vorgaenge/5/angebote/9/bearbeiten" ohne Sitzung auf die Anmeldeseite', async () => {
    ohneSitzung();

    renderApp(['/vorgaenge/5/angebote/9/bearbeiten'], 0);

    expect(await screen.findByLabelText(/^E-Mail-Adresse/)).toBeInTheDocument();
  });

  it('zeigt „/vorgaenge/5/angebote/9/bearbeiten" als Maske des Entwurfs', async () => {
    fensterbreite(1440);
    fetchNachPfad({
      'GET /api/auth/me': json(200, KONTO),
      'GET /api/instance': json(200, { version: '0.1.3' }),
      'GET /api/angebote/9': json(200, { ...ANGEBOT, nummer: null, stand: 'ENTWURF' }),
    });

    renderApp(['/vorgaenge/5/angebote/9/bearbeiten'], 0);

    expect(screen.getByRole('status')).toHaveTextContent('Sitzung wird geprüft');
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Angebot bearbeiten' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).toBeInTheDocument();
  });

  it('fuehrt „/vorgaenge/5/angebote/9/auftrag/neu" ohne Sitzung auf die Anmeldeseite', async () => {
    ohneSitzung();

    renderApp(['/vorgaenge/5/angebote/9/auftrag/neu'], 0);

    expect(await screen.findByLabelText(/^E-Mail-Adresse/)).toBeInTheDocument();
  });

  it('zeigt „/vorgaenge/5/angebote/9/auftrag/neu" als Anlege-Maske im Rahmen', async () => {
    fensterbreite(1440);
    fetchNachPfad({
      'GET /api/auth/me': json(200, KONTO),
      'GET /api/instance': json(200, { version: '0.1.3' }),
      'GET /api/angebote/9': json(200, { ...ANGEBOT, stand: 'ANGENOMMEN' }),
    });

    renderApp(['/vorgaenge/5/angebote/9/auftrag/neu'], 0);

    expect(screen.getByRole('status')).toHaveTextContent('Sitzung wird geprüft');
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Neuer Auftrag' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).toBeInTheDocument();
  });

  it.each([['/vorgaenge/5/auftraege/3'], ['/vorgaenge/5/auftraege/3/bearbeiten']])(
    'fuehrt „%s" ohne Sitzung auf die Anmeldeseite',
    async (adresse) => {
      ohneSitzung();

      renderApp([adresse], 0);

      expect(await screen.findByLabelText(/^E-Mail-Adresse/)).toBeInTheDocument();
    },
  );

  it.each([
    ['/vorgaenge/5/auftraege/3', 'Auftrag AU-2026-001'],
    ['/vorgaenge/5/auftraege/3/bearbeiten', 'Auftrag AU-2026-001 bearbeiten'],
  ])('zeigt „%s" mit Sitzung im Rahmen', async (adresse, ueberschrift) => {
    fensterbreite(1440);
    fetchNachPfad({
      'GET /api/auth/me': json(200, KONTO),
      'GET /api/instance': json(200, { version: '0.1.3' }),
      'GET /api/auftraege/3': json(200, {
        id: 3,
        vorgangId: 5,
        angebotId: 9,
        angebotNummer: 'A-2026-001',
        nummer: 'AU-2026-001',
        status: 'OFFEN',
        auftragDatum: '2026-09-28',
        kundenbestellnummer: null,
        leistungAb: null,
        leistungBis: null,
        positionen: [],
        summe: 0,
      }),
    });

    renderApp([adresse], 0);

    expect(await screen.findByRole('heading', { level: 1, name: ueberschrift })).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).toBeInTheDocument();
  });

  it('fuehrt „/pipeline" ohne Sitzung auf die Anmeldeseite', async () => {
    ohneSitzung();

    renderApp(['/pipeline'], 0);

    expect(await screen.findByLabelText(/^E-Mail-Adresse/)).toBeInTheDocument();
  });

  it('zeigt „/pipeline" mit Sitzung im Rahmen — nachgeladen, nicht im ersten Rutsch', async () => {
    fensterbreite(1440);
    fetchNachPfad({
      'GET /api/auth/me': json(200, KONTO),
      'GET /api/instance': json(200, { version: '0.1.3' }),
      'GET /api/pipeline': json(200, { zeilen: [], summe: 0, gewichteteSumme: 0 }),
    });

    renderApp(['/pipeline'], 0);

    expect(screen.getByRole('status')).toHaveTextContent('Sitzung wird geprüft');
    expect(await screen.findByRole('heading', { level: 1, name: 'Pipeline' })).toBeInTheDocument();
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
        abschlusswahrscheinlichkeit: null,
        entscheidungErwartetAm: null,
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
