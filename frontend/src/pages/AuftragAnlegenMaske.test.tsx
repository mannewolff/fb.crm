import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import AuftragAnlegenMaske from './AuftragAnlegenMaske';
import { KopfPfadProvider } from '../components/KopfPfad';
import { betrag, euro } from '../lib/geld';
import { alsJson, fetchNachPfad, json, problem } from '../test/fetchNachPfad';
import type { Routen } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';

const AUFWAND = {
  bezeichnung: 'Konzeption',
  abrechnungsmodus: 'AUFWAND',
  menge: 2.5,
  einheit: 'PERSONENTAG',
  einzelpreis: 1000.01,
  betrag: 2500.03,
};

const FESTPREIS = {
  bezeichnung: 'Einrichtung',
  abrechnungsmodus: 'FESTPREIS',
  menge: 1,
  einheit: 'PAUSCHAL',
  einzelpreis: 500,
  betrag: 500,
};

const ANGEBOT = {
  id: 9,
  vorgangId: 5,
  nummer: 'A-2026-001',
  stand: 'ANGENOMMEN',
  angebotDatum: '2026-09-24',
  gueltigBis: '2026-10-24',
  leistungsbeschreibung: null,
  zahlungsbedingungen: null,
  versendetAm: '2026-09-24T08:00:00Z',
  reaktionAm: '2026-09-26T09:30:00Z',
  positionen: [AUFWAND, FESTPREIS],
  summe: 3000.03,
};

const AUFTRAG = {
  id: 3,
  vorgangId: 5,
  angebotId: 9,
  angebotNummer: 'A-2026-001',
  nummer: 'AU-2026-001',
  status: 'OFFEN',
  auftragDatum: '2026-09-28',
  kundenbestellnummer: null,
  leistungAb: null,
  leistungBis: null,
  positionen: [],
  summe: 0,
};

function Adresse() {
  const ort = useLocation();
  return <p data-testid="adresse">{ort.pathname}</p>;
}

function renderMaske(start = '/vorgaenge/5/angebote/9/auftrag/neu') {
  return renderMitTheme(
    <MemoryRouter initialEntries={[start]}>
      <KopfPfadProvider>
        <Routes>
          <Route
            path="/vorgaenge/:id/angebote/:angebotId/auftrag/neu"
            element={<AuftragAnlegenMaske />}
          />
          <Route path="/vorgaenge/:id/angebote/:angebotId" element={<p>Angebotsansicht</p>} />
        </Routes>
        <Adresse />
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

/** Die Maske mit dem Angebot, dazu ein Anlegeweg, der den gesendeten Rumpf festhaelt. */
function mitAngebot(angebot: unknown = ANGEBOT, weitere: Routen = {}) {
  const gesendet: unknown[] = [];
  fetchNachPfad({
    'GET /api/angebote/9': json(200, angebot),
    'POST /api/angebote/9/auftrag': (rumpf) => {
      gesendet.push(alsJson(rumpf));
      return json(201, AUFTRAG)();
    },
    ...weitere,
  });
  return gesendet;
}

/** Die Zeile einer Position — dort stehen Haken, Menge und Stunden. */
function zeile(bezeichnung: string) {
  return within(screen.getByRole('group', { name: bezeichnung }));
}

async function bereit() {
  await screen.findByRole('heading', { level: 1, name: 'Neuer Auftrag' });
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('AuftragAnlegenMaske — was die Maske zeigt (Kriterien 2, 4)', () => {
  it('traegt genau eine Ueberschrift und nennt das Angebot, aus dem der Auftrag entsteht', async () => {
    mitAngebot();

    renderMaske();
    await bereit();

    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    expect(screen.getByText('Aus Angebot A-2026-001')).toBeInTheDocument();
  });

  it('nennt ein Angebot ohne Nummer allgemein', async () => {
    mitAngebot({ ...ANGEBOT, nummer: null });

    renderMaske();
    await bereit();

    expect(screen.getByText('Aus dem angenommenen Angebot')).toBeInTheDocument();
  });

  it('zeigt die Positionen des Angebots in derselben Reihenfolge mit gesetztem Haken', async () => {
    mitAngebot();

    renderMaske();
    await bereit();

    const haken = screen.getAllByRole('checkbox');
    expect(haken).toEqual([
      screen.getByRole('checkbox', { name: 'Konzeption' }),
      screen.getByRole('checkbox', { name: 'Einrichtung' }),
    ]);
    for (const feld of haken) {
      expect(feld).toBeChecked();
    }
  });

  it('zeigt Modus, Einheit und Einzelpreis nur lesbar — es gibt kein Feld dafuer', async () => {
    mitAngebot();

    renderMaske();
    await bereit();

    expect(zeile('Konzeption').getByText('Aufwand · Personentag · 1.000,01 €')).toBeInTheDocument();
    expect(
      zeile('Konzeption')
        .getAllByRole('textbox')
        .map((feld) => feld.getAttribute('name')),
    ).toEqual([
      'menge',
      'stundenJePersonentag',
    ]);
    expect(screen.queryByLabelText(/Einzelpreis/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/Bezeichnung/)).not.toBeInTheDocument();
  });

  it('traegt „Stunden je Personentag" nur an der Aufwandsposition, vorbelegt mit 8', async () => {
    mitAngebot();

    renderMaske();
    await bereit();

    expect(zeile('Konzeption').getByLabelText(/Stunden je Personentag/)).toHaveValue('8,00');
    expect(zeile('Einrichtung').queryByLabelText(/Stunden je Personentag/)).not.toBeInTheDocument();
  });

  it('bietet keine Taste zum Hinzufuegen einer Position an (F2)', async () => {
    mitAngebot();

    renderMaske();
    await bereit();

    expect(screen.queryByRole('button', { name: /hinzuf/i })).not.toBeInTheDocument();
  });

  it('laesst das Auftragsdatum leer und sagt, dass dann der heutige Tag gilt', async () => {
    mitAngebot();

    renderMaske();
    await bereit();

    expect(screen.getByLabelText('Auftragsdatum')).toHaveValue('');
    expect(screen.getByText('Leer bleibt es der heutige Tag.')).toBeInTheDocument();
  });

  it('rechnet die mittippende Summe wie lib/geld.ts', async () => {
    mitAngebot();

    renderMaske();
    await bereit();

    expect(screen.getByTestId('auftrag-summe')).toHaveTextContent(
      euro(betrag(250, 100001) + betrag(100, 50000)),
    );

    const menge = zeile('Konzeption').getByLabelText(/^Menge/);
    await userEvent.clear(menge);
    await userEvent.type(menge, '1,5');

    expect(screen.getByTestId('auftrag-summe')).toHaveTextContent(
      euro(betrag(150, 100001) + betrag(100, 50000)),
    );
  });

  it('nimmt eine abgewaehlte Position aus der Summe', async () => {
    mitAngebot();

    renderMaske();
    await bereit();
    await userEvent.click(zeile('Einrichtung').getByRole('checkbox'));

    expect(screen.getByTestId('auftrag-summe')).toHaveTextContent(euro(250003));
  });

  it('zeigt einen Strich als Summe, solange eine Menge keine Zahl ist', async () => {
    mitAngebot();

    renderMaske();
    await bereit();
    const menge = zeile('Konzeption').getByLabelText(/^Menge/);
    await userEvent.clear(menge);
    await userEvent.type(menge, 'viel');

    expect(screen.getByTestId('auftrag-summe')).toHaveTextContent('—');
  });
});

describe('AuftragAnlegenMaske — was hinausgeht (Kriterien 2, 4, Plan E7)', () => {
  it('schickt je Position genau Platz, Menge und Stunden, keinen Preis, und kein Datum', async () => {
    const gesendet = mitAngebot();

    renderMaske();
    await bereit();
    await userEvent.click(screen.getByRole('button', { name: 'Auftrag anlegen' }));

    expect(await screen.findByText('Angebotsansicht')).toBeInTheDocument();
    expect(gesendet).toEqual([
      {
        kundenbestellnummer: null,
        leistungAb: null,
        leistungBis: null,
        positionen: [
          { platz: 1, menge: '2.50', stundenJePersonentag: '8.00' },
          { platz: 2, menge: '1.00', stundenJePersonentag: null },
        ],
      },
    ]);
    expect(JSON.stringify(gesendet)).not.toMatch(/preis|bezeichnung|einheit|abrechnungsmodus/i);
  });

  it('schickt den Platz als Stellung im Angebot, auch wenn davor eine Position fehlt', async () => {
    const gesendet = mitAngebot();

    renderMaske();
    await bereit();
    await userEvent.click(zeile('Konzeption').getByRole('checkbox'));
    await userEvent.click(screen.getByRole('button', { name: 'Auftrag anlegen' }));

    await screen.findByText('Angebotsansicht');
    expect(gesendet).toEqual([
      expect.objectContaining({
        positionen: [{ platz: 2, menge: '1.00', stundenJePersonentag: null }],
      }),
    ]);
  });

  it('uebernimmt eine verringerte Menge, geaenderte Stunden und die Kopfangaben', async () => {
    const gesendet = mitAngebot();

    renderMaske();
    await bereit();
    const menge = zeile('Konzeption').getByLabelText(/^Menge/);
    await userEvent.clear(menge);
    await userEvent.type(menge, '2');
    const stunden = zeile('Konzeption').getByLabelText(/Stunden je Personentag/);
    await userEvent.clear(stunden);
    await userEvent.type(stunden, '7,5');
    await userEvent.type(screen.getByLabelText('Auftragsdatum'), '2026-09-27');
    await userEvent.type(screen.getByLabelText('Kundenbestellnummer'), ' PO-4711 ');
    await userEvent.type(screen.getByLabelText('Leistung ab'), '2026-10-01');
    await userEvent.type(screen.getByLabelText('Leistung bis'), '2026-12-31');
    await userEvent.click(screen.getByRole('button', { name: 'Auftrag anlegen' }));

    await screen.findByText('Angebotsansicht');
    expect(gesendet).toEqual([
      {
        auftragDatum: '2026-09-27',
        kundenbestellnummer: 'PO-4711',
        leistungAb: '2026-10-01',
        leistungBis: '2026-12-31',
        positionen: [
          { platz: 1, menge: '2.00', stundenJePersonentag: '7.50' },
          { platz: 2, menge: '1.00', stundenJePersonentag: null },
        ],
      },
    ]);
  });

  it('nimmt die gleiche Menge wie im Angebot an', async () => {
    const gesendet = mitAngebot();

    renderMaske();
    await bereit();
    const menge = zeile('Konzeption').getByLabelText(/^Menge/);
    await userEvent.clear(menge);
    await userEvent.type(menge, '2,50');
    await userEvent.click(screen.getByRole('button', { name: 'Auftrag anlegen' }));

    await screen.findByText('Angebotsansicht');
    expect(gesendet).toHaveLength(1);
  });

  it('weist eine Menge ueber der Angebotsmenge am Feld ab und schickt nichts (F2)', async () => {
    const gesendet = mitAngebot();

    renderMaske();
    await bereit();
    const menge = zeile('Konzeption').getByLabelText(/^Menge/);
    await userEvent.clear(menge);
    await userEvent.type(menge, '3');
    await userEvent.click(screen.getByRole('button', { name: 'Auftrag anlegen' }));

    expect(
      zeile('Konzeption').getByText('Höchstens 2,50 — die Menge des Angebots.'),
    ).toBeInTheDocument();
    expect(gesendet).toEqual([]);
    expect(screen.getByTestId('adresse')).toHaveTextContent('/vorgaenge/5/angebote/9/auftrag/neu');
  });

  it('weist eine Menge, die keine Zahl ist, und Stunden von null am Feld ab', async () => {
    const gesendet = mitAngebot();

    renderMaske();
    await bereit();
    const menge = zeile('Konzeption').getByLabelText(/^Menge/);
    await userEvent.clear(menge);
    await userEvent.type(menge, 'x');
    const stunden = zeile('Konzeption').getByLabelText(/Stunden je Personentag/);
    await userEvent.clear(stunden);
    await userEvent.type(stunden, '0');
    await userEvent.click(screen.getByRole('button', { name: 'Auftrag anlegen' }));

    expect(zeile('Konzeption').getByText('Bitte eine Zahl mit höchstens zwei Nachkommastellen.'))
      .toBeInTheDocument();
    expect(zeile('Konzeption').getByText('Bitte eine Zahl größer 0.')).toBeInTheDocument();
    expect(gesendet).toEqual([]);
  });

  it('weist Stunden, die keine Zahl sind, am Feld ab', async () => {
    const gesendet = mitAngebot();

    renderMaske();
    await bereit();
    const stunden = zeile('Konzeption').getByLabelText(/Stunden je Personentag/);
    await userEvent.clear(stunden);
    await userEvent.type(stunden, 'acht');
    await userEvent.click(screen.getByRole('button', { name: 'Auftrag anlegen' }));

    expect(zeile('Konzeption').getByText('Bitte eine Zahl mit höchstens zwei Nachkommastellen.'))
      .toBeInTheDocument();
    expect(gesendet).toEqual([]);
  });

  it('sperrt die Absendetaste, solange kein Haken gesetzt ist', async () => {
    mitAngebot();

    renderMaske();
    await bereit();
    await userEvent.click(zeile('Konzeption').getByRole('checkbox'));
    await userEvent.click(zeile('Einrichtung').getByRole('checkbox'));

    expect(screen.getByRole('button', { name: 'Auftrag anlegen' })).toBeDisabled();
    expect(zeile('Konzeption').getByLabelText(/^Menge/)).toBeDisabled();
  });

  it('setzt die Feldmeldungen eines 409 an die betroffene Zeile und an die Kopfangaben', async () => {
    mitAngebot(ANGEBOT, {
      'POST /api/angebote/9/auftrag': problem(409, 'Nicht uebernehmbar', {
        // Die Stelle zaehlt in der gesendeten Liste: Nur die zweite Position ging hinaus.
        'positionen[0].menge': ['Die Menge liegt über der Menge des Angebots.'],
        leistungBis: ['Das Ende liegt vor dem Beginn.'],
        positionen: ['Mindestens eine Position mit einer Menge über 0.'],
      }),
    });

    renderMaske();
    await bereit();
    await userEvent.click(zeile('Konzeption').getByRole('checkbox'));
    await userEvent.click(screen.getByRole('button', { name: 'Auftrag anlegen' }));

    expect(
      await zeile('Einrichtung').findByText('Die Menge liegt über der Menge des Angebots.'),
    ).toBeInTheDocument();
    expect(zeile('Konzeption').queryByText(/über der Menge/)).not.toBeInTheDocument();
    expect(screen.getByText('Das Ende liegt vor dem Beginn.')).toBeInTheDocument();
    expect(screen.getByText('Mindestens eine Position mit einer Menge über 0.')).toBeInTheDocument();
  });

  it('setzt eine Meldung zum Platz an die betroffene Zeile', async () => {
    mitAngebot(ANGEBOT, {
      'POST /api/angebote/9/auftrag': problem(409, 'Nicht uebernehmbar', {
        'positionen[1].platz': ['Diese Position gibt es im Angebot nicht mehr.'],
      }),
    });

    renderMaske();
    await bereit();
    await userEvent.click(screen.getByRole('button', { name: 'Auftrag anlegen' }));

    expect(
      await zeile('Einrichtung').findByText('Diese Position gibt es im Angebot nicht mehr.'),
    ).toBeInTheDocument();
  });

  it('meldet einen Ausfall beim Anlegen, ohne die Maske zu verlassen', async () => {
    mitAngebot(ANGEBOT, { 'POST /api/angebote/9/auftrag': problem(500, 'Kaputt') });

    renderMaske();
    await bereit();
    await userEvent.click(screen.getByRole('button', { name: 'Auftrag anlegen' }));

    expect(
      await screen.findByText('Der Auftrag wurde nicht angelegt. Bitte später erneut versuchen.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Auftrag anlegen' })).toBeEnabled();
  });
});

describe('AuftragAnlegenMaske — Lade-, Fehler- und Leerzustand', () => {
  it('sagt, dass das Angebot geladen wird', () => {
    fetchNachPfad({ 'GET /api/angebote/9': () => new Promise<Response>(() => undefined) });

    renderMaske();

    expect(screen.getByText('Das Angebot wird geladen …')).toBeInTheDocument();
  });

  it('meldet ein unbekanntes Angebot', async () => {
    fetchNachPfad({ 'GET /api/angebote/9': problem(404, 'Nicht gefunden') });

    renderMaske();

    expect(await screen.findByText('Dieses Angebot gibt es nicht.')).toBeInTheDocument();
  });

  it('meldet eine Kennung, die keine ist, ohne das Netz zu fragen', async () => {
    const fetch = fetchNachPfad({});

    renderMaske('/vorgaenge/5/angebote/abc/auftrag/neu');

    expect(await screen.findByText('Dieses Angebot gibt es nicht.')).toBeInTheDocument();
    expect(fetch).not.toHaveBeenCalled();
  });

  it('meldet einen Ausfall beim Lesen', async () => {
    fetchNachPfad({ 'GET /api/angebote/9': problem(500, 'Kaputt') });

    renderMaske();

    expect(
      await screen.findByText('Das Angebot ist gerade nicht zu erreichen. Bitte später erneut versuchen.'),
    ).toBeInTheDocument();
  });

  it('oeffnet die Maske nur an einem angenommenen Angebot', async () => {
    mitAngebot({ ...ANGEBOT, stand: 'VERSENDET' });

    renderMaske();

    expect(
      await screen.findByText('Ein Auftrag entsteht nur aus einem angenommenen Angebot.'),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Auftrag anlegen' })).not.toBeInTheDocument();
  });

  it('sagt es, wenn das Angebot keine Position traegt', async () => {
    mitAngebot({ ...ANGEBOT, positionen: [], summe: 0 });

    renderMaske();
    await bereit();

    expect(screen.getByRole('status')).toHaveTextContent('Das Angebot trägt keine Position');
    expect(screen.getByRole('button', { name: 'Auftrag anlegen' })).toBeDisabled();
  });

  it('fuehrt mit „Abbrechen" zurueck zum Angebot', async () => {
    mitAngebot();

    renderMaske();
    await bereit();
    await userEvent.click(screen.getByRole('link', { name: 'Abbrechen' }));

    expect(screen.getByTestId('adresse')).toHaveTextContent('/vorgaenge/5/angebote/9');
  });
});
