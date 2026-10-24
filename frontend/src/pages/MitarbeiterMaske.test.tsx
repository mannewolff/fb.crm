import { screen, waitFor, within } from '@testing-library/react';
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
  version: 3,
};

const MANFRED = {
  id: 1,
  displayName: 'Manfred Wolff',
  email: 'info@mwolff.org',
  role: 'C_LEVEL',
  aktiv: true,
  version: 0,
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

/** Heute um die genannte Uhrzeit in der Ortszeit — die Meldung nennt dann nur die Uhrzeit. */
function heuteUm(stunde: number, minute: number): string {
  const zeitpunkt = new Date();
  zeitpunkt.setHours(stunde, minute, 0, 0);
  return zeitpunkt.toISOString();
}

/** Die Abweisung des Servers, wie `GlobalExceptionHandler` sie schreibt (Plan #415, E4, E15). */
function abweisung(geaendertVon: string): () => Response {
  return json(409, {
    title: 'Conflict',
    status: 409,
    detail: 'Dieser Eintrag wurde inzwischen geändert.',
    konflikt: { art: 'veraltet', geaendertAm: heuteUm(10, 42), eigenesFenster: false, geaendertVon },
  });
}

/** Antwortet der Reihe nach mit den Antworten — beim ersten Aufruf mit der ersten. */
function nacheinander(...antworten: readonly (() => Response)[]): () => Response {
  let aufruf = 0;
  return () => {
    const antwort = antworten[Math.min(aufruf, antworten.length - 1)];
    aufruf += 1;
    return antwort();
  };
}

/** Die Bedingungskoepfe aller Aufrufe des Wegs, in ihrer Reihenfolge. */
function vorgelegteStaende(
  fetchMock: ReturnType<typeof fetchNachPfad>,
  methode: string,
  pfad: string,
): (string | undefined)[] {
  return fetchMock.mock.calls
    .filter(([ziel, init]) => ziel === pfad && init?.method === methode)
    .map(([, init]) => (init?.headers as Record<string, string> | undefined)?.['If-Match']);
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

  it('legt beim Anlegen keinen Stand vor (#411, Kriterium 7)', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'POST /api/mitarbeitende': json(201, { ...ANNA, version: 0, begruessungMailVersandt: true }),
    });

    renderMaske('/mitarbeitende/neu');
    await ausfuellen(nutzer);
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    await screen.findByText('Liste');
    expect(vorgelegteStaende(fetchMock, 'POST', '/api/mitarbeitende')).toEqual([undefined]);
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

describe('MitarbeiterMaske — gleichzeitig arbeiten (#411, Kriterien 1 bis 4; Plan #415, E19)', () => {
  /** Bertas Stand: Sie hat Anna inzwischen umbenannt und zu ReadOnly gemacht. */
  const BERTAS_STAND = { ...ANNA, displayName: 'Anna Adler', role: 'READ_ONLY', version: 4 };

  it('legt beim Speichern den gelesenen Stand vor', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/mitarbeitende': json(200, { mitarbeitende: [MANFRED, ANNA] }),
      'PUT /api/mitarbeitende/2': json(200, { ...ANNA, version: 4 }),
    });

    renderMaske('/mitarbeitende/2/bearbeiten');
    await screen.findByRole('textbox', { name: /^Name/ });
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    await screen.findByText('Liste');
    expect(vorgelegteStaende(fetchMock, 'PUT', '/api/mitarbeitende/2')).toEqual(['"3"']);
  });

  it('Szenario: abgewiesenes Speichern — die Eingaben bleiben, Neu laden stellt sie daneben', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/mitarbeitende': nacheinander(
        json(200, { mitarbeitende: [MANFRED, ANNA] }),
        json(200, { mitarbeitende: [MANFRED, BERTAS_STAND] }),
      ),
      'PUT /api/mitarbeitende/2': nacheinander(
        abweisung('Berta'),
        json(200, { ...BERTAS_STAND, version: 5 }),
      ),
    });

    renderMaske('/mitarbeitende/2/bearbeiten');
    const name = await screen.findByRole('textbox', { name: /^Name/ });
    await nutzer.clear(name);
    await nutzer.type(name, 'Anna Müller');
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    // Abgewiesen: Die Meldung nennt Berta, und der eigene Name steht weiter im Feld.
    const meldung = await screen.findByRole('alert');
    expect(meldung).toHaveTextContent('Nicht ausgeführt');
    expect(meldung).toHaveTextContent('Berta hat diesen Eintrag inzwischen geändert (10:42).');
    expect(meldung).not.toHaveTextContent('Bitte später erneut versuchen');
    expect(screen.getByRole('textbox', { name: /^Name/ })).toHaveValue('Anna Müller');
    expect(adresse()).toBe('/mitarbeitende/2/bearbeiten');

    // Neu laden: Bertas Stand steht in der Maske, die eigenen Werte daneben zum Kopieren.
    await nutzer.click(screen.getByRole('button', { name: 'Neu laden' }));
    await waitFor(() => {
      expect(screen.getByRole('textbox', { name: /^Name/ })).toHaveValue('Anna Adler');
    });
    expect(screen.getByRole('radio', { name: 'ReadOnly' })).toBeChecked();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    const eigene = screen.getByRole('region', { name: 'Deine Eingaben' });
    expect(eigene).toHaveTextContent('Anna Müller');
    expect(eigene).toHaveTextContent('Employee');
    expect(within(eigene).getByText('E-Mail-Adresse')).toBeInTheDocument();

    // Auf Bertas Stand geht das Speichern durch; vorgelegt wird der neu geladene Stand.
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));
    await screen.findByText('Liste');
    expect(vorgelegteStaende(fetchMock, 'PUT', '/api/mitarbeitende/2')).toEqual(['"3"', '"4"']);
  });

  it('raeumt die eigenen Eingaben mit „Verwerfen" ab', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'GET /api/mitarbeitende': nacheinander(
        json(200, { mitarbeitende: [ANNA] }),
        json(200, { mitarbeitende: [BERTAS_STAND] }),
      ),
      'PUT /api/mitarbeitende/2': abweisung('Berta'),
    });

    renderMaske('/mitarbeitende/2/bearbeiten');
    await screen.findByRole('textbox', { name: /^Name/ });
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));
    await nutzer.click(await screen.findByRole('button', { name: 'Neu laden' }));
    const eigene = await screen.findByRole('region', { name: 'Deine Eingaben' });

    await nutzer.click(within(eigene).getByRole('button', { name: 'Verwerfen' }));

    expect(screen.queryByRole('region', { name: 'Deine Eingaben' })).not.toBeInTheDocument();
  });

  it.each([
    ['die Liste ausfaellt', leer(503)],
    ['das Konto in der Liste fehlt', json(200, { mitarbeitende: [MANFRED] })],
  ])('meldet beim Neu laden, wenn %s, und laesst die Konfliktmeldung stehen', async (_fall, zweite) => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'GET /api/mitarbeitende': nacheinander(json(200, { mitarbeitende: [ANNA] }), zweite),
      'PUT /api/mitarbeitende/2': abweisung('Berta'),
    });

    renderMaske('/mitarbeitende/2/bearbeiten');
    await screen.findByRole('textbox', { name: /^Name/ });
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));
    await nutzer.click(await screen.findByRole('button', { name: 'Neu laden' }));

    expect(
      await screen.findByText(
        'Der aktuelle Stand ist gerade nicht zu erreichen. Bitte erneut versuchen.',
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Neu laden' })).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Deine Eingaben' })).not.toBeInTheDocument();
  });
});
