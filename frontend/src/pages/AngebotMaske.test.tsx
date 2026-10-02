import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import AngebotMaske from './AngebotMaske';
import { KopfPfadProvider } from '../components/KopfPfad';
import { fetchNachPfad, json, leer, problem } from '../test/fetchNachPfad';
import type { Routen } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';

/** Eine Position, wie Jackson sie schreibt. */
const POSITION = {
  id: 3,
  bezeichnung: 'Konzeption',
  abrechnungsmodus: 'AUFWAND',
  menge: 2.5,
  einheit: 'PERSONENTAG',
  einzelpreis: 1000.01,
  betrag: 2500.03,
};

const ANGEBOT = {
  id: 9,
  firmaId: 5,
  firmaName: 'Adler AG',
  ansprechpartnerId: null,
  ansprechpartnerName: null,
  status: 'ANGELEGT',
  angebotDatum: '2026-09-24',
  beschreibung: 'Neue Website',
  intern: false,
  positionen: [POSITION],
  summe: 2500.03,
};

/** Eine Firma, wie `GET /api/firmen/5` sie liefert — ein aktiver und ein stillgelegter Partner. */
const FIRMA = {
  id: 5,
  name: 'Adler AG',
  strasse: null,
  plz: null,
  ort: null,
  land: null,
  steuernummer: null,
  umsatzsteuerId: null,
  aktiv: true,
  ansprechpartner: [
    {
      id: 8,
      vorname: 'Eva',
      nachname: 'Adler',
      rolle: null,
      email: null,
      telefonFestnetz: null,
      telefonMobil: null,
      aktiv: true,
    },
    {
      id: 7,
      vorname: null,
      nachname: 'Alt',
      rolle: null,
      email: null,
      telefonFestnetz: null,
      telefonMobil: null,
      aktiv: false,
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
          <Route path="/firmen/:id/angebote/neu" element={<AngebotMaske />} />
          <Route path="/angebote/:angebotId/bearbeiten" element={<AngebotMaske />} />
          <Route path="/angebote/:angebotId" element={<p>Angebotsansicht</p>} />
          <Route path="/firmen/:id" element={<p>Firmenseite</p>} />
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

describe('AngebotMaske — Anlegen an einer Firma (Kriterium 2, Issue #126)', () => {
  it('traegt genau eine Ueberschrift der ersten Ebene und nennt die Firma', async () => {
    fetchNachPfad({ 'GET /api/firmen/5': json(200, FIRMA) });

    renderMaske('/firmen/5/angebote/neu');

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Neues Angebot' }),
    ).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    expect(screen.getByTestId('angebot-firma')).toHaveTextContent('An: Adler AG');
  });

  it('stellt nur die aktiven Ansprechpartner der Firma zur Wahl', async () => {
    fetchNachPfad({ 'GET /api/firmen/5': json(200, FIRMA) });

    renderMaske('/firmen/5/angebote/neu');

    const wahl = await screen.findByRole('combobox', { name: 'Ansprechpartner' });
    expect(within(wahl).getAllByRole('option').map((eintrag) => eintrag.textContent)).toEqual([
      '— keiner —',
      'Eva Adler',
    ]);
  });

  it('legt ohne Ansprechpartner an und fuehrt in die Maske des neuen Angebots', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/firmen/5': json(200, FIRMA),
      'POST /api/firmen/5/angebote': json(201, ANGEBOT),
      'GET /api/angebote/9': json(200, ANGEBOT),
    });

    renderMaske('/firmen/5/angebote/neu');
    await screen.findByRole('combobox', { name: 'Ansprechpartner' });
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Angebot bearbeiten' }))
      .toBeInTheDocument();
    expect(screen.getByTestId('adresse')).toHaveTextContent('/angebote/9/bearbeiten');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen/5/angebote',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ ansprechpartnerId: null, intern: false }) }),
    );
  });

  it('legt mit dem gewaehlten Ansprechpartner an', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/firmen/5': json(200, FIRMA),
      'POST /api/firmen/5/angebote': json(201, ANGEBOT),
      'GET /api/angebote/9': json(200, ANGEBOT),
    });

    renderMaske('/firmen/5/angebote/neu');
    await nutzer.selectOptions(
      await screen.findByRole('combobox', { name: 'Ansprechpartner' }),
      '8',
    );
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen/5/angebote',
      expect.objectContaining({
        body: JSON.stringify({ ansprechpartnerId: 8, intern: false }),
      }),
    );
  });

  it('meldet, wenn das Anlegen scheitert', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'GET /api/firmen/5': json(200, FIRMA),
      'POST /api/firmen/5/angebote': leer(409),
    });

    renderMaske('/firmen/5/angebote/neu');
    await screen.findByRole('combobox', { name: 'Ansprechpartner' });
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht angelegt');
  });

  it('bietet an einer stillgelegten Firma kein Anlegen an', async () => {
    fetchNachPfad({ 'GET /api/firmen/5': json(200, { ...FIRMA, aktiv: false }) });

    renderMaske('/firmen/5/angebote/neu');

    expect(await screen.findByRole('alert')).toHaveTextContent('stillgelegt');
    expect(screen.queryByRole('button', { name: 'Anlegen' })).not.toBeInTheDocument();
  });

  it('meldet eine unbekannte Firma', async () => {
    fetchNachPfad({ 'GET /api/firmen/5': leer(404) });

    renderMaske('/firmen/5/angebote/neu');

    expect(await screen.findByRole('alert')).toHaveTextContent('gibt es nicht');
  });

  it('meldet den Ausfall beim Lesen der Firma', async () => {
    fetchNachPfad({ 'GET /api/firmen/5': leer(500) });

    renderMaske('/firmen/5/angebote/neu');

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht zu erreichen');
  });

  it('fuehrt mit „Abbrechen" zurueck auf die Firma', async () => {
    fetchNachPfad({ 'GET /api/firmen/5': json(200, FIRMA) });

    renderMaske('/firmen/5/angebote/neu');

    expect(await screen.findByRole('link', { name: 'Abbrechen' })).toHaveAttribute(
      'href',
      '/firmen/5',
    );
  });

  it('faengt eine unsinnige Firmenkennung ab, bevor sie an die Schnittstelle geht', async () => {
    const fetchMock = fetchNachPfad({});

    renderMaske('/firmen/keine-zahl/angebote/neu');

    expect(await screen.findByRole('alert')).toHaveTextContent('gibt es nicht');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

/** Die beiden Lesewege der Maske beim Bearbeiten, dazu weitere Antworten. */
function lesen(
  angebot: object = ANGEBOT,
  weitere: Routen = {},
) {
  return fetchNachPfad({
    'GET /api/angebote/9': json(200, angebot),
    'GET /api/firmen/5': json(200, FIRMA),
    ...weitere,
  });
}

/** Wartet, bis die Maske die Felder aus dem Angebot belegt hat. */
async function bereit() {
  return screen.findByLabelText(/^Angebotsdatum/);
}

/** Eine zweite gespeicherte Position — sie macht das Umordnen an den Kennungen ablesbar. */
const BETREUUNG = {
  id: 4,
  bezeichnung: 'Betreuung',
  abrechnungsmodus: 'FESTPREIS',
  menge: 1,
  einheit: 'PAUSCHAL',
  einzelpreis: 500,
  betrag: 500,
};

/**
 * Die Kennungen der Positionen im zuletzt geschickten Rumpf, in der geschickten Reihenfolge.
 *
 * Steht als Helfer hier, weil drei Proben dieselbe Frage stellen: Der Rumpf ist ein Text, und
 * dreimal dieselbe Zerlegung im Testkoerper verdeckte, worum es geht.
 */
function kennungenDesRumpfs(fetchMock: ReturnType<typeof fetchNachPfad>): (number | null)[] {
  const letzter = fetchMock.mock.calls.at(-1);
  const rumpf: unknown = JSON.parse(String((letzter?.[1] as { body?: string } | undefined)?.body));
  return (rumpf as { positionen: { id: number | null }[] }).positionen.map(
    (position) => position.id,
  );
}

const KONZEPTION_EINGABE = {
  id: 3,
  bezeichnung: 'Konzeption',
  abrechnungsmodus: 'AUFWAND',
  menge: '2.50',
  einheit: 'PERSONENTAG',
  einzelpreis: '1000.01',
};

describe('AngebotMaske — das Angebot bearbeiten (Issue #127, Kriterium 5)', () => {
  it('belegt die Felder aus dem Angebot', async () => {
    lesen({ ...ANGEBOT, ansprechpartnerId: 8, ansprechpartnerName: 'Eva Adler' });

    renderMaske('/angebote/9/bearbeiten');

    expect(await bereit()).toHaveValue('2026-09-24');
    expect(screen.getByRole('combobox', { name: 'Ansprechpartner' })).toHaveValue('8');
    expect(screen.getByRole('textbox', { name: 'Beschreibung' })).toHaveValue('Neue Website');
    expect(gruppe(1).getByRole('textbox', { name: 'Bezeichnung' })).toHaveValue('Konzeption');
    expect(gruppe(1).getByRole('textbox', { name: 'Menge' })).toHaveValue('2,50');
    expect(gruppe(1).getByRole('textbox', { name: 'Einzelpreis (netto)' })).toHaveValue('1000,01');
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    // Genau drei Felder oberhalb der Positionen: Datum, Ansprechpartner, Beschreibung.
    expect(screen.queryByLabelText(/Gültig bis/)).not.toBeInTheDocument();
  });

  it('stellt die aktiven Ansprechpartner zur Wahl', async () => {
    lesen();

    renderMaske('/angebote/9/bearbeiten');
    await bereit();

    const wahl = screen.getByRole('combobox', { name: 'Ansprechpartner' });
    expect(within(wahl).getAllByRole('option').map((eintrag) => eintrag.textContent)).toEqual([
      '— keiner —',
      'Eva Adler',
    ]);
  });

  it('behaelt einen gespeicherten, inzwischen stillgelegten Ansprechpartner in der Wahl', async () => {
    lesen({ ...ANGEBOT, ansprechpartnerId: 7, ansprechpartnerName: 'Alt' });

    renderMaske('/angebote/9/bearbeiten');
    await bereit();

    const wahl = screen.getByRole('combobox', { name: 'Ansprechpartner' });
    expect(wahl).toHaveValue('7');
    expect(within(wahl).getAllByRole('option').map((eintrag) => eintrag.textContent)).toEqual([
      '— keiner —',
      'Eva Adler',
      'Alt (stillgelegt)',
    ]);
  });

  it('nimmt einen stillgelegten Ansprechpartner nach dem Wechsel aus der Wahl', async () => {
    const nutzer = userEvent.setup();
    // Gespeichert ist der stillgelegte Partner 7; gespeichert wird mit dem aktiven 8.
    lesen(
      { ...ANGEBOT, ansprechpartnerId: 7, ansprechpartnerName: 'Alt' },
      {
        'PUT /api/angebote/9': json(200, {
          ...ANGEBOT,
          ansprechpartnerId: 8,
          ansprechpartnerName: 'Eva Adler',
        }),
      },
    );

    renderMaske('/angebote/9/bearbeiten');
    await bereit();
    const wahl = screen.getByRole('combobox', { name: 'Ansprechpartner' });
    await nutzer.selectOptions(wahl, '8');
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    await screen.findByRole('status');
    expect(within(wahl).getAllByRole('option').map((eintrag) => eintrag.textContent)).toEqual([
      '— keiner —',
      'Eva Adler',
    ]);
  });

  it('behaelt den stillgelegten Ansprechpartner, wenn er gespeichert bleibt', async () => {
    const nutzer = userEvent.setup();
    const gespeichert = { ...ANGEBOT, ansprechpartnerId: 7, ansprechpartnerName: 'Alt' };
    lesen(gespeichert, { 'PUT /api/angebote/9': json(200, gespeichert) });

    renderMaske('/angebote/9/bearbeiten');
    await bereit();
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    await screen.findByRole('status');
    const wahl = screen.getByRole('combobox', { name: 'Ansprechpartner' });
    expect(wahl).toHaveValue('7');
    expect(within(wahl).getAllByRole('option').map((eintrag) => eintrag.textContent)).toEqual([
      '— keiner —',
      'Eva Adler',
      'Alt (stillgelegt)',
    ]);
  });

  it.each([['ANGELEGT'], ['ABGEGEBEN'], ['BESTELLT'], ['ERLEDIGT'], ['ABGERECHNET']])(
    'oeffnet und speichert das Angebot im Status %s',
    async (status) => {
      const nutzer = userEvent.setup();
      const fetchMock = lesen(
        { ...ANGEBOT, status },
        { 'PUT /api/angebote/9': json(200, { ...ANGEBOT, status }) },
      );

      renderMaske('/angebote/9/bearbeiten');
      await bereit();
      await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

      expect(await screen.findByRole('status')).toHaveTextContent('Gespeichert');
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/angebote/9',
        expect.objectContaining({ method: 'PUT' }),
      );
    },
  );

  it('zeigt die Summe und rechnet beim Tippen mit', async () => {
    const nutzer = userEvent.setup();
    lesen();

    renderMaske('/angebote/9/bearbeiten');
    await bereit();

    expect(screen.getByTestId('angebot-summe')).toHaveTextContent('2.500,03 €');

    const menge = gruppe(1).getByRole('textbox', { name: 'Menge' });
    await nutzer.clear(menge);
    await nutzer.type(menge, '2');

    expect(screen.getByTestId('angebot-summe')).toHaveTextContent('2.000,02 €');
  });

  it('fuegt eine Position hinzu, loescht und verschiebt sie', async () => {
    const nutzer = userEvent.setup();
    lesen();

    renderMaske('/angebote/9/bearbeiten');
    await bereit();

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

  it('sagt es, wenn das Angebot noch keine Position hat', async () => {
    lesen({ ...ANGEBOT, positionen: [], summe: 0 });

    renderMaske('/angebote/9/bearbeiten');

    expect(await screen.findByRole('status')).toHaveTextContent('Noch keine Position');
    expect(screen.getByTestId('angebot-summe')).toHaveTextContent('0,00 €');
  });

  it('schickt Datum, Ansprechpartner, Beschreibung und die Liste in der gezeigten Reihenfolge (E8)', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = lesen(ANGEBOT, { 'PUT /api/angebote/9': json(200, ANGEBOT) });

    renderMaske('/angebote/9/bearbeiten');
    const datum = await bereit();
    await nutzer.clear(datum);
    await nutzer.type(datum, '2026-09-25');
    await nutzer.selectOptions(screen.getByRole('combobox', { name: 'Ansprechpartner' }), '8');
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
          angebotDatum: '2026-09-25',
          ansprechpartnerId: 8,
          beschreibung: 'Neue Website',
          intern: false,
          positionen: [
            {
              // Eine hinzugefuegte Position hat noch keine Kennung (Plan #169, E2).
              id: null,
              bezeichnung: 'Betreuung',
              abrechnungsmodus: 'FESTPREIS',
              menge: '1.00',
              einheit: 'PAUSCHAL',
              einzelpreis: '0.00',
            },
            KONZEPTION_EINGABE,
          ],
        }),
      }),
    );
  });

  it('schickt die Kennungen eines geladenen Angebots unveraendert hinaus (Plan #169)', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = lesen(ANGEBOT, { 'PUT /api/angebote/9': json(200, ANGEBOT) });

    renderMaske('/angebote/9/bearbeiten');
    await bereit();
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    await screen.findByRole('status');
    expect(kennungenDesRumpfs(fetchMock)).toEqual([3]);
  });

  it('laesst die Kennung einer hinzugefuegten Position leer (Plan #169)', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = lesen(ANGEBOT, { 'PUT /api/angebote/9': json(200, ANGEBOT) });

    renderMaske('/angebote/9/bearbeiten');
    await bereit();
    await nutzer.click(screen.getByRole('button', { name: 'Position hinzufügen' }));
    await nutzer.type(gruppe(2).getByRole('textbox', { name: 'Bezeichnung' }), 'Betreuung');
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    await screen.findByRole('status');
    expect(kennungenDesRumpfs(fetchMock)).toEqual([3, null]);
  });

  it('laesst die Kennung beim Umordnen mit ihrer Position wandern (Plan #169)', async () => {
    const nutzer = userEvent.setup();
    const zwei = {
      ...ANGEBOT,
      positionen: [POSITION, BETREUUNG],
      summe: 3000.03,
    };
    const fetchMock = lesen(zwei, { 'PUT /api/angebote/9': json(200, zwei) });

    renderMaske('/angebote/9/bearbeiten');
    await bereit();
    expect(gruppe(1).getByRole('textbox', { name: 'Bezeichnung' })).toHaveValue('Konzeption');

    await nutzer.click(gruppe(2).getByRole('button', { name: 'Position 2 nach oben' }));
    expect(gruppe(1).getByRole('textbox', { name: 'Bezeichnung' })).toHaveValue('Betreuung');

    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    await screen.findByRole('status');
    // Nicht [3, 4]: Die Kennung haengt an der Position, nicht an der Stelle in der Liste.
    expect(kennungenDesRumpfs(fetchMock)).toEqual([4, 3]);
  });

  it('schickt eine geleerte Beschreibung und keinen Ansprechpartner als „keine Angabe"', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = lesen(
      { ...ANGEBOT, ansprechpartnerId: 8, ansprechpartnerName: 'Eva Adler' },
      { 'PUT /api/angebote/9': json(200, ANGEBOT) },
    );

    renderMaske('/angebote/9/bearbeiten');
    await bereit();
    await nutzer.clear(screen.getByRole('textbox', { name: 'Beschreibung' }));
    await nutzer.selectOptions(screen.getByRole('combobox', { name: 'Ansprechpartner' }), '');
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    await screen.findByRole('status');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/9',
      expect.objectContaining({
        body: JSON.stringify({
          angebotDatum: '2026-09-24',
          ansprechpartnerId: null,
          beschreibung: null,
          intern: false,
          positionen: [KONZEPTION_EINGABE],
        }),
      }),
    );
  });

  it('nimmt nach dem Speichern die Antwort und nicht die eigene Rechnung', async () => {
    const nutzer = userEvent.setup();
    // Der Server schreibt die Menge auf 3,00 fort und rechnet die Summe neu. Was danach in der
    // Maske steht, kommt von dort (AngebotController: „ohne zweiten Aufruf").
    lesen(ANGEBOT, {
      'PUT /api/angebote/9': json(200, {
        ...ANGEBOT,
        angebotDatum: '2026-09-30',
        positionen: [{ ...POSITION, menge: 3, betrag: 3000.03 }],
        summe: 3000.03,
      }),
    });

    renderMaske('/angebote/9/bearbeiten');
    await bereit();
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Gespeichert');
    expect(gruppe(1).getByRole('textbox', { name: 'Menge' })).toHaveValue('3,00');
    expect(screen.getByTestId('angebot-summe')).toHaveTextContent('3.000,03 €');
    expect(screen.getByLabelText(/^Angebotsdatum/)).toHaveValue('2026-09-30');
  });

  it('fuehrt mit „Zum Angebot" auf die Angebotsansicht', async () => {
    lesen();

    renderMaske('/angebote/9/bearbeiten');

    expect(await screen.findByRole('link', { name: 'Zum Angebot' })).toHaveAttribute(
      'href',
      '/angebote/9',
    );
  });
});

describe('AngebotMaske — was nicht geht', () => {
  it('meldet ein unbekanntes Angebot', async () => {
    fetchNachPfad({ 'GET /api/angebote/9': leer(404) });

    renderMaske('/angebote/9/bearbeiten');

    expect(await screen.findByRole('alert')).toHaveTextContent('gibt es nicht');
  });

  it('meldet den Ausfall beim Lesen', async () => {
    fetchNachPfad({ 'GET /api/angebote/9': leer(503) });

    renderMaske('/angebote/9/bearbeiten');

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht zu erreichen');
  });

  it('meldet den Ausfall beim Lesen der Firma', async () => {
    fetchNachPfad({
      'GET /api/angebote/9': json(200, ANGEBOT),
      'GET /api/firmen/5': leer(503),
    });

    renderMaske('/angebote/9/bearbeiten');

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht zu erreichen');
  });

  it('faengt eine unsinnige Angebotskennung ab, bevor sie an die Schnittstelle geht', async () => {
    const fetchMock = fetchNachPfad({});

    renderMaske('/angebote/keine-zahl/bearbeiten');

    expect(await screen.findByRole('alert')).toHaveTextContent('gibt es nicht');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('haelt das Speichern an, solange die Menge keine Zahl ist', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = lesen();

    renderMaske('/angebote/9/bearbeiten');
    await bereit();
    const menge = gruppe(1).getByRole('textbox', { name: 'Menge' });
    await nutzer.clear(menge);
    await nutzer.type(menge, '1,234');
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(screen.getByTestId('angebot-summe')).toHaveTextContent('—');
    expect(screen.getByRole('status')).toHaveTextContent('kann nicht gespeichert werden');
    // Nur die beiden Lesewege sind hinausgegangen: Eine halbe Liste wird nicht geschickt (E8).
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('haelt das Speichern an, solange der Einzelpreis keine Zahl ist', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = lesen();

    renderMaske('/angebote/9/bearbeiten');
    await bereit();
    await nutzer.clear(gruppe(1).getByRole('textbox', { name: 'Einzelpreis (netto)' }));
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(screen.getByTestId('angebot-summe')).toHaveTextContent('—');
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('verlangt ein Angebotsdatum, ohne das Netz zu bemuehen', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = lesen();

    renderMaske('/angebote/9/bearbeiten');
    await nutzer.clear(await bereit());
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(screen.getByText('Bitte das Datum des Angebots angeben.')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('zeigt die Meldung an einer Positionsbezeichnung an der betroffenen Position', async () => {
    const nutzer = userEvent.setup();
    lesen(ANGEBOT, {
      'PUT /api/angebote/9': problem(400, 'Die Eingabe ist ungueltig.', {
        'positionen[0].bezeichnung': ['Jede Position braucht eine Bezeichnung.'],
      }),
    });

    renderMaske('/angebote/9/bearbeiten');
    await bereit();
    await nutzer.click(screen.getByRole('button', { name: 'Position hinzufügen' }));
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(
      await gruppe(1).findByText('Jede Position braucht eine Bezeichnung.'),
    ).toBeInTheDocument();
    expect(gruppe(1).getByRole('textbox', { name: 'Bezeichnung' })).toHaveAttribute(
      'aria-invalid',
      'true',
    );
    expect(gruppe(2).getByRole('textbox', { name: 'Bezeichnung' })).toHaveAttribute(
      'aria-invalid',
      'false',
    );
  });

  it('zeigt die Meldung zum Ansprechpartner am Feld', async () => {
    const nutzer = userEvent.setup();
    lesen(ANGEBOT, {
      'PUT /api/angebote/9': problem(422, 'nicht waehlbar', {
        ansprechpartnerId: ['Dieser Ansprechpartner steht für das Angebot nicht zur Wahl.'],
      }),
    });

    renderMaske('/angebote/9/bearbeiten');
    await bereit();
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(
      await screen.findByText('Dieser Ansprechpartner steht für das Angebot nicht zur Wahl.'),
    ).toBeInTheDocument();
  });

  it('zeigt eine Feldmeldung des Servers am Datum', async () => {
    const nutzer = userEvent.setup();
    lesen(ANGEBOT, {
      'PUT /api/angebote/9': problem(400, 'ungültig', {
        angebotDatum: ['Das Datum darf nicht fehlen.'],
      }),
    });

    renderMaske('/angebote/9/bearbeiten');
    await bereit();
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(await screen.findByText('Das Datum darf nicht fehlen.')).toBeInTheDocument();
  });

  it('zeigt die Meldung zu einer berechneten Position und laesst die Eingaben stehen', async () => {
    const nutzer = userEvent.setup();
    const gebunden =
      'Die Position „Konzeption“ steht in einer Rechnung: Sie muss erhalten bleiben, und ihre' +
      ' Einheit und ihre Abrechnungsart bleiben, wie sie sind.';
    lesen(ANGEBOT, {
      'PUT /api/angebote/9': problem(422, gebunden, { positionen: [gebunden] }),
    });

    renderMaske('/angebote/9/bearbeiten');
    await bereit();
    await nutzer.clear(gruppe(1).getByRole('textbox', { name: 'Menge' }));
    await nutzer.type(gruppe(1).getByRole('textbox', { name: 'Menge' }), '3,00');
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(await screen.findByText(gebunden)).toBeInTheDocument();
    // Die Eingaben bleiben stehen: Was abgewiesen wurde, ist nicht verloren.
    expect(gruppe(1).getByRole('textbox', { name: 'Menge' })).toHaveValue('3,00');
    expect(gruppe(1).getByRole('textbox', { name: 'Bezeichnung' })).toHaveValue('Konzeption');
  });

  it('meldet den Ausfall beim Speichern', async () => {
    const nutzer = userEvent.setup();
    lesen(ANGEBOT, { 'PUT /api/angebote/9': leer(500) });

    renderMaske('/angebote/9/bearbeiten');
    await bereit();
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht gespeichert');
  });
});

/** Dieselbe Position, aber mit Angaben, die keine Zahlen sind — am internen Angebot verborgen. */
const INTERN_ANGEBOT = { ...ANGEBOT, intern: true, status: 'LAEUFT' };

describe('AngebotMaske — das Kennzeichen „Internes Projekt" (Issue #207, Kriterien 1, 3, 8)', () => {
  it('schickt beim Anlegen mit gesetztem Kaestchen „intern: true"', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/firmen/5': json(200, FIRMA),
      'POST /api/firmen/5/angebote': json(201, INTERN_ANGEBOT),
      'GET /api/angebote/9': json(200, INTERN_ANGEBOT),
    });

    renderMaske('/firmen/5/angebote/neu');
    await nutzer.click(
      await screen.findByRole('checkbox', { name: 'Internes Projekt' }),
    );
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen/5/angebote',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ ansprechpartnerId: null, intern: true }),
      }),
    );
  });

  it('schickt beim Anlegen ohne Haken „intern: false"', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/firmen/5': json(200, FIRMA),
      'POST /api/firmen/5/angebote': json(201, ANGEBOT),
      'GET /api/angebote/9': json(200, ANGEBOT),
    });

    renderMaske('/firmen/5/angebote/neu');
    expect(await screen.findByRole('checkbox', { name: 'Internes Projekt' })).not.toBeChecked();
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen/5/angebote',
      expect.objectContaining({
        body: JSON.stringify({ ansprechpartnerId: null, intern: false }),
      }),
    );
  });

  it('zeigt das Kaestchen beim Bearbeiten eines internen Angebots gesetzt', async () => {
    lesen(INTERN_ANGEBOT);

    renderMaske('/angebote/9/bearbeiten');
    await bereit();

    expect(screen.getByRole('checkbox', { name: 'Internes Projekt' })).toBeChecked();
  });

  it('zeigt das Kaestchen bei einem externen Angebot nicht gesetzt', async () => {
    lesen(ANGEBOT);

    renderMaske('/angebote/9/bearbeiten');
    await bereit();

    expect(screen.getByRole('checkbox', { name: 'Internes Projekt' })).not.toBeChecked();
  });

  it('verbirgt mit dem Haken die vier Positionsangaben und die Summenzeile sofort', async () => {
    const nutzer = userEvent.setup();
    lesen(ANGEBOT);

    renderMaske('/angebote/9/bearbeiten');
    await bereit();
    expect(screen.getByTestId('angebot-summe')).toBeInTheDocument();

    await nutzer.click(screen.getByRole('checkbox', { name: 'Internes Projekt' }));

    expect(gruppe(1).queryByRole('combobox', { name: 'Abrechnung' })).not.toBeInTheDocument();
    expect(gruppe(1).queryByRole('textbox', { name: 'Menge' })).not.toBeInTheDocument();
    expect(gruppe(1).queryByRole('combobox', { name: 'Einheit' })).not.toBeInTheDocument();
    expect(gruppe(1).queryByRole('textbox', { name: 'Einzelpreis (netto)' })).not.toBeInTheDocument();
    expect(screen.queryByTestId('angebot-summe')).not.toBeInTheDocument();
  });

  it('zeigt die Angaben nach dem Abwaehlen mit denselben Werten erneut', async () => {
    const nutzer = userEvent.setup();
    lesen(ANGEBOT);

    renderMaske('/angebote/9/bearbeiten');
    await bereit();
    const menge = gruppe(1).getByRole('textbox', { name: 'Menge' });
    await nutzer.clear(menge);
    await nutzer.type(menge, '7,25');

    const kaestchen = screen.getByRole('checkbox', { name: 'Internes Projekt' });
    await nutzer.click(kaestchen);
    await nutzer.click(kaestchen);

    // Nichts zwischengespeichert: Der Zustand der Maske blieb stehen (Entscheidung zum Issue).
    expect(gruppe(1).getByRole('textbox', { name: 'Menge' })).toHaveValue('7,25');
    expect(gruppe(1).getByRole('textbox', { name: 'Einzelpreis (netto)' })).toHaveValue('1000,01');
    expect(screen.getByTestId('angebot-summe')).toHaveTextContent('7.250,07 €');
  });

  it('schickt am internen Angebot die vier Positionsangaben nicht mit', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = lesen(INTERN_ANGEBOT, {
      'PUT /api/angebote/9': json(200, INTERN_ANGEBOT),
    });

    renderMaske('/angebote/9/bearbeiten');
    await bereit();
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    await screen.findByRole('status');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/9',
      expect.objectContaining({
        body: JSON.stringify({
          angebotDatum: '2026-09-24',
          ansprechpartnerId: null,
          beschreibung: 'Neue Website',
          intern: true,
          positionen: [{ id: 3, bezeichnung: 'Konzeption' }],
        }),
      }),
    );
  });

  it('schickt am externen Angebot die vier Positionsangaben mit', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = lesen(ANGEBOT, { 'PUT /api/angebote/9': json(200, ANGEBOT) });

    renderMaske('/angebote/9/bearbeiten');
    await bereit();
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    await screen.findByRole('status');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/9',
      expect.objectContaining({
        body: JSON.stringify({
          angebotDatum: '2026-09-24',
          ansprechpartnerId: null,
          beschreibung: 'Neue Website',
          intern: false,
          positionen: [KONZEPTION_EINGABE],
        }),
      }),
    );
  });

  it('haelt eine verborgene unlesbare Zahl das Speichern nicht auf', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = lesen(ANGEBOT, { 'PUT /api/angebote/9': json(200, INTERN_ANGEBOT) });

    renderMaske('/angebote/9/bearbeiten');
    await bereit();
    await nutzer.clear(gruppe(1).getByRole('textbox', { name: 'Menge' }));
    await nutzer.click(screen.getByRole('checkbox', { name: 'Internes Projekt' }));
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    await screen.findByRole('status');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/9',
      expect.objectContaining({
        body: JSON.stringify({
          angebotDatum: '2026-09-24',
          ansprechpartnerId: null,
          beschreibung: 'Neue Website',
          intern: true,
          positionen: [{ id: 3, bezeichnung: 'Konzeption' }],
        }),
      }),
    );
  });

  it('stellt die Meldung des Servers ans Kaestchen und laesst es bedienbar (E20)', async () => {
    const nutzer = userEvent.setup();
    const gesperrt =
      'Dieses Angebot ist bereits abgerechnet; seine Art lässt sich nicht mehr ändern.';
    lesen(ANGEBOT, {
      'PUT /api/angebote/9': problem(422, gesperrt, { intern: [gesperrt] }),
    });

    renderMaske('/angebote/9/bearbeiten');
    await bereit();
    const kaestchen = screen.getByRole('checkbox', { name: 'Internes Projekt' });
    await nutzer.click(kaestchen);
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(await screen.findByText(gesperrt)).toBeInTheDocument();
    // Keine Sammelmeldung oben, sondern die Meldung am Feld.
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(kaestchen).toBeEnabled();
    expect(kaestchen).toBeChecked();
  });
});
