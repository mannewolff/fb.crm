import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { KopfPfadProvider } from '../components/KopfPfad';
import { alsJson, fetchNachPfad, json, leer, problem } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';
import MitarbeiterMaske from './MitarbeiterMaske';

const ANNA = {
  id: 2,
  displayName: 'Anna Berg',
  email: 'anna@firma.de',
  role: 'EMPLOYEE',
  aktiv: true,
};

const MANFRED = {
  id: 1,
  displayName: 'Manfred Wolff',
  email: 'info@mwolff.org',
  role: 'C_LEVEL',
  aktiv: true,
};

const VERGEBEN = 'Diese Adresse traegt bereits ein anderes Konto.';
const LETZTER = 'Es muss mindestens ein aktiver C-Level bleiben.';

function Adresse() {
  const ort = useLocation();
  return <p data-testid="adresse">{ort.pathname}</p>;
}

function adresse() {
  return screen.getByTestId('adresse').textContent;
}

function renderMaske(start: string) {
  return renderMitTheme(
    <MemoryRouter initialEntries={[start]}>
      <KopfPfadProvider>
        <Routes>
          <Route path="/mitarbeitende" element={<p>Liste</p>} />
          <Route path="/mitarbeitende/neu" element={<MitarbeiterMaske />} />
          <Route path="/mitarbeitende/:id/bearbeiten" element={<MitarbeiterMaske />} />
        </Routes>
        <Adresse />
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

async function ausfuellen(nutzer: ReturnType<typeof userEvent.setup>) {
  await nutzer.type(screen.getByRole('textbox', { name: /^E-Mail-Adresse/ }), 'anna@firma.de');
  await nutzer.type(screen.getByRole('textbox', { name: /^Name/ }), 'Anna Berg');
  await nutzer.click(screen.getByRole('radio', { name: 'Employee' }));
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('MitarbeiterMaske — Anlegen', () => {
  it('Szenario: C-Level legt eine Mitarbeiterin an', async () => {
    const nutzer = userEvent.setup();
    let rumpf: unknown = null;
    fetchNachPfad({
      'POST /api/mitarbeitende': (gesendet) => {
        rumpf = alsJson(gesendet);
        return json(201, { ...ANNA, begruessungMailVersandt: true })();
      },
    });

    renderMaske('/mitarbeitende/neu');
    await ausfuellen(nutzer);
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(await screen.findByText('Liste')).toBeInTheDocument();
    expect(rumpf).toEqual({ email: 'anna@firma.de', displayName: 'Anna Berg', role: 'EMPLOYEE' });
    expect(adresse()).toBe('/mitarbeitende');
  });

  it('bietet die drei Rollen zur Wahl und waehlt keine vor', () => {
    renderMaske('/mitarbeitende/neu');

    const rollen = screen.getAllByRole('radio');
    expect(rollen.map((rolle) => rolle.getAttribute('value'))).toEqual([
      'C_LEVEL',
      'EMPLOYEE',
      'READ_ONLY',
    ]);
    expect(screen.getByRole('radio', { name: 'C-Level' })).not.toBeChecked();
    expect(screen.getByRole('radio', { name: 'Employee' })).not.toBeChecked();
    expect(screen.getByRole('radio', { name: 'ReadOnly' })).not.toBeChecked();
  });

  it('sagt, wenn keine Begruessungsmail hinausging, und bleibt stehen', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'POST /api/mitarbeitende': json(201, { ...ANNA, begruessungMailVersandt: false }),
    });

    renderMaske('/mitarbeitende/neu');
    await ausfuellen(nutzer);
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Das Konto für Anna Berg ist angelegt. Eine Begrüßungsmail ging nicht hinaus',
    );
    expect(adresse()).toBe('/mitarbeitende/neu');
    expect(screen.getByRole('link', { name: 'Zur Liste' })).toHaveAttribute(
      'href',
      '/mitarbeitende',
    );
  });

  it('verlangt alle drei Angaben, bevor etwas hinausgeht', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({});

    renderMaske('/mitarbeitende/neu');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(screen.getByText('Bitte eine E-Mail-Adresse angeben.')).toBeInTheDocument();
    expect(screen.getByText('Bitte einen Namen angeben.')).toBeInTheDocument();
    expect(screen.getByText('Bitte eine Rolle wählen.')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('zeigt die vergebene Adresse am Feld', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'POST /api/mitarbeitende': problem(409, VERGEBEN, { email: [VERGEBEN] }),
    });

    renderMaske('/mitarbeitende/neu');
    await ausfuellen(nutzer);
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(await screen.findByText(VERGEBEN)).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: /^E-Mail-Adresse/ })).toHaveAttribute(
      'aria-invalid',
      'true',
    );
  });

  it('meldet einen Ausfall beim Speichern', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({ 'POST /api/mitarbeitende': leer(500) });

    renderMaske('/mitarbeitende/neu');
    await ausfuellen(nutzer);
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Das Konto wurde nicht gespeichert. Bitte später erneut versuchen.',
    );
  });
});

describe('MitarbeiterMaske — Aendern', () => {
  it('legt die gespeicherten Werte vor und schickt die Aenderung', async () => {
    const nutzer = userEvent.setup();
    let rumpf: unknown = null;
    fetchNachPfad({
      'GET /api/mitarbeitende': json(200, { mitarbeitende: [MANFRED, ANNA] }),
      'PUT /api/mitarbeitende/2': (gesendet) => {
        rumpf = alsJson(gesendet);
        return json(200, { ...ANNA, role: 'READ_ONLY' })();
      },
    });

    renderMaske('/mitarbeitende/2/bearbeiten');

    const name = await screen.findByRole('textbox', { name: /^Name/ });
    expect(name).toHaveValue('Anna Berg');
    expect(screen.getByRole('radio', { name: 'Employee' })).toBeChecked();
    await nutzer.click(screen.getByRole('radio', { name: 'ReadOnly' }));
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(await screen.findByText('Liste')).toBeInTheDocument();
    expect(rumpf).toEqual({ email: 'anna@firma.de', displayName: 'Anna Berg', role: 'READ_ONLY' });
  });

  it('zeigt die Abweisung des letzten C-Level als Satz aus der Antwort', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'GET /api/mitarbeitende': json(200, { mitarbeitende: [MANFRED] }),
      'PUT /api/mitarbeitende/1': problem(409, LETZTER),
    });

    renderMaske('/mitarbeitende/1/bearbeiten');
    await nutzer.click(await screen.findByRole('radio', { name: 'Employee' }));
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(LETZTER);
    expect(adresse()).toBe('/mitarbeitende/1/bearbeiten');
  });

  it('meldet ein Konto, das es nicht gibt', async () => {
    fetchNachPfad({ 'GET /api/mitarbeitende': json(200, { mitarbeitende: [MANFRED] }) });

    renderMaske('/mitarbeitende/9/bearbeiten');

    expect(await screen.findByRole('alert')).toHaveTextContent('Dieses Konto gibt es nicht.');
  });

  it('geht mit einer Kennung, die keine ist, gar nicht erst ans Netz', async () => {
    const fetchMock = fetchNachPfad({});

    renderMaske('/mitarbeitende/abc/bearbeiten');

    expect(await screen.findByRole('alert')).toHaveTextContent('Dieses Konto gibt es nicht.');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('meldet einen Ausfall beim Lesen', async () => {
    fetchNachPfad({ 'GET /api/mitarbeitende': leer(500) });

    renderMaske('/mitarbeitende/2/bearbeiten');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Das Konto ist gerade nicht zu erreichen. Bitte später erneut versuchen.',
    );
  });
});
