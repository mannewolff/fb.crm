import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import KopfPfad, { KopfPfadProvider } from '../components/KopfPfad';
import { MAX_UPLOAD_BYTE } from '../lib/dateigroesse';
import { fetchNachPfad, formularWeg, json, leer, problem } from '../test/fetchNachPfad';
import type { Routen } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';
import NachtragMaske from './NachtragMaske';

const WEG = '/api/nachgetragene-rechnungen';

/** Die Firmenwahl, wie die Maske sie beim Aufbau holt: alle, auch stillgelegte (Kriterium 2). */
const ALLE_FIRMEN = '/api/firmen?suche=&auchStillgelegte=true';

const ADLER = { id: 5, name: 'Adler AG', ort: 'Bremen', aktiveAnsprechpartner: 1, aktiv: true };
const ALT = { id: 6, name: 'Alt GmbH', ort: null, aktiveAnsprechpartner: 0, aktiv: false };

const FIRMEN = { firmen: [ADLER, ALT], gesamt: 2 };

/** Die Meldung am Feld der Datei, wenn der zweite Aufruf ohne eigene Feldmeldung scheitert. */
const DATEI_AUSFALL =
  'Das PDF wurde nicht abgelegt. Bitte später erneut versuchen. Die übrigen Angaben sind gespeichert.';

/** Eine nachgetragene Rechnung, wie Jackson sie schreibt. */
const NACHTRAG = {
  id: 7,
  firmaId: 5,
  firmaName: 'Adler AG',
  nummer: 'RE-2026-014',
  rechnungDatum: '2026-03-12',
  netto: 1000.5,
  brutto: 1190.6,
  zustand: 'GESTELLT',
  dokument: false,
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
        <KopfPfad />
        <Routes>
          <Route path="/rechnungen" element={<p>Rechnungsliste</p>} />
          <Route path="/rechnungen/nachtragen" element={<NachtragMaske />} />
          <Route path="/rechnungen/nachgetragen/:id" element={<p>Einzelansicht</p>} />
          <Route path="/rechnungen/nachgetragen/:id/bearbeiten" element={<NachtragMaske />} />
        </Routes>
        <Adresse />
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

/** Die Antworten zum Anlegen: die Firmenwahl und, was der Test dazugibt. */
function anlegen(weitere: Routen = {}) {
  return fetchNachPfad({ [`GET ${ALLE_FIRMEN}`]: json(200, FIRMEN), ...weitere });
}

/** Die Antworten zum Aendern: Firmenwahl, die gespeicherte Rechnung und, was der Test dazugibt. */
function aendern(nachtrag: object = NACHTRAG, weitere: Routen = {}) {
  return fetchNachPfad({
    [`GET ${ALLE_FIRMEN}`]: json(200, FIRMEN),
    [`GET ${WEG}/7`]: json(200, nachtrag),
    ...weitere,
  });
}

/** Eine Antwort, die erst auf Abruf eintrifft — so laesst sich „waehrend des Sendens" pruefen. */
function angehalten(antwort: () => Response) {
  let liefere!: (wert: Response) => void;
  const spaeter = new Promise<Response>((aufloesen) => {
    liefere = aufloesen;
  });
  return {
    weg: () => spaeter,
    loesen: () => {
      liefere(antwort());
    },
  };
}

function pdf(name = 'rechnung.pdf', groesse = 12): File {
  const datei = new File(['%PDF-1.7'], name, { type: 'application/pdf' });
  Object.defineProperty(datei, 'size', { value: groesse });
  return datei;
}

function kundenwahl() {
  return screen.getByRole('combobox', { name: 'Kunde' });
}

function nummer() {
  return screen.getByRole('textbox', { name: 'Rechnungsnummer' });
}

function datum() {
  return screen.getByLabelText(/^Rechnungsdatum/);
}

function netto() {
  return screen.getByRole('textbox', { name: 'Nettobetrag' });
}

function brutto() {
  return screen.getByRole('textbox', { name: 'Bruttobetrag' });
}

function dateifeld() {
  return screen.getByLabelText('Originalrechnung (PDF)');
}

/** Wartet, bis die Maske steht, und gibt den Nutzer zurueck. */
async function bereit(nutzer = userEvent.setup()) {
  await screen.findByRole('combobox', { name: 'Kunde' });
  return nutzer;
}

/** Fuellt die Pflichtangaben gueltig aus. */
async function ausfuellen(nutzer: ReturnType<typeof userEvent.setup>) {
  await nutzer.selectOptions(kundenwahl(), '5');
  await nutzer.type(nummer(), 'RE-2026-014');
  await nutzer.type(datum(), '2026-03-12');
  await nutzer.type(netto(), '1000,5');
  await nutzer.type(brutto(), '1190,60');
}


afterEach(() => {
  vi.restoreAllMocks();
});

describe('NachtragMaske — Laden', () => {
  it('zeigt einen Ladehinweis, bis die Firmenwahl da ist', async () => {
    const spaet = angehalten(json(200, FIRMEN));
    fetchNachPfad({ [`GET ${ALLE_FIRMEN}`]: spaet.weg });

    renderMaske('/rechnungen/nachtragen');

    expect(screen.getByText('Die Angaben werden geladen …')).toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: 'Kunde' })).not.toBeInTheDocument();
    spaet.loesen();
    expect(await screen.findByRole('combobox', { name: 'Kunde' })).toBeInTheDocument();
  });

  it('schreibt nach dem Ausbau nichts mehr, auch wenn die Antwort spaeter kommt', async () => {
    const spaet = angehalten(json(200, FIRMEN));
    fetchNachPfad({ [`GET ${ALLE_FIRMEN}`]: spaet.weg });

    const { unmount } = renderMaske('/rechnungen/nachtragen');
    unmount();
    spaet.loesen();

    // Ohne Fehler zu Ende: Ein Setzen nach dem Ausbau waere eine Warnung von React.
    await expect(spaet.weg()).resolves.toBeInstanceOf(Response);
  });
});

describe('NachtragMaske — Fehler', () => {
  it('meldet einen Ausfall der Firmenwahl', async () => {
    fetchNachPfad({ [`GET ${ALLE_FIRMEN}`]: problem(500, 'kaputt') });

    renderMaske('/rechnungen/nachtragen');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Die Angaben sind gerade nicht zu erreichen. Bitte später erneut versuchen.',
    );
  });

  it('sagt beim Aendern, dass es die Rechnung nicht gibt', async () => {
    fetchNachPfad({
      [`GET ${ALLE_FIRMEN}`]: json(200, FIRMEN),
      [`GET ${WEG}/7`]: problem(404, 'nicht da'),
    });

    renderMaske('/rechnungen/nachgetragen/7/bearbeiten');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Diese nachgetragene Rechnung gibt es nicht.',
    );
  });

  it('fragt bei einer Kennung, die keine ist, gar nicht erst', async () => {
    const fetchMock = fetchNachPfad({});

    renderMaske('/rechnungen/nachgetragen/abc/bearbeiten');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Diese nachgetragene Rechnung gibt es nicht.',
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('meldet einen Ausfall beim Speichern ohne Feldfehler als allgemeine Meldung', async () => {
    anlegen({ [`POST ${WEG}`]: problem(500, 'kaputt') });

    renderMaske('/rechnungen/nachtragen');
    const nutzer = await bereit();
    await ausfuellen(nutzer);
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Die Rechnung wurde nicht gespeichert. Bitte später erneut versuchen.',
    );
    expect(screen.getByRole('button', { name: 'Anlegen' })).toBeEnabled();
  });

  it('setzt die Feldfehler eines 422 an ihr Feld und nicht in eine allgemeine Meldung', async () => {
    anlegen({
      [`POST ${WEG}`]: problem(422, 'ungueltig', {
        nummer: ['Diese Rechnungsnummer ist schon vergeben.'],
        rechnungDatum: ['Das Datum liegt nicht im laufenden Geschäftsjahr.'],
        brutto: ['Der Bruttobetrag darf nicht kleiner sein als der Nettobetrag.'],
      }),
    });

    renderMaske('/rechnungen/nachtragen');
    const nutzer = await bereit();
    await ausfuellen(nutzer);
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(await screen.findByText('Diese Rechnungsnummer ist schon vergeben.')).toBeInTheDocument();
    expect(nummer()).toHaveAccessibleDescription('Diese Rechnungsnummer ist schon vergeben.');
    expect(datum()).toHaveAccessibleDescription(
      'Das Datum liegt nicht im laufenden Geschäftsjahr.',
    );
    expect(brutto()).toHaveAccessibleDescription(
      'Der Bruttobetrag darf nicht kleiner sein als der Nettobetrag.',
    );
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('setzt einen Feldfehler zur Firma an die Kundenwahl', async () => {
    anlegen({
      [`POST ${WEG}`]: problem(422, 'ungueltig', { firmaId: ['Diese Firma gibt es nicht.'] }),
    });

    renderMaske('/rechnungen/nachtragen');
    const nutzer = await bereit();
    await ausfuellen(nutzer);
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(await screen.findByText('Diese Firma gibt es nicht.')).toBeInTheDocument();
    expect(kundenwahl()).toHaveAccessibleDescription('Diese Firma gibt es nicht.');
  });

  it('verlangt die Pflichtangaben, ohne das Netz zu bemuehen', async () => {
    const fetchMock = anlegen();

    renderMaske('/rechnungen/nachtragen');
    const nutzer = await bereit();
    await nutzer.type(netto(), '12,345');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(kundenwahl()).toHaveAccessibleDescription('Bitte einen Kunden wählen.');
    expect(nummer()).toHaveAccessibleDescription('Bitte eine Rechnungsnummer angeben.');
    expect(datum()).toHaveAccessibleDescription('Bitte ein Rechnungsdatum angeben.');
    expect(netto()).toHaveAccessibleDescription(
      'Bitte einen Betrag mit höchstens zwei Nachkommastellen angeben, etwa 1190,50.',
    );
    expect(brutto()).toHaveAccessibleDescription(
      'Bitte einen Betrag mit höchstens zwei Nachkommastellen angeben, etwa 1190,50.',
    );
    // Nur der Aufbau fragte; gesendet wurde nichts.
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('verlangt einen Kunden auch, wenn die Wahl zurueckgenommen wurde', async () => {
    const fetchMock = anlegen();

    renderMaske('/rechnungen/nachtragen');
    const nutzer = await bereit();
    await ausfuellen(nutzer);
    await nutzer.selectOptions(kundenwahl(), '');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(kundenwahl()).toHaveAccessibleDescription('Bitte einen Kunden wählen.');
    // Nur der Aufbau fragte; gesendet wurde nichts.
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});

describe('NachtragMaske — Leer', () => {
  it('fuehrt ohne jede Firma zur ersten Firma statt zu einer leeren Wahl', async () => {
    fetchNachPfad({ [`GET ${ALLE_FIRMEN}`]: json(200, { firmen: [], gesamt: 0 }) });

    renderMaske('/rechnungen/nachtragen');

    expect(await screen.findByRole('status')).toHaveTextContent('Es ist noch keine Firma angelegt.');
    expect(screen.getByRole('link', { name: 'Erste Firma anlegen' })).toHaveAttribute(
      'href',
      '/firmen/neu',
    );
    expect(screen.queryByRole('combobox', { name: 'Kunde' })).not.toBeInTheDocument();
  });

  it('sagt, wenn die Suche nach einem Kunden nichts findet', async () => {
    anlegen({
      'GET /api/firmen?suche=Zeta&auchStillgelegte=true': json(200, { firmen: [], gesamt: 2 }),
    });

    renderMaske('/rechnungen/nachtragen');
    const nutzer = await bereit();
    await nutzer.click(screen.getByRole('searchbox', { name: 'Kunde suchen' }));
    await nutzer.paste('Zeta');

    expect(await screen.findByText('Zu diesem Suchtext wurde keine Firma gefunden.')).toBeVisible();
    expect(screen.queryByRole('option', { name: 'Adler AG' })).not.toBeInTheDocument();
  });
});

describe('NachtragMaske — Kundenwahl', () => {
  it('zeigt eine stillgelegte Firma waehlbar und gekennzeichnet', async () => {
    const fetchMock = anlegen({ [`POST ${WEG}`]: json(201, { ...NACHTRAG, firmaId: 6 }) });

    renderMaske('/rechnungen/nachtragen');
    const nutzer = await bereit();

    expect(screen.getByRole('option', { name: 'Adler AG' })).toBeInTheDocument();
    const stillgelegt = screen.getByRole('option', { name: 'Alt GmbH (stillgelegt)' });
    expect(stillgelegt).toBeEnabled();

    await ausfuellen(nutzer);
    await nutzer.selectOptions(kundenwahl(), '6');
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    await screen.findByText('Einzelansicht');
    expect(fetchMock).toHaveBeenCalledWith(
      WEG,
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          firmaId: 6,
          nummer: 'RE-2026-014',
          rechnungDatum: '2026-03-12',
          netto: '1000.50',
          brutto: '1190.60',
        }),
      }),
    );
  });

  it('sucht ueber die Firmenwahl samt stillgelegten und bricht die vorige Suche ab', async () => {
    const erste = angehalten(json(200, { firmen: [ADLER], gesamt: 2 }));
    anlegen({
      'GET /api/firmen?suche=A&auchStillgelegte=true': erste.weg,
      'GET /api/firmen?suche=Al&auchStillgelegte=true': json(200, { firmen: [ALT], gesamt: 2 }),
    });

    renderMaske('/rechnungen/nachtragen');
    const nutzer = await bereit();
    await nutzer.type(screen.getByRole('searchbox', { name: 'Kunde suchen' }), 'Al');

    expect(await screen.findByRole('option', { name: 'Alt GmbH (stillgelegt)' })).toBeVisible();
    // Die langsamere erste Antwort kommt zu spaet und aendert nichts mehr.
    erste.loesen();
    await erste.weg();
    expect(screen.queryByRole('option', { name: 'Adler AG' })).not.toBeInTheDocument();
  });

  it('behaelt den gewaehlten Kunden in der Wahl, auch wenn die Suche ihn nicht findet', async () => {
    anlegen({
      'GET /api/firmen?suche=Alt&auchStillgelegte=true': json(200, { firmen: [ALT], gesamt: 2 }),
    });

    renderMaske('/rechnungen/nachtragen');
    const nutzer = await bereit();
    await nutzer.selectOptions(kundenwahl(), '5');
    await nutzer.click(screen.getByRole('searchbox', { name: 'Kunde suchen' }));
    await nutzer.paste('Alt');

    expect(await screen.findByRole('option', { name: 'Alt GmbH (stillgelegt)' })).toBeVisible();
    expect(kundenwahl()).toHaveValue('5');
    expect(screen.getByRole('option', { name: 'Adler AG' })).toBeInTheDocument();
  });

  it('meldet eine gescheiterte Suche an der Kundenwahl', async () => {
    anlegen({ 'GET /api/firmen?suche=Z&auchStillgelegte=true': problem(500, 'kaputt') });

    renderMaske('/rechnungen/nachtragen');
    const nutzer = await bereit();
    await nutzer.type(screen.getByRole('searchbox', { name: 'Kunde suchen' }), 'Z');

    expect(
      await screen.findByText('Die Suche ist gerade nicht möglich. Bitte später erneut versuchen.'),
    ).toBeVisible();
    // Die bisherige Wahl bleibt stehen.
    expect(screen.getByRole('option', { name: 'Adler AG' })).toBeInTheDocument();
  });
});

describe('NachtragMaske — Erfolg', () => {
  it('traegt eine Rechnung nach und fuehrt auf ihre Einzelansicht', async () => {
    const fetchMock = anlegen({ [`POST ${WEG}`]: json(201, NACHTRAG) });

    renderMaske('/rechnungen/nachtragen');
    const nutzer = await bereit();

    expect(screen.getByRole('heading', { level: 1, name: 'Rechnung nachtragen' })).toBeVisible();
    expect(screen.getByRole('link', { name: 'Rechnungen' })).toHaveAttribute('href', '/rechnungen');
    await ausfuellen(nutzer);
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    await screen.findByText('Einzelansicht');
    expect(adresse()).toBe('/rechnungen/nachgetragen/7');
    // Geld geht als Dezimaltext mit Punkt hinaus, nie als Gleitkommazahl.
    expect(fetchMock).toHaveBeenCalledWith(
      WEG,
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          firmaId: 5,
          nummer: 'RE-2026-014',
          rechnungDatum: '2026-03-12',
          netto: '1000.50',
          brutto: '1190.60',
        }),
      }),
    );
    expect(fetchMock).not.toHaveBeenCalledWith(`${WEG}/7/dokument`, expect.anything());
  });

  it('legt die gewaehlte Datei im zweiten Aufruf ab', async () => {
    const gesendet: FormData[] = [];
    const fetchMock = anlegen({
      [`POST ${WEG}`]: json(201, NACHTRAG),
      [`POST ${WEG}/7/dokument`]: formularWeg((formular) => {
        gesendet.push(formular);
      }, json(200, { ...NACHTRAG, dokument: true })),
    });
    const datei = pdf();

    renderMaske('/rechnungen/nachtragen');
    const nutzer = await bereit();
    await ausfuellen(nutzer);
    await nutzer.upload(dateifeld(), datei);

    expect(screen.getByText('rechnung.pdf')).toBeVisible();
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    await screen.findByText('Einzelansicht');
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock).toHaveBeenNthCalledWith(2, WEG, expect.objectContaining({ method: 'POST' }));
    expect(fetchMock).toHaveBeenNthCalledWith(
      3,
      `${WEG}/7/dokument`,
      expect.objectContaining({ method: 'POST' }),
    );
    expect(gesendet[0].get('datei')).toBe(datei);
  });

  it('oeffnet die Dateiwahl ueber die Taste', async () => {
    anlegen();

    renderMaske('/rechnungen/nachtragen');
    const nutzer = await bereit();
    const klick = vi.spyOn(dateifeld(), 'click');
    await nutzer.click(screen.getByRole('button', { name: 'PDF wählen' }));

    expect(klick).toHaveBeenCalled();
  });

  it('nimmt eine leere Dateiwahl hin, ohne etwas zu melden', async () => {
    anlegen();

    renderMaske('/rechnungen/nachtragen');
    const nutzer = await bereit();
    await nutzer.upload(dateifeld(), []);

    expect(screen.getByText('Keine Datei gewählt.')).toBeVisible();
  });

  it('belegt beim Aendern die Felder vor und speichert ueber denselben Weg', async () => {
    const fetchMock = aendern(NACHTRAG, { [`PUT ${WEG}/7`]: json(200, NACHTRAG) });

    renderMaske('/rechnungen/nachgetragen/7/bearbeiten');
    const nutzer = await bereit();

    expect(
      screen.getByRole('heading', { level: 1, name: 'Nachgetragene Rechnung bearbeiten' }),
    ).toBeVisible();
    expect(kundenwahl()).toHaveValue('5');
    expect(nummer()).toHaveValue('RE-2026-014');
    expect(datum()).toHaveValue('2026-03-12');
    expect(netto()).toHaveValue('1000,50');
    expect(brutto()).toHaveValue('1190,60');
    expect(screen.getByText('Optional; bisher ist kein PDF hinterlegt.')).toBeVisible();
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Abbrechen' })).toHaveAttribute(
      'href',
      '/rechnungen/nachgetragen/7',
    );

    await nutzer.clear(nummer());
    await nutzer.type(nummer(), 'RE-2026-015');
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    await screen.findByText('Einzelansicht');
    expect(adresse()).toBe('/rechnungen/nachgetragen/7');
    expect(fetchMock).toHaveBeenCalledWith(
      `${WEG}/7`,
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({
          firmaId: 5,
          nummer: 'RE-2026-015',
          rechnungDatum: '2026-03-12',
          netto: '1000.50',
          brutto: '1190.60',
        }),
      }),
    );
  });

  it('entfernt beim Aendern auf Wunsch das hinterlegte Original', async () => {
    const fetchMock = aendern(
      { ...NACHTRAG, dokument: true },
      {
        [`PUT ${WEG}/7`]: json(200, { ...NACHTRAG, dokument: true }),
        [`DELETE ${WEG}/7/dokument`]: leer(204),
      },
    );

    renderMaske('/rechnungen/nachgetragen/7/bearbeiten');
    const nutzer = await bereit();

    expect(screen.getByText('Ein PDF ist hinterlegt; eine neue Datei ersetzt es.')).toBeVisible();
    await nutzer.click(screen.getByRole('checkbox', { name: 'Hinterlegtes PDF entfernen' }));
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    await screen.findByText('Einzelansicht');
    expect(fetchMock).toHaveBeenCalledWith(
      `${WEG}/7/dokument`,
      expect.objectContaining({ method: 'DELETE' }),
    );
  });

  it('ersetzt das Original, statt es zu entfernen, wenn beides gewaehlt ist', async () => {
    const fetchMock = aendern(
      { ...NACHTRAG, dokument: true },
      {
        [`PUT ${WEG}/7`]: json(200, { ...NACHTRAG, dokument: true }),
        [`POST ${WEG}/7/dokument`]: json(200, { ...NACHTRAG, dokument: true }),
      },
    );

    renderMaske('/rechnungen/nachgetragen/7/bearbeiten');
    const nutzer = await bereit();
    await nutzer.click(screen.getByRole('checkbox', { name: 'Hinterlegtes PDF entfernen' }));
    await nutzer.upload(dateifeld(), pdf());
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    await screen.findByText('Einzelansicht');
    expect(fetchMock).toHaveBeenCalledWith(
      `${WEG}/7/dokument`,
      expect.objectContaining({ method: 'POST' }),
    );
    expect(fetchMock).not.toHaveBeenCalledWith(
      `${WEG}/7/dokument`,
      expect.objectContaining({ method: 'DELETE' }),
    );
  });
});

describe('NachtragMaske — zweiter Aufruf', () => {
  it('laesst die Rechnung angelegt und meldet das Scheitern am Feld der Datei', async () => {
    const fetchMock = anlegen({
      [`POST ${WEG}`]: json(201, NACHTRAG),
      [`POST ${WEG}/7/dokument`]: problem(500, 'kaputt'),
      [`PUT ${WEG}/7`]: json(200, NACHTRAG),
    });

    renderMaske('/rechnungen/nachtragen');
    const nutzer = await bereit();
    await ausfuellen(nutzer);
    await nutzer.upload(dateifeld(), pdf());
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    const meldung = DATEI_AUSFALL;
    expect(await screen.findByText(meldung)).toBeVisible();
    expect(dateifeld()).toHaveAccessibleDescription(meldung);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(adresse()).toBe('/rechnungen/nachtragen');
    // Die Rechnung steht: Der naechste Versuch aendert sie, statt eine zweite anzulegen.
    expect(screen.getByRole('button', { name: 'Speichern' })).toBeEnabled();
    expect(fetchMock).not.toHaveBeenCalledWith(
      `${WEG}/7`,
      expect.objectContaining({ method: 'DELETE' }),
    );

    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));
    expect(await screen.findByText(meldung)).toBeVisible();
    // Aufbau, Anlegen, Original, dann Aendern und Original — ein zweites Anlegen gibt es nicht.
    expect(fetchMock).toHaveBeenCalledTimes(5);
    expect(fetchMock).toHaveBeenNthCalledWith(
      4,
      `${WEG}/7`,
      expect.objectContaining({ method: 'PUT' }),
    );
  });

  it('nimmt die Feldmeldung des Servers zur Datei, wo es eine gibt', async () => {
    anlegen({
      [`POST ${WEG}`]: json(201, NACHTRAG),
      [`POST ${WEG}/7/dokument`]: problem(422, 'ungueltig', {
        datei: ['Die Datei ist kein PDF.'],
      }),
    });

    renderMaske('/rechnungen/nachtragen');
    const nutzer = await bereit();
    await ausfuellen(nutzer);
    await nutzer.upload(dateifeld(), pdf());
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    const meldung = 'Die Datei ist kein PDF. Die übrigen Angaben sind gespeichert.';
    expect(await screen.findByText(meldung)).toBeVisible();
    expect(dateifeld()).toHaveAccessibleDescription(meldung);
  });

  it('meldet auch ein gescheitertes Entfernen am Feld der Datei', async () => {
    aendern(
      { ...NACHTRAG, dokument: true },
      {
        [`PUT ${WEG}/7`]: json(200, { ...NACHTRAG, dokument: true }),
        [`DELETE ${WEG}/7/dokument`]: problem(500, 'kaputt'),
      },
    );

    renderMaske('/rechnungen/nachgetragen/7/bearbeiten');
    const nutzer = await bereit();
    await nutzer.click(screen.getByRole('checkbox', { name: 'Hinterlegtes PDF entfernen' }));
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(
      await screen.findByText(DATEI_AUSFALL),
    ).toBeVisible();
  });
});

describe('NachtragMaske — Vorpruefung der Datei', () => {
  it('meldet eine Datei ueber der Uploadgrenze vor dem Senden am Feld', async () => {
    const fetchMock = anlegen();

    renderMaske('/rechnungen/nachtragen');
    const nutzer = await bereit();
    await ausfuellen(nutzer);
    await nutzer.upload(dateifeld(), pdf('gross.pdf', MAX_UPLOAD_BYTE + 1));
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(dateifeld()).toHaveAccessibleDescription('Die Datei darf höchstens 25 MB groß sein.');
    // Nur der Aufbau fragte; gesendet wurde nichts.
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('meldet eine Datei, die kein PDF ist, vor dem Senden am Feld', async () => {
    const fetchMock = anlegen();

    renderMaske('/rechnungen/nachtragen');
    // Ohne `accept`-Filter: So laesst sich im Dateidialog jede Datei waehlen.
    const nutzer = await bereit(userEvent.setup({ applyAccept: false }));
    await ausfuellen(nutzer);
    await nutzer.upload(dateifeld(), new File(['<html>'], 'rechnung.html', { type: 'text/html' }));
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(dateifeld()).toHaveAccessibleDescription('Die Datei ist kein PDF.');
    // Nur der Aufbau fragte; gesendet wurde nichts.
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('meldet eine leere Datei vor dem Senden am Feld', async () => {
    const fetchMock = anlegen();

    renderMaske('/rechnungen/nachtragen');
    const nutzer = await bereit();
    await ausfuellen(nutzer);
    await nutzer.upload(dateifeld(), pdf('leer.pdf', 0));
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(dateifeld()).toHaveAccessibleDescription('Die Datei ist leer.');
    // Nur der Aufbau fragte; gesendet wurde nichts.
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});

describe('NachtragMaske — Deaktiviert', () => {
  it('sperrt die Absendetaste, solange gesendet wird', async () => {
    const spaet = angehalten(json(201, NACHTRAG));
    anlegen({ [`POST ${WEG}`]: spaet.weg });

    renderMaske('/rechnungen/nachtragen');
    const nutzer = await bereit();
    await ausfuellen(nutzer);
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }));

    expect(screen.getByRole('button', { name: 'Anlegen' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'PDF wählen' })).toBeDisabled();
    spaet.loesen();
    await screen.findByText('Einzelansicht');
  });

  it('fuehrt mit „Abbrechen" ohne Speichern zur Rechnungsliste', async () => {
    const fetchMock = anlegen();

    renderMaske('/rechnungen/nachtragen');
    const nutzer = await bereit();
    await nutzer.click(screen.getByRole('link', { name: 'Abbrechen' }));

    expect(await screen.findByText('Rechnungsliste')).toBeVisible();
    // Nur der Aufbau fragte; gesendet wurde nichts.
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
