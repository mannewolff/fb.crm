import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { Firma } from '../api/firmen';
import { fetchNachPfad, json, leer, problem } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';
import FirmaMaske from './FirmaMaske';

const FIRMA: Firma = {
  id: 7,
  name: 'Beispiel GmbH',
  strasse: 'Hauptstraße 1',
  plz: '28195',
  ort: 'Bremen',
  land: 'Österreich',
  steuernummer: '75/123/45678',
  umsatzsteuerId: 'DE123456789',
  aktiv: true,
  ansprechpartner: [],
};

/** Die Adresse, an der sich ablesen laesst, wohin die Maske gefuehrt hat. */
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
      <Routes>
        <Route path="/firmen" element={<p>Übersicht</p>} />
        <Route path="/firmen/neu" element={<FirmaMaske />} />
        <Route path="/firmen/:id" element={<p>Detailansicht</p>} />
        <Route path="/firmen/:id/bearbeiten" element={<FirmaMaske />} />
      </Routes>
      <Adresse />
    </MemoryRouter>,
  );
}

function feld(name: string | RegExp) {
  return screen.getByRole('textbox', { name });
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('FirmaMaske — Anlegen', () => {
  it('legt Land mit „Deutschland" vor und schickt nur die ausgefuellten Angaben', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ 'POST /api/firmen': json(200, FIRMA) });

    renderMaske('/firmen/neu');

    expect(screen.getByRole('heading', { name: 'Neue Firma' })).toBeInTheDocument();
    expect(feld('Land')).toHaveValue('Deutschland');

    await nutzer.type(feld('Name'), 'Neue GmbH');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          name: 'Neue GmbH',
          strasse: null,
          plz: null,
          ort: null,
          land: 'Deutschland',
          steuernummer: null,
          umsatzsteuerId: null,
        }),
      }),
    );
    expect(await screen.findByText('Detailansicht')).toBeInTheDocument();
    // Kriterium 7: der Weg fuehrt auf die Detailansicht der neuen Firma.
    expect(adresse()).toBe('/firmen/7');
  });

  it('meldet den fehlenden Namen am Feld und ruft die Schnittstelle nicht auf', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({});

    renderMaske('/firmen/neu');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(feld('Name')).toHaveAccessibleDescription('Bitte einen Namen angeben.');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('haengt die Meldung des Servers an ihr Feld', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'POST /api/firmen': problem(400, 'Ungültig', { name: ['Dieser Name ist schon vergeben.'] }),
    });

    renderMaske('/firmen/neu');
    await nutzer.type(feld('Name'), 'Beispiel GmbH');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(await screen.findByText('Dieser Name ist schon vergeben.')).toBeInTheDocument();
    expect(feld('Name')).toHaveAccessibleDescription('Dieser Name ist schon vergeben.');
    expect(adresse()).toBe('/firmen/neu');
  });

  it('zeigt beim Ausfall eine Meldung und bleibt in der Maske', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({ 'POST /api/firmen': leer(500) });

    renderMaske('/firmen/neu');
    await nutzer.type(feld('Name'), 'Neue GmbH');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht gespeichert');
    expect(adresse()).toBe('/firmen/neu');
  });

  it('sendet bei doppeltem Absenden nur einen Aufruf', async () => {
    // Ohne `pointerEventsCheck` verweigert user-event den zweiten Klick schon deshalb, weil die
    // gesperrte Taste `pointer-events: none` traegt — gemessen werden soll aber, dass auch ein
    // durchgereichter zweiter Klick keinen zweiten Aufruf ausloest.
    const nutzer = userEvent.setup({ pointerEventsCheck: 0 });
    // Der Aufruf antwortet nie — genau die Lage, in der ein offener Knopf ein zweites Mal traefe.
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(() => new Promise(() => {}));

    renderMaske('/firmen/neu');
    await nutzer.type(feld('Name'), 'Neue GmbH');
    const knopf = screen.getByRole('button', { name: 'Anlegen' });
    await nutzer.click(knopf);
    await nutzer.click(knopf);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(knopf).toBeDisabled();
  });

  it('fuehrt „Abbrechen" zurueck auf die Uebersicht', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({});

    renderMaske('/firmen/neu');
    await nutzer.click(screen.getByRole('link', { name: 'Abbrechen' }));

    expect(adresse()).toBe('/firmen');
  });
});

describe('FirmaMaske — Ändern', () => {
  it('fuellt die gespeicherten Werte und kehrt nach dem Speichern zurueck', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/firmen/7': json(200, FIRMA),
      'PUT /api/firmen/7': leer(204),
    });

    renderMaske('/firmen/7/bearbeiten');

    expect(await screen.findByRole('heading', { name: 'Firma bearbeiten' })).toBeInTheDocument();
    expect(feld('Name')).toHaveValue('Beispiel GmbH');
    expect(feld(/^Straße/)).toHaveValue('Hauptstraße 1');
    expect(feld('Postleitzahl')).toHaveValue('28195');
    expect(feld('Ort')).toHaveValue('Bremen');
    // Nicht vorbelegt, sondern uebernommen: beim Aendern gilt der gespeicherte Wert.
    expect(feld('Land')).toHaveValue('Österreich');
    expect(feld('Steuernummer')).toHaveValue('75/123/45678');
    expect(feld(/^Umsatzsteuer/)).toHaveValue('DE123456789');

    await nutzer.clear(feld('Ort'));
    await nutzer.type(feld('Ort'), 'Oldenburg');
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen/7',
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({
          name: 'Beispiel GmbH',
          strasse: 'Hauptstraße 1',
          plz: '28195',
          ort: 'Oldenburg',
          land: 'Österreich',
          steuernummer: '75/123/45678',
          umsatzsteuerId: 'DE123456789',
        }),
      }),
    );
    expect(await screen.findByText('Detailansicht')).toBeInTheDocument();
    expect(adresse()).toBe('/firmen/7');
  });

  it('laesst leere Angaben als „keine Angabe" stehen statt als Leerstring', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/firmen/7': json(200, { ...FIRMA, strasse: null }),
      'PUT /api/firmen/7': leer(204),
    });

    renderMaske('/firmen/7/bearbeiten');
    await screen.findByRole('heading', { name: 'Firma bearbeiten' });
    // Eine fehlende Angabe wird zum leeren Feld, nicht zu einem Platzhalter im Eingabefeld.
    expect(feld(/^Straße/)).toHaveValue('');
    await nutzer.clear(feld('Steuernummer'));
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen/7',
      expect.objectContaining({
        body: JSON.stringify({
          name: 'Beispiel GmbH',
          strasse: null,
          plz: '28195',
          ort: 'Bremen',
          land: 'Österreich',
          steuernummer: null,
          umsatzsteuerId: 'DE123456789',
        }),
      }),
    );
  });

  it('fuehrt „Abbrechen" zurueck auf die Detailansicht', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({ 'GET /api/firmen/7': json(200, FIRMA) });

    renderMaske('/firmen/7/bearbeiten');
    await screen.findByRole('heading', { name: 'Firma bearbeiten' });
    await nutzer.click(screen.getByRole('link', { name: 'Abbrechen' }));

    expect(adresse()).toBe('/firmen/7');
  });

  it('faengt eine unsinnige Kennung ab, bevor sie an die Schnittstelle geht', async () => {
    const fetchMock = fetchNachPfad({});

    renderMaske('/firmen/keine-zahl/bearbeiten');

    expect(await screen.findByRole('alert')).toHaveTextContent('gibt es nicht');
    expect(fetchMock).not.toHaveBeenCalled();
    expect(screen.queryByRole('textbox', { name: 'Name' })).not.toBeInTheDocument();
  });

  it('meldet eine unbekannte Firma statt eines leeren Formulars', async () => {
    fetchNachPfad({ 'GET /api/firmen/7': leer(404) });

    renderMaske('/firmen/7/bearbeiten');

    expect(await screen.findByRole('alert')).toHaveTextContent('gibt es nicht');
  });

  it('meldet den Ausfall beim Lesen', async () => {
    fetchNachPfad({ 'GET /api/firmen/7': leer(500) });

    renderMaske('/firmen/7/bearbeiten');

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht zu erreichen');
  });
});

describe('FirmaMaske — Tastatur und Benennung', () => {
  it('fuehrt mit dem Tabulator ueber alle Felder, die Taste und den Weg zurueck', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({});

    renderMaske('/firmen/neu');

    const reihe = [
      feld('Name'),
      feld(/^Straße/),
      feld('Postleitzahl'),
      feld('Ort'),
      feld('Land'),
      feld('Steuernummer'),
      feld(/^Umsatzsteuer/),
      screen.getByRole('button', { name: 'Anlegen' }),
      screen.getByRole('link', { name: 'Abbrechen' }),
    ];
    for (const element of reihe) {
      await nutzer.tab();
      expect(element).toHaveFocus();
    }
  });
});
