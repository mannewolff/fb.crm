import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import StartseitePage from './StartseitePage';
import { KopfPfadProvider } from '../components/KopfPfad';
import { fetchNachPfad, json, leer } from '../test/fetchNachPfad';
import type { Routen } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';

const OHNE_MONAT = 'GET /api/startseite';
const WEG_SEPTEMBER = '/api/startseite?monat=2026-09';
const SEPTEMBER = `GET ${WEG_SEPTEMBER}`;

/** Die zwoelf waehlbaren Monate, wie der Server sie schickt — neuester zuerst. */
const MONATE: readonly string[] = [
  '2026-10',
  '2026-09',
  '2026-08',
  '2026-07',
  '2026-06',
  '2026-05',
  '2026-04',
  '2026-03',
  '2026-02',
  '2026-01',
  '2025-12',
  '2025-11',
];

/** Ein Stand mit allen drei Kennzahlen gefuellt, wie das Backend ihn schreibt. */
const STAND = {
  monat: '2026-10',
  monate: MONATE,
  inArbeit: [
    {
      angebotId: 11,
      firmaName: 'IT Bildungshaus',
      angebotDatum: '2026-09-24',
      status: 'BESTELLT',
    },
    { angebotId: 12, firmaName: 'Adler AG', angebotDatum: '2026-09-28', status: 'ERLEDIGT' },
  ],
  nichtAbgerechnet: {
    netto: 1800,
    erfasstImMonat: 600,
    angebote: [
      { angebotId: 11, firmaName: 'IT Bildungshaus', angebotDatum: '2026-09-24', netto: 1800 },
    ],
  },
  abgerechnet: { netto: 9600, brutto: 11424, anzahl: 3 },
};

/** Derselbe Monat, aber nichts darin — alle drei Kennzahlen stehen auf null. */
const LEERER_STAND = {
  monat: '2026-10',
  monate: MONATE,
  inArbeit: [],
  nichtAbgerechnet: { netto: 0, erfasstImMonat: 0, angebote: [] },
  abgerechnet: { netto: 0, brutto: 0, anzahl: 0 },
};

/**
 * Ein Stand, dessen Monate keine Browser-Uhr liefern wuerde — weit in der Zukunft.
 *
 * Er traegt den Nachweis, dass die Wahl die Monate der Antwort anbietet und keine hier
 * gerechneten (Entscheidung am Issue, Plan #208 E17).
 */
const FERNER_STAND = {
  ...LEERER_STAND,
  monat: '2031-03',
  monate: ['2031-03', '2031-02', '2031-01'],
};

/** Die Adresse — daran haengt, was der Monatswechsel in `?monat=` geschrieben hat. */
function Adresse() {
  const ort = useLocation();
  return <p data-testid="adresse">{`${ort.pathname}${ort.search}`}</p>;
}

function renderSeite(adresse = '/') {
  return renderMitTheme(
    <MemoryRouter initialEntries={[adresse]}>
      <KopfPfadProvider>
        <StartseitePage />
        <Adresse />
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

function mitRouten(routen: Routen) {
  return fetchNachPfad(routen);
}

/** Die Stellen der drei Kacheln im Raster, in der Reihenfolge der Kennzahlen (#206, 4 bis 7). */
const IN_ARBEIT = 0;
const OFFEN = 1;
const ABGERECHNET = 2;

/**
 * Die Kachel an ihrer Stelle im Raster.
 *
 * Nach Stelle und nicht nach Beschriftung gesucht: Die Reihenfolge ist Teil der Zusage, und eine
 * Suche braeuchte einen Zweig fuer „nicht gefunden", den kein Testfall geht.
 */
async function kachel(stelle: number): Promise<HTMLElement> {
  return (await screen.findAllByTestId('kennzahlkachel'))[stelle];
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('StartseitePage (Issue #216; #206 Kriterien 1, 3 bis 8)', () => {
  it('nennt sich „Start" und zeigt den Ladehinweis, solange nichts da ist', () => {
    mitRouten({ [OHNE_MONAT]: json(200, STAND) });

    renderSeite();

    expect(screen.getByRole('heading', { level: 1, name: 'Start' })).toBeInTheDocument();
    expect(screen.getByText('Der Geschäftsstand wird geladen …')).toBeInTheDocument();
  });

  it('zeigt die drei Kennzahlen als Kacheln mit ihren Zahlen', async () => {
    mitRouten({ [OHNE_MONAT]: json(200, STAND) });

    renderSeite();

    expect(await kachel(IN_ARBEIT)).toHaveTextContent('Angebote in Arbeit2');
    const offen = await kachel(OFFEN);
    expect(offen).toHaveTextContent('Noch nicht abgerechnet');
    expect(offen).toHaveTextContent('1.800,00 €');
    expect(offen).toHaveTextContent('Im Monat erfasst: 600,00 €');
    const abgerechnet = await kachel(ABGERECHNET);
    expect(abgerechnet).toHaveTextContent('Abgerechnet');
    expect(abgerechnet).toHaveTextContent('9.600,00 €');
    expect(abgerechnet).toHaveTextContent('11.424,00 € brutto');
  });

  it('zeigt die Angebote in Arbeit mit Firma, Datum und Status', async () => {
    mitRouten({ [OHNE_MONAT]: json(200, STAND) });

    renderSeite();

    const tafel = await screen.findByRole('table', { name: 'Angebote in Arbeit' });
    expect(within(tafel).getByText('IT Bildungshaus')).toBeInTheDocument();
    expect(within(tafel).getByText('Bestellt')).toBeInTheDocument();
    expect(within(tafel).getByText('Erledigt')).toBeInTheDocument();
    expect(within(tafel).getByRole('link', { name: '24.09.2026' })).toHaveAttribute(
      'href',
      '/angebote/11',
    );
  });

  it('zeigt die nicht abgerechneten Angebote mit ihrem Anteil und fuehrt zum Angebot', async () => {
    mitRouten({ [OHNE_MONAT]: json(200, STAND) });

    renderSeite();

    const tafel = await screen.findByRole('table', { name: 'Noch nicht abgerechnet' });
    expect(within(tafel).getByText('1.800,00 €')).toBeInTheDocument();
    expect(within(tafel).getByRole('link', { name: '24.09.2026' })).toHaveAttribute(
      'href',
      '/angebote/11',
    );
  });

  it('traegt bei „Abgerechnet" eine weiche Taste auf die Rechnungen', async () => {
    mitRouten({ [OHNE_MONAT]: json(200, STAND) });

    renderSeite();

    expect(await screen.findByRole('link', { name: 'Zu den Rechnungen' })).toHaveAttribute(
      'href',
      '/rechnungen',
    );
  });

  it('bietet genau die Monate aus dem Feld „monate" an, nicht hier gerechnete', async () => {
    mitRouten({ [OHNE_MONAT]: json(200, FERNER_STAND) });

    renderSeite();

    const wahl = await screen.findByRole('combobox', { name: 'Monat' });
    expect(
      within(wahl)
        .getAllByRole('option')
        .map((option) => (option as HTMLOptionElement).value),
    ).toEqual(FERNER_STAND.monate);
    expect(wahl).toHaveValue('2031-03');
    expect(screen.getByRole('option', { name: 'März 2031' })).toBeInTheDocument();
  });

  it('schreibt den gewaehlten Monat in die Adresse und laedt ihn neu', async () => {
    const nutzer = userEvent.setup();
    mitRouten({
      [OHNE_MONAT]: json(200, STAND),
      [SEPTEMBER]: json(200, { ...LEERER_STAND, monat: '2026-09' }),
    });

    renderSeite();
    await nutzer.selectOptions(await screen.findByRole('combobox', { name: 'Monat' }), '2026-09');

    expect(screen.getByTestId('adresse')).toHaveTextContent('/?monat=2026-09');
    expect(await screen.findByText('Keine Rechnung in diesem Monat.')).toBeInTheDocument();
  });

  it('gibt den Monat aus der Adresse in den Abruf', async () => {
    const aufruf = mitRouten({ [SEPTEMBER]: json(200, { ...STAND, monat: '2026-09' }) });

    renderSeite('/?monat=2026-09');

    await screen.findByRole('table', { name: 'Angebote in Arbeit' });
    expect(aufruf.mock.calls.map(([ziel]) => ziel)).toEqual([WEG_SEPTEMBER]);
  });

  it('fragt ohne Parameter, wenn in der Adresse kein Monat steht', async () => {
    const aufruf = mitRouten({ [OHNE_MONAT]: json(200, STAND) });

    renderSeite('/?monat=uebermorgen');

    await screen.findByRole('table', { name: 'Angebote in Arbeit' });
    expect(aufruf.mock.calls.map(([ziel]) => ziel)).toEqual(['/api/startseite']);
  });

  it('meldet den Ausfall, wenn der Stand nicht zu erreichen ist', async () => {
    mitRouten({ [OHNE_MONAT]: leer(500) });

    renderSeite();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Der Geschäftsstand ist gerade nicht zu erreichen.',
    );
    expect(screen.queryByRole('combobox', { name: 'Monat' })).not.toBeInTheDocument();
  });

  it('zeigt im leeren Monat Nullen in den Kacheln und je Liste einen Satz', async () => {
    mitRouten({ [OHNE_MONAT]: json(200, LEERER_STAND) });

    renderSeite();

    expect(await kachel(IN_ARBEIT)).toHaveTextContent('Angebote in Arbeit0');
    expect(await kachel(OFFEN)).toHaveTextContent('0,00 €');
    expect(await kachel(ABGERECHNET)).toHaveTextContent('0,00 €');
    expect(screen.getByText('Kein Angebot ist gerade in Arbeit.')).toBeInTheDocument();
    expect(
      screen.getByText('Nichts offen — alle erfasste Zeit ist abgerechnet.'),
    ).toBeInTheDocument();
    expect(screen.getByText('Keine Rechnung in diesem Monat.')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
});
