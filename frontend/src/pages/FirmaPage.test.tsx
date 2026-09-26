import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link, MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { Ansprechpartner, Firma } from '../api/firmen';
import type { VorgaengeDerFirma, VorgangZeile } from '../api/vorgaenge';
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

const OFFEN: VorgangZeile = {
  id: 31,
  nummer: 101,
  titel: 'Relaunch der Website',
  firma: 'Beispiel GmbH',
  phase: 'ANBAHNUNG',
  abgeschlossen: false,
  letzteAktivitaet: '2026-09-20T09:00:00Z',
};

const ZWEITER_OFFEN: VorgangZeile = { ...OFFEN, id: 32, nummer: 102, titel: 'Wartungsvertrag' };

const FERTIG: VorgangZeile = {
  ...OFFEN,
  id: 33,
  nummer: 99,
  titel: 'Schulung Redaktion',
  abgeschlossen: true,
};

const VORGAENGE: VorgaengeDerFirma = { offene: [OFFEN, ZWEITER_OFFEN], abgeschlossene: [FERTIG] };

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
function firmaDoppel(start: Firma = FIRMA, vorgaenge: VorgaengeDerFirma = VORGAENGE) {
  let firma: Firma = start;
  /**
   * Schaltet den Ansprechpartner der Firma — so, wie der Server es taete.
   *
   * Die Faelle, die eine Zeile schalten, tragen genau einen Ansprechpartner; deshalb braucht das
   * Doppel den Eintrag nicht erst herauszusuchen.
   */
  const schaltePartner = (aktiv: boolean) => {
    firma = {
      ...firma,
      ansprechpartner: firma.ansprechpartner.map((einer) => ({ ...einer, aktiv })),
    };
    return new Response(null, { status: 204 });
  };
  return fetchNachPfad({
    'GET /api/firmen/7': () =>
      new Response(JSON.stringify(firma), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    'GET /api/firmen/7/vorgaenge': json(200, vorgaenge),
    'POST /api/firmen/7/stilllegen': () => {
      firma = { ...firma, aktiv: false };
      return new Response(null, { status: 204 });
    },
    'POST /api/firmen/7/aktivieren': () => {
      firma = { ...firma, aktiv: true };
      return new Response(null, { status: 204 });
    },
    'POST /api/firmen/7/ansprechpartner/11/stilllegen': () => schaltePartner(false),
    'POST /api/firmen/7/ansprechpartner/11/aktivieren': () => schaltePartner(true),
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
      'GET /api/firmen/7/vorgaenge': json(200, VORGAENGE),
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

describe('FirmaPage — die Zeilen-Aktionen der Ansprechpartner (Kriterium 15)', () => {
  it('fuehrt „Bearbeiten" je Zeile auf die Maske des Ansprechpartners', async () => {
    firmaDoppel();

    renderSeite();

    expect(await screen.findByRole('link', { name: 'Bearbeiten: Anna Berg' })).toHaveAttribute(
      'href',
      '/firmen/7/ansprechpartner/11/bearbeiten',
    );
    expect(screen.getByRole('link', { name: 'Bearbeiten: Clausen' })).toHaveAttribute(
      'href',
      '/firmen/7/ansprechpartner/12/bearbeiten',
    );
    // Der Weg der Firma bleibt daneben eindeutig benannt.
    expect(screen.getByRole('link', { name: 'Bearbeiten' })).toHaveAttribute(
      'href',
      '/firmen/7/bearbeiten',
    );
  });

  it('verschiebt die Zeile beim Stilllegen unter „Stillgelegt" und wieder zurueck', async () => {
    const nutzer = userEvent.setup();
    firmaDoppel({ ...FIRMA, ansprechpartner: [ANNA] });

    renderSeite();

    const aktive = within(await screen.findByRole('list', { name: 'Aktive Ansprechpartner' }));
    expect(aktive.getByText('Anna Berg')).toBeInTheDocument();

    await nutzer.click(screen.getByRole('button', { name: 'Stilllegen: Anna Berg' }));

    const ruhende = within(
      await screen.findByRole('list', { name: 'Stillgelegte Ansprechpartner' }),
    );
    expect(ruhende.getByText('Anna Berg')).toBeInTheDocument();
    expect(screen.queryByRole('list', { name: 'Aktive Ansprechpartner' })).not.toBeInTheDocument();

    await nutzer.click(screen.getByRole('button', { name: 'Wieder aktivieren: Anna Berg' }));

    expect(await screen.findByRole('list', { name: 'Aktive Ansprechpartner' })).toBeInTheDocument();
    expect(
      screen.queryByRole('list', { name: 'Stillgelegte Ansprechpartner' }),
    ).not.toBeInTheDocument();
  });

  it('haelt die Zeilen-Aktionen auch bei stillgelegter Firma offen (Kriterium 14)', async () => {
    const nutzer = userEvent.setup();
    firmaDoppel({ ...FIRMA, aktiv: false, ansprechpartner: [ANNA] });

    renderSeite();

    expect(await screen.findByText('stillgelegt')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Bearbeiten: Anna Berg' })).toBeInTheDocument();

    await nutzer.click(screen.getByRole('button', { name: 'Stilllegen: Anna Berg' }));

    const ruhende = within(
      await screen.findByRole('list', { name: 'Stillgelegte Ansprechpartner' }),
    );
    expect(ruhende.getByText('Anna Berg')).toBeInTheDocument();

    await nutzer.click(screen.getByRole('button', { name: 'Wieder aktivieren: Anna Berg' }));

    expect(await screen.findByRole('list', { name: 'Aktive Ansprechpartner' })).toBeInTheDocument();
  });

  it('meldet, wenn das Schalten einer Zeile nicht durchgeht', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'GET /api/firmen/7': json(200, { ...FIRMA, ansprechpartner: [ANNA] }),
      'GET /api/firmen/7/vorgaenge': json(200, VORGAENGE),
      'POST /api/firmen/7/ansprechpartner/11/stilllegen': leer(500),
    });

    renderSeite();
    await screen.findByRole('heading', { name: 'Beispiel GmbH' });
    await nutzer.click(screen.getByRole('button', { name: 'Stilllegen: Anna Berg' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Der Stand des Ansprechpartners wurde nicht geändert',
    );
  });
});

describe('FirmaPage — die Vorgaenge der Firma (Kriterium 12)', () => {
  it('zeigt die offenen in der Reihenfolge der Antwort, mit Nummer, Titel und Phase', async () => {
    firmaDoppel();

    renderSeite();

    const offene = within(await screen.findByRole('list', { name: 'Offene Vorgänge' }));
    const wege = offene.getAllByRole('link');
    expect(wege.map((weg) => weg.textContent)).toEqual([
      '#101Relaunch der Website',
      '#102Wartungsvertrag',
    ]);
    expect(wege[0]).toHaveAttribute('href', '/vorgaenge/31');
    expect(wege[1]).toHaveAttribute('href', '/vorgaenge/32');
    expect(offene.getAllByText('Anbahnung')).toHaveLength(2);
  });

  it('setzt die abgeschlossenen darunter ab und nennt den Stand im zugaenglichen Namen', async () => {
    firmaDoppel();

    renderSeite();

    await screen.findByRole('list', { name: 'Offene Vorgänge' });
    const ueberschriften = screen
      .getAllByRole('heading')
      .map((element) => element.textContent)
      .filter((text) => text === 'Vorgänge' || text === 'Abgeschlossen');
    expect(ueberschriften).toEqual(['Vorgänge', 'Abgeschlossen']);

    const fertige = within(screen.getByRole('list', { name: 'Abgeschlossene Vorgänge' }));
    const weg = fertige.getByRole('link', { name: '#99 Schulung Redaktion abgeschlossen' });
    expect(weg).toHaveAttribute('href', '/vorgaenge/33');
    // Der Stand haengt am Weg selbst, nicht nur an einer Farbe.
    expect(
      within(screen.getByRole('list', { name: 'Offene Vorgänge' })).queryByText('abgeschlossen'),
    ).not.toBeInTheDocument();
  });

  it('laesst die Ueberschrift „Abgeschlossen" weg, wo keiner abgeschlossen ist', async () => {
    firmaDoppel(FIRMA, { offene: [OFFEN], abgeschlossene: [] });

    renderSeite();

    await screen.findByRole('list', { name: 'Offene Vorgänge' });
    expect(screen.queryByRole('heading', { name: 'Abgeschlossen' })).not.toBeInTheDocument();
    expect(
      screen.queryByRole('list', { name: 'Abgeschlossene Vorgänge' }),
    ).not.toBeInTheDocument();
  });

  it('zeigt nur die abgeschlossenen, wo keiner offen ist', async () => {
    firmaDoppel(FIRMA, { offene: [], abgeschlossene: [FERTIG] });

    renderSeite();

    expect(
      await screen.findByRole('list', { name: 'Abgeschlossene Vorgänge' }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('list', { name: 'Offene Vorgänge' })).not.toBeInTheDocument();
  });

  it('sagt es, wenn es keinen Vorgang gibt', async () => {
    firmaDoppel({ ...FIRMA, ansprechpartner: [ANNA] }, { offene: [], abgeschlossene: [] });

    renderSeite();

    expect(await screen.findByRole('status')).toHaveTextContent('Noch kein Vorgang angelegt');
    expect(screen.queryByRole('list', { name: 'Offene Vorgänge' })).not.toBeInTheDocument();
  });

  it('zeigt den Ladehinweis, solange der zweite Leseweg noch laeuft', async () => {
    // Ohne Platzhalter-Funktion: Ein nie gerufener Vorbelegungswert waere ungetesteter Code.
    let liefere!: (antwort: Response) => void;
    const spaeter = new Promise<Response>((aufloesen) => {
      liefere = aufloesen;
    });
    fetchNachPfad({
      'GET /api/firmen/7': json(200, FIRMA),
      'GET /api/firmen/7/vorgaenge': () => spaeter,
    });

    renderSeite();

    // Die Firma ist da, die Vorgaenge noch nicht — die Platte sagt es, statt leer zu bleiben.
    await screen.findByRole('heading', { name: 'Beispiel GmbH' });
    expect(screen.getByText('Vorgänge werden geladen …')).toBeInTheDocument();

    liefere(json(200, VORGAENGE)());

    expect(await screen.findByRole('list', { name: 'Offene Vorgänge' })).toBeInTheDocument();
  });

  it('meldet den Ausfall des zweiten Lesewegs, laesst die Angaben der Firma aber stehen', async () => {
    fetchNachPfad({
      'GET /api/firmen/7': json(200, FIRMA),
      'GET /api/firmen/7/vorgaenge': leer(500),
    });

    renderSeite();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Die Vorgänge sind gerade nicht zu erreichen',
    );
    expect(screen.getByRole('heading', { name: 'Beispiel GmbH' })).toBeInTheDocument();
    expect(screen.getByText('Hauptstraße 1')).toBeInTheDocument();
    expect(screen.getByRole('list', { name: 'Aktive Ansprechpartner' })).toBeInTheDocument();
  });

  it('fragt die Vorgaenge nicht ab, wo die Kennung keine ist', async () => {
    const fetchMock = fetchNachPfad({});

    renderSeite('/firmen/keine-zahl');

    await screen.findByRole('alert');
    expect(fetchMock).not.toHaveBeenCalled();
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
      'GET /api/firmen/7/vorgaenge': json(200, VORGAENGE),
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
    await nutzer.tab();
    expect(screen.getByRole('link', { name: 'Bearbeiten: Anna Berg' })).toHaveFocus();
    await nutzer.tab();
    expect(screen.getByRole('button', { name: 'Stilllegen: Anna Berg' })).toHaveFocus();
  });
});
