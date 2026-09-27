import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import AngebotMaske from './AngebotMaske';
import { KopfPfadProvider } from '../components/KopfPfad';
import { fetchNachPfad, json, leer, problem } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';

/** Eine Position, wie Jackson sie schreibt. */
const POSITION = {
  bezeichnung: 'Konzeption',
  abrechnungsmodus: 'AUFWAND',
  menge: 2.5,
  einheit: 'PERSONENTAG',
  einzelpreis: 1000.01,
  betrag: 2500.03,
};

const ENTWURF = {
  id: 9,
  vorgangId: 5,
  nummer: null,
  stand: 'ENTWURF',
  angebotDatum: '2026-09-24',
  gueltigBis: '2026-10-24',
  leistungsbeschreibung: 'Neue Website',
  zahlungsbedingungen: 'Zahlbar in 14 Tagen',
  versendetAm: null,
  reaktionAm: null,
  positionen: [POSITION],
  summe: 2500.03,
};

const LISTE = {
  angebote: [
    { id: 12, nummer: null, stand: 'ENTWURF', angebotDatum: '2026-09-26', gueltigBis: '2026-10-26', summe: 0 },
    {
      id: 9,
      nummer: 'A-2026-001',
      stand: 'VERSENDET',
      angebotDatum: '2026-09-24',
      gueltigBis: '2026-10-24',
      summe: 2500.03,
    },
  ],
};

/** Die Adresse, an der sich ablesen laesst, wohin ein Weg gefuehrt hat. */
function Adresse() {
  const ort = useLocation();
  return <p data-testid="adresse">{ort.pathname}</p>;
}

function renderMaske(start: string) {
  return renderMitTheme(
    <MemoryRouter initialEntries={[start]}>
      <KopfPfadProvider>
        <Routes>
          <Route path="/vorgaenge/:id/angebote/neu" element={<AngebotMaske />} />
          <Route
            path="/vorgaenge/:id/angebote/:angebotId/bearbeiten"
            element={<AngebotMaske />}
          />
          <Route path="/vorgaenge/:id/angebote/:angebotId" element={<p>Angebotsansicht</p>} />
          <Route path="/vorgaenge/:id" element={<p>Vorgangsseite</p>} />
        </Routes>
        <Adresse />
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

function gruppe(nummer: number) {
  return within(screen.getByRole('group', { name: `Position ${String(nummer)}` }));
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('AngebotMaske — Anlegen mit Vorlagewahl (Kriterien 2, 8)', () => {
  it('traegt genau eine Ueberschrift der ersten Ebene', async () => {
    fetchNachPfad({ 'GET /api/vorgaenge/5/angebote': json(200, LISTE) });

    renderMaske('/vorgaenge/5/angebote/neu');

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Neues Angebot' }),
    ).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
  });

  it('stellt die Angebote des Vorgangs als Vorlage zur Wahl', async () => {
    fetchNachPfad({ 'GET /api/vorgaenge/5/angebote': json(200, LISTE) });

    renderMaske('/vorgaenge/5/angebote/neu');

    const wahl = await screen.findByRole('combobox', { name: 'Vorlage' });
    expect(within(wahl).getAllByRole('option').map((eintrag) => eintrag.textContent)).toEqual([
      '— keine —',
      'Entwurf vom 26.09.2026',
      'A-2026-001 vom 24.09.2026',
    ]);
  });

  it('legt ohne Vorlage an und fuehrt in die Maske des neuen Entwurfs', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/vorgaenge/5/angebote': json(200, LISTE),
      'POST /api/vorgaenge/5/angebote': json(201, ENTWURF),
      'GET /api/angebote/9': json(200, ENTWURF),
    });

    renderMaske('/vorgaenge/5/angebote/neu');
    await screen.findByRole('combobox', { name: 'Vorlage' });
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Angebot bearbeiten' }))
      .toBeInTheDocument();
    expect(screen.getByTestId('adresse')).toHaveTextContent('/vorgaenge/5/angebote/9/bearbeiten');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/vorgaenge/5/angebote',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ vorlageAngebotId: null }) }),
    );
  });

  it('legt mit der gewaehlten Vorlage an (Kriterium 8)', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/vorgaenge/5/angebote': json(200, LISTE),
      'POST /api/vorgaenge/5/angebote': json(201, ENTWURF),
      'GET /api/angebote/9': json(200, ENTWURF),
    });

    renderMaske('/vorgaenge/5/angebote/neu');
    await nutzer.selectOptions(await screen.findByRole('combobox', { name: 'Vorlage' }), '9');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/vorgaenge/5/angebote',
      expect.objectContaining({ body: JSON.stringify({ vorlageAngebotId: 9 }) }),
    );
  });

  it('meldet, wenn am abgeschlossenen Vorgang nicht angelegt werden darf (Kriterium 9)', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'GET /api/vorgaenge/5/angebote': json(200, LISTE),
      'POST /api/vorgaenge/5/angebote': leer(409),
    });

    renderMaske('/vorgaenge/5/angebote/neu');
    await screen.findByRole('combobox', { name: 'Vorlage' });
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht angelegt');
  });

  it('meldet den Ausfall beim Holen der Vorlagen', async () => {
    fetchNachPfad({ 'GET /api/vorgaenge/5/angebote': leer(500) });

    renderMaske('/vorgaenge/5/angebote/neu');

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht zu erreichen');
  });

  it('fuehrt mit „Abbrechen" zurueck auf den Vorgang', async () => {
    fetchNachPfad({ 'GET /api/vorgaenge/5/angebote': json(200, LISTE) });

    renderMaske('/vorgaenge/5/angebote/neu');

    expect(await screen.findByRole('link', { name: 'Abbrechen' })).toHaveAttribute(
      'href',
      '/vorgaenge/5',
    );
  });

  it('faengt eine unsinnige Vorgangskennung ab, bevor sie an die Schnittstelle geht', async () => {
    const fetchMock = fetchNachPfad({});

    renderMaske('/vorgaenge/keine-zahl/angebote/neu');

    expect(await screen.findByRole('alert')).toHaveTextContent('gibt es nicht');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('AngebotMaske — den Entwurf bearbeiten (Kriterien 3, 6)', () => {
  it('belegt die Felder aus dem Entwurf und nennt das Angebotsdatum', async () => {
    fetchNachPfad({ 'GET /api/angebote/9': json(200, ENTWURF) });

    renderMaske('/vorgaenge/5/angebote/9/bearbeiten');

    expect(await screen.findByLabelText(/^Gültig bis/)).toHaveValue('2026-10-24');
    expect(screen.getByRole('textbox', { name: 'Leistungsbeschreibung' })).toHaveValue(
      'Neue Website',
    );
    expect(screen.getByRole('textbox', { name: 'Zahlungsbedingungen' })).toHaveValue(
      'Zahlbar in 14 Tagen',
    );
    // Das Angebotsdatum entsteht beim Anlegen und ist kein Feld des Entwurfs (E8).
    expect(screen.getByTestId('angebot-datum')).toHaveTextContent('24.09.2026');
    expect(gruppe(1).getByRole('textbox', { name: 'Bezeichnung' })).toHaveValue('Konzeption');
    expect(gruppe(1).getByRole('textbox', { name: 'Menge' })).toHaveValue('2,50');
    expect(gruppe(1).getByRole('textbox', { name: 'Einzelpreis (netto)' })).toHaveValue('1000,01');
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
  });

  it('zeigt die Summe und rechnet beim Tippen mit (Kriterium 5)', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({ 'GET /api/angebote/9': json(200, ENTWURF) });

    renderMaske('/vorgaenge/5/angebote/9/bearbeiten');
    await screen.findByLabelText(/^Gültig bis/);

    expect(screen.getByTestId('angebot-summe')).toHaveTextContent('2.500,03 €');

    const menge = gruppe(1).getByRole('textbox', { name: 'Menge' });
    await nutzer.clear(menge);
    await nutzer.type(menge, '2');

    expect(screen.getByTestId('angebot-summe')).toHaveTextContent('2.000,02 €');
  });

  it('fuegt eine Position hinzu, loescht und verschiebt sie (Kriterium 6)', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({ 'GET /api/angebote/9': json(200, ENTWURF) });

    renderMaske('/vorgaenge/5/angebote/9/bearbeiten');
    await screen.findByLabelText(/^Gültig bis/);

    await nutzer.click(screen.getByRole('button', { name: 'Position hinzufügen' }));
    await nutzer.type(gruppe(2).getByRole('textbox', { name: 'Bezeichnung' }), 'Betreuung');
    expect(screen.getAllByRole('group')).toHaveLength(2);

    await nutzer.click(gruppe(2).getByRole('button', { name: 'Position 2 nach oben' }));
    expect(gruppe(1).getByRole('textbox', { name: 'Bezeichnung' })).toHaveValue('Betreuung');
    expect(gruppe(2).getByRole('textbox', { name: 'Bezeichnung' })).toHaveValue('Konzeption');

    await nutzer.click(gruppe(1).getByRole('button', { name: 'Position 1 nach unten' }));
    expect(gruppe(1).getByRole('textbox', { name: 'Bezeichnung' })).toHaveValue('Konzeption');

    await nutzer.click(gruppe(2).getByRole('button', { name: 'Position 2 löschen' }));
    expect(screen.getAllByRole('group')).toHaveLength(1);
    expect(gruppe(1).getByRole('textbox', { name: 'Bezeichnung' })).toHaveValue('Konzeption');
  });

  it('sagt es, wenn der Entwurf noch keine Position hat', async () => {
    fetchNachPfad({
      'GET /api/angebote/9': json(200, { ...ENTWURF, positionen: [], summe: 0 }),
    });

    renderMaske('/vorgaenge/5/angebote/9/bearbeiten');

    expect(await screen.findByRole('status')).toHaveTextContent('Noch keine Position');
    expect(screen.getByTestId('angebot-summe')).toHaveTextContent('0,00 €');
  });

  it('schickt Texte und die vollstaendige Liste in der gezeigten Reihenfolge (E8)', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/angebote/9': json(200, ENTWURF),
      'PUT /api/angebote/9': json(200, ENTWURF),
    });

    renderMaske('/vorgaenge/5/angebote/9/bearbeiten');
    await screen.findByLabelText(/^Gültig bis/);
    await nutzer.click(screen.getByRole('button', { name: 'Position hinzufügen' }));
    await nutzer.type(gruppe(2).getByRole('textbox', { name: 'Bezeichnung' }), 'Betreuung');
    await nutzer.selectOptions(gruppe(2).getByRole('combobox', { name: 'Abrechnung' }), 'FESTPREIS');
    await nutzer.click(gruppe(2).getByRole('button', { name: 'Position 2 nach oben' }));
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    await screen.findByRole('status');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/9',
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({
          gueltigBis: '2026-10-24',
          leistungsbeschreibung: 'Neue Website',
          zahlungsbedingungen: 'Zahlbar in 14 Tagen',
          positionen: [
            {
              bezeichnung: 'Betreuung',
              abrechnungsmodus: 'FESTPREIS',
              menge: '1.00',
              einheit: 'PAUSCHAL',
              einzelpreis: '0.00',
            },
            {
              bezeichnung: 'Konzeption',
              abrechnungsmodus: 'AUFWAND',
              menge: '2.50',
              einheit: 'PERSONENTAG',
              einzelpreis: '1000.01',
            },
          ],
        }),
      }),
    );
  });

  it('schickt einen geleerten Text als „keine Angabe" und nicht als leere Zeichenkette', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/angebote/9': json(200, ENTWURF),
      'PUT /api/angebote/9': json(200, ENTWURF),
    });

    renderMaske('/vorgaenge/5/angebote/9/bearbeiten');
    await nutzer.clear(await screen.findByRole('textbox', { name: 'Leistungsbeschreibung' }));
    await nutzer.clear(screen.getByRole('textbox', { name: 'Zahlungsbedingungen' }));
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    await screen.findByRole('status');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/9',
      expect.objectContaining({
        body: JSON.stringify({
          gueltigBis: '2026-10-24',
          leistungsbeschreibung: null,
          zahlungsbedingungen: null,
          positionen: [
            {
              bezeichnung: 'Konzeption',
              abrechnungsmodus: 'AUFWAND',
              menge: '2.50',
              einheit: 'PERSONENTAG',
              einzelpreis: '1000.01',
            },
          ],
        }),
      }),
    );
  });

  it('nimmt nach dem Speichern die Antwort und nicht die eigene Rechnung', async () => {
    const nutzer = userEvent.setup();
    // Der Server schreibt die Menge auf 3,00 fort und rechnet die Summe neu. Was danach in der
    // Maske steht, kommt von dort (AngebotController: „ohne zweiten Aufruf").
    fetchNachPfad({
      'GET /api/angebote/9': json(200, ENTWURF),
      'PUT /api/angebote/9': json(200, {
        ...ENTWURF,
        gueltigBis: '2026-11-30',
        positionen: [{ ...POSITION, menge: 3, betrag: 3000.03 }],
        summe: 3000.03,
      }),
    });

    renderMaske('/vorgaenge/5/angebote/9/bearbeiten');
    await screen.findByLabelText(/^Gültig bis/);
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Gespeichert');
    expect(gruppe(1).getByRole('textbox', { name: 'Menge' })).toHaveValue('3,00');
    expect(screen.getByTestId('angebot-summe')).toHaveTextContent('3.000,03 €');
    expect(screen.getByLabelText(/^Gültig bis/)).toHaveValue('2026-11-30');
  });

  it('fuehrt mit „Zum Angebot" auf die Angebotsansicht', async () => {
    fetchNachPfad({ 'GET /api/angebote/9': json(200, ENTWURF) });

    renderMaske('/vorgaenge/5/angebote/9/bearbeiten');

    expect(await screen.findByRole('link', { name: 'Zum Angebot' })).toHaveAttribute(
      'href',
      '/vorgaenge/5/angebote/9',
    );
  });
});

describe('AngebotMaske — was nicht geht', () => {
  it('weist ein versendetes Angebot ab, statt es zur Bearbeitung zu oeffnen (Kriterium 13)', async () => {
    fetchNachPfad({
      'GET /api/angebote/9': json(200, { ...ENTWURF, nummer: 'A-2026-001', stand: 'VERSENDET' }),
    });

    renderMaske('/vorgaenge/5/angebote/9/bearbeiten');

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht mehr änderbar');
    expect(screen.queryByRole('button', { name: 'Speichern' })).not.toBeInTheDocument();
  });

  it('meldet ein unbekanntes Angebot', async () => {
    fetchNachPfad({ 'GET /api/angebote/9': leer(404) });

    renderMaske('/vorgaenge/5/angebote/9/bearbeiten');

    expect(await screen.findByRole('alert')).toHaveTextContent('gibt es nicht');
  });

  it('meldet den Ausfall beim Lesen', async () => {
    fetchNachPfad({ 'GET /api/angebote/9': leer(503) });

    renderMaske('/vorgaenge/5/angebote/9/bearbeiten');

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht zu erreichen');
  });

  it('faengt eine unsinnige Angebotskennung ab, bevor sie an die Schnittstelle geht', async () => {
    const fetchMock = fetchNachPfad({});

    renderMaske('/vorgaenge/5/angebote/keine-zahl/bearbeiten');

    expect(await screen.findByRole('alert')).toHaveTextContent('gibt es nicht');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('haelt das Speichern an, solange die Menge keine Zahl ist', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ 'GET /api/angebote/9': json(200, ENTWURF) });

    renderMaske('/vorgaenge/5/angebote/9/bearbeiten');
    const menge = await screen.findByRole('textbox', { name: 'Menge' });
    await nutzer.clear(menge);
    await nutzer.type(menge, '1,234');
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(screen.getByTestId('angebot-summe')).toHaveTextContent('—');
    expect(screen.getByRole('status')).toHaveTextContent('kann nicht gespeichert werden');
    // Nur das Lesen ist hinausgegangen: Eine halbe Liste wird nicht geschickt (E8).
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('haelt das Speichern an, solange der Einzelpreis keine Zahl ist', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ 'GET /api/angebote/9': json(200, ENTWURF) });

    renderMaske('/vorgaenge/5/angebote/9/bearbeiten');
    await nutzer.clear(await screen.findByRole('textbox', { name: 'Einzelpreis (netto)' }));
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(screen.getByTestId('angebot-summe')).toHaveTextContent('—');
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('verlangt eine Gueltigkeit, ohne das Netz zu bemuehen', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ 'GET /api/angebote/9': json(200, ENTWURF) });

    renderMaske('/vorgaenge/5/angebote/9/bearbeiten');
    await nutzer.clear(await screen.findByLabelText(/^Gültig bis/));
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(screen.getByText('Bitte einen Tag angeben, bis zu dem das Angebot gilt.'))
      .toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('zeigt eine Feldmeldung des Servers am Feld', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'GET /api/angebote/9': json(200, ENTWURF),
      'PUT /api/angebote/9': problem(400, 'ungültig', {
        gueltigBis: ['Die Gültigkeit darf nicht fehlen.'],
      }),
    });

    renderMaske('/vorgaenge/5/angebote/9/bearbeiten');
    await screen.findByLabelText(/^Gültig bis/);
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(await screen.findByText('Die Gültigkeit darf nicht fehlen.')).toBeInTheDocument();
  });

  it('meldet den Ausfall beim Speichern', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'GET /api/angebote/9': json(200, ENTWURF),
      'PUT /api/angebote/9': leer(500),
    });

    renderMaske('/vorgaenge/5/angebote/9/bearbeiten');
    await screen.findByLabelText(/^Gültig bis/);
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht gespeichert');
  });
});

describe('AngebotMaske — der Pfad im Kopf (E16)', () => {
  it('fuehrt ueber „Vorgänge" und den Vorgang, ohne ihn nachzufragen', async () => {
    const fetchMock = fetchNachPfad({ 'GET /api/angebote/9': json(200, ENTWURF) });

    renderMaske('/vorgaenge/5/angebote/9/bearbeiten');
    await screen.findByLabelText(/^Gültig bis/);

    // Genau ein Aufruf: Der Pfad steht in der Adresse, der Vorgang wird dafuer nicht gelesen.
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
