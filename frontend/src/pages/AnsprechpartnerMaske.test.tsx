import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { Ansprechpartner, Firma } from '../api/firmen';
import KopfPfad, { KopfPfadProvider } from '../components/KopfPfad';
import { NICHT_OFFEN } from '../routes/RollenRoute';
import { appAlsRolle } from '../test/appAlsRolle';
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
  version: 3,
};

const FIRMA: Firma = {
  id: 7,
  name: 'Beispiel GmbH',
  strasse: 'Hauptstraße 1',
  plz: '28195',
  ort: 'Bremen',
  land: 'DE',
  landName: 'Deutschland',
  steuernummer: null,
  umsatzsteuerId: null,
  aktiv: true,
  ansprechpartner: [ANNA],
  version: 0,
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
      <KopfPfadProvider>
        <Routes>
          <Route path="/firmen/:id" element={<p>Detailansicht</p>} />
          <Route path="/firmen/:id/ansprechpartner/neu" element={<AnsprechpartnerMaske />} />
          <Route
            path="/firmen/:id/ansprechpartner/:ansprechpartnerId/bearbeiten"
            element={<AnsprechpartnerMaske />}
          />
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
          <Route path="/firmen/:id/ansprechpartner/neu" element={<AnsprechpartnerMaske />} />
          <Route
            path="/firmen/:id/ansprechpartner/:ansprechpartnerId/bearbeiten"
            element={<AnsprechpartnerMaske />}
          />
        </Routes>
      </KopfPfadProvider>
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

  it('fuehrt „Abbrechen" als weiche Taste zurueck auf die Detailansicht', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({ 'GET /api/firmen/7': json(200, FIRMA) });

    renderMaske('/firmen/7/ansprechpartner/neu');
    await screen.findByRole('heading', { name: 'Neuer Ansprechpartner' });
    const abbrechen = screen.getByRole('link', { name: 'Abbrechen' });
    // Weiche Taste neben der Kupfertaste, kein Textlink (CLAUDE-design.md, „Tasten").
    expect(abbrechen).toHaveClass('MuiButton-root');

    await nutzer.click(abbrechen);

    expect(adresse()).toBe('/firmen/7');
  });
});

describe('AnsprechpartnerMaske — Ändern', () => {
  it('fuellt die gespeicherten Werte und kehrt nach dem Speichern zurueck', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/firmen/7': json(200, FIRMA),
      'PUT /api/firmen/7/ansprechpartner/11': json(200, { ...ANNA, version: 4 }),
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
      'PUT /api/firmen/7/ansprechpartner/11': json(200, { ...ANNA, version: 4 }),
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

describe('AnsprechpartnerMaske — nur mit Schreibrecht (Kriterium 10 von #409, Plan #414, A11)', () => {
  it.each([
    ['/firmen/7/ansprechpartner/neu', 'Neuer Ansprechpartner'],
    ['/firmen/7/ansprechpartner/11/bearbeiten', 'Ansprechpartner bearbeiten'],
  ])('zeigt ReadOnly auf „%s" den Satz statt der Maske „%s"', async (adresse, titel) => {
    const aufruf = appAlsRolle('READ_ONLY', adresse);

    expect(await screen.findByText(new RegExp(NICHT_OFFEN))).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: titel })).not.toBeInTheDocument();
    // Die Maske entsteht gar nicht — sie liest auch nichts.
    expect(aufruf).toHaveBeenCalledTimes(2);
  });

  it('zeigt einem Employee die Maske, denn er darf fachlich schreiben', async () => {
    appAlsRolle('EMPLOYEE', '/firmen/7/ansprechpartner/neu', { 'GET /api/firmen/7': json(200, FIRMA) });

    expect(await screen.findByRole('heading', { name: 'Neuer Ansprechpartner' })).toBeInTheDocument();
    expect(screen.queryByText(new RegExp(NICHT_OFFEN))).not.toBeInTheDocument();
  });
});

describe('AnsprechpartnerMaske — der Pfad im Kopf (E6)', () => {
  it.each([
    ['/firmen/7/ansprechpartner/neu', 'Neuer Ansprechpartner'],
    ['/firmen/7/ansprechpartner/11/bearbeiten', 'Ansprechpartner bearbeiten'],
  ])('meldet unter %s den Pfad „Firmen › %s"', async (start, endstufe) => {
    fetchNachPfad({ 'GET /api/firmen/7': json(200, FIRMA) });

    renderMitKopf(start);

    const pfad = within(await screen.findByRole('navigation', { name: 'Pfad' }));
    expect(pfad.getByRole('link', { name: 'Firmen' })).toHaveAttribute('href', '/firmen');
    expect(pfad.getByText(endstufe)).toHaveAttribute('aria-current', 'page');
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

describe('AnsprechpartnerMaske — gleichzeitig arbeiten (#411, Kriterien 1 bis 4; Plan #415)', () => {
  const WEG = '/api/firmen/7/ansprechpartner/11';
  /** Bernds Stand: Er hat Rolle und Nachnamen inzwischen geaendert. */
  const BERNDS_STAND: Firma = {
    ...FIRMA,
    ansprechpartner: [{ ...ANNA, nachname: 'Berg-Weber', rolle: 'Vertrieb', version: 4 }],
  };

  it('legt beim Speichern den gelesenen Stand des Ansprechpartners vor', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/firmen/7': json(200, FIRMA),
      [`PUT ${WEG}`]: json(200, { ...ANNA, version: 4 }),
    });

    renderMaske('/firmen/7/ansprechpartner/11/bearbeiten');
    await screen.findByRole('heading', { name: 'Ansprechpartner bearbeiten' });
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    await screen.findByText('Detailansicht');
    expect(vorgelegteStaende(fetchMock, 'PUT', WEG)).toEqual(['"3"']);
  });

  it('legt beim Anlegen keinen Stand vor (Kriterium 7)', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/firmen/7': json(200, FIRMA),
      'POST /api/firmen/7/ansprechpartner': json(201, ANNA),
    });

    renderMaske('/firmen/7/ansprechpartner/neu');
    await screen.findByRole('heading', { name: 'Neuer Ansprechpartner' });
    await nutzer.type(feld('Nachname'), 'Berg');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    await screen.findByText('Detailansicht');
    expect(vorgelegteStaende(fetchMock, 'POST', '/api/firmen/7/ansprechpartner')).toEqual([
      undefined,
    ]);
  });

  it('Szenario: abgewiesenes Speichern — die Eingaben bleiben, Neu laden stellt sie daneben', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/firmen/7': nacheinander(json(200, FIRMA), json(200, BERNDS_STAND)),
      [`PUT ${WEG}`]: nacheinander(abweisung('Bernd'), json(200, { ...ANNA, version: 5 })),
    });

    renderMaske('/firmen/7/ansprechpartner/11/bearbeiten');
    await screen.findByRole('heading', { name: 'Ansprechpartner bearbeiten' });
    await nutzer.clear(feld('Rolle'));
    await nutzer.type(feld('Rolle'), 'Geschäftsführung');
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    // Abgewiesen: Die Meldung nennt Bernd, und die eigene Rolle steht weiter im Feld.
    const meldung = await screen.findByRole('alert');
    expect(meldung).toHaveTextContent('Bernd hat diesen Eintrag inzwischen geändert (10:42).');
    expect(feld('Rolle')).toHaveValue('Geschäftsführung');
    expect(adresse()).toBe('/firmen/7/ansprechpartner/11/bearbeiten');

    // Neu laden: Bernds Stand steht in der Maske, die eigenen Werte daneben zum Kopieren.
    await nutzer.click(screen.getByRole('button', { name: 'Neu laden' }));
    await waitFor(() => {
      expect(feld('Rolle')).toHaveValue('Vertrieb');
    });
    expect(feld('Nachname')).toHaveValue('Berg-Weber');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    const eigene = screen.getByRole('region', { name: 'Deine Eingaben' });
    expect(eigene).toHaveTextContent('Geschäftsführung');
    expect(within(eigene).getByText('Telefon Mobil')).toBeInTheDocument();

    // Auf Bernds Stand geht das Speichern durch; vorgelegt wird der neu geladene Stand.
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));
    await screen.findByText('Detailansicht');
    expect(vorgelegteStaende(fetchMock, 'PUT', WEG)).toEqual(['"3"', '"4"']);
  });

  it('raeumt die eigenen Eingaben mit „Verwerfen" ab', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'GET /api/firmen/7': nacheinander(json(200, FIRMA), json(200, BERNDS_STAND)),
      [`PUT ${WEG}`]: abweisung('Bernd'),
    });

    renderMaske('/firmen/7/ansprechpartner/11/bearbeiten');
    await screen.findByRole('heading', { name: 'Ansprechpartner bearbeiten' });
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));
    await nutzer.click(await screen.findByRole('button', { name: 'Neu laden' }));
    const eigene = await screen.findByRole('region', { name: 'Deine Eingaben' });

    await nutzer.click(within(eigene).getByRole('button', { name: 'Verwerfen' }));

    expect(screen.queryByRole('region', { name: 'Deine Eingaben' })).not.toBeInTheDocument();
  });

  it('meldet beim Neu laden, wenn die Firma den Ansprechpartner nicht mehr traegt', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'GET /api/firmen/7': nacheinander(
        json(200, FIRMA),
        json(200, { ...FIRMA, ansprechpartner: [] }),
      ),
      [`PUT ${WEG}`]: abweisung('Bernd'),
    });

    renderMaske('/firmen/7/ansprechpartner/11/bearbeiten');
    await screen.findByRole('heading', { name: 'Ansprechpartner bearbeiten' });
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));
    await nutzer.click(await screen.findByRole('button', { name: 'Neu laden' }));

    expect(await screen.findByText('Diesen Ansprechpartner gibt es nicht.')).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Deine Eingaben' })).not.toBeInTheDocument();
  });

  it('meldet einen Ausfall beim Neu laden und laesst die Konfliktmeldung stehen', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'GET /api/firmen/7': nacheinander(json(200, FIRMA), problem(503, 'kaputt')),
      [`PUT ${WEG}`]: abweisung('Bernd'),
    });

    renderMaske('/firmen/7/ansprechpartner/11/bearbeiten');
    await screen.findByRole('heading', { name: 'Ansprechpartner bearbeiten' });
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));
    await nutzer.click(await screen.findByRole('button', { name: 'Neu laden' }));

    expect(
      await screen.findByText(
        'Der aktuelle Stand ist gerade nicht zu erreichen. Bitte erneut versuchen.',
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Neu laden' })).toBeInTheDocument();
  });
});
