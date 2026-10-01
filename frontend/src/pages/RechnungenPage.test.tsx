import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import RechnungenPage from './RechnungenPage';
import { KopfPfadProvider } from '../components/KopfPfad';
import { fetchNachPfad, json, problem } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';

const WEG_LISTE = 'GET /api/rechnungen';
const WEG_WAHL = 'GET /api/rechnungen/abrechenbare-angebote';
const WEG_ANLEGEN = 'POST /api/angebote/9/rechnungen';

/** Eine gestellte Rechnung, wie das Backend sie schreibt. */
const GESTELLT = {
  id: 4,
  nummer: '0001-2026',
  firmaId: 5,
  firmaName: 'Adler AG',
  rechnungDatum: '2026-10-01',
  brutto: 11424,
  zustand: 'GESTELLT',
};

/** Ein Entwurf — ohne Nummer, denn sie entsteht erst mit dem Stellen. */
const ENTWURF = {
  id: 7,
  nummer: null,
  firmaId: 6,
  firmaName: 'Biber GmbH',
  rechnungDatum: '2026-09-28',
  brutto: 2500.03,
  zustand: 'ENTWURF',
};

/** Ein abrechenbares Angebot der Wahl „Neue Rechnung". */
const WAHL = {
  angebotId: 9,
  firmaId: 5,
  firmaName: 'Adler AG',
  angebotDatum: '2026-09-24',
  offenerBetrag: 9600,
};

/** Der frische Entwurf, mit dem das Anlegen antwortet — die Seite braucht nur seine Kennung. */
const NEUER_ENTWURF = {
  id: 7,
  angebotId: 9,
  firmaId: 5,
  firmaName: 'Adler AG',
  rechnungDatum: '2026-10-01',
  leistungszeitraum: 'Oktober 2026',
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

/** Die Adresse — daran haengt, ob die Wahl auf die neue Rechnung gefuehrt hat. */
function Adresse() {
  const ort = useLocation();
  return <p data-testid="adresse">{ort.pathname}</p>;
}

function renderSeite() {
  return renderMitTheme(
    <MemoryRouter initialEntries={['/rechnungen']}>
      <KopfPfadProvider>
        <Routes>
          <Route path="/rechnungen" element={<RechnungenPage />} />
          <Route path="/rechnungen/:rechnungId" element={<p>Die Rechnung</p>} />
        </Routes>
        <Adresse />
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

/**
 * Die Kupfertasten der Ansicht.
 *
 * Sie tragen den Kupferverlauf, die weichen eine Flaeche (CLAUDE-design.md, „Tasten") — die Rolle
 * unterscheidet beide nicht. Gezaehlt wird darum am Verlauf, wie in `AngebotPage.test.tsx`.
 */
function kupfertasten(): readonly HTMLElement[] {
  return [...screen.queryAllByRole('link'), ...screen.queryAllByRole('button')].filter((taste) =>
    getComputedStyle(taste).background.includes('linear-gradient'),
  );
}

/** Die Wahl des Monats im Dialog — ihr zugaenglicher Name ist ihre Beschriftung. */
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

describe('RechnungenPage — die Liste (#160, Kriterien 1, 2, 24)', () => {
  it('zeigt waehrend des Ladens einen Hinweis und danach „Rechnungen" als die eine Ueberschrift', async () => {
    fetchNachPfad({ [WEG_LISTE]: json(200, { rechnungen: [GESTELLT] }) });

    renderSeite();

    expect(screen.getByText('Rechnungen werden geladen …')).toBeInTheDocument();
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Rechnungen' }),
    ).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
  });

  it('zeigt die Zeilen in der Reihenfolge der Antwort, neueste zuerst', async () => {
    fetchNachPfad({ [WEG_LISTE]: json(200, { rechnungen: [GESTELLT, ENTWURF] }) });

    renderSeite();

    const tafel = within(await screen.findByRole('table', { name: 'Rechnungen' }));
    expect(tafel.getAllByRole('columnheader').map((kopf) => kopf.textContent)).toEqual([
      'Nummer',
      'Firma',
      'Rechnungsdatum',
      'Betrag',
      'Zustand',
      'Dokument',
    ]);
    const zeilen = tafel.getAllByRole('row').slice(1);
    expect(zeilen.map((zeile) => within(zeile).getAllByRole('cell')[0].textContent)).toEqual([
      '0001-2026',
      'Entwurf',
    ]);
  });

  it('traegt an der gestellten Rechnung Nummer, Firma, Datum, Betrag, Zustand und den Download', async () => {
    fetchNachPfad({ [WEG_LISTE]: json(200, { rechnungen: [GESTELLT] }) });

    renderSeite();

    const zeile = within((await screen.findAllByRole('row'))[1]);
    expect(zeile.getByRole('link', { name: '0001-2026' })).toHaveAttribute(
      'href',
      '/rechnungen/4',
    );
    expect(zeile.getByText('Adler AG')).toBeInTheDocument();
    expect(zeile.getByText('01.10.2026')).toBeInTheDocument();
    expect(zeile.getByText('11.424,00 €')).toBeInTheDocument();
    expect(zeile.getByText('Gestellt')).toBeInTheDocument();
    const download = zeile.getByRole('link', { name: 'Herunterladen' });
    expect(download).toHaveAttribute('href', '/api/rechnungen/4/dokument');
    expect(download).toHaveAttribute('download');
  });

  it('stellt den Entwurf ohne Nummer und ohne „Herunterladen" dar', async () => {
    fetchNachPfad({ [WEG_LISTE]: json(200, { rechnungen: [ENTWURF] }) });

    renderSeite();

    const zeile = within((await screen.findAllByRole('row'))[1]);
    expect(zeile.getByRole('link', { name: 'Entwurf' })).toHaveAttribute('href', '/rechnungen/7');
    expect(zeile.queryByRole('link', { name: 'Herunterladen' })).not.toBeInTheDocument();
    expect(zeile.getByText('2.500,03 €')).toBeInTheDocument();
  });

  it('sagt im leeren Zustand „Noch keine Rechnung." und laedt dazu ein', async () => {
    fetchNachPfad({ [WEG_LISTE]: json(200, { rechnungen: [] }) });

    renderSeite();

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Noch keine Rechnung. Schreiben Sie die erste über „Neue Rechnung".',
    );
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('meldet den Ausfall des Weges', async () => {
    fetchNachPfad({ [WEG_LISTE]: problem(500, 'kaputt') });

    renderSeite();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Die Rechnungen sind gerade nicht zu erreichen.',
    );
  });

  it('traegt genau eine Kupfertaste — „Neue Rechnung"', async () => {
    fetchNachPfad({ [WEG_LISTE]: json(200, { rechnungen: [GESTELLT] }) });

    renderSeite();

    await screen.findByRole('table', { name: 'Rechnungen' });
    // Genau eine Kupfertaste je Ansicht (CLAUDE-design.md, Leitgedanke 2). Die Rolle unterscheidet
    // sie nicht von der weichen: „Herunterladen" ist ein Weg, „Neue Rechnung" eine Handlung.
    expect(kupfertasten().map((taste) => taste.textContent)).toEqual(['Neue Rechnung']);
  });
});

describe('RechnungenPage — die Wahl „Neue Rechnung" (#160, Kriterium 2)', () => {
  it('zeigt die abrechenbaren Angebote mit Firma, Angebotsdatum und offenem Betrag', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [WEG_LISTE]: json(200, { rechnungen: [] }),
      [WEG_WAHL]: json(200, { angebote: [WAHL] }),
    });

    renderSeite();
    await nutzer.click(await screen.findByRole('button', { name: 'Neue Rechnung' }));

    const wahl = within(await screen.findByRole('dialog'));
    const eintrag = wahl.getByRole('button', { name: /Adler AG/ });
    expect(eintrag).toHaveTextContent('24.09.2026');
    expect(eintrag).toHaveTextContent('9.600,00 €');
  });

  it('traegt die Monatswahl ueber der Liste, vorbelegt mit dem laufenden Monat', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [WEG_LISTE]: json(200, { rechnungen: [] }),
      [WEG_WAHL]: json(200, { angebote: [WAHL] }),
    });

    renderSeite();
    await nutzer.click(await screen.findByRole('button', { name: 'Neue Rechnung' }));
    await screen.findByRole('dialog');

    expect(monatswahl()).toHaveValue('2026-11');
    // Ueber der Liste: Erst der Monat, dann das Angebot, mit dem die Wahl zuschlaegt.
    expect(monatswahl().compareDocumentPosition(screen.getByRole('button', { name: /Adler AG/ })))
      .toBe(Node.DOCUMENT_POSITION_FOLLOWING);
  });

  it('legt mit der Wahl den Entwurf fuer den laufenden Monat an und fuehrt auf die Rechnung', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      [WEG_LISTE]: json(200, { rechnungen: [] }),
      [WEG_WAHL]: json(200, { angebote: [WAHL] }),
      [WEG_ANLEGEN]: json(201, NEUER_ENTWURF),
    });

    renderSeite();
    await nutzer.click(await screen.findByRole('button', { name: 'Neue Rechnung' }));
    await nutzer.click(await screen.findByRole('button', { name: /Adler AG/ }));

    expect(await screen.findByText('Die Rechnung')).toBeInTheDocument();
    expect(screen.getByTestId('adresse')).toHaveTextContent('/rechnungen/7');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/9/rechnungen',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ monat: '2026-11' }) }),
    );
  });

  it('legt mit dem gewaehlten Vormonat an', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      [WEG_LISTE]: json(200, { rechnungen: [] }),
      [WEG_WAHL]: json(200, { angebote: [WAHL] }),
      [WEG_ANLEGEN]: json(201, NEUER_ENTWURF),
    });

    renderSeite();
    await nutzer.click(await screen.findByRole('button', { name: 'Neue Rechnung' }));
    await screen.findByRole('dialog');
    await nutzer.selectOptions(monatswahl(), '2026-10');
    await nutzer.click(screen.getByRole('button', { name: /Adler AG/ }));

    expect(await screen.findByText('Die Rechnung')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/9/rechnungen',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ monat: '2026-10' }) }),
    );
  });

  it('schickt bei „ohne Arbeitszeit" keinen Rumpf', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      [WEG_LISTE]: json(200, { rechnungen: [] }),
      [WEG_WAHL]: json(200, { angebote: [WAHL] }),
      [WEG_ANLEGEN]: json(201, NEUER_ENTWURF),
    });

    renderSeite();
    await nutzer.click(await screen.findByRole('button', { name: 'Neue Rechnung' }));
    await screen.findByRole('dialog');
    await nutzer.selectOptions(monatswahl(), '');
    await nutzer.click(screen.getByRole('button', { name: /Adler AG/ }));

    expect(await screen.findByText('Die Rechnung')).toBeInTheDocument();
    const anlegen = fetchMock.mock.calls.find(([weg]) => weg === '/api/angebote/9/rechnungen');
    expect(anlegen?.[1]).not.toHaveProperty('body');
  });

  it('sagt mit einem Satz, wenn es kein abrechenbares Angebot gibt', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [WEG_LISTE]: json(200, { rechnungen: [] }),
      [WEG_WAHL]: json(200, { angebote: [] }),
    });

    renderSeite();
    await nutzer.click(await screen.findByRole('button', { name: 'Neue Rechnung' }));

    const wahl = within(await screen.findByRole('dialog'));
    expect(
      await wahl.findByText(
        'Kein Angebot ist abrechenbar. Ein Angebot muss bestellt sein und noch etwas offen haben.',
      ),
    ).toBeInTheDocument();
  });

  it('zeigt die Meldung des Servers, wenn das Anlegen mit 409 abweist', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [WEG_LISTE]: json(200, { rechnungen: [] }),
      [WEG_WAHL]: json(200, { angebote: [WAHL] }),
      [WEG_ANLEGEN]: problem(409, 'An diesem Angebot ist nichts mehr offen.'),
    });

    renderSeite();
    await nutzer.click(await screen.findByRole('button', { name: 'Neue Rechnung' }));
    await nutzer.click(await screen.findByRole('button', { name: /Adler AG/ }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'An diesem Angebot ist nichts mehr offen.',
    );
    expect(screen.getByTestId('adresse')).toHaveTextContent('/rechnungen');
  });

  it('sperrt die Tasten, solange das Anlegen laeuft', async () => {
    const nutzer = userEvent.setup();
    // Ohne Platzhalter-Funktion: Ein nie gerufener Vorbelegungswert waere ungetesteter Code.
    let liefere!: (antwort: Response) => void;
    const spaeter = new Promise<Response>((aufloesen) => {
      liefere = aufloesen;
    });
    fetchNachPfad({
      [WEG_LISTE]: json(200, { rechnungen: [] }),
      [WEG_WAHL]: json(200, { angebote: [WAHL] }),
      [WEG_ANLEGEN]: () => spaeter,
    });

    renderSeite();
    await nutzer.click(await screen.findByRole('button', { name: 'Neue Rechnung' }));
    await nutzer.click(await screen.findByRole('button', { name: /Adler AG/ }));

    // Gesperrt ist, was in der offenen Wahl erreichbar ist: der Eintrag und „Abbrechen".
    // „Neue Rechnung" liegt hinter dem Modal und ist fuer Hilfsmittel ohnehin verdeckt.
    expect(screen.getByRole('button', { name: /Adler AG/ })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Abbrechen' })).toBeDisabled();
    expect(monatswahl()).toBeDisabled();
    liefere(json(201, NEUER_ENTWURF)());
    expect(await screen.findByText('Die Rechnung')).toBeInTheDocument();
  });

  it('nennt die allgemeine Meldung, wenn das Anlegen ohne Antwort des Servers scheitert', async () => {
    // Ein Abbruch des Netzes ist kein `ApiError` und traegt keine Meldung des Servers.
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [WEG_LISTE]: json(200, { rechnungen: [] }),
      [WEG_WAHL]: json(200, { angebote: [WAHL] }),
      [WEG_ANLEGEN]: () => Promise.reject(new TypeError('Netz weg')),
    });

    renderSeite();
    await nutzer.click(await screen.findByRole('button', { name: 'Neue Rechnung' }));
    await nutzer.click(await screen.findByRole('button', { name: /Adler AG/ }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Die Anfrage konnte nicht ausgeführt werden.',
    );
  });

  it('meldet den Ausfall der Wahl, ohne die Liste zu verlieren', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [WEG_LISTE]: json(200, { rechnungen: [GESTELLT] }),
      [WEG_WAHL]: problem(500, 'kaputt'),
    });

    renderSeite();
    await nutzer.click(await screen.findByRole('button', { name: 'Neue Rechnung' }));

    const wahl = within(await screen.findByRole('dialog'));
    expect(await wahl.findByRole('alert')).toHaveTextContent(
      'Die abrechenbaren Angebote sind gerade nicht zu erreichen.',
    );
    // Die Liste dahinter steht weiter; sichtbar wird sie wieder, sobald die Wahl zu ist.
    await nutzer.click(screen.getByRole('button', { name: 'Abbrechen' }));
    expect(screen.getByRole('table', { name: 'Rechnungen' })).toBeInTheDocument();
  });

  it('schliesst die Wahl mit „Abbrechen", ohne etwas anzulegen', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [WEG_LISTE]: json(200, { rechnungen: [] }),
      [WEG_WAHL]: json(200, { angebote: [WAHL] }),
    });

    renderSeite();
    await nutzer.click(await screen.findByRole('button', { name: 'Neue Rechnung' }));
    await nutzer.click(await screen.findByRole('button', { name: 'Abbrechen' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByTestId('adresse')).toHaveTextContent('/rechnungen');
  });
});
