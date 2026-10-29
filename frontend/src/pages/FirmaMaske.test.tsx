import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { Firma } from '../api/firmen';
import KopfPfad, { KopfPfadProvider } from '../components/KopfPfad';
import { NICHT_OFFEN } from '../routes/RollenRoute';
import { appAlsRolle } from '../test/appAlsRolle';
import { fetchNachPfad, json, leer, problem } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';
import FirmaMaske from './FirmaMaske';

const FIRMA: Firma = {
  id: 7,
  name: 'Beispiel GmbH',
  strasse: 'Hauptstraße 1',
  plz: '28195',
  ort: 'Bremen',
  land: 'FR',
  landName: 'Frankreich',
  steuernummer: '75/123/45678',
  umsatzsteuerId: 'DE123456789',
  aktiv: true,
  ansprechpartner: [],
  version: 3,
};

/** Die Laenderliste, wie `GET /api/laender` sie liefert — sortiert nach dem deutschen Namen. */
const LAENDER = [
  { kuerzel: 'DE', name: 'Deutschland' },
  { kuerzel: 'FR', name: 'Frankreich' },
  { kuerzel: 'AT', name: 'Österreich' },
];

/** Der Weg der Laenderliste, den jede Maske beim Aufbau geht. */
const LAENDER_WEG = { 'GET /api/laender': json(200, LAENDER) };

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
      <KopfPfadProvider>
        <Routes>
          <Route path="/firmen" element={<p>Übersicht</p>} />
          <Route path="/firmen/neu" element={<FirmaMaske />} />
          <Route path="/firmen/:id" element={<p>Detailansicht</p>} />
          <Route path="/firmen/:id/bearbeiten" element={<FirmaMaske />} />
        </Routes>
        <Adresse />
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

/** Dieselbe Maske, aber mit dem Kopf darueber — fuer den Pfad, den sie meldet. */
function renderMitKopf(start: string) {
  return renderMitTheme(
    <MemoryRouter initialEntries={[start]}>
      <KopfPfadProvider>
        <KopfPfad />
        <Routes>
          <Route path="/firmen/neu" element={<FirmaMaske />} />
          <Route path="/firmen/:id/bearbeiten" element={<FirmaMaske />} />
        </Routes>
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

function feld(name: string | RegExp) {
  return screen.getByRole('textbox', { name });
}

function landAuswahl() {
  return screen.getByRole('combobox', { name: 'Land' });
}

/** Wartet, bis das Formular steht — die Maske laedt vorher die Laenderliste. */
async function formularBereit() {
  await screen.findByRole('textbox', { name: 'Name' });
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('FirmaMaske — Anlegen', () => {
  it('Szenario: Das Land eines neuen Kunden', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ ...LAENDER_WEG, 'POST /api/firmen': json(200, FIRMA) });

    // Angenommen ich lege einen neuen Kunden an, wenn ich das Formular oeffne …
    renderMaske('/firmen/neu');
    await formularBereit();

    // … dann ist das Land mit Deutschland vorbelegt …
    expect(screen.getByRole('heading', { name: 'Neue Firma' })).toBeInTheDocument();
    expect(landAuswahl()).toHaveValue('Deutschland');

    // … und ich kann ein anderes Land aus der Liste waehlen.
    await nutzer.click(landAuswahl());
    await nutzer.click(screen.getByRole('option', { name: 'Österreich' }));
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
          land: 'AT',
          steuernummer: null,
          umsatzsteuerId: null,
        }),
      }),
    );
    expect(await screen.findByText('Detailansicht')).toBeInTheDocument();
    // Kriterium 7: der Weg fuehrt auf die Detailansicht der neuen Firma.
    expect(adresse()).toBe('/firmen/7');
  });

  it('schickt die Vorgabe „DE" und nur die ausgefuellten Angaben', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ ...LAENDER_WEG, 'POST /api/firmen': json(200, FIRMA) });

    renderMaske('/firmen/neu');
    await formularBereit();
    await nutzer.type(feld('Name'), 'Neue GmbH');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen',
      expect.objectContaining({
        body: JSON.stringify({
          name: 'Neue GmbH',
          strasse: null,
          plz: null,
          ort: null,
          land: 'DE',
          steuernummer: null,
          umsatzsteuerId: null,
        }),
      }),
    );
  });

  it('faellt ohne Laenderliste auf das Textfeld mit dem Kuerzel zurueck', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/laender': leer(500),
      'POST /api/firmen': json(200, FIRMA),
    });

    renderMaske('/firmen/neu');
    await formularBereit();

    expect(feld('Land')).toHaveValue('DE');
    expect(screen.queryByRole('combobox', { name: 'Land' })).not.toBeInTheDocument();
    await nutzer.type(feld('Name'), 'Neue GmbH');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/firmen',
      expect.objectContaining({ body: expect.stringContaining('"land":"DE"') as unknown }),
    );
  });

  it('zeigt den Ladehinweis, bis die Laenderliste da ist', () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => new Promise(() => {}));

    renderMaske('/firmen/neu');

    expect(screen.getByText(/wird geladen/)).toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: 'Name' })).not.toBeInTheDocument();
  });

  it('meldet den fehlenden Namen am Feld und ruft die Schnittstelle nicht auf', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad(LAENDER_WEG);

    renderMaske('/firmen/neu');
    await formularBereit();
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(feld('Name')).toHaveAccessibleDescription('Bitte einen Namen angeben.');
    expect(fetchMock).not.toHaveBeenCalledWith('/api/firmen', expect.anything());
  });

  it('haengt die Meldung des Servers an ihr Feld', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      ...LAENDER_WEG,
      'POST /api/firmen': problem(400, 'Ungültig', { name: ['Dieser Name ist schon vergeben.'] }),
    });

    renderMaske('/firmen/neu');
    await formularBereit();
    await nutzer.type(feld('Name'), 'Beispiel GmbH');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(await screen.findByText('Dieser Name ist schon vergeben.')).toBeInTheDocument();
    expect(feld('Name')).toHaveAccessibleDescription('Dieser Name ist schon vergeben.');
    expect(adresse()).toBe('/firmen/neu');
  });

  it('zeigt beim Ausfall eine Meldung und bleibt in der Maske', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({ ...LAENDER_WEG, 'POST /api/firmen': leer(500) });

    renderMaske('/firmen/neu');
    await formularBereit();
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
    // Das Anlegen antwortet nie — genau die Lage, in der ein offener Knopf ein zweites Mal traefe.
    const fetchMock = fetchNachPfad({ ...LAENDER_WEG, 'POST /api/firmen': () => new Promise(() => {}) });

    renderMaske('/firmen/neu');
    await formularBereit();
    await nutzer.type(feld('Name'), 'Neue GmbH');
    const knopf = screen.getByRole('button', { name: 'Anlegen' });
    await nutzer.click(knopf);
    await nutzer.click(knopf);

    expect(vorgelegteStaende(fetchMock, 'POST', '/api/firmen')).toHaveLength(1);
    expect(knopf).toBeDisabled();
  });

  it('fuehrt „Abbrechen" als weiche Taste zurueck auf die Uebersicht', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad(LAENDER_WEG);

    renderMaske('/firmen/neu');
    await formularBereit();
    const abbrechen = screen.getByRole('link', { name: 'Abbrechen' });
    // Weiche Taste neben der Kupfertaste, kein Textlink (CLAUDE-design.md, „Tasten").
    expect(abbrechen).toHaveClass('MuiButton-root');

    await nutzer.click(abbrechen);

    expect(adresse()).toBe('/firmen');
  });
});

describe('FirmaMaske — Ändern', () => {
  it('fuellt die gespeicherten Werte und kehrt nach dem Speichern zurueck', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      ...LAENDER_WEG,
      'GET /api/firmen/7': json(200, FIRMA),
      'PUT /api/firmen/7': json(200, { ...FIRMA, version: 4 }),
    });

    renderMaske('/firmen/7/bearbeiten');

    expect(await screen.findByRole('heading', { name: 'Firma bearbeiten' })).toBeInTheDocument();
    await formularBereit();
    expect(feld('Name')).toHaveValue('Beispiel GmbH');
    expect(feld(/^Straße/)).toHaveValue('Hauptstraße 1');
    expect(feld('Postleitzahl')).toHaveValue('28195');
    expect(feld('Ort')).toHaveValue('Bremen');
    // Nicht vorbelegt, sondern uebernommen: beim Aendern ist das gespeicherte Land gewaehlt.
    expect(landAuswahl()).toHaveValue('Frankreich');
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
          land: 'FR',
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
      ...LAENDER_WEG,
      'GET /api/firmen/7': json(200, { ...FIRMA, strasse: null }),
      'PUT /api/firmen/7': json(200, { ...FIRMA, version: 4 }),
    });

    renderMaske('/firmen/7/bearbeiten');
    await formularBereit();
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
          land: 'FR',
          steuernummer: null,
          umsatzsteuerId: 'DE123456789',
        }),
      }),
    );
  });

  it('fuehrt „Abbrechen" zurueck auf die Detailansicht', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({ ...LAENDER_WEG, 'GET /api/firmen/7': json(200, FIRMA) });

    renderMaske('/firmen/7/bearbeiten');
    await formularBereit();
    await nutzer.click(screen.getByRole('link', { name: 'Abbrechen' }));

    expect(adresse()).toBe('/firmen/7');
  });

  it('faengt eine unsinnige Kennung ab, bevor sie an die Schnittstelle geht', async () => {
    const fetchMock = fetchNachPfad(LAENDER_WEG);

    renderMaske('/firmen/keine-zahl/bearbeiten');

    expect(await screen.findByRole('alert')).toHaveTextContent('gibt es nicht');
    expect(fetchMock).not.toHaveBeenCalledWith(
      expect.stringMatching(/^\/api\/firmen/),
      expect.anything(),
    );
    expect(screen.queryByRole('textbox', { name: 'Name' })).not.toBeInTheDocument();
  });

  it('meldet eine unbekannte Firma statt eines leeren Formulars', async () => {
    fetchNachPfad({ ...LAENDER_WEG, 'GET /api/firmen/7': leer(404) });

    renderMaske('/firmen/7/bearbeiten');

    expect(await screen.findByRole('alert')).toHaveTextContent('gibt es nicht');
  });

  it('meldet den Ausfall beim Lesen', async () => {
    fetchNachPfad({ ...LAENDER_WEG, 'GET /api/firmen/7': leer(500) });

    renderMaske('/firmen/7/bearbeiten');

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht zu erreichen');
  });
});

describe('FirmaMaske — nur mit Schreibrecht (Kriterium 10 von #409, Plan #414, A11)', () => {
  it.each([
    ['/firmen/neu', 'Neue Firma'],
    ['/firmen/7/bearbeiten', 'Firma bearbeiten'],
  ])('zeigt ReadOnly auf „%s" den Satz statt der Maske „%s"', async (adresse, titel) => {
    const aufruf = appAlsRolle('READ_ONLY', adresse);

    expect(await screen.findByText(new RegExp(NICHT_OFFEN))).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: titel })).not.toBeInTheDocument();
    // Die Maske entsteht gar nicht — sie liest auch nichts.
    expect(aufruf).toHaveBeenCalledTimes(2);
  });

  it('zeigt einem Employee die Maske, denn er darf fachlich schreiben', async () => {
    appAlsRolle('EMPLOYEE', '/firmen/7/bearbeiten', {
      ...LAENDER_WEG,
      'GET /api/firmen/7': json(200, FIRMA),
    });

    expect(await screen.findByRole('heading', { name: 'Firma bearbeiten' })).toBeInTheDocument();
    expect(screen.queryByText(new RegExp(NICHT_OFFEN))).not.toBeInTheDocument();
  });
});

describe('FirmaMaske — der Pfad im Kopf (E6)', () => {
  it.each([
    ['/firmen/neu', 'Neue Firma'],
    ['/firmen/7/bearbeiten', 'Firma bearbeiten'],
  ])('meldet unter %s den Pfad „Firmen › %s"', async (start, endstufe) => {
    fetchNachPfad({ ...LAENDER_WEG, 'GET /api/firmen/7': json(200, FIRMA) });

    renderMitKopf(start);

    const pfad = within(await screen.findByRole('navigation', { name: 'Pfad' }));
    expect(pfad.getByRole('link', { name: 'Firmen' })).toHaveAttribute('href', '/firmen');
    expect(pfad.getByText(endstufe)).toHaveAttribute('aria-current', 'page');
  });
});

describe('FirmaMaske — Tastatur und Benennung', () => {
  it('fuehrt mit dem Tabulator ueber alle Felder, die Taste und den Weg zurueck', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad(LAENDER_WEG);

    renderMaske('/firmen/neu');
    await formularBereit();

    const reihe = [
      feld('Name'),
      feld(/^Straße/),
      feld('Postleitzahl'),
      feld('Ort'),
      landAuswahl(),
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

/** Heute um die genannte Uhrzeit in der Ortszeit — die Meldung nennt dann nur die Uhrzeit. */
function heuteUm(stunde: number, minute: number): string {
  const zeitpunkt = new Date();
  zeitpunkt.setHours(stunde, minute, 0, 0);
  return zeitpunkt.toISOString();
}

/** Die Abweisung des Servers, wie `GlobalExceptionHandler` sie schreibt (Plan #415, E4, E15). */
function abweisung(geaendertVon: string): () => Response {
  return json(409, {
    title: 'Conflict',
    status: 409,
    detail: 'Dieser Eintrag wurde inzwischen geändert.',
    konflikt: { art: 'veraltet', geaendertAm: heuteUm(10, 42), eigenesFenster: false, geaendertVon },
  });
}

/** Antwortet der Reihe nach mit den Antworten — beim ersten Aufruf mit der ersten. */
function nacheinander(...antworten: readonly (() => Response)[]): () => Response {
  let aufruf = 0;
  return () => {
    const antwort = antworten[Math.min(aufruf, antworten.length - 1)];
    aufruf += 1;
    return antwort();
  };
}

/** Die Bedingungskoepfe aller Aufrufe des Wegs, in ihrer Reihenfolge. */
function vorgelegteStaende(
  fetchMock: ReturnType<typeof fetchNachPfad>,
  methode: string,
  pfad: string,
): (string | undefined)[] {
  return fetchMock.mock.calls
    .filter(([ziel, init]) => ziel === pfad && init?.method === methode)
    .map(([, init]) => (init?.headers as Record<string, string> | undefined)?.['If-Match']);
}

describe('FirmaMaske — gleichzeitig arbeiten (#411, Kriterien 1 bis 4; Plan #415)', () => {
  /** Annas Stand: Sie hat Name und Ort inzwischen geaendert. */
  const ANNAS_STAND: Firma = { ...FIRMA, name: 'Beispiel AG', ort: 'Hamburg', version: 4 };

  it('legt beim Speichern den gelesenen Stand vor', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      ...LAENDER_WEG,
      'GET /api/firmen/7': json(200, FIRMA),
      'PUT /api/firmen/7': json(200, { ...FIRMA, version: 4 }),
    });

    renderMaske('/firmen/7/bearbeiten');
    await formularBereit();
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    await screen.findByText('Detailansicht');
    expect(vorgelegteStaende(fetchMock, 'PUT', '/api/firmen/7')).toEqual(['"3"']);
  });

  it('legt beim Anlegen keinen Stand vor (Kriterium 7)', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ ...LAENDER_WEG, 'POST /api/firmen': json(201, FIRMA) });

    renderMaske('/firmen/neu');
    await formularBereit();
    await nutzer.type(feld('Name'), 'Neue GmbH');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    await screen.findByText('Detailansicht');
    expect(vorgelegteStaende(fetchMock, 'POST', '/api/firmen')).toEqual([undefined]);
  });

  it('Szenario: abgewiesenes Speichern — die Eingaben bleiben, Neu laden stellt sie daneben', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      ...LAENDER_WEG,
      'GET /api/firmen/7': nacheinander(json(200, FIRMA), json(200, ANNAS_STAND)),
      'PUT /api/firmen/7': nacheinander(abweisung('Anna'), json(200, { ...ANNAS_STAND, version: 5 })),
    });

    renderMaske('/firmen/7/bearbeiten');
    await formularBereit();
    await nutzer.clear(feld('Ort'));
    await nutzer.type(feld('Ort'), 'Oldenburg');
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    // Abgewiesen: Die Meldung nennt Anna, und der eigene Ort steht weiter im Feld.
    const meldung = await screen.findByRole('alert');
    expect(meldung).toHaveTextContent('Nicht ausgeführt');
    expect(meldung).toHaveTextContent('Anna hat diesen Eintrag inzwischen geändert (10:42).');
    expect(meldung).not.toHaveTextContent('Bitte später erneut versuchen');
    expect(feld('Ort')).toHaveValue('Oldenburg');
    expect(adresse()).toBe('/firmen/7/bearbeiten');

    // Neu laden: Annas Stand steht in der Maske, die eigenen Werte daneben zum Kopieren.
    await nutzer.click(screen.getByRole('button', { name: 'Neu laden' }));
    await waitFor(() => {
      expect(feld('Ort')).toHaveValue('Hamburg');
    });
    expect(feld('Name')).toHaveValue('Beispiel AG');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    const eigene = screen.getByRole('region', { name: 'Deine Eingaben' });
    expect(eigene).toHaveTextContent('Oldenburg');
    expect(eigene).toHaveTextContent('Beispiel GmbH');
    expect(within(eigene).getByText('Postleitzahl')).toBeInTheDocument();

    // Auf Annas Stand geht das Speichern durch; vorgelegt wird der neu geladene Stand.
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));
    await screen.findByText('Detailansicht');
    expect(vorgelegteStaende(fetchMock, 'PUT', '/api/firmen/7')).toEqual(['"3"', '"4"']);
  });

  it('raeumt die eigenen Eingaben mit „Verwerfen" ab', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      ...LAENDER_WEG,
      'GET /api/firmen/7': nacheinander(json(200, FIRMA), json(200, ANNAS_STAND)),
      'PUT /api/firmen/7': abweisung('Anna'),
    });

    renderMaske('/firmen/7/bearbeiten');
    await formularBereit();
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));
    await nutzer.click(await screen.findByRole('button', { name: 'Neu laden' }));
    const eigene = await screen.findByRole('region', { name: 'Deine Eingaben' });

    await nutzer.click(within(eigene).getByRole('button', { name: 'Verwerfen' }));

    expect(screen.queryByRole('region', { name: 'Deine Eingaben' })).not.toBeInTheDocument();
  });

  it('meldet einen Ausfall beim Neu laden und laesst die Konfliktmeldung stehen', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      ...LAENDER_WEG,
      'GET /api/firmen/7': nacheinander(json(200, FIRMA), problem(503, 'kaputt')),
      'PUT /api/firmen/7': abweisung('Anna'),
    });

    renderMaske('/firmen/7/bearbeiten');
    await formularBereit();
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));
    await nutzer.click(await screen.findByRole('button', { name: 'Neu laden' }));

    expect(
      await screen.findByText(
        'Der aktuelle Stand ist gerade nicht zu erreichen. Bitte erneut versuchen.',
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Neu laden' })).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Deine Eingaben' })).not.toBeInTheDocument();
  });
});
