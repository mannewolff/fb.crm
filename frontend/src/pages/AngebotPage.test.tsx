import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import AngebotPage from './AngebotPage';
import { KopfPfadProvider } from '../components/KopfPfad';
import { fetchNachPfad, json, leer, problem } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';

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
  ansprechpartnerId: 8,
  ansprechpartnerName: 'Eva Adler',
  status: 'ABGEGEBEN',
  angebotDatum: '2026-09-24',
  beschreibung: 'Neue Website mit Redaktionssystem',
  intern: false,
  positionen: [POSITION],
  summe: 2500.03,
};

const KOMMENTARE = 'GET /api/angebote/9/kommentare';
const ANLAGEN = 'GET /api/angebote/9/anlagen';
const ABRECHNUNG = 'GET /api/angebote/9/abrechnung';

/** Ein Angebot, zu dem es noch keine Rechnung gibt (Issue #187). */
const LEERER_STAND = { positionen: [], rechnungen: [], angefallen: 0 };

/**
 * Die selbstladenden Bereiche mit leerem Bestand — sie stehen in jeder Tabelle dieser Datei.
 *
 * Die Bereiche „Anlagen" (Issue #148) und „Kommentare" (Issue #146) laden selbst, und der
 * Abrechnungsstand (Issue #187) kommt als eigener Weg; `fetchNachPfad` weist jeden nicht
 * gelisteten Weg ab: Ohne diese Eintraege liefe jede Probe in den Ausfallzweig eines der drei Wege.
 */
const LEERE_BEREICHE = {
  [KOMMENTARE]: json(200, { kommentare: [] }),
  [ANLAGEN]: json(200, { anlagen: [] }),
  [ABRECHNUNG]: json(200, LEERER_STAND),
};

/**
 * Die Position des Angebots mit ihrem Stand: 160 angeboten, 80 davon abgerechnet.
 *
 * Nicht buchbar: Sie rechnet in Personentagen ab, und Arbeitszeit traegt nur eine Position nach
 * Aufwand in Stunden (Issue #193, Antworten 3 und 5).
 */
const STAND_POSITION = {
  angebotPositionId: 3,
  bezeichnung: 'Konzeption',
  einheit: 'PERSONENTAG',
  angeboten: 160,
  abgerechnet: 80,
  offen: 80,
  ueberschreitung: 0,
  buchbar: false,
  angefallen: 0,
};

/** Eine Position nach Aufwand in Stunden — auf sie laesst sich Arbeitszeit buchen (Issue #193). */
const STUNDEN_POSITION = {
  id: 4,
  bezeichnung: 'Umsetzung',
  abrechnungsmodus: 'AUFWAND',
  menge: 20,
  einheit: 'STUNDE',
  einzelpreis: 120,
  betrag: 2400,
};

/** Dasselbe Angebot, dessen eine Position Stunden traegt. */
const ANGEBOT_MIT_STUNDEN = { ...ANGEBOT, positionen: [STUNDEN_POSITION], summe: 2400 };

/** Ihr Stand: 20 angeboten, davon 8 Stunden angefallen — noch innerhalb des Kontingents. */
const STAND_STUNDEN = {
  angebotPositionId: 4,
  bezeichnung: 'Umsetzung',
  einheit: 'STUNDE',
  angeboten: 20,
  abgerechnet: 0,
  offen: 20,
  ueberschreitung: 0,
  buchbar: true,
  angefallen: 8,
};

/** Eine gestellte Rechnung dieses Angebots. */
const GESTELLTE = {
  id: 4,
  nummer: '0001-2026',
  rechnungDatum: '2026-09-28',
  brutto: 95.2,
  zustand: 'GESTELLT',
};

/** Der Stand einer Teilabrechnung: eine gestellte Rechnung über die Hälfte der Menge. */
const STAND_TEIL = { positionen: [STAND_POSITION], rechnungen: [GESTELLTE], angefallen: 0 };

/** Ein bestelltes Angebot — ab diesem Status lässt sich eine Rechnung schreiben. */
const BESTELLT = { ...ANGEBOT, status: 'BESTELLT' };

const UEBERSCHRIFT = 'Angebot vom 24.09.2026';

function renderSeite(start = '/angebote/9') {
  return renderMitTheme(
    <MemoryRouter initialEntries={[start]}>
      <KopfPfadProvider>
        <Routes>
          <Route path="/angebote/:angebotId" element={<AngebotPage />} />
          {/* Das Ziel von „Rechnung schreiben" — die Probe liest hier ab, dass der Weg fuehrt. */}
          <Route path="/rechnungen/:rechnungId" element={<p>Rechnung angekommen</p>} />
        </Routes>
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

/** Die Aktionen neben der Ueberschrift — dort wird die eine Kupfertaste gezaehlt. */
function aktionen() {
  return within(screen.getByTestId('angebot-aktionen'));
}

/**
 * Die Kupfertasten unter den Aktionen.
 *
 * Sie tragen den Kupferverlauf, die weichen eine Flaeche (CLAUDE-design.md, „Tasten"). Die Rolle
 * unterscheidet beide nicht: „Bearbeiten" ist ein Weg und damit ein Link, „Status weiter" eine
 * Handlung und damit ein Knopf.
 */
function kupfertasten(): readonly HTMLElement[] {
  const feld = screen.getByTestId('angebot-aktionen');
  return [...within(feld).queryAllByRole('link'), ...within(feld).queryAllByRole('button')].filter(
    (taste) => getComputedStyle(taste).background.includes('linear-gradient'),
  );
}

/** Die Wahl des Monats im Dialog „Rechnung schreiben". */
function monatswahl(): HTMLSelectElement {
  return screen.getByRole('combobox', { name: 'Monat der Arbeitszeit' });
}

beforeEach(() => {
  // Die Monatswahl belegt mit dem laufenden Monat vor; ohne feste Zeit liefe die Probe zum
  // Monatswechsel auf einen anderen Erwartungswert.
  vi.useFakeTimers({ shouldAdvanceTime: true });
  vi.setSystemTime(new Date('2026-11-12T10:00:00Z'));
});

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
});

describe('AngebotPage — was das Angebot zeigt (Issue #127)', () => {
  it('traegt „Angebot vom <Datum>" als die eine Ueberschrift', async () => {
    fetchNachPfad({
      ...LEERE_BEREICHE,
      'GET /api/angebote/9': json(200, ANGEBOT),
    });

    renderSeite();

    expect(await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT })).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
  });

  it('nennt Firma, Ansprechpartner, Angebotsdatum und Status in den Angaben', async () => {
    fetchNachPfad({
      ...LEERE_BEREICHE,
      'GET /api/angebote/9': json(200, ANGEBOT),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    const angaben = within(screen.getByTestId('angebot-angaben'));
    expect(angaben.getAllByRole('term').map((teil) => teil.textContent)).toEqual([
      'Firma',
      'Ansprechpartner',
      'Angebotsdatum',
      'Status',
    ]);
    expect(angaben.getByRole('link', { name: 'Adler AG' })).toHaveAttribute('href', '/firmen/5');
    expect(angaben.getByText('Eva Adler')).toBeInTheDocument();
    expect(angaben.getByText('24.09.2026')).toBeInTheDocument();
    // Das Wort traegt den Status, die Toenung stuetzt ihn (CLAUDE-design.md, „Zustandsformen").
    expect(angaben.getByTestId('chip')).toHaveTextContent('Abgegeben');
  });

  it('laesst die Zeile des Ansprechpartners weg, wo das Angebot keinen traegt', async () => {
    fetchNachPfad({
      ...LEERE_BEREICHE,
      'GET /api/angebote/9': json(200, {
        ...ANGEBOT,
        ansprechpartnerId: null,
        ansprechpartnerName: null,
      }),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    const angaben = within(screen.getByTestId('angebot-angaben'));
    expect(angaben.queryByText('Ansprechpartner')).not.toBeInTheDocument();
    expect(angaben.getByText('Adler AG')).toBeInTheDocument();
  });

  it('zeigt die Beschreibung', async () => {
    fetchNachPfad({
      ...LEERE_BEREICHE,
      'GET /api/angebote/9': json(200, ANGEBOT),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    expect(screen.getByRole('heading', { name: 'Beschreibung' })).toBeInTheDocument();
    expect(screen.getByText('Neue Website mit Redaktionssystem')).toBeInTheDocument();
  });

  it('laesst die Karte der Beschreibung weg, wo keine steht — ohne Platzhalter', async () => {
    fetchNachPfad({
      ...LEERE_BEREICHE,
      'GET /api/angebote/9': json(200, { ...ANGEBOT, beschreibung: null }),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    expect(screen.queryByRole('heading', { name: 'Beschreibung' })).not.toBeInTheDocument();
    expect(screen.queryByText('—')).not.toBeInTheDocument();
  });

  it('stellt die Positionen als Tafel mit Menge, Einheit, Einzelpreis und Betrag', async () => {
    fetchNachPfad({
      ...LEERE_BEREICHE,
      'GET /api/angebote/9': json(200, ANGEBOT),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    expect(screen.getAllByRole('columnheader').map((kopf) => kopf.textContent)).toEqual([
      'Bezeichnung',
      'Abrechnung',
      'Menge',
      'Einheit',
      'Einzelpreis',
      'Betrag',
    ]);
    const zeile = within(screen.getAllByRole('row')[1]);
    expect(zeile.getByText('Konzeption')).toBeInTheDocument();
    expect(zeile.getByText('Aufwand')).toBeInTheDocument();
    expect(zeile.getByText('2,50')).toBeInTheDocument();
    expect(zeile.getByText('Personentag')).toBeInTheDocument();
    expect(zeile.getByText('1.000,01 €')).toBeInTheDocument();
    expect(zeile.getByText('2.500,03 €')).toBeInTheDocument();
  });

  it('nennt die Summe mit dem Hinweis auf die Umsatzsteuer', async () => {
    fetchNachPfad({
      ...LEERE_BEREICHE,
      'GET /api/angebote/9': json(200, ANGEBOT),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    expect(screen.getByTestId('angebot-summe')).toHaveTextContent('2.500,03 €');
    expect(
      screen.getByText('Alle Beträge netto, zzgl. gesetzlicher Umsatzsteuer'),
    ).toBeInTheDocument();
  });

  it('sagt es, wenn das Angebot noch keine Position hat', async () => {
    fetchNachPfad({
      ...LEERE_BEREICHE,
      'GET /api/angebote/9': json(200, { ...ANGEBOT, positionen: [], summe: 0 }),
    });

    renderSeite();

    // Nach dem Namen und nicht nach der Rolle: Der leere Kommentarbereich meldet sich ebenfalls
    // als `status`, und beide Saetze sagen etwas anderes (Issue #146).
    expect(await screen.findByText('Noch keine Position.')).toBeInTheDocument();
  });
});

describe('AngebotPage — die Aktionen (Issue #127, Kriterien 4, 5)', () => {
  it('traegt „Status weiter" als einzige Kupfertaste, „Status zurück" und „Bearbeiten" weich', async () => {
    fetchNachPfad({
      ...LEERE_BEREICHE,
      'GET /api/angebote/9': json(200, ANGEBOT),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    // Genau eine Kupfertaste je Ansicht (CLAUDE-design.md, Leitgedanke 2).
    expect(kupfertasten().map((taste) => taste.textContent)).toEqual(['Status weiter']);
    expect(aktionen().getByRole('button', { name: 'Status zurück' })).toBeInTheDocument();
    expect(aktionen().getByRole('link', { name: 'Bearbeiten' })).toHaveAttribute(
      'href',
      '/angebote/9/bearbeiten',
    );
  });

  it('bietet bei „angelegt" kein „Status zurück" an', async () => {
    fetchNachPfad({
      ...LEERE_BEREICHE,
      'GET /api/angebote/9': json(200, { ...ANGEBOT, status: 'ANGELEGT' }),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    expect(aktionen().queryByRole('button', { name: 'Status zurück' })).not.toBeInTheDocument();
    expect(aktionen().getByRole('button', { name: 'Status weiter' })).toBeInTheDocument();
  });

  it('bietet bei „abgerechnet" kein „Status weiter" an, Bearbeiten aber schon', async () => {
    fetchNachPfad({
      ...LEERE_BEREICHE,
      'GET /api/angebote/9': json(200, { ...ANGEBOT, status: 'ABGERECHNET' }),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    expect(aktionen().queryByRole('button', { name: 'Status weiter' })).not.toBeInTheDocument();
    expect(kupfertasten()).toEqual([]);
    expect(aktionen().getByRole('button', { name: 'Status zurück' })).toBeInTheDocument();
    expect(aktionen().getByRole('link', { name: 'Bearbeiten' })).toBeInTheDocument();
  });

  it.each([
    ['Status weiter', 'POST /api/angebote/9/status/weiter', 'BESTELLT', 'Bestellt'],
    ['Status zurück', 'POST /api/angebote/9/status/zurueck', 'ANGELEGT', 'Angelegt'],
  ])('schaltet mit „%s" und nimmt den Status aus der Antwort', async (
    taste,
    schluessel,
    status,
    wort,
  ) => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      ...LEERE_BEREICHE,
      'GET /api/angebote/9': json(200, ANGEBOT),
      [schluessel]: json(200, { ...ANGEBOT, status }),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });
    await nutzer.click(aktionen().getByRole('button', { name: taste }));

    expect(await screen.findByText(wort)).toBeInTheDocument();
    expect(within(screen.getByTestId('angebot-angaben')).getByTestId('chip')).toHaveTextContent(
      wort,
    );
    expect(fetchMock).toHaveBeenCalledWith(
      schluessel.slice('POST '.length),
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('meldet, wenn der Statuswechsel nicht durchgeht, und behaelt den bisherigen Status', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      ...LEERE_BEREICHE,
      'GET /api/angebote/9': json(200, ANGEBOT),
      'POST /api/angebote/9/status/weiter': problem(409, 'In diese Richtung gibt es keinen weiteren Status.'),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });
    await nutzer.click(aktionen().getByRole('button', { name: 'Status weiter' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht geändert');
    expect(within(screen.getByTestId('angebot-angaben')).getByTestId('chip')).toHaveTextContent(
      'Abgegeben',
    );
  });
});

describe('AngebotPage — unsinnige Kennung, unbekanntes Angebot, Ausfall', () => {
  it('faengt eine nicht numerische Kennung ab, bevor sie an die Schnittstelle geht', async () => {
    const fetchMock = fetchNachPfad({});

    renderSeite('/angebote/keine-zahl');

    expect(await screen.findByRole('alert')).toHaveTextContent('gibt es nicht');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('meldet ein unbekanntes Angebot', async () => {
    fetchNachPfad({
      ...LEERE_BEREICHE,
      'GET /api/angebote/9': leer(404),
    });

    renderSeite();

    expect(await screen.findByRole('alert')).toHaveTextContent('gibt es nicht');
  });

  it('meldet den Ausfall der Schnittstelle', async () => {
    fetchNachPfad({
      ...LEERE_BEREICHE,
      'GET /api/angebote/9': leer(503),
    });

    renderSeite();

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht zu erreichen');
  });

  it('zeigt waehrend des Ladens einen Hinweis statt einer leeren Seite', () => {
    fetchNachPfad({
      ...LEERE_BEREICHE,
      'GET /api/angebote/9': json(200, ANGEBOT),
    });

    renderSeite();

    expect(screen.getByText('Das Angebot wird geladen …')).toBeInTheDocument();
  });
});

describe('AngebotPage — der Bereich „Kommentare" (Issue #146)', () => {
  it('stellt die Karte „Kommentare" unter die Positionen', async () => {
    fetchNachPfad({
      ...LEERE_BEREICHE,
      'GET /api/angebote/9': json(200, ANGEBOT),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    expect(
      screen.getAllByRole('heading', { level: 2 }).map((kopf) => kopf.textContent),
    ).toEqual(['Beschreibung', 'Positionen', 'Rechnungen', 'Anlagen', 'Kommentare']);
    // Der Satz steht nur, wenn der Kommentarweg geantwortet hat — keine Probe dieser Datei laeuft
    // in „Unerwarteter Aufruf".
    expect(screen.getByText('Noch kein Kommentar.')).toBeInTheDocument();
  });

  it('laesst Ueberschrift, Angaben und Positionen stehen, wenn der Kommentarweg ausfaellt', async () => {
    fetchNachPfad({
      [KOMMENTARE]: leer(503),
      [ANLAGEN]: json(200, { anlagen: [] }),
      'GET /api/angebote/9': json(200, ANGEBOT),
    });

    renderSeite();

    expect(
      await screen.findByText('Die Kommentare sind gerade nicht zu erreichen. Bitte später erneut versuchen.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: UEBERSCHRIFT })).toBeInTheDocument();
    expect(within(screen.getByTestId('angebot-angaben')).getByText('Adler AG')).toBeInTheDocument();
    expect(screen.getByText('Konzeption')).toBeInTheDocument();
    expect(screen.getByTestId('angebot-summe')).toHaveTextContent('2.500,03 €');
  });
});

describe('AngebotPage — der Bereich „Anlagen" (Issue #148)', () => {
  it('stellt die Karte „Anlagen" zwischen die Positionen und die Kommentare', async () => {
    fetchNachPfad({
      ...LEERE_BEREICHE,
      'GET /api/angebote/9': json(200, ANGEBOT),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    expect(
      screen.getAllByRole('heading', { level: 2 }).map((kopf) => kopf.textContent),
    ).toEqual(['Beschreibung', 'Positionen', 'Rechnungen', 'Anlagen', 'Kommentare']);
    // Der Satz steht nur, wenn der Anlagenweg geantwortet hat — keine Probe dieser Datei laeuft
    // in „Unerwarteter Aufruf".
    expect(screen.getByText('Noch keine Anlage.')).toBeInTheDocument();
  });

  it('laesst Ueberschrift, Angaben, Positionen und Kommentare stehen, wenn der Anlagenweg ausfaellt', async () => {
    fetchNachPfad({
      [KOMMENTARE]: json(200, { kommentare: [] }),
      [ANLAGEN]: leer(503),
      'GET /api/angebote/9': json(200, ANGEBOT),
    });

    renderSeite();

    expect(
      await screen.findByText('Die Anlagen sind gerade nicht zu erreichen. Bitte später erneut versuchen.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: UEBERSCHRIFT })).toBeInTheDocument();
    expect(within(screen.getByTestId('angebot-angaben')).getByText('Adler AG')).toBeInTheDocument();
    expect(screen.getByText('Konzeption')).toBeInTheDocument();
    expect(screen.getByTestId('angebot-summe')).toHaveTextContent('2.500,03 €');
    expect(screen.getByText('Noch kein Kommentar.')).toBeInTheDocument();
  });
});

describe('AngebotPage — der Abrechnungsstand an den Positionen (Issue #187, Kriterium 26)', () => {
  it('stellt abgerechnet und offen je Position, sobald es eine Rechnung gibt', async () => {
    fetchNachPfad({
      ...LEERE_BEREICHE,
      [ABRECHNUNG]: json(200, STAND_TEIL),
      'GET /api/angebote/9': json(200, ANGEBOT),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    expect(await screen.findByText('Abgerechnet')).toBeInTheDocument();
    const tafel = within(screen.getByRole('table', { name: 'Positionen' }));
    expect(tafel.getAllByRole('columnheader').map((kopf) => kopf.textContent)).toEqual([
      'Bezeichnung',
      'Abrechnung',
      'Menge',
      'Abgerechnet',
      'Offen',
      'Einheit',
      'Einzelpreis',
      'Betrag',
    ]);
    const zeile = within(tafel.getAllByRole('row')[1]);
    // 80 von 160: beide Zahlen stehen gleich da, abgerechnet und offen.
    expect(zeile.getAllByText('80,00')).toHaveLength(2);
  });

  it('laesst die beiden Spalten weg, solange es zu dem Angebot keine Rechnung gibt', async () => {
    fetchNachPfad({
      ...LEERE_BEREICHE,
      'GET /api/angebote/9': json(200, ANGEBOT),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    expect(screen.getAllByRole('columnheader').map((kopf) => kopf.textContent)).toEqual([
      'Bezeichnung',
      'Abrechnung',
      'Menge',
      'Einheit',
      'Einzelpreis',
      'Betrag',
    ]);
    expect(screen.queryByText('Abgerechnet')).not.toBeInTheDocument();
  });

  it('laesst die Zellen leer, wo der Stand die Position nicht nennt', async () => {
    fetchNachPfad({
      ...LEERE_BEREICHE,
      // Rechnungen, aber kein Stand zu dieser Position — die Zellen stehen, sie sagen nichts.
      [ABRECHNUNG]: json(200, { positionen: [], rechnungen: [GESTELLTE], angefallen: 0 }),
      'GET /api/angebote/9': json(200, ANGEBOT),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    await screen.findByText('Abgerechnet');
    const zellen = within(
      within(screen.getByRole('table', { name: 'Positionen' })).getAllByRole('row')[1],
    ).getAllByRole('cell');
    expect(zellen[3]).toHaveTextContent('');
    expect(zellen[4]).toHaveTextContent('');
  });

  it('stellt eine Ueberschreitung als Hinweis mit Wort und Symbol in die Zeile', async () => {
    fetchNachPfad({
      ...LEERE_BEREICHE,
      [ABRECHNUNG]: json(200, {
        positionen: [{ ...STAND_POSITION, abgerechnet: 200, offen: 0, ueberschreitung: 40 }],
        rechnungen: [GESTELLTE],
        angefallen: 0,
      }),
      'GET /api/angebote/9': json(200, ANGEBOT),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    expect(await screen.findByTestId('zeile-hinweis-3')).toHaveTextContent(
      '40,00 über dem Angebot',
    );
  });
});

describe('AngebotPage — die Spalte „Angefallen" (Issue #193, Kriterien 7, 8, 11)', () => {
  it('stellt die Spalte an einer buchbaren Position, auch ohne Rechnung', async () => {
    fetchNachPfad({
      ...LEERE_BEREICHE,
      [ABRECHNUNG]: json(200, { positionen: [STAND_STUNDEN], rechnungen: [], angefallen: 8 }),
      'GET /api/angebote/9': json(200, ANGEBOT_MIT_STUNDEN),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    expect(await screen.findByText('Angefallen')).toBeInTheDocument();
    const tafel = within(screen.getByRole('table', { name: 'Positionen' }));
    // „Abgerechnet" und „Offen" fehlen: Die Regel „erst mit Rechnung" gilt weiter nur fuer sie.
    expect(tafel.getAllByRole('columnheader').map((kopf) => kopf.textContent)).toEqual([
      'Bezeichnung',
      'Abrechnung',
      'Menge',
      'Angefallen',
      'Einheit',
      'Einzelpreis',
      'Betrag',
    ]);
    expect(within(tafel.getAllByRole('row')[1]).getByText('8,00')).toBeInTheDocument();
  });

  it('laesst die Spalte weg, wenn keine Position buchbar ist', async () => {
    fetchNachPfad({
      ...LEERE_BEREICHE,
      [ABRECHNUNG]: json(200, { positionen: [STAND_POSITION], rechnungen: [], angefallen: 0 }),
      'GET /api/angebote/9': json(200, ANGEBOT),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    expect(screen.getAllByRole('columnheader').map((kopf) => kopf.textContent)).toEqual([
      'Bezeichnung',
      'Abrechnung',
      'Menge',
      'Einheit',
      'Einzelpreis',
      'Betrag',
    ]);
    expect(screen.queryByText('Angefallen')).not.toBeInTheDocument();
  });

  it('laesst die Zelle an einer Festpreisposition leer', async () => {
    fetchNachPfad({
      ...LEERE_BEREICHE,
      [ABRECHNUNG]: json(200, {
        positionen: [STAND_POSITION, STAND_STUNDEN],
        rechnungen: [],
        angefallen: 8,
      }),
      'GET /api/angebote/9': json(200, {
        ...ANGEBOT,
        positionen: [POSITION, STUNDEN_POSITION],
        summe: 4900.03,
      }),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    await screen.findByText('Angefallen');
    const zeilen = within(screen.getByRole('table', { name: 'Positionen' })).getAllByRole('row');
    // Die vierte Zelle ist „Angefallen": an der Stundenposition 8,00, an der anderen nichts.
    expect(within(zeilen[1]).getAllByRole('cell')[3]).toHaveTextContent('');
    expect(within(zeilen[2]).getAllByRole('cell')[3]).toHaveTextContent('8,00');
  });

  it('stellt eine Ueberschreitung des Kontingents als Hinweis in die Zelle', async () => {
    fetchNachPfad({
      ...LEERE_BEREICHE,
      [ABRECHNUNG]: json(200, {
        positionen: [{ ...STAND_STUNDEN, angefallen: 22 }],
        rechnungen: [],
        angefallen: 22,
      }),
      'GET /api/angebote/9': json(200, ANGEBOT_MIT_STUNDEN),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    expect(await screen.findByTestId('zeile-angefallen-hinweis-4')).toHaveTextContent(
      'Kontingent um 2,00 Std. überschritten',
    );
  });

  it('haelt Spalte und Hinweis auch an einem abgerechneten Angebot', async () => {
    fetchNachPfad({
      ...LEERE_BEREICHE,
      [ABRECHNUNG]: json(200, {
        positionen: [{ ...STAND_STUNDEN, abgerechnet: 20, offen: 0, angefallen: 22 }],
        rechnungen: [GESTELLTE],
        angefallen: 22,
      }),
      'GET /api/angebote/9': json(200, { ...ANGEBOT_MIT_STUNDEN, status: 'ABGERECHNET' }),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    expect(await screen.findByText('Angefallen')).toBeInTheDocument();
    expect(screen.getByTestId('zeile-angefallen-hinweis-4')).toHaveTextContent(
      'Kontingent um 2,00 Std. überschritten',
    );
  });
});

describe('AngebotPage — der Bereich „Rechnungen" (Issue #187, Kriterium 26)', () => {
  it('stellt die Karte zwischen die Positionen und die Anlagen', async () => {
    fetchNachPfad({
      ...LEERE_BEREICHE,
      'GET /api/angebote/9': json(200, ANGEBOT),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    expect(screen.getAllByRole('heading', { level: 2 }).map((kopf) => kopf.textContent)).toEqual([
      'Beschreibung',
      'Positionen',
      'Rechnungen',
      'Anlagen',
      'Kommentare',
    ]);
    expect(await screen.findByText('Noch keine Rechnung.')).toBeInTheDocument();
  });

  it('listet den Entwurf als „Entwurf" und die gestellte Rechnung mit ihrer Nummer', async () => {
    fetchNachPfad({
      ...LEERE_BEREICHE,
      [ABRECHNUNG]: json(200, {
        positionen: [STAND_POSITION],
        rechnungen: [
          GESTELLTE,
          { id: 7, nummer: null, rechnungDatum: '2026-09-30', brutto: 47.6, zustand: 'ENTWURF' },
        ],
        angefallen: 0,
      }),
      'GET /api/angebote/9': json(200, ANGEBOT),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    const tafel = within(await screen.findByRole('table', { name: 'Rechnungen' }));
    expect(tafel.getByRole('link', { name: '0001-2026' })).toHaveAttribute(
      'href',
      '/rechnungen/4',
    );
    expect(tafel.getByRole('link', { name: 'Entwurf' })).toHaveAttribute('href', '/rechnungen/7');
    expect(tafel.getByText('28.09.2026')).toBeInTheDocument();
    expect(tafel.getByText('95,20 €')).toBeInTheDocument();
    expect(tafel.getAllByTestId('chip').map((chip) => chip.textContent)).toEqual([
      'Gestellt',
      'Entwurf',
    ]);
    // Die Anzahl steht im Kartenkopf — wie bei „Positionen" und „Anlagen".
    expect(screen.getAllByTestId('karte-anzahl').map((feld) => feld.textContent)).toContain('2');
  });
});

describe('AngebotPage — „Rechnung schreiben" (Issue #187, Kriterium 3)', () => {
  /** Der frische Entwurf, mit dem das Anlegen antwortet — die Seite braucht nur seine Kennung. */
  const FRISCH = {
    id: 7,
    angebotId: 9,
    firmaId: 5,
    firmaName: 'Adler AG',
    rechnungDatum: '2026-09-30',
    leistungszeitraum: null,
    zustand: 'ENTWURF',
    nummer: null,
    steuersatz: 19,
    netto: 0,
    steuer: 0,
    brutto: 0,
    zahlungszielTage: null,
    empfaenger: null,
    absender: null,
    zeilen: [],
  };

  /** Die Wege eines bestellten Angebots mit einer Teilabrechnung. */
  const OFFEN = {
    ...LEERE_BEREICHE,
    [ABRECHNUNG]: json(200, STAND_TEIL),
    'GET /api/angebote/9': json(200, BESTELLT),
  };

  it('steht bei einem bestellten Angebot mit Offenem, ohne eine zweite Kupfertaste', async () => {
    fetchNachPfad(OFFEN);

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    expect(
      await aktionen().findByRole('button', { name: 'Rechnung schreiben' }),
    ).toBeInTheDocument();
    expect(kupfertasten().map((taste) => taste.textContent)).toEqual(['Status weiter']);
  });

  it('fehlt bei einem Angebot im Status „angelegt"', async () => {
    fetchNachPfad({
      ...LEERE_BEREICHE,
      [ABRECHNUNG]: json(200, STAND_TEIL),
      'GET /api/angebote/9': json(200, { ...ANGEBOT, status: 'ANGELEGT' }),
    });

    renderSeite();
    await screen.findByText('Noch keine Anlage.');

    expect(
      aktionen().queryByRole('button', { name: 'Rechnung schreiben' }),
    ).not.toBeInTheDocument();
  });

  it('fehlt, wo an keiner Position mehr etwas offen ist', async () => {
    fetchNachPfad({
      ...LEERE_BEREICHE,
      [ABRECHNUNG]: json(200, {
        positionen: [{ ...STAND_POSITION, abgerechnet: 160, offen: 0 }],
        rechnungen: [GESTELLTE],
        angefallen: 0,
      }),
      'GET /api/angebote/9': json(200, BESTELLT),
    });

    renderSeite();
    await screen.findByText('0001-2026');

    expect(
      aktionen().queryByRole('button', { name: 'Rechnung schreiben' }),
    ).not.toBeInTheDocument();
  });

  it('oeffnet die Wahl des Monats, statt sofort anzulegen', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad(OFFEN);

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });
    await nutzer.click(await aktionen().findByRole('button', { name: 'Rechnung schreiben' }));

    const dialog = within(await screen.findByRole('dialog'));
    expect(monatswahl()).toHaveValue('2026-11');
    expect(dialog.getByRole('button', { name: 'Anlegen' })).toBeInTheDocument();
    // Der Klick auf die Taste legt noch nichts an — erst „Anlegen" im Dialog.
    expect(fetchMock.mock.calls.map(([weg]) => weg)).not.toContain('/api/angebote/9/rechnungen');
  });

  it('schliesst die Wahl mit „Abbrechen", ohne etwas anzulegen', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad(OFFEN);

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });
    await nutzer.click(await aktionen().findByRole('button', { name: 'Rechnung schreiben' }));
    await screen.findByRole('dialog');
    await nutzer.click(screen.getByRole('button', { name: 'Abbrechen' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.map(([weg]) => weg)).not.toContain('/api/angebote/9/rechnungen');
  });

  it('legt mit dem gewaehlten Vormonat an', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ ...OFFEN, 'POST /api/angebote/9/rechnungen': json(201, FRISCH) });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });
    await nutzer.click(await aktionen().findByRole('button', { name: 'Rechnung schreiben' }));
    await screen.findByRole('dialog');
    await nutzer.selectOptions(monatswahl(), '2026-10');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(await screen.findByText('Rechnung angekommen')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/9/rechnungen',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ monat: '2026-10' }) }),
    );
  });

  it('schickt bei „ohne Arbeitszeit" keinen Rumpf', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ ...OFFEN, 'POST /api/angebote/9/rechnungen': json(201, FRISCH) });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });
    await nutzer.click(await aktionen().findByRole('button', { name: 'Rechnung schreiben' }));
    await screen.findByRole('dialog');
    await nutzer.selectOptions(monatswahl(), '');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(await screen.findByText('Rechnung angekommen')).toBeInTheDocument();
    const anlegen = fetchMock.mock.calls.find(([weg]) => weg === '/api/angebote/9/rechnungen');
    expect(anlegen?.[1]).not.toHaveProperty('body');
  });

  it('sperrt die Tasten der Wahl, solange das Anlegen laeuft', async () => {
    const nutzer = userEvent.setup();
    // Ohne Platzhalter-Funktion: Ein nie gerufener Vorbelegungswert waere ungetesteter Code.
    let liefere!: (antwort: Response) => void;
    const spaeter = new Promise<Response>((aufloesen) => {
      liefere = aufloesen;
    });
    fetchNachPfad({ ...OFFEN, 'POST /api/angebote/9/rechnungen': () => spaeter });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });
    await nutzer.click(await aktionen().findByRole('button', { name: 'Rechnung schreiben' }));
    await screen.findByRole('dialog');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(screen.getByRole('button', { name: 'Anlegen' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Abbrechen' })).toBeDisabled();
    expect(monatswahl()).toBeDisabled();
    liefere(json(201, FRISCH)());
    expect(await screen.findByText('Rechnung angekommen')).toBeInTheDocument();
  });

  it('legt den Entwurf fuer den laufenden Monat an und fuehrt auf die Rechnung', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      ...OFFEN,
      'POST /api/angebote/9/rechnungen': json(200, FRISCH),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });
    await nutzer.click(await aktionen().findByRole('button', { name: 'Rechnung schreiben' }));
    await screen.findByRole('dialog');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(await screen.findByText('Rechnung angekommen')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/9/rechnungen',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ monat: '2026-11' }) }),
    );
  });

  it('zeigt die Meldung des Servers, wo das Anlegen mit 409 abgewiesen wird', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      ...OFFEN,
      'POST /api/angebote/9/rechnungen': problem(
        409,
        'Zu diesem Angebot ist nichts mehr offen.',
      ),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });
    await nutzer.click(await aktionen().findByRole('button', { name: 'Rechnung schreiben' }));
    await screen.findByRole('dialog');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    // Die Meldung steht im Dialog, und der bleibt offen — der Betrachter kann einen anderen
    // Monat waehlen, ohne die Wahl neu zu oeffnen.
    const dialog = within(await screen.findByRole('dialog'));
    expect(await dialog.findByRole('alert')).toHaveTextContent('nichts mehr offen');
    expect(screen.queryByText('Rechnung angekommen')).not.toBeInTheDocument();
  });

  it('laesst Ueberschrift, Angaben und Positionen stehen, wenn der Abrechnungsweg ausfaellt', async () => {
    fetchNachPfad({
      ...LEERE_BEREICHE,
      [ABRECHNUNG]: leer(503),
      'GET /api/angebote/9': json(200, BESTELLT),
    });

    renderSeite();

    expect(
      await screen.findByText(
        'Der Abrechnungsstand ist gerade nicht zu erreichen. Bitte später erneut versuchen.',
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: UEBERSCHRIFT })).toBeInTheDocument();
    expect(within(screen.getByTestId('angebot-angaben')).getByText('Adler AG')).toBeInTheDocument();
    expect(screen.getByText('Konzeption')).toBeInTheDocument();
    expect(screen.getByTestId('angebot-summe')).toHaveTextContent('2.500,03 €');
    expect(
      aktionen().queryByRole('button', { name: 'Rechnung schreiben' }),
    ).not.toBeInTheDocument();
  });
});
