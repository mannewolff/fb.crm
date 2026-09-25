import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link, MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { Ansprechpartner, Firma } from '../api/firmen';
import { AuthProvider } from '../auth/AuthContext';
import AppShell from '../components/AppShell';
import { KopfAktionProvider } from '../components/KopfAktion';
import { fetchNachPfad, json, leer } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';
import FirmaPage from './FirmaPage';

const ANNA: Ansprechpartner = {
  id: 11,
  vorname: 'Anna',
  nachname: 'Berg',
  rolle: 'Einkauf',
  email: 'anna.berg@beispiel.de',
  telefonFestnetz: '+49 421 123456',
  telefonMobil: null,
  aktiv: true,
};

const BRUNO: Ansprechpartner = {
  id: 12,
  vorname: null,
  nachname: 'Clausen',
  rolle: null,
  email: null,
  telefonFestnetz: 'Durchwahl über die Zentrale',
  telefonMobil: '0170 9876543',
  aktiv: false,
};

const FIRMA: Firma = {
  id: 7,
  name: 'Beispiel GmbH',
  strasse: 'Hauptstraße 1',
  plz: '28195',
  ort: 'Bremen',
  land: 'Deutschland',
  steuernummer: '75/123/45678',
  umsatzsteuerId: 'DE123456789',
  aktiv: true,
  ansprechpartner: [ANNA, BRUNO],
};

const KONTO = { id: 1, displayName: 'Manfred Wolff', email: 'info@mwolff.org' };

/** Die Adresse, an der sich ablesen laesst, wohin ein Weg gefuehrt hat. */
function Adresse() {
  const ort = useLocation();
  return <p data-testid="adresse">{ort.pathname}</p>;
}

function renderSeite(start = '/firmen/7') {
  return renderMitTheme(
    <MemoryRouter initialEntries={[start]}>
      <KopfAktionProvider>
        <Routes>
          <Route path="/firmen" element={<p>Übersicht</p>} />
          <Route path="/firmen/:id" element={<FirmaPage />} />
          <Route path="/firmen/:id/bearbeiten" element={<p>Maske</p>} />
        </Routes>
        <Adresse />
      </KopfAktionProvider>
    </MemoryRouter>,
  );
}

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

/**
 * Ein Doppel, dessen Firma sich mit dem Stilllegen aendert.
 *
 * Die Ansicht liest nach jeder Schaltung neu — ein Doppel mit festem Rumpf wuerde deshalb
 * gruen bleiben, auch wenn die Ansicht das Ergebnis gar nicht uebernaehme.
 */
function firmaDoppel(start: Firma = FIRMA) {
  let firma: Firma = start;
  return fetchNachPfad({
    'GET /api/firmen/7': () =>
      new Response(JSON.stringify(firma), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    'POST /api/firmen/7/stilllegen': () => {
      firma = { ...firma, aktiv: false };
      return new Response(null, { status: 204 });
    },
    'POST /api/firmen/7/aktivieren': () => {
      firma = { ...firma, aktiv: true };
      return new Response(null, { status: 204 });
    },
  });
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('FirmaPage — die Angaben', () => {
  it('zeigt die Angaben als Feldpaare', async () => {
    firmaDoppel();

    renderSeite();

    expect(await screen.findByRole('heading', { name: 'Beispiel GmbH' })).toBeInTheDocument();
    expect(screen.getByText('Hauptstraße 1')).toBeInTheDocument();
    expect(screen.getByText('28195')).toBeInTheDocument();
    expect(screen.getByText('Bremen')).toBeInTheDocument();
    expect(screen.getByText('Deutschland')).toBeInTheDocument();
    expect(screen.getByText('75/123/45678')).toBeInTheDocument();
    expect(screen.getByText('DE123456789')).toBeInTheDocument();
  });

  it('laesst leere Angaben weg — ohne Platzhaltertext', async () => {
    firmaDoppel({ ...FIRMA, strasse: null, plz: null, steuernummer: null, umsatzsteuerId: null });

    renderSeite();

    await screen.findByRole('heading', { name: 'Beispiel GmbH' });
    expect(screen.queryByText('Straße und Hausnummer')).not.toBeInTheDocument();
    expect(screen.queryByText('Steuernummer')).not.toBeInTheDocument();
    expect(screen.queryByText('—')).not.toBeInTheDocument();
    expect(screen.getByText('Ort')).toBeInTheDocument();
  });

  it('fuehrt „Bearbeiten" auf die Maske', async () => {
    firmaDoppel();

    renderSeite();

    expect(await screen.findByRole('link', { name: 'Bearbeiten' })).toHaveAttribute(
      'href',
      '/firmen/7/bearbeiten',
    );
  });
});

describe('FirmaPage — Stilllegen und Wiederaktivieren', () => {
  it('schaltet Schild und Taste um', async () => {
    const nutzer = userEvent.setup();
    firmaDoppel();

    renderSeite();

    await screen.findByRole('heading', { name: 'Beispiel GmbH' });
    expect(screen.queryByText('stillgelegt')).not.toBeInTheDocument();

    await nutzer.click(screen.getByRole('button', { name: 'Stilllegen' }));

    expect(await screen.findByText('stillgelegt')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Stilllegen' })).not.toBeInTheDocument();

    await nutzer.click(screen.getByRole('button', { name: 'Wieder aktivieren' }));

    expect(await screen.findByRole('button', { name: 'Stilllegen' })).toBeInTheDocument();
    expect(screen.queryByText('stillgelegt')).not.toBeInTheDocument();
  });

  it('haelt alle Wege auch bei stillgelegter Firma offen (Kriterium 14)', async () => {
    firmaDoppel({ ...FIRMA, aktiv: false });

    renderSeite();

    expect(await screen.findByText('stillgelegt')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Bearbeiten' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Wieder aktivieren' })).toBeEnabled();
    expect(screen.getByRole('list', { name: 'Aktive Ansprechpartner' })).toBeInTheDocument();
  });

  it('meldet, wenn das Schalten nicht durchgeht', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'GET /api/firmen/7': json(200, FIRMA),
      'POST /api/firmen/7/stilllegen': leer(500),
    });

    renderSeite();
    await screen.findByRole('heading', { name: 'Beispiel GmbH' });
    await nutzer.click(screen.getByRole('button', { name: 'Stilllegen' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht geändert');
  });
});

describe('FirmaPage — die Ansprechpartner', () => {
  it('stellt die aktiven vor die stillgelegten, unter eigener Ueberschrift', async () => {
    firmaDoppel();

    renderSeite();

    await screen.findByRole('heading', { name: 'Beispiel GmbH' });
    const ueberschriften = screen
      .getAllByRole('heading')
      .map((element) => element.textContent)
      .filter((text) => text === 'Ansprechpartner' || text === 'Stillgelegt');
    expect(ueberschriften).toEqual(['Ansprechpartner', 'Stillgelegt']);

    const aktive = within(screen.getByRole('list', { name: 'Aktive Ansprechpartner' }));
    expect(aktive.getByText('Anna Berg')).toBeInTheDocument();
    expect(aktive.queryByText('Clausen')).not.toBeInTheDocument();
    const ruhende = within(screen.getByRole('list', { name: 'Stillgelegte Ansprechpartner' }));
    expect(ruhende.getByText('Clausen')).toBeInTheDocument();
  });

  it('macht E-Mail und Rufnummer zu Wegen — und laesst stehen, was keiner ist', async () => {
    firmaDoppel();

    renderSeite();

    expect(await screen.findByRole('link', { name: 'anna.berg@beispiel.de' })).toHaveAttribute(
      'href',
      'mailto:anna.berg%40beispiel.de',
    );
    expect(screen.getByRole('link', { name: '+49 421 123456' })).toHaveAttribute(
      'href',
      'tel:+49421123456',
    );
    expect(screen.getByRole('link', { name: '0170 9876543' })).toHaveAttribute(
      'href',
      'tel:01709876543',
    );
    // Keine waehlbare Nummer — der Text bleibt, der Link entfaellt (telefonlink.ts).
    expect(screen.getByText('Durchwahl über die Zentrale')).toBeInTheDocument();
    expect(
      screen.queryByRole('link', { name: 'Durchwahl über die Zentrale' }),
    ).not.toBeInTheDocument();
  });

  it('zeigt die Rolle, wo eine hinterlegt ist', async () => {
    firmaDoppel();

    renderSeite();

    const aktive = within(await screen.findByRole('list', { name: 'Aktive Ansprechpartner' }));
    expect(aktive.getByText('Einkauf')).toBeInTheDocument();
  });

  it('weist ohne Ansprechpartner auf den Weg zum ersten hin', async () => {
    firmaDoppel({ ...FIRMA, ansprechpartner: [] });

    renderSeite();

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Noch kein Ansprechpartner angelegt',
    );
    expect(screen.queryByRole('list', { name: 'Aktive Ansprechpartner' })).not.toBeInTheDocument();
    expect(
      screen.queryByRole('list', { name: 'Stillgelegte Ansprechpartner' }),
    ).not.toBeInTheDocument();
  });

  it('zeigt nur die aktive Liste, wenn keiner stillgelegt ist', async () => {
    firmaDoppel({ ...FIRMA, ansprechpartner: [ANNA] });

    renderSeite();

    expect(await screen.findByRole('list', { name: 'Aktive Ansprechpartner' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Stillgelegt' })).not.toBeInTheDocument();
  });

  it('zeigt nur die stillgelegte Liste, wenn keiner aktiv ist', async () => {
    firmaDoppel({ ...FIRMA, ansprechpartner: [BRUNO] });

    renderSeite();

    expect(
      await screen.findByRole('list', { name: 'Stillgelegte Ansprechpartner' }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('list', { name: 'Aktive Ansprechpartner' })).not.toBeInTheDocument();
  });
});

describe('FirmaPage — unsinnige Kennung, unbekannte Firma, Ausfall', () => {
  it('faengt eine nicht numerische Kennung ab, bevor sie an die Schnittstelle geht', async () => {
    const fetchMock = fetchNachPfad({});

    renderSeite('/firmen/keine-zahl');

    expect(await screen.findByRole('alert')).toHaveTextContent('gibt es nicht');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('meldet eine unbekannte Firma', async () => {
    fetchNachPfad({ 'GET /api/firmen/7': leer(404) });

    renderSeite();

    expect(await screen.findByRole('alert')).toHaveTextContent('gibt es nicht');
  });

  it('meldet den Ausfall der Schnittstelle', async () => {
    fetchNachPfad({ 'GET /api/firmen/7': leer(500) });

    renderSeite();

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht zu erreichen');
  });
});

describe('FirmaPage — Kopfaktion und Tastatur', () => {
  it('legt „Neuer Ansprechpartner" in den Kopf und raeumt den Platz beim Verlassen', async () => {
    const nutzer = userEvent.setup();
    fensterbreite(1440);
    const firma: Firma = FIRMA;
    fetchNachPfad({
      'GET /api/auth/me': json(200, KONTO),
      'GET /api/instance': json(200, { version: '0.1.3' }),
      'GET /api/firmen/7': () =>
        new Response(JSON.stringify(firma), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
    });

    renderMitTheme(
      <MemoryRouter initialEntries={['/firmen/7']}>
        <AuthProvider>
          <AppShell>
            <Routes>
              <Route path="/firmen/:id" element={<FirmaPage />} />
              <Route path="/woanders" element={<p>Andere Seite</p>} />
            </Routes>
            <Link to="/woanders">Weiter</Link>
          </AppShell>
        </AuthProvider>
      </MemoryRouter>,
    );

    const kopf = within(screen.getByRole('banner'));
    expect(kopf.getByRole('link', { name: 'Neuer Ansprechpartner' })).toHaveAttribute(
      'href',
      '/firmen/7/ansprechpartner/neu',
    );
    await screen.findByRole('heading', { name: 'Beispiel GmbH' });

    await nutzer.click(screen.getByRole('link', { name: 'Weiter' }));

    expect(screen.getByText('Andere Seite')).toBeInTheDocument();
    expect(screen.getByTestId('kopf-aktion')).toBeEmptyDOMElement();
  });

  it('fuehrt mit dem Tabulator ueber Bearbeiten, Stilllegen und die Wege der Zeilen', async () => {
    const nutzer = userEvent.setup();
    firmaDoppel({ ...FIRMA, ansprechpartner: [ANNA] });

    renderSeite();
    await screen.findByRole('heading', { name: 'Beispiel GmbH' });

    await nutzer.tab();
    expect(screen.getByRole('link', { name: 'Bearbeiten' })).toHaveFocus();
    await nutzer.tab();
    expect(screen.getByRole('button', { name: 'Stilllegen' })).toHaveFocus();
    await nutzer.tab();
    expect(screen.getByRole('link', { name: 'anna.berg@beispiel.de' })).toHaveFocus();
    await nutzer.tab();
    expect(screen.getByRole('link', { name: '+49 421 123456' })).toHaveFocus();
  });
});
