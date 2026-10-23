import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { KopfPfadProvider } from '../components/KopfPfad';
import { fetchNachPfad, json, leer, problem } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';
import MitarbeitendePage from './MitarbeitendePage';

const MANFRED = {
  id: 1,
  displayName: 'Manfred Wolff',
  email: 'info@mwolff.org',
  role: 'C_LEVEL',
  aktiv: true,
};

const ANNA = {
  id: 2,
  displayName: 'Anna Berg',
  email: 'anna@firma.de',
  role: 'EMPLOYEE',
  aktiv: true,
};

const BEN = {
  id: 3,
  displayName: 'Ben Kurz',
  email: 'ben@firma.de',
  role: 'READ_ONLY',
  aktiv: false,
};

const LETZTER = 'Es muss mindestens ein aktiver C-Level bleiben.';

function Adresse() {
  const ort = useLocation();
  return <p data-testid="adresse">{ort.pathname}</p>;
}

function renderListe() {
  return renderMitTheme(
    <MemoryRouter initialEntries={['/mitarbeitende']}>
      <KopfPfadProvider>
        <Routes>
          <Route path="/mitarbeitende" element={<MitarbeitendePage />} />
          <Route path="/mitarbeitende/:id/bearbeiten" element={<p>Maske</p>} />
        </Routes>
        <Adresse />
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

/** Die Zeile eines Kontos, gefunden ueber seinen Namen. */
async function zeileVon(name: string) {
  return screen.findByRole('row', { name: new RegExp(name, 'u') });
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('MitarbeitendePage', () => {
  it('Szenario: C-Level legt eine Mitarbeiterin an', async () => {
    fetchNachPfad({
      'GET /api/mitarbeitende': json(200, { mitarbeitende: [ANNA, MANFRED] }),
    });

    renderListe();

    const anna = await zeileVon('Anna Berg');
    expect(within(anna).getByText('anna@firma.de')).toBeInTheDocument();
    expect(within(anna).getByText('Employee')).toBeInTheDocument();
    expect(within(anna).getByText('Aktiv')).toBeInTheDocument();
  });

  it('zeigt Namen, Adresse, Rolle und Zustand je Konto, stillgelegte als Wort', async () => {
    fetchNachPfad({
      'GET /api/mitarbeitende': json(200, { mitarbeitende: [ANNA, BEN, MANFRED] }),
    });

    renderListe();

    const tafel = await screen.findByRole('table', { name: 'Mitarbeitende' });
    expect(within(tafel).getAllByRole('columnheader').map((kopf) => kopf.textContent)).toEqual([
      'Name',
      'E-Mail-Adresse',
      'Rolle',
      'Zustand',
      'Aktionen',
    ]);
    const ben = await zeileVon('Ben Kurz');
    expect(within(ben).getByText('ReadOnly')).toBeInTheDocument();
    expect(within(ben).getByText('Stillgelegt')).toBeInTheDocument();
    expect(within(await zeileVon('Manfred Wolff')).getByText('C-Level')).toBeInTheDocument();
  });

  it('fuehrt mit „Neues Konto" zur Maske', async () => {
    fetchNachPfad({ 'GET /api/mitarbeitende': json(200, { mitarbeitende: [MANFRED] }) });

    renderListe();

    expect(await screen.findByRole('link', { name: 'Neues Konto' })).toHaveAttribute(
      'href',
      '/mitarbeitende/neu',
    );
  });

  it('fuehrt mit „Bearbeiten" zur Maske des Kontos', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({ 'GET /api/mitarbeitende': json(200, { mitarbeitende: [ANNA] }) });

    renderListe();

    await nutzer.click(await screen.findByRole('button', { name: 'Aktionen für Anna Berg' }));
    await nutzer.click(screen.getByRole('menuitem', { name: 'Bearbeiten' }));

    expect(screen.getByTestId('adresse')).toHaveTextContent('/mitarbeitende/2/bearbeiten');
  });

  it('legt nach der Rueckfrage still und liest die Liste neu', async () => {
    const nutzer = userEvent.setup();
    let stillgelegt = false;
    const fetchMock = fetchNachPfad({
      'GET /api/mitarbeitende': () =>
        json(200, { mitarbeitende: [{ ...ANNA, aktiv: !stillgelegt }] })(),
      'POST /api/mitarbeitende/2/stilllegen': () => {
        stillgelegt = true;
        return leer(204)();
      },
    });

    renderListe();

    await nutzer.click(await screen.findByRole('button', { name: 'Aktionen für Anna Berg' }));
    await nutzer.click(screen.getByRole('menuitem', { name: 'Stilllegen' }));
    const dialog = screen.getByRole('dialog', { name: 'Stilllegen: Anna Berg' });
    await nutzer.click(within(dialog).getByRole('button', { name: 'Stilllegen' }));

    expect(await within(await zeileVon('Anna Berg')).findByText('Stillgelegt')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/mitarbeitende/2/stilllegen', expect.anything());
  });

  it('nimmt ohne Rueckfrage wieder in Betrieb', async () => {
    const nutzer = userEvent.setup();
    let aktiv = false;
    fetchNachPfad({
      'GET /api/mitarbeitende': () => json(200, { mitarbeitende: [{ ...BEN, aktiv }] })(),
      'POST /api/mitarbeitende/3/aktivieren': () => {
        aktiv = true;
        return leer(204)();
      },
    });

    renderListe();

    await nutzer.click(await screen.findByRole('button', { name: 'Aktionen für Ben Kurz' }));
    await nutzer.click(screen.getByRole('menuitem', { name: 'Wieder aktivieren' }));

    expect(await within(await zeileVon('Ben Kurz')).findByText('Aktiv')).toBeInTheDocument();
  });

  it('zeigt die Abweisung des letzten C-Level als Satz aus der Antwort', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'GET /api/mitarbeitende': json(200, { mitarbeitende: [MANFRED] }),
      'POST /api/mitarbeitende/1/stilllegen': problem(409, LETZTER),
    });

    renderListe();

    await nutzer.click(await screen.findByRole('button', { name: 'Aktionen für Manfred Wolff' }));
    await nutzer.click(screen.getByRole('menuitem', { name: 'Stilllegen' }));
    await nutzer.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: 'Stilllegen' }),
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(LETZTER);
    expect(within(await zeileVon('Manfred Wolff')).getByText('Aktiv')).toBeInTheDocument();
  });

  it('meldet einen Ausfall beim Schalten mit dem eigenen Satz', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'GET /api/mitarbeitende': json(200, { mitarbeitende: [BEN] }),
      'POST /api/mitarbeitende/3/aktivieren': leer(500),
    });

    renderListe();

    await nutzer.click(await screen.findByRole('button', { name: 'Aktionen für Ben Kurz' }));
    await nutzer.click(screen.getByRole('menuitem', { name: 'Wieder aktivieren' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Der Stand des Kontos wurde nicht geändert. Bitte später erneut versuchen.',
    );
  });

  it('meldet einen Ausfall der Liste ohne technische Einzelheiten', async () => {
    fetchNachPfad({ 'GET /api/mitarbeitende': leer(500) });

    renderListe();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Die Konten sind gerade nicht zu erreichen. Bitte später erneut versuchen.',
    );
  });
});
