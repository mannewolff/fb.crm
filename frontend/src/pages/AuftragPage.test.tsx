import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import AuftragPage from './AuftragPage';
import KopfPfad, { KopfPfadProvider } from '../components/KopfPfad';
import { fetchNachPfad, json, leer, problem } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';

const AUFWAND = {
  bezeichnung: 'Konzeption',
  abrechnungsmodus: 'AUFWAND',
  menge: 2.5,
  einheit: 'PERSONENTAG',
  einzelpreis: 1000.01,
  stundenJePersonentag: 7.5,
  betrag: 2500.03,
};

const FESTPREIS = {
  bezeichnung: 'Einrichtung',
  abrechnungsmodus: 'FESTPREIS',
  menge: 1,
  einheit: 'PAUSCHAL',
  einzelpreis: 500,
  stundenJePersonentag: null,
  betrag: 500,
};

const AUFTRAG = {
  id: 3,
  vorgangId: 5,
  angebotId: 9,
  angebotNummer: 'A-2026-001',
  nummer: 'AU-2026-001',
  status: 'IN_ARBEIT',
  auftragDatum: '2026-09-28',
  kundenbestellnummer: 'PO-4711',
  leistungAb: '2026-10-01',
  leistungBis: '2026-12-31',
  positionen: [AUFWAND, FESTPREIS],
  summe: 3000.03,
};

function Adresse() {
  const ort = useLocation();
  return <p data-testid="adresse">{ort.pathname}</p>;
}

function renderSeite(start = '/vorgaenge/5/auftraege/3') {
  return renderMitTheme(
    <MemoryRouter initialEntries={[start]}>
      <KopfPfadProvider>
        <KopfPfad />
        <Routes>
          <Route path="/vorgaenge/:id/auftraege/:auftragId" element={<AuftragPage />} />
          <Route path="/vorgaenge/:id/auftraege/:auftragId/bearbeiten" element={<p>Maske</p>} />
          <Route path="/vorgaenge/:id/angebote/:angebotId" element={<p>Angebotsansicht</p>} />
          <Route path="/vorgaenge/:id" element={<p>Vorgangsseite</p>} />
        </Routes>
        <Adresse />
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

function aktionen() {
  return within(screen.getByTestId('auftrag-aktionen'));
}

/** Die Zeile der Positionstafel, in der die Bezeichnung steht. */
function positionszeile(bezeichnung: string) {
  // Der Name einer Tabellenzeile ist ihr Text — die Bezeichnung steht am Anfang.
  return within(screen.getByRole('row', { name: new RegExp(`^${bezeichnung}`, 'u') }));
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('AuftragPage — was der Auftrag zeigt (Kriterium 3)', () => {
  it('traegt die Nummer als die eine Ueberschrift samt Statuschip als Wort', async () => {
    fetchNachPfad({ 'GET /api/auftraege/3': json(200, AUFTRAG) });

    renderSeite();

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Auftrag AU-2026-001' }),
    ).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    expect(screen.getByTestId('chip')).toHaveTextContent('In Arbeit');
  });

  it('stellt alle Positionen mit Betrag und die Summe', async () => {
    fetchNachPfad({ 'GET /api/auftraege/3': json(200, AUFTRAG) });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: 'Auftrag AU-2026-001' });

    expect(screen.getAllByRole('columnheader').map((kopf) => kopf.textContent)).toEqual([
      'Bezeichnung',
      'Abrechnung',
      'Menge',
      'Einheit',
      'Einzelpreis',
      'Std. je PT',
      'Betrag',
    ]);
    expect(positionszeile('Konzeption').getByText('2.500,03 €')).toBeInTheDocument();
    // Einzelpreis und Betrag der Festpreisposition sind beide 500,00 €.
    expect(positionszeile('Einrichtung').getAllByText('500,00 €')).toHaveLength(2);
    expect(screen.getByTestId('auftrag-summe')).toHaveTextContent('3.000,03 €');
  });

  it('zeigt „Stunden je Personentag" nur an der Aufwandsposition', async () => {
    fetchNachPfad({ 'GET /api/auftraege/3': json(200, AUFTRAG) });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: 'Auftrag AU-2026-001' });

    // Die sechste Spalte traegt die Stunden je Personentag.
    expect(positionszeile('Konzeption').getAllByRole('cell')[5]).toHaveTextContent('7,50');
    const festpreis = positionszeile('Einrichtung').getAllByRole('cell')[5];
    expect(festpreis).toHaveTextContent('—');
    expect(within(festpreis).getByLabelText('keine Angabe')).toBeInTheDocument();
  });

  it('nennt Datum, Bestellnummer und Leistungszeitraum in den Feldern', async () => {
    fetchNachPfad({ 'GET /api/auftraege/3': json(200, AUFTRAG) });

    renderSeite();

    const felder = within(await screen.findByTestId('auftrag-felder'));
    expect(felder.getByText('28.09.2026')).toBeInTheDocument();
    expect(felder.getByText('PO-4711')).toBeInTheDocument();
    expect(felder.getByText('01.10.2026 – 31.12.2026')).toBeInTheDocument();
  });

  it('sagt „keine", wenn Bestellnummer und Zeitraum fehlen', async () => {
    fetchNachPfad({
      'GET /api/auftraege/3': json(200, {
        ...AUFTRAG,
        kundenbestellnummer: null,
        leistungAb: null,
        leistungBis: null,
      }),
    });

    renderSeite();

    const felder = within(await screen.findByTestId('auftrag-felder'));
    expect(felder.getByText('keine')).toBeInTheDocument();
    expect(felder.getByText('nicht angegeben')).toBeInTheDocument();
  });

  it('fuehrt mit „Angebot A-2026-001" auf die Angebotsansicht — die Nummer, nicht die Kennung', async () => {
    fetchNachPfad({ 'GET /api/auftraege/3': json(200, AUFTRAG) });

    renderSeite();

    const weg = await screen.findByRole('link', { name: 'Angebot A-2026-001' });
    expect(weg).toHaveAttribute('href', '/vorgaenge/5/angebote/9');
    await userEvent.click(weg);
    expect(screen.getByText('Angebotsansicht')).toBeInTheDocument();
  });

  it('nennt ein Angebot ohne Nummer einfach „Angebot"', async () => {
    fetchNachPfad({ 'GET /api/auftraege/3': json(200, { ...AUFTRAG, angebotNummer: null }) });

    renderSeite();

    expect(await screen.findByRole('link', { name: 'Angebot' })).toHaveAttribute(
      'href',
      '/vorgaenge/5/angebote/9',
    );
  });

  it('traegt den Auftrag als Endstufe des Kopfpfads', async () => {
    fetchNachPfad({ 'GET /api/auftraege/3': json(200, AUFTRAG) });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: 'Auftrag AU-2026-001' });

    const pfad = within(screen.getByRole('navigation', { name: 'Pfad' }));
    expect(pfad.getByRole('link', { name: 'Vorgang' })).toHaveAttribute('href', '/vorgaenge/5');
    expect(pfad.getByText('Auftrag AU-2026-001')).toBeInTheDocument();
  });
});

describe('AuftragPage — Bearbeiten und Loeschen (Kriterien 7, 15)', () => {
  it('fuehrt mit „Bearbeiten" in die Pflege-Maske', async () => {
    fetchNachPfad({ 'GET /api/auftraege/3': json(200, AUFTRAG) });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: 'Auftrag AU-2026-001' });

    expect(aktionen().getByRole('link', { name: 'Bearbeiten' })).toHaveAttribute(
      'href',
      '/vorgaenge/5/auftraege/3/bearbeiten',
    );
  });

  it('legt „Löschen" ins ⋯-Menue und loescht nicht, wenn die Rueckfrage abgebrochen wird', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/auftraege/3': json(200, AUFTRAG),
      'DELETE /api/auftraege/3': leer(204),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: 'Auftrag AU-2026-001' });
    expect(aktionen().queryByRole('button', { name: 'Löschen' })).not.toBeInTheDocument();
    await nutzer.click(aktionen().getByRole('button', { name: 'Weitere Aktionen' }));
    await nutzer.click(screen.getByRole('menuitem', { name: 'Löschen' }));

    const dialog = within(await screen.findByRole('dialog'));
    expect(dialog.getByText(/verschwindet samt seinen Positionen/)).toBeInTheDocument();
    await nutzer.click(dialog.getByRole('button', { name: 'Abbrechen' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalledWith(
      '/api/auftraege/3',
      expect.objectContaining({ method: 'DELETE' }),
    );
    expect(screen.getByTestId('adresse')).toHaveTextContent('/vorgaenge/5/auftraege/3');
  });

  it('loescht nach bestaetigter Rueckfrage und fuehrt auf den Vorgang', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/auftraege/3': json(200, AUFTRAG),
      'DELETE /api/auftraege/3': leer(204),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: 'Auftrag AU-2026-001' });
    await nutzer.click(aktionen().getByRole('button', { name: 'Weitere Aktionen' }));
    await nutzer.click(screen.getByRole('menuitem', { name: 'Löschen' }));
    await nutzer.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Löschen' }),
    );

    expect(await screen.findByText('Vorgangsseite')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/auftraege/3',
      expect.objectContaining({ method: 'DELETE' }),
    );
  });

  it('meldet, wenn das Loeschen nicht durchgeht', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'GET /api/auftraege/3': json(200, AUFTRAG),
      'DELETE /api/auftraege/3': leer(500),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: 'Auftrag AU-2026-001' });
    await nutzer.click(aktionen().getByRole('button', { name: 'Weitere Aktionen' }));
    await nutzer.click(screen.getByRole('menuitem', { name: 'Löschen' }));
    await nutzer.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Löschen' }),
    );

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht gelöscht');
    expect(screen.getByTestId('adresse')).toHaveTextContent('/vorgaenge/5/auftraege/3');
  });
});

describe('AuftragPage — Lade-, Fehler- und Leerzustand', () => {
  it('sagt, dass der Auftrag geladen wird', () => {
    fetchNachPfad({ 'GET /api/auftraege/3': () => new Promise<Response>(() => undefined) });

    renderSeite();

    expect(screen.getByText('Der Auftrag wird geladen …')).toBeInTheDocument();
  });

  it('meldet einen unbekannten Auftrag', async () => {
    fetchNachPfad({ 'GET /api/auftraege/3': problem(404, 'Nicht gefunden') });

    renderSeite();

    expect(await screen.findByText('Diesen Auftrag gibt es nicht.')).toBeInTheDocument();
  });

  it('meldet eine Kennung, die keine ist, ohne das Netz zu fragen', async () => {
    const fetchMock = fetchNachPfad({});

    renderSeite('/vorgaenge/5/auftraege/abc');

    expect(await screen.findByText('Diesen Auftrag gibt es nicht.')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('meldet einen Ausfall', async () => {
    fetchNachPfad({ 'GET /api/auftraege/3': leer(503) });

    renderSeite();

    expect(
      await screen.findByText('Der Auftrag ist gerade nicht zu erreichen. Bitte später erneut versuchen.'),
    ).toBeInTheDocument();
  });

  it('sagt es, wenn der Auftrag keine Position traegt', async () => {
    fetchNachPfad({ 'GET /api/auftraege/3': json(200, { ...AUFTRAG, positionen: [], summe: 0 }) });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: 'Auftrag AU-2026-001' });

    expect(screen.getByRole('status')).toHaveTextContent('Keine Position.');
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
});
