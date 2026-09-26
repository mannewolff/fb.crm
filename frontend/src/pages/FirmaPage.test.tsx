import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { Ansprechpartner, Firma } from '../api/firmen';
import type { VorgaengeDerFirma, VorgangZeile } from '../api/vorgaenge';
import { AuthProvider } from '../auth/AuthContext';
import AppShell from '../components/AppShell';
import KopfPfad, { KopfPfadProvider } from '../components/KopfPfad';
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

function adresse() {
  return screen.getByTestId('adresse').textContent;
}

function renderSeite(start = '/firmen/7') {
  return renderMitTheme(
    <MemoryRouter initialEntries={[start]}>
      <KopfPfadProvider>
        <Routes>
          <Route path="/firmen" element={<p>Übersicht</p>} />
          <Route path="/firmen/:id" element={<FirmaPage />} />
          <Route path="/firmen/:id/bearbeiten" element={<p>Maske</p>} />
          <Route
            path="/firmen/:id/ansprechpartner/:ansprechpartnerId/bearbeiten"
            element={<p>Partner-Maske</p>}
          />
        </Routes>
        <Adresse />
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

/** Dieselbe Ansicht, aber mit dem Kopf darueber — fuer den Pfad, den sie meldet. */
function renderMitKopf(start = '/firmen/7') {
  return renderMitTheme(
    <MemoryRouter initialEntries={[start]}>
      <KopfPfadProvider>
        <KopfPfad />
        <Routes>
          <Route path="/firmen/:id" element={<FirmaPage />} />
        </Routes>
      </KopfPfadProvider>
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

/** Die Kopfkarte der Firma — Titel, Adresszeile, Zustands-Chip und die Aktionen. */
function kopfkarte() {
  return screen.getByTestId('kopfkarte');
}

/**
 * Oeffnet ein ⋯-Menue und waehlt einen Eintrag.
 *
 * Ein eigener Helfer, weil jede Aktion der Ansicht diesen Weg nimmt: Die folgenreichen stehen im
 * Menue, nicht als Taste daneben (CLAUDE-design.md, „Tasten").
 */
async function waehle(
  nutzer: ReturnType<typeof userEvent.setup>,
  menue: string,
  eintrag: string,
) {
  await nutzer.click(screen.getByRole('button', { name: menue }));
  await nutzer.click(await screen.findByRole('menuitem', { name: eintrag }));
}

/** Bestaetigt die offene Rueckfrage mit ihrer Taste. */
async function bestaetige(nutzer: ReturnType<typeof userEvent.setup>, aktion: string) {
  const dialog = await screen.findByRole('dialog');
  await nutzer.click(within(dialog).getByRole('button', { name: aktion }));
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

describe('FirmaPage — die Kopfkarte', () => {
  it('traegt den Namen als einzige Ueberschrift erster Ebene, dazu Adresszeile und Chip', async () => {
    firmaDoppel();

    renderSeite();

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Beispiel GmbH' }),
    ).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    const kopf = within(kopfkarte());
    expect(kopf.getByText('Hauptstraße 1 · 28195 Bremen · Deutschland')).toBeInTheDocument();
    // Genau ein Zustands-Chip: keine Kennzahlen, fuer die es keine Daten gibt (Plan A3).
    expect(kopf.getAllByTestId('chip')).toHaveLength(1);
    expect(kopf.getByText('Aktiv')).toBeInTheDocument();
  });

  it('nennt die stillgelegte Firma im Chip statt in einer zweiten Farbe', async () => {
    firmaDoppel({ ...FIRMA, aktiv: false });

    renderSeite();

    await screen.findByRole('heading', { level: 1, name: 'Beispiel GmbH' });
    const kopf = within(kopfkarte());
    expect(kopf.getAllByTestId('chip')).toHaveLength(1);
    expect(kopf.getByText('Stillgelegt')).toBeInTheDocument();
  });

  it('laesst leere Teile der Adresszeile weg — ohne Platzhalter', async () => {
    firmaDoppel({ ...FIRMA, strasse: null, plz: null, land: null });

    renderSeite();

    await screen.findByRole('heading', { level: 1, name: 'Beispiel GmbH' });
    expect(within(kopfkarte()).getByText('Bremen')).toBeInTheDocument();
    expect(screen.queryByText('—')).not.toBeInTheDocument();
  });

  it('bleibt ohne jede Adressangabe eine Kopfkarte ohne Zeile', async () => {
    firmaDoppel({ ...FIRMA, strasse: null, plz: null, ort: null, land: null });

    renderSeite();

    await screen.findByRole('heading', { level: 1, name: 'Beispiel GmbH' });
    expect(screen.queryByTestId('kopfkarte-zeile')).not.toBeInTheDocument();
  });

  it('fuehrt „Bearbeiten" als weiche Taste auf die Maske', async () => {
    firmaDoppel();

    renderSeite();

    const bearbeiten = await screen.findByRole('link', { name: 'Bearbeiten' });
    expect(bearbeiten).toHaveAttribute('href', '/firmen/7/bearbeiten');
    expect(bearbeiten).toHaveClass('MuiButton-root');
  });

  it('fuehrt die Kupfertaste auf die Maske des neuen Ansprechpartners', async () => {
    firmaDoppel();

    renderSeite();

    await screen.findByRole('heading', { level: 1, name: 'Beispiel GmbH' });
    expect(within(kopfkarte()).getByRole('link', { name: 'Neuer Ansprechpartner' })).toHaveAttribute(
      'href',
      '/firmen/7/ansprechpartner/neu',
    );
  });

  it('zeigt Steuernummer und Umsatzsteuer-Id als Stammdaten', async () => {
    firmaDoppel();

    renderSeite();

    await screen.findByRole('heading', { level: 1, name: 'Beispiel GmbH' });
    expect(screen.getByRole('heading', { name: 'Stammdaten' })).toBeInTheDocument();
    expect(screen.getByText('75/123/45678')).toBeInTheDocument();
    expect(screen.getByText('DE123456789')).toBeInTheDocument();
  });

  it('laesst die Stammdaten ganz weg, wo keine hinterlegt sind', async () => {
    firmaDoppel({ ...FIRMA, steuernummer: null, umsatzsteuerId: null });

    renderSeite();

    await screen.findByRole('heading', { level: 1, name: 'Beispiel GmbH' });
    expect(screen.queryByRole('heading', { name: 'Stammdaten' })).not.toBeInTheDocument();
    expect(screen.queryByText('Steuernummer')).not.toBeInTheDocument();
  });
});

describe('FirmaPage — Stilllegen und Wiederaktivieren der Firma', () => {
  it('fragt vor dem Stilllegen nach und ruft bei „Abbrechen" die Schnittstelle nicht', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = firmaDoppel();

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: 'Beispiel GmbH' });
    const vorher = fetchMock.mock.calls.length;

    await waehle(nutzer, 'Aktionen für Beispiel GmbH', 'Stilllegen');
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveAccessibleName('Stilllegen: Beispiel GmbH');
    await nutzer.click(within(dialog).getByRole('button', { name: 'Abbrechen' }));

    expect(fetchMock).toHaveBeenCalledTimes(vorher);
    expect(within(kopfkarte()).getByText('Aktiv')).toBeInTheDocument();
  });

  it('legt nach dem Bestaetigen genau einmal still und zeigt den neuen Stand', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = firmaDoppel();

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: 'Beispiel GmbH' });

    await waehle(nutzer, 'Aktionen für Beispiel GmbH', 'Stilllegen');
    await bestaetige(nutzer, 'Stilllegen');

    expect(await within(kopfkarte()).findByText('Stillgelegt')).toBeInTheDocument();
    expect(
      fetchMock.mock.calls.filter(([weg]) => weg === '/api/firmen/7/stilllegen'),
    ).toHaveLength(1);
  });

  it('aktiviert ohne Rueckfrage wieder — die Aktion ist nicht folgenreich', async () => {
    const nutzer = userEvent.setup();
    firmaDoppel({ ...FIRMA, aktiv: false });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: 'Beispiel GmbH' });

    await waehle(nutzer, 'Aktionen für Beispiel GmbH', 'Wieder aktivieren');

    expect(await within(kopfkarte()).findByText('Aktiv')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('haelt alle Wege auch bei stillgelegter Firma offen (Kriterium 14)', async () => {
    firmaDoppel({ ...FIRMA, aktiv: false });

    renderSeite();

    await screen.findByRole('heading', { level: 1, name: 'Beispiel GmbH' });
    expect(within(kopfkarte()).getByText('Stillgelegt')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Bearbeiten' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Neuer Ansprechpartner' })).toBeEnabled();
    expect(screen.getByTestId('innenkarten-raster')).toBeInTheDocument();
  });

  it('meldet, wenn das Schalten nicht durchgeht', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'GET /api/firmen/7': json(200, FIRMA),
      'GET /api/firmen/7/vorgaenge': json(200, VORGAENGE),
      'POST /api/firmen/7/stilllegen': leer(500),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: 'Beispiel GmbH' });
    await waehle(nutzer, 'Aktionen für Beispiel GmbH', 'Stilllegen');
    await bestaetige(nutzer, 'Stilllegen');

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht geändert');
  });
});

describe('FirmaPage — die Ansprechpartner als Innenkarten', () => {
  it('stellt sie als Kacheln im Raster, aktive vor stillgelegten', async () => {
    firmaDoppel();

    renderSeite();

    const raster = within(await screen.findByTestId('innenkarten-raster'));
    const namen = raster
      .getAllByRole('button', { name: /^Aktionen für / })
      .map((taste) => taste.getAttribute('aria-label'));
    expect(namen).toEqual(['Aktionen für Anna Berg', 'Aktionen für Clausen']);
    expect(raster.getByText('Anna Berg')).toBeInTheDocument();
    expect(raster.getByText('Einkauf')).toBeInTheDocument();
  });

  it('nennt den stillgelegten Ansprechpartner mit Chip in seiner Kachel', async () => {
    firmaDoppel();

    renderSeite();

    await screen.findByTestId('innenkarten-raster');
    const chips = screen.getAllByTestId('innenkarte-zustand');
    expect(chips).toHaveLength(1);
    expect(within(chips[0]).getByText('Stillgelegt')).toBeInTheDocument();
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

  it('laesst den Kontaktbereich weg, wo kein Weg hinterlegt ist — ohne leere Zeile', async () => {
    firmaDoppel({
      ...FIRMA,
      ansprechpartner: [
        { ...ANNA, email: null, telefonFestnetz: null, telefonMobil: null },
      ],
    });

    renderSeite();

    await screen.findByTestId('innenkarten-raster');
    expect(screen.getByText('Anna Berg')).toBeInTheDocument();
    expect(screen.queryByTestId('innenkarte-kontakt')).not.toBeInTheDocument();
  });

  it('fuehrt die Hinzufuegen-Kachel auf die Maske', async () => {
    firmaDoppel();

    renderSeite();

    const raster = within(await screen.findByTestId('innenkarten-raster'));
    expect(raster.getByRole('link', { name: 'Ansprechpartner hinzufügen' })).toHaveAttribute(
      'href',
      '/firmen/7/ansprechpartner/neu',
    );
  });

  it('sagt es, wo noch kein Ansprechpartner angelegt ist — und laesst die Kachel stehen', async () => {
    firmaDoppel({ ...FIRMA, ansprechpartner: [] });

    renderSeite();

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Noch kein Ansprechpartner angelegt',
    );
    expect(
      screen.getByRole('link', { name: 'Ansprechpartner hinzufügen' }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Aktionen für Anna/ })).not.toBeInTheDocument();
  });
});

describe('FirmaPage — die Aktionen je Ansprechpartner (Kriterium 15)', () => {
  it('fuehrt „Bearbeiten" aus dem Menue auf die Maske des Ansprechpartners', async () => {
    const nutzer = userEvent.setup();
    firmaDoppel();

    renderSeite();
    await screen.findByTestId('innenkarten-raster');

    await waehle(nutzer, 'Aktionen für Anna Berg', 'Bearbeiten');

    expect(await screen.findByText('Partner-Maske')).toBeInTheDocument();
    expect(adresse()).toBe('/firmen/7/ansprechpartner/11/bearbeiten');
  });

  it('fragt vor dem Stilllegen einer Kachel nach und schaltet erst danach', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = firmaDoppel({ ...FIRMA, ansprechpartner: [ANNA] });

    renderSeite();
    await screen.findByTestId('innenkarten-raster');
    expect(screen.queryByTestId('innenkarte-zustand')).not.toBeInTheDocument();

    await waehle(nutzer, 'Aktionen für Anna Berg', 'Stilllegen');
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveAccessibleName('Stilllegen: Anna Berg');
    await nutzer.click(within(dialog).getByRole('button', { name: 'Stilllegen' }));

    expect(await screen.findByTestId('innenkarte-zustand')).toBeInTheDocument();
    expect(
      fetchMock.mock.calls.filter(([weg]) => weg === '/api/firmen/7/ansprechpartner/11/stilllegen'),
    ).toHaveLength(1);

    await waehle(nutzer, 'Aktionen für Anna Berg', 'Wieder aktivieren');

    await vi.waitFor(() => {
      expect(screen.queryByTestId('innenkarte-zustand')).not.toBeInTheDocument();
    });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('meldet, wenn das Schalten einer Kachel nicht durchgeht', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'GET /api/firmen/7': json(200, { ...FIRMA, ansprechpartner: [ANNA] }),
      'GET /api/firmen/7/vorgaenge': json(200, VORGAENGE),
      'POST /api/firmen/7/ansprechpartner/11/stilllegen': leer(500),
    });

    renderSeite();
    await screen.findByTestId('innenkarten-raster');
    await waehle(nutzer, 'Aktionen für Anna Berg', 'Stilllegen');
    await bestaetige(nutzer, 'Stilllegen');

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

    // Die Firma ist da, die Vorgaenge noch nicht — die Karte sagt es, statt leer zu bleiben.
    await screen.findByRole('heading', { level: 1, name: 'Beispiel GmbH' });
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
    expect(screen.getByRole('heading', { level: 1, name: 'Beispiel GmbH' })).toBeInTheDocument();
    expect(within(kopfkarte()).getByText(/Hauptstraße 1/)).toBeInTheDocument();
    expect(screen.getByTestId('innenkarten-raster')).toBeInTheDocument();
  });

  it('fragt die Vorgaenge nicht ab, wo die Kennung keine ist', async () => {
    const fetchMock = fetchNachPfad({});

    renderSeite('/firmen/keine-zahl');

    await screen.findByRole('alert');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('FirmaPage — der Pfad im Kopf (E6)', () => {
  it('meldet „Firmen" als Weg und den Namen der Firma als Endstufe', async () => {
    fetchNachPfad({
      'GET /api/firmen/7': json(200, FIRMA),
      'GET /api/firmen/7/vorgaenge': json(200, VORGAENGE),
    });

    renderMitKopf();

    const pfad = within(await screen.findByRole('navigation', { name: 'Pfad' }));
    expect(pfad.getByRole('link', { name: 'Firmen' })).toHaveAttribute('href', '/firmen');
    expect(await pfad.findByText('Beispiel GmbH')).toHaveAttribute('aria-current', 'page');
  });

  it('nennt die Endstufe „Firma", solange die Firma noch nicht gelesen ist', async () => {
    fetchNachPfad({ 'GET /api/firmen/7': leer(503), 'GET /api/firmen/7/vorgaenge': leer(503) });

    renderMitKopf();

    const pfad = within(await screen.findByRole('navigation', { name: 'Pfad' }));
    expect(pfad.getByText('Firma')).toHaveAttribute('aria-current', 'page');
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

describe('FirmaPage — Kopf und Tastatur', () => {
  it('laesst den Kopf des Rahmens ohne Aktion — die Hauptaktion steht in der Kopfkarte', async () => {
    fensterbreite(1440);
    fetchNachPfad({
      'GET /api/auth/me': json(200, KONTO),
      'GET /api/instance': json(200, { version: '0.1.3' }),
      'GET /api/firmen/7': json(200, FIRMA),
      'GET /api/firmen/7/vorgaenge': json(200, VORGAENGE),
    });

    renderMitTheme(
      <MemoryRouter initialEntries={['/firmen/7']}>
        <AuthProvider>
          <AppShell>
            <Routes>
              <Route path="/firmen/:id" element={<FirmaPage />} />
            </Routes>
          </AppShell>
        </AuthProvider>
      </MemoryRouter>,
    );

    await screen.findByRole('heading', { level: 1, name: 'Beispiel GmbH' });
    const kopf = within(screen.getByRole('banner'));
    expect(kopf.queryByRole('link', { name: 'Neuer Ansprechpartner' })).not.toBeInTheDocument();
    expect(
      within(kopfkarte()).getByRole('link', { name: 'Neuer Ansprechpartner' }),
    ).toBeInTheDocument();
  });

  it('fuehrt mit dem Tabulator ueber die Aktionen der Kopfkarte und dann die Kacheln', async () => {
    const nutzer = userEvent.setup();
    firmaDoppel({ ...FIRMA, ansprechpartner: [ANNA] });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: 'Beispiel GmbH' });

    const reihe = [
      screen.getByRole('link', { name: 'Bearbeiten' }),
      screen.getByRole('link', { name: 'Neuer Ansprechpartner' }),
      screen.getByRole('button', { name: 'Aktionen für Beispiel GmbH' }),
      screen.getByRole('link', { name: 'anna.berg@beispiel.de' }),
      screen.getByRole('link', { name: '+49 421 123456' }),
      screen.getByRole('button', { name: 'Aktionen für Anna Berg' }),
      screen.getByRole('link', { name: 'Ansprechpartner hinzufügen' }),
    ];
    for (const element of reihe) {
      await nutzer.tab();
      expect(element).toHaveFocus();
    }
  });
});
