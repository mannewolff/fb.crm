import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { Ansprechpartner, Firma } from '../api/firmen';
import { fetchNachPfad, json, leer, problem } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';
import AnsprechpartnerMaske from './AnsprechpartnerMaske';

const ANNA: Ansprechpartner = {
  id: 11,
  vorname: 'Anna',
  nachname: 'Berg',
  rolle: 'Einkauf',
  email: 'anna.berg@beispiel.de',
  telefonFestnetz: '+49 421 123456',
  telefonMobil: null,
  aktiv: true,
};

const FIRMA: Firma = {
  id: 7,
  name: 'Beispiel GmbH',
  strasse: 'Hauptstraße 1',
  plz: '28195',
  ort: 'Bremen',
  land: 'Deutschland',
  steuernummer: null,
  umsatzsteuerId: null,
  aktiv: true,
  ansprechpartner: [ANNA],
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
        <Route path="/firmen/:id" element={<p>Detailansicht</p>} />
        <Route path="/firmen/:id/ansprechpartner/neu" element={<AnsprechpartnerMaske />} />
        <Route
          path="/firmen/:id/ansprechpartner/:ansprechpartnerId/bearbeiten"
          element={<AnsprechpartnerMaske />}
        />
      </Routes>
      <Adresse />
    </MemoryRouter>,
  );
}

function feld(name: string | RegExp) {
  return screen.getByRole('textbox', { name });
}

/**
 * Der erwartete Rumpf als Text.
 *
 * Bewusst die ganze Zeichenkette und nicht einzelne Felder: `JSON.stringify` haelt die Reihenfolge
 * der Schluessel, und damit belegt ein Vergleich auf Gleichheit zugleich, dass **kein weiterer**
 * Schluessel im Rumpf steht — insbesondere keiner fuer die Firma (Kriterium 12).
 */
function rumpf(werte: Partial<Record<string, string | null>>): string {
  return JSON.stringify({
    vorname: null,
    nachname: 'Berg',
    rolle: null,
    email: null,
    telefonFestnetz: null,
    telefonMobil: null,
    ...werte,
  });
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('AnsprechpartnerMaske — Anlegen', () => {
  it('nennt die Firma im Kopf und schickt nur die ausgefuellten Angaben', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/firmen/7': json(200, FIRMA),
      'POST /api/firmen/7/ansprechpartner': json(200, ANNA),
    });

    renderMaske('/firmen/7/ansprechpartner/neu');

    expect(
      await screen.findByRole('heading', { name: 'Neuer Ansprechpartner' }),
    ).toBeInTheDocument();
    expect(screen.getByText('Beispiel GmbH')).toBeInTheDocument();

    await nutzer.type(feld('Vorname'), 'Anna');
    await nutzer.type(feld('Nachname'), 'Berg');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen/7/ansprechpartner',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          vorname: 'Anna',
          nachname: 'Berg',
          rolle: null,
          email: null,
          telefonFestnetz: null,
          telefonMobil: null,
        }),
      }),
    );
    expect(await screen.findByText('Detailansicht')).toBeInTheDocument();
    expect(adresse()).toBe('/firmen/7');
  });

  it('traegt kein Feld fuer die Firma — weder in der Maske noch im Rumpf (Kriterium 12)', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/firmen/7': json(200, FIRMA),
      'POST /api/firmen/7/ansprechpartner': json(200, ANNA),
    });

    renderMaske('/firmen/7/ansprechpartner/neu');
    await screen.findByRole('heading', { name: 'Neuer Ansprechpartner' });

    expect(screen.queryByRole('textbox', { name: /Firma/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();

    await nutzer.type(feld('Nachname'), 'Berg');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen/7/ansprechpartner',
      expect.objectContaining({ body: rumpf({}) }),
    );
  });

  it('meldet den fehlenden Nachnamen am Feld und ruft die Schnittstelle nicht auf', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ 'GET /api/firmen/7': json(200, FIRMA) });

    renderMaske('/firmen/7/ansprechpartner/neu');
    await screen.findByRole('heading', { name: 'Neuer Ansprechpartner' });
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(feld('Nachname')).toHaveAccessibleDescription('Bitte einen Nachnamen angeben.');
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('weist „max@firma" ab und laesst „max@firma.de" durch (E8)', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/firmen/7': json(200, FIRMA),
      'POST /api/firmen/7/ansprechpartner': json(200, ANNA),
    });

    renderMaske('/firmen/7/ansprechpartner/neu');
    await screen.findByRole('heading', { name: 'Neuer Ansprechpartner' });

    await nutzer.type(feld('Nachname'), 'Berg');
    await nutzer.type(feld('E-Mail-Adresse'), 'max@firma');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(feld('E-Mail-Adresse')).toHaveAccessibleDescription(
      'Bitte eine E-Mail-Adresse in der Form name@beispiel.de angeben.',
    );
    // Nur der Lesezugriff beim Aufbau, kein Anlegen.
    expect(fetchMock).toHaveBeenCalledTimes(1);

    await nutzer.type(feld('E-Mail-Adresse'), '.de');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(await screen.findByText('Detailansicht')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen/7/ansprechpartner',
      expect.objectContaining({ body: rumpf({ email: 'max@firma.de' }) }),
    );
  });

  it('haengt die Meldung des Servers an ihr Feld', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'GET /api/firmen/7': json(200, FIRMA),
      'POST /api/firmen/7/ansprechpartner': problem(400, 'Ungültig', {
        email: ['Diese E-Mail-Adresse ist nicht gültig.'],
      }),
    });

    renderMaske('/firmen/7/ansprechpartner/neu');
    await screen.findByRole('heading', { name: 'Neuer Ansprechpartner' });
    await nutzer.type(feld('Nachname'), 'Berg');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(await screen.findByText('Diese E-Mail-Adresse ist nicht gültig.')).toBeInTheDocument();
    expect(feld('E-Mail-Adresse')).toHaveAccessibleDescription(
      'Diese E-Mail-Adresse ist nicht gültig.',
    );
    expect(adresse()).toBe('/firmen/7/ansprechpartner/neu');
  });

  it('zeigt beim Ausfall eine Meldung und bleibt in der Maske', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'GET /api/firmen/7': json(200, FIRMA),
      'POST /api/firmen/7/ansprechpartner': leer(500),
    });

    renderMaske('/firmen/7/ansprechpartner/neu');
    await screen.findByRole('heading', { name: 'Neuer Ansprechpartner' });
    await nutzer.type(feld('Nachname'), 'Berg');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht gespeichert');
    expect(adresse()).toBe('/firmen/7/ansprechpartner/neu');
  });

  it('sendet bei doppeltem Absenden nur einen Aufruf', async () => {
    // Wie in der Firma-Maske: der zweite Klick wird durchgereicht, damit die Sperre selbst misst.
    const nutzer = userEvent.setup({ pointerEventsCheck: 0 });
    const fetchMock = fetchNachPfad({
      'GET /api/firmen/7': json(200, FIRMA),
      // Der Aufruf antwortet nie — genau die Lage, in der ein offener Knopf ein zweites Mal traefe.
      'POST /api/firmen/7/ansprechpartner': () => new Promise<Response>(() => {}),
    });

    renderMaske('/firmen/7/ansprechpartner/neu');
    await screen.findByRole('heading', { name: 'Neuer Ansprechpartner' });
    await nutzer.type(feld('Nachname'), 'Berg');
    const knopf = screen.getByRole('button', { name: 'Anlegen' });
    await nutzer.click(knopf);
    await nutzer.click(knopf);

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(knopf).toBeDisabled();
  });

  it('fuehrt „Abbrechen" zurueck auf die Detailansicht', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({ 'GET /api/firmen/7': json(200, FIRMA) });

    renderMaske('/firmen/7/ansprechpartner/neu');
    await screen.findByRole('heading', { name: 'Neuer Ansprechpartner' });
    await nutzer.click(screen.getByRole('link', { name: 'Abbrechen' }));

    expect(adresse()).toBe('/firmen/7');
  });
});

describe('AnsprechpartnerMaske — Ändern', () => {
  it('fuellt die gespeicherten Werte und kehrt nach dem Speichern zurueck', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/firmen/7': json(200, FIRMA),
      'PUT /api/firmen/7/ansprechpartner/11': leer(204),
    });

    renderMaske('/firmen/7/ansprechpartner/11/bearbeiten');

    expect(
      await screen.findByRole('heading', { name: 'Ansprechpartner bearbeiten' }),
    ).toBeInTheDocument();
    expect(feld('Vorname')).toHaveValue('Anna');
    expect(feld('Nachname')).toHaveValue('Berg');
    expect(feld('Rolle')).toHaveValue('Einkauf');
    expect(feld('E-Mail-Adresse')).toHaveValue('anna.berg@beispiel.de');
    expect(feld('Telefon Festnetz')).toHaveValue('+49 421 123456');
    // Eine fehlende Angabe wird zum leeren Feld, nicht zu einem Platzhalter.
    expect(feld('Telefon Mobil')).toHaveValue('');

    await nutzer.type(feld('Telefon Mobil'), '0170 9876543');
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen/7/ansprechpartner/11',
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({
          vorname: 'Anna',
          nachname: 'Berg',
          rolle: 'Einkauf',
          email: 'anna.berg@beispiel.de',
          telefonFestnetz: '+49 421 123456',
          telefonMobil: '0170 9876543',
        }),
      }),
    );
    expect(await screen.findByText('Detailansicht')).toBeInTheDocument();
    expect(adresse()).toBe('/firmen/7');
  });

  it('macht aus einer geleerten Angabe „keine Angabe" statt eines Leerstrings', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/firmen/7': json(200, FIRMA),
      'PUT /api/firmen/7/ansprechpartner/11': leer(204),
    });

    renderMaske('/firmen/7/ansprechpartner/11/bearbeiten');
    await screen.findByRole('heading', { name: 'Ansprechpartner bearbeiten' });
    await nutzer.clear(feld('Rolle'));
    await nutzer.clear(feld('Vorname'));
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen/7/ansprechpartner/11',
      expect.objectContaining({
        body: rumpf({
          email: 'anna.berg@beispiel.de',
          telefonFestnetz: '+49 421 123456',
        }),
      }),
    );
  });
});

describe('AnsprechpartnerMaske — unsinnige Kennung, Unbekanntes, Ausfall', () => {
  it('faengt eine unsinnige Firma-Kennung ab, bevor sie an die Schnittstelle geht', async () => {
    const fetchMock = fetchNachPfad({});

    renderMaske('/firmen/keine-zahl/ansprechpartner/neu');

    expect(await screen.findByRole('alert')).toHaveTextContent('Diese Firma gibt es nicht.');
    expect(fetchMock).not.toHaveBeenCalled();
    expect(screen.queryByRole('textbox', { name: 'Nachname' })).not.toBeInTheDocument();
  });

  it('faengt eine unsinnige Ansprechpartner-Kennung ab', async () => {
    const fetchMock = fetchNachPfad({});

    renderMaske('/firmen/7/ansprechpartner/keine-zahl/bearbeiten');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Diesen Ansprechpartner gibt es nicht.',
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('meldet einen Ansprechpartner, den die Firma nicht traegt', async () => {
    fetchNachPfad({ 'GET /api/firmen/7': json(200, FIRMA) });

    renderMaske('/firmen/7/ansprechpartner/99/bearbeiten');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Diesen Ansprechpartner gibt es nicht.',
    );
  });

  it('meldet eine unbekannte Firma statt eines leeren Formulars', async () => {
    fetchNachPfad({ 'GET /api/firmen/7': leer(404) });

    renderMaske('/firmen/7/ansprechpartner/neu');

    expect(await screen.findByRole('alert')).toHaveTextContent('Diese Firma gibt es nicht.');
  });

  it('meldet den Ausfall beim Lesen', async () => {
    fetchNachPfad({ 'GET /api/firmen/7': leer(500) });

    renderMaske('/firmen/7/ansprechpartner/neu');

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht zu erreichen');
  });
});

describe('AnsprechpartnerMaske — Tastatur und Benennung', () => {
  it('fuehrt mit dem Tabulator ueber alle Felder, die Taste und den Weg zurueck', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({ 'GET /api/firmen/7': json(200, FIRMA) });

    renderMaske('/firmen/7/ansprechpartner/neu');
    await screen.findByRole('heading', { name: 'Neuer Ansprechpartner' });

    const reihe = [
      feld('Vorname'),
      feld('Nachname'),
      feld('Rolle'),
      feld('E-Mail-Adresse'),
      feld('Telefon Festnetz'),
      feld('Telefon Mobil'),
      screen.getByRole('button', { name: 'Anlegen' }),
      screen.getByRole('link', { name: 'Abbrechen' }),
    ];
    for (const element of reihe) {
      await nutzer.tab();
      expect(element).toHaveFocus();
    }
  });
});
