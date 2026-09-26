import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { Firma, FirmenUebersicht } from '../api/firmen';
import type { Vorgang } from '../api/vorgaenge';
import { fetchNachPfad, json, leer, problem } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';
import VorgangMaske from './VorgangMaske';

/** Der Leseweg der Auswahlliste: ohne Schalter liefert er nur aktive Firmen (E18). */
const FIRMEN_WEG = 'GET /api/firmen?suche=&auchStillgelegte=false';

const FIRMEN: FirmenUebersicht = {
  firmen: [
    { id: 7, name: 'Beispiel GmbH', ort: 'Bremen', aktiveAnsprechpartner: 2, aktiv: true },
    { id: 8, name: 'Zweite AG', ort: null, aktiveAnsprechpartner: 1, aktiv: true },
  ],
  gesamt: 2,
};

const OHNE_FIRMA: FirmenUebersicht = { firmen: [], gesamt: 0 };

/** Nur die Zweite AG ist aktiv — die Beispiel GmbH ist stillgelegt und fehlt darum hier. */
const NUR_ZWEITE: FirmenUebersicht = {
  firmen: [{ id: 8, name: 'Zweite AG', ort: null, aktiveAnsprechpartner: 1, aktiv: true }],
  gesamt: 2,
};

function partner(id: number, vorname: string | null, nachname: string, aktiv: boolean) {
  return {
    id,
    vorname,
    nachname,
    rolle: null,
    email: null,
    telefonFestnetz: null,
    telefonMobil: null,
    aktiv,
  };
}

const FIRMA_7: Firma = {
  id: 7,
  name: 'Beispiel GmbH',
  strasse: null,
  plz: null,
  ort: 'Bremen',
  land: 'Deutschland',
  steuernummer: null,
  umsatzsteuerId: null,
  aktiv: true,
  ansprechpartner: [
    partner(31, 'Anna', 'Berg', true),
    partner(32, null, 'Clausen', true),
    partner(33, 'Dora', 'Stillgelegt', false),
  ],
};

const FIRMA_8: Firma = { ...FIRMA_7, id: 8, name: 'Zweite AG', ansprechpartner: [] };

/** Der Vorgang, den die Maske beim Aendern vorbelegt (Kriterium 10). */
const VORGANG: Vorgang = {
  id: 5,
  nummer: 941,
  titel: 'Anteilsbalken je Vorgang',
  phase: 'ANBAHNUNG',
  abgeschlossen: false,
  firma: { id: 7, name: 'Beispiel GmbH', aktiv: true },
  ansprechpartner: { id: 31, name: 'Anna Berg', aktiv: true },
  historie: [],
};

/** Die Adresse, an der sich ablesen laesst, wohin die Maske gefuehrt hat. */
function Adresse() {
  const ort = useLocation();
  return <p data-testid="adresse">{ort.pathname}</p>;
}

function adresse() {
  return screen.getByTestId('adresse').textContent;
}

function renderMaske() {
  return renderMitTheme(
    <MemoryRouter initialEntries={['/vorgaenge/neu']}>
      <Routes>
        <Route path="/vorgaenge" element={<p>Übersicht</p>} />
        <Route path="/vorgaenge/neu" element={<VorgangMaske />} />
        <Route path="/vorgaenge/:id" element={<p>Detailansicht</p>} />
        <Route path="/firmen/neu" element={<p>Neue Firma</p>} />
      </Routes>
      <Adresse />
    </MemoryRouter>,
  );
}

/** Dieselbe Komponente unter der Adresse zum Aendern — der Unterschied ist die Kennung. */
function renderMaskeZumAendern(id = '5') {
  return renderMitTheme(
    <MemoryRouter initialEntries={[`/vorgaenge/${id}/bearbeiten`]}>
      <Routes>
        <Route path="/vorgaenge" element={<p>Übersicht</p>} />
        <Route path="/vorgaenge/:id" element={<p>Detailansicht</p>} />
        <Route path="/vorgaenge/:id/bearbeiten" element={<VorgangMaske />} />
      </Routes>
      <Adresse />
    </MemoryRouter>,
  );
}

function titelFeld() {
  return screen.getByRole('textbox', { name: 'Titel' });
}

function wahl(name: string) {
  return screen.getByRole('combobox', { name });
}

/** Die Aufschriften einer Auswahlliste, ohne den Eintrag fuer „nichts gewaehlt". */
function angebot(name: string) {
  return within(wahl(name))
    .getAllByRole('option')
    .map((eintrag) => eintrag.textContent)
    .filter((aufschrift) => aufschrift !== null && !aufschrift.startsWith('—'));
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('VorgangMaske — Anlegen', () => {
  it('legt den Vorgang mit Titel, Firma und Ansprechpartner an und fuehrt auf die Detailansicht', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      [FIRMEN_WEG]: json(200, FIRMEN),
      'GET /api/firmen/7': json(200, FIRMA_7),
      'POST /api/vorgaenge': json(201, { id: 12, nummer: 3 }),
    });

    renderMaske();

    expect(await screen.findByRole('heading', { name: 'Neuer Vorgang' })).toBeInTheDocument();
    // Die Firmen kommen ohne Schalter — damit nur die aktiven (E18).
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen?suche=&auchStillgelegte=false',
      expect.objectContaining({ method: 'GET' }),
    );
    expect(angebot('Firma')).toEqual(['Beispiel GmbH', 'Zweite AG']);

    await nutzer.type(titelFeld(), 'Anteilsbalken je Vorgang');
    await nutzer.selectOptions(wahl('Firma'), '7');
    expect(await screen.findByRole('option', { name: 'Anna Berg' })).toBeInTheDocument();
    await nutzer.selectOptions(wahl('Ansprechpartner'), '31');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/vorgaenge',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          titel: 'Anteilsbalken je Vorgang',
          firmaId: 7,
          ansprechpartnerId: 31,
        }),
      }),
    );
    expect(await screen.findByText('Detailansicht')).toBeInTheDocument();
    // Kriterium 5: der Weg fuehrt auf die Detailansicht des neuen Vorgangs.
    expect(adresse()).toBe('/vorgaenge/12');
  });

  it('stellt nur die aktiven Ansprechpartner der gewaehlten Firma zur Wahl', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      [FIRMEN_WEG]: json(200, FIRMEN),
      'GET /api/firmen/7': json(200, FIRMA_7),
      'POST /api/vorgaenge': json(201, { id: 12, nummer: 3 }),
    });

    renderMaske();
    await screen.findByRole('heading', { name: 'Neuer Vorgang' });
    await nutzer.type(titelFeld(), 'Anteilsbalken je Vorgang');
    await nutzer.selectOptions(wahl('Firma'), '7');

    expect(await screen.findByRole('option', { name: 'Anna Berg' })).toBeInTheDocument();
    // Ohne Vornamen nur der Nachname — und die stillgelegte Dora Stillgelegt fehlt (Kriterium 6).
    expect(angebot('Ansprechpartner')).toEqual(['Anna Berg', 'Clausen']);

    // Der Ansprechpartner ist optional (Kriterium 5): ohne Wahl geht `null` hinaus.
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/vorgaenge',
      expect.objectContaining({
        body: JSON.stringify({
          titel: 'Anteilsbalken je Vorgang',
          firmaId: 7,
          ansprechpartnerId: null,
        }),
      }),
    );
  });

  it('leert den gewaehlten Ansprechpartner beim Wechsel der Firma', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [FIRMEN_WEG]: json(200, FIRMEN),
      'GET /api/firmen/7': json(200, FIRMA_7),
      'GET /api/firmen/8': json(200, FIRMA_8),
    });

    renderMaske();
    await screen.findByRole('heading', { name: 'Neuer Vorgang' });
    await nutzer.selectOptions(wahl('Firma'), '7');
    await screen.findByRole('option', { name: 'Anna Berg' });
    await nutzer.selectOptions(wahl('Ansprechpartner'), '31');
    expect(wahl('Ansprechpartner')).toHaveValue('31');

    await nutzer.selectOptions(wahl('Firma'), '8');

    // Kriterium 6: der zuvor gewaehlte Ansprechpartner ist wieder leer.
    expect(wahl('Ansprechpartner')).toHaveValue('');
    // Die Zweite AG hat keinen aktiven Ansprechpartner — also steht keiner zur Wahl.
    expect(angebot('Ansprechpartner')).toEqual([]);
  });

  it('verwirft die spaete Antwort zur zuvor gewaehlten Firma', async () => {
    const nutzer = userEvent.setup();
    let loesen: (() => void) | undefined;
    fetchNachPfad({
      [FIRMEN_WEG]: json(200, FIRMEN),
      'GET /api/firmen/7': () =>
        new Promise<Response>((fertig) => {
          loesen = () => {
            fertig(json(200, FIRMA_7)());
          };
        }),
      'GET /api/firmen/8': json(200, { ...FIRMA_8, ansprechpartner: [partner(41, 'Bo', 'Bahr', true)] }),
    });

    renderMaske();
    await screen.findByRole('heading', { name: 'Neuer Vorgang' });
    await nutzer.selectOptions(wahl('Firma'), '7');
    await nutzer.selectOptions(wahl('Firma'), '8');
    expect(await screen.findByRole('option', { name: 'Bo Bahr' })).toBeInTheDocument();

    // Die Antwort zu Firma 7 trifft erst jetzt ein. Sie gehoert zu einer Wahl, die es nicht
    // mehr gibt, und darf die Liste der Firma 8 nicht ueberschreiben.
    await act(async () => {
      loesen?.();
      await Promise.resolve();
    });

    expect(angebot('Ansprechpartner')).toEqual(['Bo Bahr']);
  });

  it('meldet fehlenden Titel und fehlende Firma am Feld und ruft die Schnittstelle nicht auf', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ [FIRMEN_WEG]: json(200, FIRMEN) });

    renderMaske();
    await screen.findByRole('heading', { name: 'Neuer Vorgang' });
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(titelFeld()).toHaveAccessibleDescription('Bitte einen Titel angeben.');
    expect(wahl('Firma')).toHaveAccessibleDescription('Bitte eine Firma wählen.');
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('haengt die Meldungen des Servers an ihre Felder', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [FIRMEN_WEG]: json(200, FIRMEN),
      'GET /api/firmen/7': json(200, FIRMA_7),
      'POST /api/vorgaenge': problem(400, 'Ungültig', {
        titel: ['Dieser Titel ist zu lang.'],
        firmaId: ['Diese Firma ist stillgelegt.'],
        ansprechpartnerId: ['Dieser Ansprechpartner gehört nicht zu dieser Firma.'],
      }),
    });

    renderMaske();
    await screen.findByRole('heading', { name: 'Neuer Vorgang' });
    await nutzer.type(titelFeld(), 'Anteilsbalken');
    await nutzer.selectOptions(wahl('Firma'), '7');
    await screen.findByRole('option', { name: 'Anna Berg' });
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(await screen.findByText('Dieser Titel ist zu lang.')).toBeInTheDocument();
    expect(titelFeld()).toHaveAccessibleDescription('Dieser Titel ist zu lang.');
    expect(wahl('Firma')).toHaveAccessibleDescription('Diese Firma ist stillgelegt.');
    expect(wahl('Ansprechpartner')).toHaveAccessibleDescription(
      'Dieser Ansprechpartner gehört nicht zu dieser Firma.',
    );
    expect(adresse()).toBe('/vorgaenge/neu');
  });

  it('zeigt beim Ausfall des Anlegens eine Meldung und bleibt in der Maske', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [FIRMEN_WEG]: json(200, FIRMEN),
      'GET /api/firmen/7': json(200, FIRMA_7),
      'POST /api/vorgaenge': leer(500),
    });

    renderMaske();
    await screen.findByRole('heading', { name: 'Neuer Vorgang' });
    await nutzer.type(titelFeld(), 'Anteilsbalken');
    await nutzer.selectOptions(wahl('Firma'), '7');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht gespeichert');
    expect(adresse()).toBe('/vorgaenge/neu');
  });

  it('sendet bei doppeltem Absenden nur einen Aufruf', async () => {
    // Ohne `pointerEventsCheck` verweigert user-event den zweiten Klick schon deshalb, weil die
    // gesperrte Taste `pointer-events: none` traegt — gemessen werden soll aber, dass auch ein
    // durchgereichter zweiter Klick keinen zweiten Aufruf ausloest.
    const nutzer = userEvent.setup({ pointerEventsCheck: 0 });
    const fetchMock = fetchNachPfad({
      [FIRMEN_WEG]: json(200, FIRMEN),
      'GET /api/firmen/7': json(200, FIRMA_7),
      // Der Aufruf antwortet nie — genau die Lage, in der ein offener Knopf ein zweites Mal traefe.
      'POST /api/vorgaenge': () => new Promise<Response>(() => {}),
    });

    renderMaske();
    await screen.findByRole('heading', { name: 'Neuer Vorgang' });
    await nutzer.type(titelFeld(), 'Anteilsbalken');
    await nutzer.selectOptions(wahl('Firma'), '7');
    await screen.findByRole('option', { name: 'Anna Berg' });
    const knopf = screen.getByRole('button', { name: 'Anlegen' });
    await nutzer.click(knopf);
    await nutzer.click(knopf);

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(knopf).toBeDisabled();
  });

  it('weist ohne aktive Firma den Weg zum Anlegen einer Firma und laesst nicht speichern', async () => {
    const nutzer = userEvent.setup({ pointerEventsCheck: 0 });
    const fetchMock = fetchNachPfad({ [FIRMEN_WEG]: json(200, OHNE_FIRMA) });

    renderMaske();
    await screen.findByRole('heading', { name: 'Neuer Vorgang' });

    // Kriterium 7: Hinweis an der Auswahl, Weg zur Firma, Speichern gesperrt.
    expect(wahl('Firma')).toHaveAccessibleDescription(/Es ist keine aktive Firma vorhanden/);
    const knopf = screen.getByRole('button', { name: 'Anlegen' });
    expect(knopf).toBeDisabled();
    await nutzer.type(titelFeld(), 'Anteilsbalken');
    await nutzer.click(knopf);
    expect(fetchMock).toHaveBeenCalledTimes(1);

    await nutzer.click(screen.getByRole('link', { name: 'Firma anlegen' }));
    expect(screen.getByText('Neue Firma')).toBeInTheDocument();
    expect(adresse()).toBe('/firmen/neu');
  });

  it('meldet den Ausfall beim Laden der Firmen statt einer leeren Wahl', async () => {
    fetchNachPfad({ [FIRMEN_WEG]: leer(500) });

    renderMaske();

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht zu erreichen');
    expect(screen.queryByRole('textbox', { name: 'Titel' })).not.toBeInTheDocument();
  });

  it('meldet den Ausfall beim Laden der Ansprechpartner am Feld', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [FIRMEN_WEG]: json(200, FIRMEN),
      'GET /api/firmen/7': leer(500),
    });

    renderMaske();
    await screen.findByRole('heading', { name: 'Neuer Vorgang' });
    await nutzer.selectOptions(wahl('Firma'), '7');

    expect(
      await screen.findByText(/Die Ansprechpartner sind gerade nicht zu erreichen/),
    ).toBeInTheDocument();
    expect(wahl('Ansprechpartner')).toHaveAccessibleDescription(
      /Die Ansprechpartner sind gerade nicht zu erreichen/,
    );
  });

  it('zeigt bis zur Antwort einen Ladehinweis', () => {
    fetchNachPfad({ [FIRMEN_WEG]: json(200, FIRMEN) });

    renderMaske();

    expect(screen.getByText('Die Firmen werden geladen …')).toBeInTheDocument();
  });

  it('legt beim Verlassen ohne Speichern nichts an', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      [FIRMEN_WEG]: json(200, FIRMEN),
      'GET /api/firmen/7': json(200, FIRMA_7),
    });

    renderMaske();
    await screen.findByRole('heading', { name: 'Neuer Vorgang' });
    await nutzer.type(titelFeld(), 'Anteilsbalken');
    await nutzer.selectOptions(wahl('Firma'), '7');
    await screen.findByRole('option', { name: 'Anna Berg' });
    await nutzer.click(screen.getByRole('link', { name: 'Abbrechen' }));

    expect(screen.getByText('Übersicht')).toBeInTheDocument();
    expect(adresse()).toBe('/vorgaenge');
    expect(fetchMock).not.toHaveBeenCalledWith('/api/vorgaenge', expect.anything());
  });
});

describe('VorgangMaske — Ändern (Kriterium 10)', () => {
  it('belegt Titel, Firma und Ansprechpartner vor und schreibt sie fort', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/vorgaenge/5': json(200, VORGANG),
      [FIRMEN_WEG]: json(200, FIRMEN),
      'GET /api/firmen/7': json(200, FIRMA_7),
      'PUT /api/vorgaenge/5': leer(204),
    });

    renderMaskeZumAendern();

    expect(await screen.findByRole('heading', { name: 'Vorgang bearbeiten' })).toBeInTheDocument();
    expect(titelFeld()).toHaveValue('Anteilsbalken je Vorgang');
    expect(wahl('Firma')).toHaveValue('7');
    // Der Ansprechpartner steht erst da, wenn die Liste seiner Firma eingetroffen ist.
    expect(await screen.findByRole('option', { name: 'Clausen' })).toBeInTheDocument();
    expect(wahl('Ansprechpartner')).toHaveValue('31');

    await nutzer.clear(titelFeld());
    await nutzer.type(titelFeld(), 'Anteilsbalken je Vorgang statt Band');
    await nutzer.selectOptions(wahl('Ansprechpartner'), '32');
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/vorgaenge/5',
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({
          titel: 'Anteilsbalken je Vorgang statt Band',
          firmaId: 7,
          ansprechpartnerId: 32,
        }),
      }),
    );
    expect(await screen.findByText('Detailansicht')).toBeInTheDocument();
    expect(adresse()).toBe('/vorgaenge/5');
  });

  it('laesst den Ansprechpartner auch ohne Zuordnung am Vorgang leer', async () => {
    fetchNachPfad({
      'GET /api/vorgaenge/5': json(200, { ...VORGANG, ansprechpartner: null }),
      [FIRMEN_WEG]: json(200, FIRMEN),
      'GET /api/firmen/7': json(200, FIRMA_7),
    });

    renderMaskeZumAendern();

    await screen.findByRole('heading', { name: 'Vorgang bearbeiten' });
    expect(await screen.findByRole('option', { name: 'Anna Berg' })).toBeInTheDocument();
    expect(wahl('Ansprechpartner')).toHaveValue('');
  });

  it('aendert beim Verlassen ohne Speichern nichts und fuehrt auf die Detailansicht', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/vorgaenge/5': json(200, VORGANG),
      [FIRMEN_WEG]: json(200, FIRMEN),
      'GET /api/firmen/7': json(200, FIRMA_7),
    });

    renderMaskeZumAendern();
    await screen.findByRole('heading', { name: 'Vorgang bearbeiten' });
    await nutzer.type(titelFeld(), ' — verworfen');
    await nutzer.click(screen.getByRole('link', { name: 'Abbrechen' }));

    // Kriterium 10: ohne Speichern bleibt der alte Stand — es geht nichts hinaus.
    expect(screen.getByText('Detailansicht')).toBeInTheDocument();
    expect(adresse()).toBe('/vorgaenge/5');
    expect(fetchMock).not.toHaveBeenCalledWith(
      '/api/vorgaenge/5',
      expect.objectContaining({ method: 'PUT' }),
    );
  });

  it('haengt die Meldungen des Servers an ihre Felder und bleibt in der Maske', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'GET /api/vorgaenge/5': json(200, VORGANG),
      [FIRMEN_WEG]: json(200, FIRMEN),
      'GET /api/firmen/7': json(200, FIRMA_7),
      'PUT /api/vorgaenge/5': problem(400, 'Ungültig', {
        titel: ['Dieser Titel ist zu lang.'],
        ansprechpartnerId: ['Dieser Ansprechpartner gehört nicht zu dieser Firma.'],
      }),
    });

    renderMaskeZumAendern();
    await screen.findByRole('heading', { name: 'Vorgang bearbeiten' });
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(await screen.findByText('Dieser Titel ist zu lang.')).toBeInTheDocument();
    expect(titelFeld()).toHaveAccessibleDescription('Dieser Titel ist zu lang.');
    expect(wahl('Ansprechpartner')).toHaveAccessibleDescription(
      'Dieser Ansprechpartner gehört nicht zu dieser Firma.',
    );
    expect(adresse()).toBe('/vorgaenge/5/bearbeiten');
  });

  it('zeigt bis zur Antwort einen Ladehinweis auf den Vorgang', () => {
    fetchNachPfad({ 'GET /api/vorgaenge/5': json(200, VORGANG) });

    renderMaskeZumAendern();

    expect(screen.getByText('Der Vorgang wird geladen …')).toBeInTheDocument();
  });

  it('faengt eine nicht numerische Kennung ab, bevor sie an die Schnittstelle geht', async () => {
    const fetchMock = fetchNachPfad({});

    renderMaskeZumAendern('keine-zahl');

    expect(await screen.findByRole('alert')).toHaveTextContent('gibt es nicht');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('meldet einen unbekannten Vorgang', async () => {
    fetchNachPfad({ 'GET /api/vorgaenge/5': leer(404) });

    renderMaskeZumAendern();

    expect(await screen.findByRole('alert')).toHaveTextContent('gibt es nicht');
    expect(screen.queryByRole('textbox', { name: 'Titel' })).not.toBeInTheDocument();
  });

  it('meldet den Ausfall beim Lesen des Vorgangs', async () => {
    fetchNachPfad({ 'GET /api/vorgaenge/5': leer(500) });

    renderMaskeZumAendern();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Der Vorgang ist gerade nicht zu erreichen',
    );
  });
});

describe('VorgangMaske — die stillgelegte Zuordnung (Kriterien 23, 26)', () => {
  /** Ein Vorgang, dessen Firma inzwischen stillgelegt ist. */
  const MIT_STILLER_FIRMA: Vorgang = {
    ...VORGANG,
    firma: { id: 7, name: 'Beispiel GmbH', aktiv: false },
  };

  /** Ein Vorgang, dessen Ansprechpartner inzwischen stillgelegt ist. */
  const MIT_STILLEM_PARTNER: Vorgang = {
    ...VORGANG,
    ansprechpartner: { id: 33, name: 'Dora Stillgelegt', aktiv: false },
  };

  it('stellt die stillgelegte Firma gekennzeichnet zur Wahl und speichert sie unveraendert', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/vorgaenge/5': json(200, MIT_STILLER_FIRMA),
      // Der Leseweg liefert nur aktive Firmen — die stillgelegte Beispiel GmbH ist nicht dabei.
      [FIRMEN_WEG]: json(200, NUR_ZWEITE),
      'GET /api/firmen/7': json(200, FIRMA_7),
      'PUT /api/vorgaenge/5': leer(204),
    });

    renderMaskeZumAendern();
    await screen.findByRole('heading', { name: 'Vorgang bearbeiten' });

    // Der Stand steht als Wort in der Aufschrift — ein `option` traegt nichts anderes, und so
    // liest der Screenreader ihn mit dem Namen (Kriterium 26).
    expect(angebot('Firma')).toEqual(['Beispiel GmbH (stillgelegt)', 'Zweite AG']);
    expect(wahl('Firma')).toHaveValue('7');

    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/vorgaenge/5',
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({
          titel: 'Anteilsbalken je Vorgang',
          firmaId: 7,
          ansprechpartnerId: 31,
        }),
      }),
    );
    expect(adresse()).toBe('/vorgaenge/5');
  });

  it('nimmt die stillgelegte Firma nach dem Wechsel aus der Wahl', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'GET /api/vorgaenge/5': json(200, MIT_STILLER_FIRMA),
      [FIRMEN_WEG]: json(200, NUR_ZWEITE),
      'GET /api/firmen/7': json(200, FIRMA_7),
      'GET /api/firmen/8': json(200, FIRMA_8),
    });

    renderMaskeZumAendern();
    await screen.findByRole('heading', { name: 'Vorgang bearbeiten' });
    await nutzer.selectOptions(wahl('Firma'), '8');

    // Kriterium 23: Wer etwas anderes waehlt, findet die stillgelegte Zuordnung nicht wieder.
    expect(angebot('Firma')).toEqual(['Zweite AG']);
    expect(wahl('Ansprechpartner')).toHaveValue('');
  });

  it('stellt den stillgelegten Ansprechpartner gekennzeichnet zur Wahl und speichert ihn unveraendert', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/vorgaenge/5': json(200, MIT_STILLEM_PARTNER),
      [FIRMEN_WEG]: json(200, FIRMEN),
      'GET /api/firmen/7': json(200, FIRMA_7),
      'PUT /api/vorgaenge/5': leer(204),
    });

    renderMaskeZumAendern();
    await screen.findByRole('heading', { name: 'Vorgang bearbeiten' });
    expect(await screen.findByRole('option', { name: 'Anna Berg' })).toBeInTheDocument();

    expect(angebot('Ansprechpartner')).toEqual([
      'Dora Stillgelegt (stillgelegt)',
      'Anna Berg',
      'Clausen',
    ]);
    expect(wahl('Ansprechpartner')).toHaveValue('33');

    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/vorgaenge/5',
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({
          titel: 'Anteilsbalken je Vorgang',
          firmaId: 7,
          ansprechpartnerId: 33,
        }),
      }),
    );
  });

  it('nimmt den stillgelegten Ansprechpartner nach dem Wechsel aus der Wahl', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'GET /api/vorgaenge/5': json(200, MIT_STILLEM_PARTNER),
      [FIRMEN_WEG]: json(200, FIRMEN),
      'GET /api/firmen/7': json(200, FIRMA_7),
    });

    renderMaskeZumAendern();
    await screen.findByRole('heading', { name: 'Vorgang bearbeiten' });
    await screen.findByRole('option', { name: 'Anna Berg' });
    await nutzer.selectOptions(wahl('Ansprechpartner'), '31');

    expect(angebot('Ansprechpartner')).toEqual(['Anna Berg', 'Clausen']);
  });

  it('laesst ohne jede aktive Firma den Vorgang mit seiner stillgelegten Firma speichern', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/vorgaenge/5': json(200, MIT_STILLER_FIRMA),
      [FIRMEN_WEG]: json(200, OHNE_FIRMA),
      'GET /api/firmen/7': json(200, FIRMA_7),
      'PUT /api/vorgaenge/5': leer(204),
    });

    renderMaskeZumAendern();
    await screen.findByRole('heading', { name: 'Vorgang bearbeiten' });

    // Kriterium 23 geht hier vor Kriterium 7: Es gibt etwas zu waehlen — die eigene Zuordnung.
    expect(angebot('Firma')).toEqual(['Beispiel GmbH (stillgelegt)']);
    expect(wahl('Firma')).not.toHaveAccessibleDescription(/Es ist keine aktive Firma vorhanden/);
    const knopf = screen.getByRole('button', { name: 'Speichern' });
    expect(knopf).toBeEnabled();

    await nutzer.click(knopf);

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/vorgaenge/5',
      expect.objectContaining({ method: 'PUT' }),
    );
  });
});

describe('VorgangMaske — Tastatur und Benennung', () => {
  it('fuehrt mit dem Tabulator ueber alle Felder, die Taste und den Weg zurueck', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({ [FIRMEN_WEG]: json(200, FIRMEN) });

    renderMaske();
    await screen.findByRole('heading', { name: 'Neuer Vorgang' });

    const reihe = [
      titelFeld(),
      wahl('Firma'),
      wahl('Ansprechpartner'),
      screen.getByRole('button', { name: 'Anlegen' }),
      screen.getByRole('link', { name: 'Abbrechen' }),
    ];
    for (const element of reihe) {
      await nutzer.tab();
      expect(element).toHaveFocus();
    }
  });
});
