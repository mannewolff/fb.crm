import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import AuftragMaske from './AuftragMaske';
import { KopfPfadProvider } from '../components/KopfPfad';
import { alsJson, fetchNachPfad, json, leer, problem } from '../test/fetchNachPfad';
import type { Routen } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';

const AUFWAND = {
  bezeichnung: 'Konzeption',
  abrechnungsmodus: 'AUFWAND',
  menge: 2.5,
  einheit: 'PERSONENTAG',
  einzelpreis: 1000.01,
  stundenJePersonentag: 8,
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
  status: 'ABGESCHLOSSEN',
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

function renderMaske(start = '/vorgaenge/5/auftraege/3/bearbeiten') {
  return renderMitTheme(
    <MemoryRouter initialEntries={[start]}>
      <KopfPfadProvider>
        <Routes>
          <Route path="/vorgaenge/:id/auftraege/:auftragId/bearbeiten" element={<AuftragMaske />} />
          <Route path="/vorgaenge/:id/auftraege/:auftragId" element={<p>Auftragsansicht</p>} />
        </Routes>
        <Adresse />
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

/** Die Maske mit dem Auftrag und ein Pflegeweg, der den gesendeten Rumpf festhaelt. */
function mitAuftrag(weitere: Routen = {}) {
  const gesendet: unknown[] = [];
  fetchNachPfad({
    'GET /api/auftraege/3': json(200, AUFTRAG),
    'PUT /api/auftraege/3': (rumpf) => {
      gesendet.push(alsJson(rumpf));
      return json(200, AUFTRAG)();
    },
    ...weitere,
  });
  return gesendet;
}

async function bereit() {
  await screen.findByRole('heading', { level: 1, name: 'Auftrag AU-2026-001 bearbeiten' });
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('AuftragMaske — die vier aenderbaren Angaben (Kriterium 7)', () => {
  it('traegt genau eine Ueberschrift und belegt die vier Felder vor', async () => {
    mitAuftrag();

    renderMaske();
    await bereit();

    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    expect(screen.getByLabelText(/^Auftragsdatum/)).toHaveValue('2026-09-28');
    expect(screen.getByLabelText('Kundenbestellnummer')).toHaveValue('PO-4711');
    expect(screen.getByLabelText('Leistung ab')).toHaveValue('2026-10-01');
    expect(screen.getByLabelText('Leistung bis')).toHaveValue('2026-12-31');
    expect(screen.getByLabelText('Status')).toHaveValue('ABGESCHLOSSEN');
  });

  it('bietet die drei Status als Worte an', async () => {
    mitAuftrag();

    renderMaske();
    await bereit();

    expect(screen.getAllByRole('option').map((wahl) => wahl.textContent)).toEqual([
      'Offen',
      'In Arbeit',
      'Abgeschlossen',
    ]);
  });

  it('setzt den Status auch von „Abgeschlossen" zurueck auf „In Arbeit" und schickt alle vier Angaben (F6)', async () => {
    const gesendet = mitAuftrag();

    renderMaske();
    await bereit();
    await userEvent.selectOptions(screen.getByLabelText('Status'), 'IN_ARBEIT');
    const nummer = screen.getByLabelText('Kundenbestellnummer');
    await userEvent.clear(nummer);
    await userEvent.type(nummer, ' PO-4712 ');
    await userEvent.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(await screen.findByText('Auftragsansicht')).toBeInTheDocument();
    expect(screen.getByTestId('adresse')).toHaveTextContent('/vorgaenge/5/auftraege/3');
    expect(gesendet).toEqual([
      {
        auftragDatum: '2026-09-28',
        kundenbestellnummer: 'PO-4712',
        leistungAb: '2026-10-01',
        leistungBis: '2026-12-31',
        status: 'IN_ARBEIT',
      },
    ]);
  });

  it.each([['OFFEN'], ['IN_ARBEIT'], ['ABGESCHLOSSEN']])(
    'schickt den gewaehlten Status %s',
    async (status) => {
      const gesendet = mitAuftrag();

      renderMaske();
      await bereit();
      // Erst weg vom Ausgangswert, dann auf den gewuenschten — so laeuft jeder Wert durch die Wahl.
      await userEvent.selectOptions(
        screen.getByLabelText('Status'),
        status === 'OFFEN' ? 'IN_ARBEIT' : 'OFFEN',
      );
      await userEvent.selectOptions(screen.getByLabelText('Status'), status);
      await userEvent.click(screen.getByRole('button', { name: 'Speichern' }));

      await screen.findByText('Auftragsansicht');
      expect(gesendet).toEqual([expect.objectContaining({ status })]);
    },
  );

  it('nimmt „beide leer" als gueltigen Leistungszeitraum und eine leere Bestellnummer als keine', async () => {
    const gesendet = mitAuftrag();

    renderMaske();
    await bereit();
    await userEvent.clear(screen.getByLabelText('Leistung ab'));
    await userEvent.clear(screen.getByLabelText('Leistung bis'));
    await userEvent.clear(screen.getByLabelText('Kundenbestellnummer'));
    await userEvent.click(screen.getByRole('button', { name: 'Speichern' }));

    await screen.findByText('Auftragsansicht');
    expect(gesendet).toEqual([
      expect.objectContaining({ kundenbestellnummer: null, leistungAb: null, leistungBis: null }),
    ]);
  });

  it('meldet einen halb gesetzten Leistungszeitraum am fehlenden Feld und schickt nichts', async () => {
    const gesendet = mitAuftrag();

    renderMaske();
    await bereit();
    await userEvent.clear(screen.getByLabelText('Leistung bis'));
    await userEvent.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(
      screen.getByText('Bitte auch das Ende angeben — oder beide Felder leer lassen.'),
    ).toBeInTheDocument();
    expect(gesendet).toEqual([]);
  });

  it('meldet einen fehlenden Beginn ebenso', async () => {
    const gesendet = mitAuftrag();

    renderMaske();
    await bereit();
    await userEvent.clear(screen.getByLabelText('Leistung ab'));
    await userEvent.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(
      screen.getByText('Bitte auch den Beginn angeben — oder beide Felder leer lassen.'),
    ).toBeInTheDocument();
    expect(gesendet).toEqual([]);
  });

  it('meldet ein Ende vor dem Beginn', async () => {
    const gesendet = mitAuftrag();

    renderMaske();
    await bereit();
    const bis = screen.getByLabelText('Leistung bis');
    await userEvent.clear(bis);
    await userEvent.type(bis, '2026-09-01');
    await userEvent.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(screen.getByText('Das Ende liegt vor dem Beginn.')).toBeInTheDocument();
    expect(gesendet).toEqual([]);
  });

  it('verlangt ein Auftragsdatum', async () => {
    const gesendet = mitAuftrag();

    renderMaske();
    await bereit();
    await userEvent.clear(screen.getByLabelText(/^Auftragsdatum/));
    await userEvent.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(screen.getByText('Bitte ein Auftragsdatum angeben.')).toBeInTheDocument();
    expect(gesendet).toEqual([]);
  });

  it('setzt die fieldErrors einer 400 an das Feld', async () => {
    mitAuftrag({
      'PUT /api/auftraege/3': problem(400, 'Ungueltig', {
        kundenbestellnummer: ['Höchstens 100 Zeichen.'],
      }),
    });

    renderMaske();
    await bereit();
    await userEvent.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(await screen.findByText('Höchstens 100 Zeichen.')).toBeInTheDocument();
    expect(screen.getByLabelText('Kundenbestellnummer')).toHaveAttribute('aria-invalid', 'true');
  });

  it('meldet einen Ausfall beim Speichern, ohne die Maske zu verlassen', async () => {
    mitAuftrag({ 'PUT /api/auftraege/3': leer(500) });

    renderMaske();
    await bereit();
    await userEvent.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(
      await screen.findByText('Der Auftrag wurde nicht gespeichert. Bitte später erneut versuchen.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Speichern' })).toBeEnabled();
  });
});

describe('AuftragMaske — die Positionen stehen fest (Kriterium 7, F3, R4)', () => {
  it('zeigt die Positionen, aber kein Feld fuer sie und keines fuer die Stunden', async () => {
    mitAuftrag();

    renderMaske();
    await bereit();

    expect(screen.getByRole('table', { name: 'Positionen' })).toBeInTheDocument();
    expect(screen.getByText('Konzeption')).toBeInTheDocument();
    expect(screen.getByText('8,00')).toBeInTheDocument();
    // Nur die vier Angaben sind Felder: Datum, Bestellnummer, Beginn, Ende — dazu die Auswahl.
    expect(screen.getAllByRole('textbox')).toHaveLength(1);
    expect(screen.queryByLabelText(/Menge/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/Stunden je Personentag/)).not.toBeInTheDocument();
  });

  it('fuehrt mit „Zum Auftrag" zurueck, ohne zu speichern', async () => {
    const gesendet = mitAuftrag();

    renderMaske();
    await bereit();
    await userEvent.click(screen.getByRole('link', { name: 'Zum Auftrag' }));

    expect(screen.getByTestId('adresse')).toHaveTextContent('/vorgaenge/5/auftraege/3');
    expect(gesendet).toEqual([]);
  });
});

describe('AuftragMaske — Lade- und Fehlerzustand', () => {
  it('sagt, dass der Auftrag geladen wird', () => {
    fetchNachPfad({ 'GET /api/auftraege/3': () => new Promise<Response>(() => undefined) });

    renderMaske();

    expect(screen.getByText('Der Auftrag wird geladen …')).toBeInTheDocument();
  });

  it('meldet einen unbekannten Auftrag', async () => {
    fetchNachPfad({ 'GET /api/auftraege/3': problem(404, 'Nicht gefunden') });

    renderMaske();

    expect(await screen.findByText('Diesen Auftrag gibt es nicht.')).toBeInTheDocument();
  });

  it('meldet eine Kennung, die keine ist, ohne das Netz zu fragen', async () => {
    const fetchMock = fetchNachPfad({});

    renderMaske('/vorgaenge/5/auftraege/abc/bearbeiten');

    expect(await screen.findByText('Diesen Auftrag gibt es nicht.')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('meldet einen Ausfall beim Lesen', async () => {
    fetchNachPfad({ 'GET /api/auftraege/3': leer(503) });

    renderMaske();

    expect(
      await screen.findByText('Der Auftrag ist gerade nicht zu erreichen. Bitte später erneut versuchen.'),
    ).toBeInTheDocument();
  });
});
