import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import AngebotMaske from './AngebotMaske';
import { KEIN_JAHR } from '../components/Positionsmaske';
import { KopfPfadProvider } from '../components/KopfPfad';
import { NICHT_OFFEN } from '../routes/RollenRoute';
import { appAlsRolle } from '../test/appAlsRolle';
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
  leistungsjahr: '2026',
};

const ANGEBOT = {
  id: 9,
  firmaId: 5,
  firmaName: 'Adler AG',
  ansprechpartnerId: null,
  ansprechpartnerName: null,
  status: 'ANGELEGT',
  angebotDatum: '2026-09-24',
  titel: 'Relaunch',
  beschreibung: 'Neue Website',
  intern: false,
  storniert: false,
  stornoGrund: null,
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

/** Die Meldungen am Titel — derselbe Wortlaut wie in der Maske (Plan #315, E5). */
const TITEL_FEHLT = 'Bitte einen Titel für das Angebot angeben.';
const TITEL_ZU_LANG = 'Der Titel darf höchstens 200 Zeichen lang sein.';

/** Das Titelfeld — `required` haengt der Beschriftung ein Sternchen an. */
function titelFeld() {
  return screen.getByRole('textbox', { name: /^Titel/ });
}

/** Wartet auf den ersten Schritt und traegt einen Titel ein. */
async function titelEintragen(nutzer: ReturnType<typeof userEvent.setup>, titel: string) {
  await screen.findByRole('combobox', { name: 'Ansprechpartner' });
  await nutzer.type(titelFeld(), titel);
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
    await titelEintragen(nutzer, 'Relaunch');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Angebot bearbeiten' }))
      .toBeInTheDocument();
    expect(screen.getByTestId('adresse')).toHaveTextContent('/angebote/9/bearbeiten');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen/5/angebote',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ titel: 'Relaunch', ansprechpartnerId: null, intern: false }),
      }),
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
    await titelEintragen(nutzer, 'Relaunch');
    await nutzer.selectOptions(screen.getByRole('combobox', { name: 'Ansprechpartner' }), '8');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen/5/angebote',
      expect.objectContaining({
        body: JSON.stringify({ titel: 'Relaunch', ansprechpartnerId: 8, intern: false }),
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
    await titelEintragen(nutzer, 'Relaunch');
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
  leistungsjahr: '2027',
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
  leistungsjahr: '2026',
};

describe('AngebotMaske — das Angebot bearbeiten (Issue #127, Kriterium 5)', () => {
  it('belegt die Felder aus dem Angebot', async () => {
    lesen({ ...ANGEBOT, ansprechpartnerId: 8, ansprechpartnerName: 'Eva Adler' });

    renderMaske('/angebote/9/bearbeiten');

    expect(await bereit()).toHaveValue('2026-09-24');
    expect(titelFeld()).toHaveValue('Relaunch');
    expect(screen.getByRole('combobox', { name: 'Ansprechpartner' })).toHaveValue('8');
    expect(screen.getByRole('textbox', { name: 'Beschreibung' })).toHaveValue('Neue Website');
    expect(gruppe(1).getByRole('textbox', { name: 'Bezeichnung' })).toHaveValue('Konzeption');
    expect(gruppe(1).getByRole('textbox', { name: 'Menge' })).toHaveValue('2,50');
    expect(gruppe(1).getByRole('textbox', { name: 'Einzelpreis (netto)' })).toHaveValue('1000,01');
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    // Genau vier Felder oberhalb der Positionen: Titel, Datum, Ansprechpartner, Beschreibung.
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
          titel: 'Relaunch',
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
              // Vorbelegt mit dem Jahr des Datumsfeldes beim Hinzufuegen (Kriterium 4).
              leistungsjahr: '2026',
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
          titel: 'Relaunch',
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

describe('AngebotMaske — nur mit Schreibrecht (Kriterium 10 von #409, Plan #414, A11)', () => {
  it.each([
    ['/firmen/5/angebote/neu', 'Neues Angebot'],
    ['/angebote/9/bearbeiten', 'Angebot bearbeiten'],
  ])('zeigt ReadOnly auf „%s" den Satz statt der Maske „%s"', async (adresse, titel) => {
    const aufruf = appAlsRolle('READ_ONLY', adresse);

    expect(await screen.findByText(new RegExp(NICHT_OFFEN))).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: titel })).not.toBeInTheDocument();
    // Die Maske entsteht gar nicht — sie liest auch nichts.
    expect(aufruf).toHaveBeenCalledTimes(2);
  });

  it('zeigt einem Employee die Maske, denn er darf fachlich schreiben', async () => {
    appAlsRolle('EMPLOYEE', '/firmen/5/angebote/neu', { 'GET /api/firmen/5': json(200, FIRMA) });

    expect(await screen.findByRole('heading', { name: 'Neues Angebot' })).toBeInTheDocument();
    expect(screen.queryByText(new RegExp(NICHT_OFFEN))).not.toBeInTheDocument();
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
    await titelEintragen(nutzer, 'Relaunch');
    await nutzer.click(screen.getByRole('checkbox', { name: 'Internes Projekt' }));
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen/5/angebote',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ titel: 'Relaunch', ansprechpartnerId: null, intern: true }),
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
    await titelEintragen(nutzer, 'Relaunch');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen/5/angebote',
      expect.objectContaining({
        body: JSON.stringify({ titel: 'Relaunch', ansprechpartnerId: null, intern: false }),
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
    expect(gruppe(1).queryByRole('textbox', { name: 'Leistungsjahr' })).not.toBeInTheDocument();
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
          titel: 'Relaunch',
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
          titel: 'Relaunch',
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
    const jahr = gruppe(1).getByRole('textbox', { name: 'Leistungsjahr' });
    await nutzer.clear(jahr);
    await nutzer.type(jahr, '2k26');
    await nutzer.click(screen.getByRole('checkbox', { name: 'Internes Projekt' }));
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    await screen.findByRole('status');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/9',
      expect.objectContaining({
        body: JSON.stringify({
          angebotDatum: '2026-09-24',
          titel: 'Relaunch',
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

describe('AngebotMaske — das Leistungsjahr der Positionen (Plan #303, Kriterium 4, E16)', () => {
  it('zeigt das Jahr einer gespeicherten Position', async () => {
    lesen({ ...ANGEBOT, positionen: [POSITION, BETREUUNG], summe: 3000.03 });

    renderMaske('/angebote/9/bearbeiten');
    await bereit();

    expect(gruppe(1).getByRole('textbox', { name: 'Leistungsjahr' })).toHaveValue('2026');
    expect(gruppe(2).getByRole('textbox', { name: 'Leistungsjahr' })).toHaveValue('2027');
  });

  it('belegt eine hinzugefuegte Position mit dem Jahr des Datumsfeldes vor', async () => {
    const nutzer = userEvent.setup();
    lesen();

    renderMaske('/angebote/9/bearbeiten');
    const datum = await bereit();
    await nutzer.clear(datum);
    await nutzer.type(datum, '2027-03-01');
    await nutzer.click(screen.getByRole('button', { name: 'Position hinzufügen' }));

    expect(gruppe(2).getByRole('textbox', { name: 'Leistungsjahr' })).toHaveValue('2027');
  });

  it('laesst die Jahre bestehender Zeilen stehen, wenn sich das Angebotsdatum aendert', async () => {
    const nutzer = userEvent.setup();
    lesen();

    renderMaske('/angebote/9/bearbeiten');
    const datum = await bereit();
    await nutzer.click(screen.getByRole('button', { name: 'Position hinzufügen' }));
    await nutzer.clear(datum);
    await nutzer.type(datum, '2028-01-15');

    // Die Vorbelegung gilt nur beim Anlegen der Zeile (Kriterium 4) — weder die geladene noch die
    // eben hinzugefuegte Zeile folgt dem neuen Datum.
    expect(gruppe(1).getByRole('textbox', { name: 'Leistungsjahr' })).toHaveValue('2026');
    expect(gruppe(2).getByRole('textbox', { name: 'Leistungsjahr' })).toHaveValue('2026');

    await nutzer.click(screen.getByRole('button', { name: 'Position hinzufügen' }));
    expect(gruppe(3).getByRole('textbox', { name: 'Leistungsjahr' })).toHaveValue('2028');
  });

  it('laesst das Jahr einer hinzugefuegten Zeile leer, solange das Datumsfeld leer ist', async () => {
    const nutzer = userEvent.setup();
    lesen();

    renderMaske('/angebote/9/bearbeiten');
    await nutzer.clear(await bereit());
    await nutzer.click(screen.getByRole('button', { name: 'Position hinzufügen' }));

    const jahr = gruppe(2).getByRole('textbox', { name: 'Leistungsjahr' });
    expect(jahr).toHaveValue('');
    // Leer ist „keine Angabe" und keine Meldung (E16).
    expect(jahr).toHaveAttribute('aria-invalid', 'false');
    expect(gruppe(2).queryByText(KEIN_JAHR)).not.toBeInTheDocument();
  });

  it('schickt das eingegebene Jahr mit der Position hinaus', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = lesen(ANGEBOT, { 'PUT /api/angebote/9': json(200, ANGEBOT) });

    renderMaske('/angebote/9/bearbeiten');
    await bereit();
    const jahr = gruppe(1).getByRole('textbox', { name: 'Leistungsjahr' });
    await nutzer.clear(jahr);
    await nutzer.type(jahr, '2027');
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    await screen.findByRole('status');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/9',
      expect.objectContaining({
        body: JSON.stringify({
          angebotDatum: '2026-09-24',
          titel: 'Relaunch',
          ansprechpartnerId: null,
          beschreibung: 'Neue Website',
          intern: false,
          positionen: [{ ...KONZEPTION_EINGABE, leistungsjahr: '2027' }],
        }),
      }),
    );
  });

  it('schickt ein geleertes Jahr als „keine Angabe" hinaus (E16)', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = lesen(ANGEBOT, { 'PUT /api/angebote/9': json(200, ANGEBOT) });

    renderMaske('/angebote/9/bearbeiten');
    await bereit();
    await nutzer.clear(gruppe(1).getByRole('textbox', { name: 'Leistungsjahr' }));
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    await screen.findByRole('status');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/9',
      expect.objectContaining({
        body: JSON.stringify({
          angebotDatum: '2026-09-24',
          titel: 'Relaunch',
          ansprechpartnerId: null,
          beschreibung: 'Neue Website',
          intern: false,
          positionen: [{ ...KONZEPTION_EINGABE, leistungsjahr: null }],
        }),
      }),
    );
  });

  it('haelt das Speichern am Kundenangebot an, solange ein Jahr unlesbar ist (E16)', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = lesen();

    renderMaske('/angebote/9/bearbeiten');
    await bereit();
    const jahr = gruppe(1).getByRole('textbox', { name: 'Leistungsjahr' });
    await nutzer.clear(jahr);
    await nutzer.type(jahr, '2k26');
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(gruppe(1).getByText(KEIN_JAHR)).toBeInTheDocument();
    expect(screen.queryByText('Gespeichert.')).not.toBeInTheDocument();
    // Nur die beiden Lesewege sind hinausgegangen: Alles oder nichts (E8).
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});

/** Steht `vorne` im Dokument vor `hinten`? */
function steht(vorne: HTMLElement, hinten: HTMLElement): boolean {
  return (vorne.compareDocumentPosition(hinten) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0;
}

describe('AngebotMaske — der Titel im ersten Schritt (Issue #318, Kriterium 2)', () => {
  it('fragt den Titel als erstes Feld und als Pflichtfeld ab (E8)', async () => {
    fetchNachPfad({ 'GET /api/firmen/5': json(200, FIRMA) });

    renderMaske('/firmen/5/angebote/neu');
    const person = await screen.findByRole('combobox', { name: 'Ansprechpartner' });

    expect(titelFeld()).toHaveValue('');
    expect(titelFeld()).toBeRequired();
    expect(steht(titelFeld(), person)).toBe(true);
  });

  it('Szenario: Neues Angebot ohne Titel — es entsteht keines, und die Meldung steht am Feld', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/firmen/5': json(200, FIRMA),
      'POST /api/firmen/5/angebote': json(201, ANGEBOT),
    });

    renderMaske('/firmen/5/angebote/neu');
    await screen.findByRole('combobox', { name: 'Ansprechpartner' });
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(titelFeld()).toHaveAccessibleDescription(TITEL_FEHLT);
    expect(titelFeld()).toHaveAttribute('aria-invalid', 'true');
    // Nur der Leseweg der Firma ist hinausgegangen: Ohne Titel entsteht nichts.
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId('adresse')).toHaveTextContent('/firmen/5/angebote/neu');

    // Ein Titel nur aus Leerzeichen zaehlt als fehlend.
    await nutzer.type(titelFeld(), '   ');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(titelFeld()).toHaveAccessibleDescription(TITEL_FEHLT);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId('adresse')).toHaveTextContent('/firmen/5/angebote/neu');
  });

  it('weist einen Titel ueber 200 Zeichen ab, ohne das Netz zu bemuehen', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ 'GET /api/firmen/5': json(200, FIRMA) });

    renderMaske('/firmen/5/angebote/neu');
    await screen.findByRole('combobox', { name: 'Ansprechpartner' });
    await nutzer.click(titelFeld());
    await nutzer.paste('x'.repeat(201));
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(titelFeld()).toHaveAccessibleDescription(TITEL_ZU_LANG);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('schickt einen gueltigen Titel getrimmt hinaus (E7)', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/firmen/5': json(200, FIRMA),
      'POST /api/firmen/5/angebote': json(201, ANGEBOT),
      'GET /api/angebote/9': json(200, ANGEBOT),
    });

    renderMaske('/firmen/5/angebote/neu');
    await screen.findByRole('combobox', { name: 'Ansprechpartner' });
    // 200 Zeichen und Leerzeichen an beiden Enden: Gemessen wird, was hinausgeht.
    await nutzer.click(titelFeld());
    await nutzer.paste(`  ${'x'.repeat(200)}  `);
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Angebot bearbeiten' }))
      .toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen/5/angebote',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ titel: 'x'.repeat(200), ansprechpartnerId: null, intern: false }),
      }),
    );
  });

  it('stellt eine Feldmeldung des Servers ans Feld „Ansprechpartner" statt einer Ausfallmeldung (E6)', async () => {
    const nutzer = userEvent.setup();
    const nichtWaehlbar = 'Dieser Ansprechpartner steht für das Angebot nicht zur Wahl.';
    fetchNachPfad({
      'GET /api/firmen/5': json(200, FIRMA),
      'POST /api/firmen/5/angebote': problem(422, 'nicht waehlbar', {
        ansprechpartnerId: [nichtWaehlbar],
      }),
    });

    renderMaske('/firmen/5/angebote/neu');
    await titelEintragen(nutzer, 'Relaunch');
    await nutzer.selectOptions(screen.getByRole('combobox', { name: 'Ansprechpartner' }), '8');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(await screen.findByText(nichtWaehlbar)).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Ansprechpartner' })).toHaveAccessibleDescription(
      nichtWaehlbar,
    );
    expect(screen.queryByText('nicht angelegt', { exact: false })).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('laesst nach einem Fund am Titel keine stehengebliebene Meldung die des Servers verdecken', async () => {
    const nutzer = userEvent.setup();
    const vomServer = 'Der Titel ist so nicht zulässig.';
    fetchNachPfad({
      'GET /api/firmen/5': json(200, FIRMA),
      'POST /api/firmen/5/angebote': problem(400, 'ungültig', { titel: [vomServer] }),
    });

    renderMaske('/firmen/5/angebote/neu');
    await screen.findByRole('combobox', { name: 'Ansprechpartner' });
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));
    expect(titelFeld()).toHaveAccessibleDescription(TITEL_FEHLT);

    await nutzer.type(titelFeld(), 'Relaunch');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(await screen.findByText(vomServer)).toBeInTheDocument();
    expect(titelFeld()).toHaveAccessibleDescription(vomServer);
    expect(screen.queryByText(TITEL_FEHLT)).not.toBeInTheDocument();
  });

  it('nimmt die Ausfallmeldung eines frueheren Versuchs beim naechsten Anlegen weg', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'GET /api/firmen/5': json(200, FIRMA),
      'POST /api/firmen/5/angebote': leer(500),
    });

    renderMaske('/firmen/5/angebote/neu');
    await titelEintragen(nutzer, 'Relaunch');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('nicht angelegt');

    await nutzer.clear(titelFeld());
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(titelFeld()).toHaveAccessibleDescription(TITEL_FEHLT);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});

describe('AngebotMaske — der Titel beim Bearbeiten (Issue #318, Kriterien 3, 4)', () => {
  it('fuehrt den Titel als erstes Feld vor dem Angebotsdatum (E8)', async () => {
    lesen();

    renderMaske('/angebote/9/bearbeiten');
    const datum = await bereit();

    expect(titelFeld()).toBeRequired();
    expect(steht(titelFeld(), datum)).toBe(true);
  });

  it('schickt einen geaenderten Titel getrimmt hinaus', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = lesen(ANGEBOT, {
      'PUT /api/angebote/9': json(200, { ...ANGEBOT, titel: 'Schulung Java' }),
    });

    renderMaske('/angebote/9/bearbeiten');
    await bereit();
    await nutzer.clear(titelFeld());
    await nutzer.type(titelFeld(), '  Schulung Java ');
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Gespeichert');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/9',
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({
          angebotDatum: '2026-09-24',
          titel: 'Schulung Java',
          ansprechpartnerId: null,
          beschreibung: 'Neue Website',
          intern: false,
          positionen: [KONZEPTION_EINGABE],
        }),
      }),
    );
    expect(titelFeld()).toHaveValue('Schulung Java');
  });

  it('Szenario: Zu langer Titel — nicht gespeichert, Meldung am Feld', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = lesen(ANGEBOT, { 'PUT /api/angebote/9': json(200, ANGEBOT) });

    renderMaske('/angebote/9/bearbeiten');
    await bereit();
    await nutzer.clear(titelFeld());
    await nutzer.paste('x'.repeat(201));
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(titelFeld()).toHaveAccessibleDescription(TITEL_ZU_LANG);
    expect(titelFeld()).toHaveAttribute('aria-invalid', 'true');
    // Nur die beiden Lesewege: kein PUT.
    expect(fetchMock).toHaveBeenCalledTimes(2);

    // 200 Zeichen gehen durch.
    await nutzer.clear(titelFeld());
    await nutzer.paste('x'.repeat(200));
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Gespeichert');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/9',
      expect.objectContaining({ method: 'PUT' }),
    );
  });

  it('Szenario: Bestehendes Angebot ohne Titel speichern — nicht gespeichert, Meldung am Feld', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = lesen({ ...ANGEBOT, titel: null });

    renderMaske('/angebote/9/bearbeiten');
    await bereit();

    // `null` wird zum leeren Feld, nicht zu „null" (E9).
    expect(titelFeld()).toHaveValue('');
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(titelFeld()).toHaveAccessibleDescription(TITEL_FEHLT);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('zeigt die Meldungen an Titel und Datum zugleich', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = lesen();

    renderMaske('/angebote/9/bearbeiten');
    await nutzer.clear(await bereit());
    await nutzer.clear(titelFeld());
    await nutzer.type(titelFeld(), '  ');
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(titelFeld()).toHaveAccessibleDescription(TITEL_FEHLT);
    expect(screen.getByText('Bitte das Datum des Angebots angeben.')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});
