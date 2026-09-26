import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { Eintrag, Vorgang } from '../api/vorgaenge';
import KopfPfad, { KopfPfadProvider } from '../components/KopfPfad';
import { fetchNachPfad, json, leer } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';
import VorgangPage from './VorgangPage';

const KOMMENTAR: Eintrag = {
  id: 31,
  art: 'KOMMENTAR',
  text: 'Kunde hat telefonisch angefragt.',
  geschehenAm: '2026-09-20T08:30:00Z',
  herkunft: 'VON_HAND',
  dateiName: null,
  dateiGroesse: null,
  geaendertAm: null,
};

const ANHANG: Eintrag = {
  id: 32,
  art: 'ANHANG',
  text: null,
  geschehenAm: '2026-09-21T09:00:00Z',
  herkunft: 'VON_HAND',
  dateiName: 'anfrage.pdf',
  dateiGroesse: 20480,
  geaendertAm: null,
};

const VORGANG: Vorgang = {
  id: 5,
  nummer: 941,
  titel: 'Anteilsbalken je Vorgang statt Band über alle',
  phase: 'ANBAHNUNG',
  abgeschlossen: false,
  firma: { id: 7, name: 'Beispiel GmbH', aktiv: true },
  ansprechpartner: { id: 11, name: 'Anna Berg', aktiv: true },
  historie: [ANHANG, KOMMENTAR],
};

/**
 * Der Titel der Kopfkarte: Nummer und Titel bilden zusammen die **eine `h1`** der Ansicht.
 *
 * Wer mit dem Screenreader auf die Seite kommt, findet damit an erster Stelle, um welchen Vorgang
 * es geht (Baustein `Kopfkarte`, #79).
 */
const KOPFZEILE = `#${String(VORGANG.nummer)} ${VORGANG.titel}`;

/** Die Adresse, an der sich ablesen laesst, wohin ein Weg gefuehrt hat. */
function Adresse() {
  const ort = useLocation();
  return <p data-testid="adresse">{ort.pathname}</p>;
}

function renderSeite(start = '/vorgaenge/5') {
  return renderMitTheme(
    <MemoryRouter initialEntries={[start]}>
      <KopfPfadProvider>
        <Routes>
          <Route path="/vorgaenge/:id" element={<VorgangPage />} />
          <Route path="/vorgaenge/:id/bearbeiten" element={<p>Maske</p>} />
          <Route path="/firmen/:id" element={<p>Firma</p>} />
        </Routes>
        <Adresse />
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

/** Dieselbe Ansicht, aber mit dem Kopf darueber — fuer den Pfad, den sie meldet. */
function renderMitKopf(start = '/vorgaenge/5') {
  return renderMitTheme(
    <MemoryRouter initialEntries={[start]}>
      <KopfPfadProvider>
        <KopfPfad />
        <Routes>
          <Route path="/vorgaenge/:id" element={<VorgangPage />} />
        </Routes>
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

/**
 * Ein Doppel, dessen Vorgang sich mit dem Abschliessen aendert.
 *
 * Die Ansicht liest nach jeder Schaltung neu — ein Doppel mit festem Rumpf wuerde deshalb gruen
 * bleiben, auch wenn die Ansicht das Ergebnis gar nicht uebernaehme.
 */
function vorgangDoppel(start: Vorgang = VORGANG) {
  let vorgang: Vorgang = start;
  const schalte = (abgeschlossen: boolean) => {
    vorgang = { ...vorgang, abgeschlossen };
    return new Response(null, { status: 204 });
  };
  return fetchNachPfad({
    'GET /api/vorgaenge/5': () =>
      new Response(JSON.stringify(vorgang), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    'POST /api/vorgaenge/5/abschliessen': () => schalte(true),
    'POST /api/vorgaenge/5/wiedereroeffnen': () => schalte(false),
  });
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('VorgangPage — die Kopfkarte (Kriterien 9, 11)', () => {
  it('traegt Nummer und Titel als die eine Ueberschrift der Ansicht', async () => {
    vorgangDoppel();

    renderSeite();

    expect(await screen.findByRole('heading', { level: 1, name: KOPFZEILE })).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    // Die Nummer steht als eigener Teil, damit sie ihre Tabellenziffern tragen kann.
    expect(screen.getByText('#941')).toBeInTheDocument();
  });

  it('nennt Firma und Ansprechpartner in der Zeile unter dem Titel', async () => {
    vorgangDoppel();

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: KOPFZEILE });

    expect(screen.getByTestId('kopfkarte-zeile')).toHaveTextContent('Beispiel GmbH · Anna Berg');
  });

  it('nennt die Phase als Chip und beim offenen Vorgang keinen Abschluss', async () => {
    vorgangDoppel();

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: KOPFZEILE });

    const chips = within(screen.getByTestId('kopfkarte-chips'));
    expect(chips.getByText('Anbahnung')).toBeInTheDocument();
    expect(chips.queryByText('Abgeschlossen')).not.toBeInTheDocument();
  });

  it('stellt am offenen Vorgang genau „Bearbeiten" und „Abschließen" in den Kopf', async () => {
    vorgangDoppel();

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: KOPFZEILE });

    // „Abschliessen" ist die Hauptaktion und damit die einzige Kupfertaste der Ansicht
    // (CLAUDE-design.md, „Tasten"); „Bearbeiten" tritt als weiche Taste daneben zurueck.
    const aktionen = within(screen.getByTestId('kopfkarte-aktionen'));
    expect(aktionen.getByRole('link', { name: 'Bearbeiten' })).toBeInTheDocument();
    expect(aktionen.getAllByRole('button')).toHaveLength(1);
    expect(aktionen.getByRole('button', { name: 'Abschließen' })).toBeInTheDocument();
  });

  it('stellt am abgeschlossenen Vorgang „Wieder öffnen" statt „Abschließen" in den Kopf', async () => {
    vorgangDoppel({ ...VORGANG, abgeschlossen: true });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: KOPFZEILE });

    const aktionen = within(screen.getByTestId('kopfkarte-aktionen'));
    expect(aktionen.getByRole('link', { name: 'Bearbeiten' })).toBeInTheDocument();
    expect(aktionen.getAllByRole('button')).toHaveLength(1);
    expect(aktionen.getByRole('button', { name: 'Wieder öffnen' })).toBeInTheDocument();
  });

  it('zeigt waehrend des Ladens einen Hinweis statt einer leeren Seite', () => {
    vorgangDoppel();

    renderSeite();

    expect(screen.getByText('Der Vorgang wird geladen …')).toBeInTheDocument();
  });

  it('fuehrt mit „Bearbeiten" auf die Maske des Vorgangs (Kriterium 10)', async () => {
    const nutzer = userEvent.setup();
    vorgangDoppel();

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: KOPFZEILE });

    expect(screen.getByRole('link', { name: 'Bearbeiten' })).toHaveAttribute(
      'href',
      '/vorgaenge/5/bearbeiten',
    );
    await nutzer.click(screen.getByRole('link', { name: 'Bearbeiten' }));

    expect(screen.getByText('Maske')).toBeInTheDocument();
    expect(screen.getByTestId('adresse')).toHaveTextContent('/vorgaenge/5/bearbeiten');
  });

  it('traegt „Bearbeiten" auch am abgeschlossenen Vorgang (Kriterium 10)', async () => {
    vorgangDoppel({ ...VORGANG, abgeschlossen: true });

    renderSeite();

    expect(await screen.findByRole('link', { name: 'Bearbeiten' })).toBeInTheDocument();
  });
});

describe('VorgangPage — die Karte „Felder" (Kriterien 9, 23)', () => {
  it('stellt die Felder als Beschriftung–Wert-Liste', async () => {
    vorgangDoppel();

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: KOPFZEILE });

    // Stammdaten-Liste der Vorlage (`.stamm` Z. 100–102): Beschriftung und Wert als echte
    // Begriffsliste, keine Tabelle — die Felder eines Vorgangs sind Paare, keine Matrix. Die
    // Rollen `term` und `definition` gibt es nur in einem `dl` aus `dt` und `dd`.
    const felder = within(screen.getByTestId('vorgang-felder'));
    expect(felder.getAllByRole('term').map((teil) => teil.textContent)).toEqual([
      'Firma',
      'Ansprechpartner',
    ]);
    expect(felder.getAllByRole('definition').map((teil) => teil.textContent)).toEqual([
      'Beispiel GmbH',
      'Anna Berg',
    ]);
  });

  it('macht Firma und Ansprechpartner zu Wegen auf die Detailansicht der Firma', async () => {
    vorgangDoppel();

    renderSeite();

    expect(await screen.findByRole('link', { name: 'Beispiel GmbH' })).toHaveAttribute(
      'href',
      '/firmen/7',
    );
    expect(screen.getByRole('link', { name: 'Anna Berg' })).toHaveAttribute('href', '/firmen/7');
  });

  it('kennzeichnet eine stillgelegte Zuordnung und sagt sie im Namen des Weges an', async () => {
    vorgangDoppel({
      ...VORGANG,
      firma: { id: 7, name: 'Beispiel GmbH', aktiv: false },
      ansprechpartner: { id: 11, name: 'Anna Berg', aktiv: false },
    });

    renderSeite();

    // Der Stand steht als Wort da — und er gehoert zum Namen des Weges, damit der Screenreader
    // ihn nicht erst in der Nachbarschaft suchen muss (Kriterium 26).
    expect(
      await screen.findByRole('link', { name: 'Beispiel GmbH (stillgelegt)' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Anna Berg (stillgelegt)' })).toBeInTheDocument();
    expect(screen.getAllByText('stillgelegt')).toHaveLength(2);
    // Auch die Zeile der Kopfkarte sagt es — dort als Zusatz am Namen, weil in einer Textzeile
    // kein Schild steht (CLAUDE-design.md, „Zustandsformen").
    expect(screen.getByTestId('kopfkarte-zeile')).toHaveTextContent(
      'Beispiel GmbH (stillgelegt) · Anna Berg (stillgelegt)',
    );
  });

  it('laesst die Zeile ohne Ansprechpartner weg — ohne Platzhaltertext', async () => {
    vorgangDoppel({ ...VORGANG, ansprechpartner: null });

    renderSeite();

    await screen.findByRole('link', { name: 'Beispiel GmbH' });
    expect(screen.queryByText('Ansprechpartner')).not.toBeInTheDocument();
    expect(screen.queryByText('—')).not.toBeInTheDocument();
  });
});

describe('VorgangPage — Abschliessen und Wieder oeffnen (Kriterien 20, 21)', () => {
  it('schaltet Kennzeichnung und Taste um und liest dabei neu', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = vorgangDoppel();

    renderSeite();

    await screen.findByRole('heading', { level: 1, name: KOPFZEILE });
    expect(screen.queryByText('Abgeschlossen')).not.toBeInTheDocument();

    await nutzer.click(screen.getByRole('button', { name: 'Abschließen' }));

    expect(await screen.findByText('Abgeschlossen')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Abschließen' })).not.toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/vorgaenge/5/abschliessen',
      expect.objectContaining({ method: 'POST' }),
    );

    await nutzer.click(screen.getByRole('button', { name: 'Wieder öffnen' }));

    expect(await screen.findByRole('button', { name: 'Abschließen' })).toBeInTheDocument();
    expect(screen.queryByText('Abgeschlossen')).not.toBeInTheDocument();
  });

  it('traegt am abgeschlossenen Vorgang die Kennzeichnung und die Taste „Wieder oeffnen"', async () => {
    vorgangDoppel({ ...VORGANG, abgeschlossen: true });

    renderSeite();

    expect(await screen.findByText('Abgeschlossen')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Wieder öffnen' })).toBeEnabled();
    // Phase, Zuordnung und Historie bleiben (Kriterium 21).
    expect(screen.getByText('Anbahnung')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Beispiel GmbH' })).toBeInTheDocument();
    expect(screen.getByRole('list', { name: 'Historie' })).toBeInTheDocument();
  });

  it('meldet, wenn das Schalten nicht durchgeht', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'GET /api/vorgaenge/5': json(200, VORGANG),
      'POST /api/vorgaenge/5/abschliessen': leer(500),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: KOPFZEILE });
    await nutzer.click(screen.getByRole('button', { name: 'Abschließen' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht geändert');
  });
});

describe('VorgangPage — die Maske fuer Eintraege (E20, Kriterien 13, 14)', () => {
  it('stellt die Maske als Karte ueber die Historie', async () => {
    vorgangDoppel();

    renderSeite();

    await screen.findByRole('heading', { level: 1, name: KOPFZEILE });
    expect(
      screen.getAllByRole('heading', { level: 2 }).map((ueberschrift) => ueberschrift.textContent),
    ).toEqual(['Eintrag hinzufügen', 'Historie', 'Felder']);
  });

  it('liest die Historie neu, nachdem ein Eintrag hinzugefuegt wurde', async () => {
    const nutzer = userEvent.setup();
    const NEUER: Eintrag = {
      id: 33,
      art: 'KOMMENTAR',
      text: 'Angebot zugesagt.',
      geschehenAm: '2026-09-22T10:00:00Z',
      herkunft: 'VON_HAND',
      dateiName: null,
      dateiGroesse: null,
      geaendertAm: null,
    };
    let historie: readonly Eintrag[] = [ANHANG, KOMMENTAR];
    fetchNachPfad({
      'GET /api/vorgaenge/5': () =>
        new Response(JSON.stringify({ ...VORGANG, historie }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      'POST /api/vorgaenge/5/eintraege': () => {
        historie = [NEUER, ANHANG, KOMMENTAR];
        return new Response(null, { status: 201 });
      },
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: KOPFZEILE });
    await nutzer.type(screen.getByRole('textbox', { name: /^Text/ }), 'Angebot zugesagt.');
    await nutzer.click(screen.getByRole('button', { name: 'Hinzufügen' }));

    expect(await screen.findByText('Angebot zugesagt.')).toBeInTheDocument();
    expect(within(screen.getByRole('list', { name: 'Historie' })).getAllByRole('listitem'))
      .toHaveLength(3);
  });

  it('liest die Historie neu und zeigt den Vermerk „geaendert" (Kriterium 19)', async () => {
    const nutzer = userEvent.setup();
    let historie: readonly Eintrag[] = [ANHANG, KOMMENTAR];
    fetchNachPfad({
      'GET /api/vorgaenge/5': () =>
        new Response(JSON.stringify({ ...VORGANG, historie }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      // Der Vermerk und die neue Einordnung kommen vom Server, nicht aus der Oberflaeche.
      'PUT /api/vorgaenge/5/eintraege/31': () => {
        historie = [
          ANHANG,
          { ...KOMMENTAR, text: 'Nachgetragen.', geaendertAm: '2026-09-23T12:00:00Z' },
        ];
        return new Response(null, { status: 204 });
      },
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: KOPFZEILE });
    const zeile = within(screen.getByRole('list', { name: 'Historie' })).getAllByRole(
      'listitem',
    )[1];
    await nutzer.click(within(zeile).getByRole('button', { name: /^Ändern: Kommentar/ }));
    const feld = within(zeile).getByRole('textbox', { name: /^Text/ });
    await nutzer.clear(feld);
    await nutzer.type(feld, 'Nachgetragen.');
    await nutzer.click(within(zeile).getByRole('button', { name: 'Speichern' }));

    expect(await screen.findByText('Nachgetragen.')).toBeInTheDocument();
    expect(screen.getByText(/^geändert/)).toBeInTheDocument();
  });
});

describe('VorgangPage — die Historie (Kriterium 15)', () => {
  it('gibt die Eintraege der Antwort in ihrer Reihenfolge an den Baustein weiter', async () => {
    vorgangDoppel();

    renderSeite();

    const historie = within(await screen.findByRole('list', { name: 'Historie' }));
    const zeilen = historie.getAllByRole('listitem');
    expect(zeilen).toHaveLength(2);
    expect(zeilen[0]).toHaveAccessibleName(/^Anhang/);
    expect(zeilen[1]).toHaveAccessibleName(/^Kommentar/);
    // Der Weg zur Datei traegt die Kennung des Vorgangs aus der Adresse.
    expect(historie.getByRole('link', { name: 'anfrage.pdf' })).toHaveAttribute(
      'href',
      '/api/vorgaenge/5/eintraege/32/datei',
    );
  });

  it('sagt es, wenn die Historie noch leer ist', async () => {
    vorgangDoppel({ ...VORGANG, historie: [] });

    renderSeite();

    expect(await screen.findByRole('status')).toHaveTextContent('Noch kein Eintrag in der Historie');
  });
});

describe('VorgangPage — der Pfad im Kopf (E6)', () => {
  it('meldet „Vorgänge" als Weg und Nummer samt Titel als Endstufe', async () => {
    fetchNachPfad({ 'GET /api/vorgaenge/5': json(200, VORGANG) });

    renderMitKopf();

    const pfad = within(await screen.findByRole('navigation', { name: 'Pfad' }));
    expect(pfad.getByRole('link', { name: 'Vorgänge' })).toHaveAttribute('href', '/vorgaenge');
    expect(
      await pfad.findByText('#941 Anteilsbalken je Vorgang statt Band über alle'),
    ).toHaveAttribute('aria-current', 'page');
  });

  it('nennt die Endstufe „Vorgang", solange der Vorgang noch nicht gelesen ist', async () => {
    fetchNachPfad({ 'GET /api/vorgaenge/5': leer(503) });

    renderMitKopf();

    const pfad = within(await screen.findByRole('navigation', { name: 'Pfad' }));
    expect(pfad.getByText('Vorgang')).toHaveAttribute('aria-current', 'page');
  });
});

describe('VorgangPage — unsinnige Kennung, unbekannter Vorgang, Ausfall', () => {
  it('faengt eine nicht numerische Kennung ab, bevor sie an die Schnittstelle geht', async () => {
    const fetchMock = fetchNachPfad({});

    renderSeite('/vorgaenge/keine-zahl');

    expect(await screen.findByRole('alert')).toHaveTextContent('gibt es nicht');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('meldet einen unbekannten Vorgang', async () => {
    fetchNachPfad({ 'GET /api/vorgaenge/5': leer(404) });

    renderSeite();

    expect(await screen.findByRole('alert')).toHaveTextContent('gibt es nicht');
  });

  it('meldet den Ausfall der Schnittstelle', async () => {
    fetchNachPfad({ 'GET /api/vorgaenge/5': leer(500) });

    renderSeite();

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht zu erreichen');
  });
});

describe('VorgangPage — Tastatur (Kriterium 26)', () => {
  it('fuehrt mit dem Tabulator ueber die Taste und die Wege der Zuordnung', async () => {
    const nutzer = userEvent.setup();
    vorgangDoppel();

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: KOPFZEILE });

    await nutzer.tab();
    expect(screen.getByRole('link', { name: 'Bearbeiten' })).toHaveFocus();
    await nutzer.tab();
    expect(screen.getByRole('button', { name: 'Abschließen' })).toHaveFocus();
    // Der Tabulator folgt der Lesefolge des Dokuments: erst das Blatt mit Kopf, Maske und
    // Historie, danach die Saeule mit den Feldern.
    await nutzer.tab();
    expect(screen.getByRole('combobox', { name: 'Art' })).toHaveFocus();
    await nutzer.tab();
    expect(screen.getByLabelText(/^Zeitpunkt/)).toHaveFocus();
    await nutzer.tab();
    expect(screen.getByRole('textbox', { name: /^Text/ })).toHaveFocus();
    await nutzer.tab();
    expect(screen.getByRole('button', { name: 'Hinzufügen' })).toHaveFocus();
    await nutzer.tab();
    expect(screen.getByRole('link', { name: 'anfrage.pdf' })).toHaveFocus();
    // In jeder Zeile der Historie steht danach ihre Taste „Aendern" (Kriterien 19, 26).
    await nutzer.tab();
    expect(screen.getByRole('button', { name: /^Ändern: Anhang/ })).toHaveFocus();
    await nutzer.tab();
    expect(screen.getByRole('button', { name: /^Ändern: Kommentar/ })).toHaveFocus();
    await nutzer.tab();
    expect(screen.getByRole('link', { name: 'Beispiel GmbH' })).toHaveFocus();
    await nutzer.tab();
    expect(screen.getByRole('link', { name: 'Anna Berg' })).toHaveFocus();
  });
});
