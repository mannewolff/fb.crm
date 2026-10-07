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
  intern: false,
  positionen: [],
  summe: 0,
};

/** Eine gestellte Rechnung, wie das Backend sie schreibt (Issue #184). */
const RECHNUNG = {
  id: 4,
  angebotId: 9,
  firmaId: 5,
  firmaName: 'Adler AG',
  rechnungDatum: '2026-10-01',
  leistungszeitraum: 'Oktober 2026',
  zustand: 'GESTELLT',
  nummer: '0001-2026',
  steuersatz: 19,
  netto: 9600,
  steuer: 1824,
  brutto: 11424,
  zahlungszielTage: 14,
  empfaenger: null,
  absender: null,
  zeilen: [],
};

/** Eine nachgetragene Rechnung, wie das Backend sie schreibt (Issue #270). */
const NACHTRAG = {
  id: 7,
  firmaId: 5,
  firmaName: 'Adler AG',
  nummer: 'RE-2026-014',
  rechnungDatum: '2026-03-12',
  netto: 1000,
  brutto: 1190,
  zustand: 'GESTELLT',
  dokument: false,
};

/** Die Firmenwahl der Nachtrags-Maske: alle Firmen, auch stillgelegte. */
const FIRMENWAHL = {
  firmen: [{ id: 5, name: 'Adler AG', ort: null, aktiveAnsprechpartner: 0, aktiv: true }],
  gesamt: 1,
};

/** Der Stand der Startseite, wie das Backend ihn schreibt (Issue #216). */
const STARTSEITENSTAND = {
  monat: '2026-10',
  monate: ['2026-10', '2026-09'],
  inArbeit: [],
  nichtAbgerechnet: { netto: 0, erfasstImMonat: 0, angebote: [] },
  abgerechnet: { netto: 0, brutto: 0, anzahl: 0 },
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
  // Ohne Sitzung fuehrt jede geschuetzte Adresse auf die Anmeldeseite — derselbe Ablauf je Pfad.
  it.each([
    ['zeigt ohne Sitzung die Anmeldeseite', '/anmelden'],
    ['fuehrt „/firmen/5/angebote/neu" ohne Sitzung auf die Anmeldeseite', '/firmen/5/angebote/neu'],
    ['fuehrt „/angebote" ohne Sitzung auf die Anmeldeseite', '/angebote'],
    ['fuehrt „/angebote/9" ohne Sitzung auf die Anmeldeseite', '/angebote/9'],
    ['fuehrt „/angebote/9/bearbeiten" ohne Sitzung auf die Anmeldeseite', '/angebote/9/bearbeiten'],
    ['fuehrt „/rechnungen" ohne Sitzung auf die Anmeldeseite', '/rechnungen'],
    ['fuehrt „/rechnungen/4" ohne Sitzung auf die Anmeldeseite', '/rechnungen/4'],
    ['fuehrt „/rechnungen/nachtragen" ohne Sitzung auf die Anmeldeseite', '/rechnungen/nachtragen'],
    [
      'fuehrt „/rechnungen/nachgetragen/7" ohne Sitzung auf die Anmeldeseite',
      '/rechnungen/nachgetragen/7',
    ],
    [
      'fuehrt „/rechnungen/nachgetragen/7/bearbeiten" ohne Sitzung auf die Anmeldeseite',
      '/rechnungen/nachgetragen/7/bearbeiten',
    ],
    ['fuehrt „/jahresabschluesse" ohne Sitzung auf die Anmeldeseite', '/jahresabschluesse'],
    ['fuehrt „/eigene-angaben" ohne Sitzung auf die Anmeldeseite', '/eigene-angaben'],
    ['fuehrt „/administration“ ohne Sitzung auf die Anmeldeseite', '/administration'],
  ])('%s', async (_name, pfad) => {
    ohneSitzung();

    renderApp([pfad], 0);

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

  it('zeigt „/rechnungen" als Liste im Rahmen und nicht als einzelne Rechnung', async () => {
    fensterbreite(1440);
    fetchNachPfad({
      'GET /api/auth/me': json(200, KONTO),
      'GET /api/instance': json(200, { version: '0.1.3' }),
      'GET /api/rechnungen': json(200, { rechnungen: [] }),
    });

    renderApp(['/rechnungen'], 0);

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Rechnungen' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).toBeInTheDocument();
  });

  it('zeigt „/jahresabschluesse" als Uebersicht der Jahre im Rahmen', async () => {
    fensterbreite(1440);
    fetchNachPfad({
      'GET /api/auth/me': json(200, KONTO),
      'GET /api/instance': json(200, { version: '0.1.3' }),
      'GET /api/jahresabschluesse': json(200, []),
    });

    renderApp(['/jahresabschluesse'], 0);

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Jahresabschlüsse' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).toBeInTheDocument();
  });

  it('zeigt „/rechnungen/4" mit Sitzung im Rahmen — nachgeladen', async () => {
    fensterbreite(1440);
    fetchNachPfad({
      'GET /api/auth/me': json(200, KONTO),
      'GET /api/instance': json(200, { version: '0.1.3' }),
      'GET /api/rechnungen/4': json(200, RECHNUNG),
    });

    renderApp(['/rechnungen/4'], 0);

    // Lazy und geschuetzt: Beim ersten Rendern steht erst die Sitzungspruefung da.
    expect(screen.getByRole('status')).toHaveTextContent('Sitzung wird geprüft');
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Rechnung 0001-2026' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).toBeInTheDocument();
  });

  it('zeigt „/rechnungen/nachtragen" als Maske und nicht als Rechnung „nachtragen"', async () => {
    fensterbreite(1440);
    fetchNachPfad({
      'GET /api/auth/me': json(200, KONTO),
      'GET /api/instance': json(200, { version: '0.1.3' }),
      'GET /api/firmen?suche=&auchStillgelegte=true': json(200, FIRMENWAHL),
    });

    renderApp(['/rechnungen/nachtragen'], 0);

    // Statisch vor dynamisch (E21): `nachtragen` ist der Weg zur Maske, keine Kennung.
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Rechnung nachtragen' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).toBeInTheDocument();
  });

  it('zeigt „/rechnungen/nachgetragen/7" als Einzelansicht der nachgetragenen Rechnung', async () => {
    fensterbreite(1440);
    fetchNachPfad({
      'GET /api/auth/me': json(200, KONTO),
      'GET /api/instance': json(200, { version: '0.1.3' }),
      'GET /api/nachgetragene-rechnungen/7': json(200, NACHTRAG),
    });

    renderApp(['/rechnungen/nachgetragen/7'], 0);

    // Ein eigener Kennungsraum (E21): Die Ansicht fragt den Weg der nachgetragenen Rechnung.
    expect(screen.getByRole('status')).toHaveTextContent('Sitzung wird geprüft');
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Rechnung RE-2026-014' }),
    ).toBeInTheDocument();
    expect(screen.getByText('nachgetragen')).toBeInTheDocument();
  });

  it('zeigt „/rechnungen/nachgetragen/7/bearbeiten" als Maske der nachgetragenen Rechnung', async () => {
    fensterbreite(1440);
    fetchNachPfad({
      'GET /api/auth/me': json(200, KONTO),
      'GET /api/instance': json(200, { version: '0.1.3' }),
      'GET /api/firmen?suche=&auchStillgelegte=true': json(200, FIRMENWAHL),
      'GET /api/nachgetragene-rechnungen/7': json(200, NACHTRAG),
    });

    renderApp(['/rechnungen/nachgetragen/7/bearbeiten'], 0);

    expect(screen.getByRole('status')).toHaveTextContent('Sitzung wird geprüft');
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Nachgetragene Rechnung bearbeiten' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Rechnungsnummer' })).toHaveValue('RE-2026-014');
  });

  it('zeigt „/eigene-angaben" mit Sitzung im Rahmen — nachgeladen, nicht im ersten Rutsch', async () => {
    fensterbreite(1440);
    fetchNachPfad({
      'GET /api/auth/me': json(200, KONTO),
      'GET /api/instance': json(200, { version: '0.1.3' }),
      'GET /api/eigene-angaben': json(200, {
        name: 'Manfred Wolff',
        berufsbezeichnung: null,
        strasse: null,
        plz: null,
        ort: 'Bremen',
        land: 'Deutschland',
        email: null,
        telefon: null,
        webadresse: null,
        steuernummer: null,
        umsatzsteuerId: null,
        bankverbindung: null,
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

  it('zeigt „/administration“ mit Sitzung im Rahmen — nachgeladen, nicht im ersten Rutsch', async () => {
    fensterbreite(1440);
    fetchNachPfad({
      'GET /api/auth/me': json(200, KONTO),
      'GET /api/instance': json(200, { version: '0.1.3' }),
      'GET /api/rechnung/einstellungen': json(200, {
        nummerMuster: '{NNNN}-{JJJJ}',
        naechsteNummer: 1,
        steuersatz: 19,
        zahlungszielTage: 10,
      }),
    });

    renderApp(['/administration'], 0);

    // Lazy und geschuetzt: Beim ersten Rendern steht erst die Sitzungspruefung da.
    expect(screen.getByRole('status')).toHaveTextContent('Sitzung wird geprüft');
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Administration' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).toBeInTheDocument();
  });

  it('zeigt „/" als Startseite im Rahmen und nicht mehr das leere Panel', async () => {
    fensterbreite(1440);
    fetchNachPfad({
      'GET /api/auth/me': json(200, KONTO),
      'GET /api/instance': json(200, { version: '0.1.3' }),
      'GET /api/startseite': json(200, STARTSEITENSTAND),
    });

    renderApp(['/'], 0);

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Start' }),
    ).toBeInTheDocument();
    expect(screen.queryByTestId('leeres-panel')).not.toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).toBeInTheDocument();
  });

  it('zeigt „/dokumentation" weiter das leere Panel', async () => {
    fensterbreite(1440);
    fetchNachPfad({
      'GET /api/auth/me': json(200, KONTO),
      'GET /api/instance': json(200, { version: '0.1.3' }),
    });

    renderApp(['/dokumentation'], 0);

    expect(await screen.findByTestId('leeres-panel')).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).toBeInTheDocument();
  });
});
