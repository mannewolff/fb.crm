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
const WEG_SEPTEMBER = '/api/startseite?zeitraum=2026-09';
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
  zeitraum: { art: 'MONAT', wert: '2026-10' },
  waehlbar: { jahre: ['2026', '2025'], monate: MONATE },
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
    erfasstImZeitraum: 600,
    angebote: [
      { angebotId: 11, firmaName: 'IT Bildungshaus', angebotDatum: '2026-09-24', netto: 1800 },
    ],
  },
  abgerechnet: { netto: 9600, brutto: 11424, anzahl: 3, monate: [] },
  interneStundenImZeitraum: 12.5,
};

/** Derselbe Monat, aber nichts darin — alle drei Kennzahlen stehen auf null. */
const LEERER_STAND = {
  zeitraum: { art: 'MONAT', wert: '2026-10' },
  waehlbar: { jahre: ['2026', '2025'], monate: MONATE },
  inArbeit: [],
  nichtAbgerechnet: { netto: 0, erfasstImZeitraum: 0, angebote: [] },
  abgerechnet: { netto: 0, brutto: 0, anzahl: 0, monate: [] },
  interneStundenImZeitraum: 0,
};

/**
 * Ein Stand, dessen Monate keine Browser-Uhr liefern wuerde — weit in der Zukunft.
 *
 * Er traegt den Nachweis, dass die Wahl die Monate der Antwort anbietet und keine hier
 * gerechneten (Entscheidung am Issue, Plan #208 E17).
 */
const FERNER_STAND = {
  ...LEERER_STAND,
  zeitraum: { art: 'MONAT', wert: '2031-03' },
  waehlbar: { jahre: ['2031'], monate: ['2031-03', '2031-02', '2031-01'] },
};

/**
 * Dasselbe Geschaeft bei Jahreswahl (#273, Kriterien 4, 5 und 7).
 *
 * „Angebote in Arbeit" und die grosse Zahl von „Noch nicht abgerechnet" stehen wie in
 * {@link STAND}: Sie haben keinen Zeitraum (Kriterium 6). Die Monatszeilen ergeben zusammen die
 * Kennzahl, wie der Server sie schreibt.
 */
const JAHRES_STAND = {
  ...STAND,
  zeitraum: { art: 'JAHR', wert: '2026' },
  nichtAbgerechnet: { ...STAND.nichtAbgerechnet, erfasstImZeitraum: 7200 },
  abgerechnet: {
    netto: 9600,
    brutto: 11424,
    anzahl: 3,
    monate: [
      { monat: '2026-03', anzahl: 1, netto: 3600, brutto: 4284 },
      { monat: '2026-09', anzahl: 2, netto: 6000, brutto: 7140 },
    ],
  },
  interneStundenImZeitraum: 80,
};

/** Ein Jahr ganz ohne gestellte Rechnung. */
const LEERES_JAHR = {
  ...LEERER_STAND,
  zeitraum: { art: 'JAHR', wert: '2026' },
};

const JAHR_2026 = 'GET /api/startseite?zeitraum=2026';

/** Die Werte der Eintraege in einer Gruppe der Wahl, in ihrer Reihenfolge. */
function werteIn(gruppe: HTMLElement): string[] {
  return within(gruppe)
    .getAllByRole('option')
    .map((option) => (option as HTMLOptionElement).value);
}

/** Die Adresse — daran haengt, was der Monatswechsel in `?zeitraum=` geschrieben hat. */
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

    const wahl = await screen.findByRole('combobox', { name: 'Zeitraum' });
    expect(werteIn(within(wahl).getByRole('group', { name: 'Monate' }))).toEqual(
      FERNER_STAND.waehlbar.monate,
    );
    expect(wahl).toHaveValue('2031-03');
    expect(screen.getByRole('option', { name: 'März 2031' })).toBeInTheDocument();
  });

  it('schreibt den gewaehlten Monat in die Adresse und laedt ihn neu', async () => {
    const nutzer = userEvent.setup();
    mitRouten({
      [OHNE_MONAT]: json(200, STAND),
      [SEPTEMBER]: json(200, { ...LEERER_STAND, zeitraum: { art: 'MONAT', wert: '2026-09' } }),
    });

    renderSeite();
    await nutzer.selectOptions(await screen.findByRole('combobox', { name: 'Zeitraum' }), '2026-09');

    expect(screen.getByTestId('adresse')).toHaveTextContent('/?zeitraum=2026-09');
    expect(await screen.findByText('Keine Rechnung in diesem Monat.')).toBeInTheDocument();
  });

  it('gibt den Monat aus der Adresse in den Abruf', async () => {
    const aufruf = mitRouten({ [SEPTEMBER]: json(200, { ...STAND, zeitraum: { art: 'MONAT', wert: '2026-09' } }) });

    renderSeite('/?zeitraum=2026-09');

    await screen.findByRole('table', { name: 'Angebote in Arbeit' });
    expect(aufruf.mock.calls.map(([ziel]) => ziel)).toEqual([WEG_SEPTEMBER]);
  });

  it('fragt ohne Parameter, wenn in der Adresse kein Monat steht', async () => {
    const aufruf = mitRouten({ [OHNE_MONAT]: json(200, STAND) });

    renderSeite('/?zeitraum=uebermorgen');

    await screen.findByRole('table', { name: 'Angebote in Arbeit' });
    expect(aufruf.mock.calls.map(([ziel]) => ziel)).toEqual(['/api/startseite']);
  });

  it('meldet den Ausfall, wenn der Stand nicht zu erreichen ist', async () => {
    mitRouten({ [OHNE_MONAT]: leer(500) });

    renderSeite();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Der Geschäftsstand ist gerade nicht zu erreichen.',
    );
    expect(screen.queryByRole('combobox', { name: 'Zeitraum' })).not.toBeInTheDocument();
  });

  it('zeigt die internen Stunden des Monats getrennt von allen Betraegen', async () => {
    mitRouten({ [OHNE_MONAT]: json(200, STAND) });

    renderSeite();

    const zeile = await screen.findByText('Interne Stunden im gewählten Monat: 12,50 Std.');
    const kacheln = await screen.findAllByTestId('kennzahlkachel');
    expect(kacheln.some((eine) => eine.contains(zeile))).toBe(false);
  });

  it('zeigt die internen Stunden auch bei null — als 0,00 Std.', async () => {
    mitRouten({ [OHNE_MONAT]: json(200, LEERER_STAND) });

    renderSeite();

    expect(
      await screen.findByText('Interne Stunden im gewählten Monat: 0,00 Std.'),
    ).toBeInTheDocument();
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

describe('StartseitePage: Zeitraumwahl (Issue #281; #273 Kriterien 1, 2, 5 bis 9)', () => {
  it('ordnet die Wahl in die Gruppen „Jahre" und „Monate"', async () => {
    mitRouten({ [OHNE_MONAT]: json(200, STAND) });

    renderSeite();

    const wahl = await screen.findByRole('combobox', { name: 'Zeitraum' });
    expect(werteIn(within(wahl).getByRole('group', { name: 'Jahre' }))).toEqual(['2026', '2025']);
    expect(werteIn(within(wahl).getByRole('group', { name: 'Monate' }))).toEqual(MONATE);
    expect(within(wahl).getByRole('option', { name: '2025' })).toBeInTheDocument();
    expect(within(wahl).getByRole('option', { name: 'Oktober 2026' })).toBeInTheDocument();
  });

  it('schreibt das gewaehlte Jahr in die Adresse und laedt es', async () => {
    const nutzer = userEvent.setup();
    mitRouten({ [OHNE_MONAT]: json(200, STAND), [JAHR_2026]: json(200, JAHRES_STAND) });

    renderSeite();
    await nutzer.selectOptions(await screen.findByRole('combobox', { name: 'Zeitraum' }), '2026');

    expect(screen.getByTestId('adresse')).toHaveTextContent('/?zeitraum=2026');
    expect(await screen.findByRole('table', { name: 'Abgerechnet' })).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Zeitraum' })).toHaveValue('2026');
  });

  it('nennt bei Jahreswahl das Jahr in der Kachel und bei den internen Stunden', async () => {
    mitRouten({ [JAHR_2026]: json(200, JAHRES_STAND) });

    renderSeite('/?zeitraum=2026');

    expect(await kachel(OFFEN)).toHaveTextContent('Im Jahr erfasst: 7.200,00 €');
    expect(
      screen.getByText('Interne Stunden im gewählten Jahr: 80,00 Std.'),
    ).toBeInTheDocument();
    expect(screen.queryByText(/Im Monat erfasst/u)).not.toBeInTheDocument();
  });

  it('nennt bei Monatswahl den Monat in der Kachel und bei den internen Stunden', async () => {
    mitRouten({ [OHNE_MONAT]: json(200, STAND) });

    renderSeite();

    expect(await kachel(OFFEN)).toHaveTextContent('Im Monat erfasst: 600,00 €');
    expect(
      screen.getByText('Interne Stunden im gewählten Monat: 12,50 Std.'),
    ).toBeInTheDocument();
    expect(screen.queryByText(/Im Jahr erfasst/u)).not.toBeInTheDocument();
  });

  it('zeigt bei Jahreswahl je Monat eine Zeile und darunter die Summe des Jahres', async () => {
    mitRouten({ [JAHR_2026]: json(200, JAHRES_STAND) });

    renderSeite('/?zeitraum=2026');

    const tafel = await screen.findByRole('table', { name: 'Abgerechnet' });
    expect(
      within(tafel)
        .getAllByRole('columnheader')
        .map((kopf) => kopf.textContent),
    ).toEqual(['Monat', 'Rechnungen', 'Netto', 'Brutto']);
    const zeilen = within(tafel).getAllByRole('row').slice(1);
    expect(zeilen.map((zeile) => zeile.textContent)).toEqual([
      'März 202613.600,00 €4.284,00 €',
      'September 202626.000,00 €7.140,00 €',
      'Summe 202639.600,00 €11.424,00 €',
    ]);
    expect(within(tafel).getByRole('rowheader', { name: 'Summe 2026' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Zu den Rechnungen' })).toHaveAttribute(
      'href',
      '/rechnungen',
    );
  });

  it('fuehrt vom Monatsnamen auf genau diesen Monat', async () => {
    const nutzer = userEvent.setup();
    mitRouten({
      [JAHR_2026]: json(200, JAHRES_STAND),
      [SEPTEMBER]: json(200, { ...STAND, zeitraum: { art: 'MONAT', wert: '2026-09' } }),
    });

    renderSeite('/?zeitraum=2026');

    const tafel = await screen.findByRole('table', { name: 'Abgerechnet' });
    const weg = within(tafel).getByRole('link', { name: 'September 2026' });
    expect(weg).toHaveAttribute('href', '/?zeitraum=2026-09');
    await nutzer.click(weg);

    expect(screen.getByTestId('adresse')).toHaveTextContent('/?zeitraum=2026-09');
    expect(await screen.findByRole('combobox', { name: 'Zeitraum' })).toHaveValue('2026-09');
  });

  it('sagt im Jahr ohne Rechnung „Keine Rechnung in diesem Jahr."', async () => {
    mitRouten({ [JAHR_2026]: json(200, LEERES_JAHR) });

    renderSeite('/?zeitraum=2026');

    expect(await screen.findByText('Keine Rechnung in diesem Jahr.')).toBeInTheDocument();
    expect(screen.queryByText('Keine Rechnung in diesem Monat.')).not.toBeInTheDocument();
    expect(screen.queryByRole('table', { name: 'Abgerechnet' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Zu den Rechnungen' })).toBeInTheDocument();
  });

  it('laesst die Karte bei Monatswahl, wie sie ist — ohne Tafel und mit ihrem Satz', async () => {
    mitRouten({ [OHNE_MONAT]: json(200, LEERER_STAND) });

    renderSeite();

    expect(await screen.findByText('Keine Rechnung in diesem Monat.')).toBeInTheDocument();
    expect(screen.queryByText('Keine Rechnung in diesem Jahr.')).not.toBeInTheDocument();
    expect(screen.queryByRole('table', { name: 'Abgerechnet' })).not.toBeInTheDocument();
  });

  it('zeigt bei Monatswahl mit Rechnungen keine Monatsliste', async () => {
    mitRouten({ [OHNE_MONAT]: json(200, STAND) });

    renderSeite();

    await screen.findByRole('table', { name: 'Angebote in Arbeit' });
    expect(screen.queryByRole('table', { name: 'Abgerechnet' })).not.toBeInTheDocument();
    expect(screen.queryByText(/Keine Rechnung/u)).not.toBeInTheDocument();
  });

  it('fragt ohne Parameter, wenn die Adresse keinen traegt', async () => {
    const aufruf = mitRouten({ [OHNE_MONAT]: json(200, STAND) });

    renderSeite('/');

    await screen.findByRole('table', { name: 'Angebote in Arbeit' });
    expect(aufruf.mock.calls.map(([ziel]) => ziel)).toEqual(['/api/startseite']);
  });

  it('laesst „Angebote in Arbeit" und den offenen Stand beim Wechsel aufs Jahr stehen', async () => {
    const nutzer = userEvent.setup();
    mitRouten({ [OHNE_MONAT]: json(200, STAND), [JAHR_2026]: json(200, JAHRES_STAND) });

    renderSeite();
    const imMonat = {
      inArbeit: (await kachel(IN_ARBEIT)).textContent,
      offen: within(await kachel(OFFEN)).getByText('1.800,00 €').textContent,
      tafel: within(screen.getByRole('table', { name: 'Angebote in Arbeit' })).getAllByRole('row')
        .length,
    };
    await nutzer.selectOptions(screen.getByRole('combobox', { name: 'Zeitraum' }), '2026');
    await screen.findByRole('table', { name: 'Abgerechnet' });

    expect((await kachel(IN_ARBEIT)).textContent).toBe(imMonat.inArbeit);
    expect(within(await kachel(OFFEN)).getByText('1.800,00 €').textContent).toBe(imMonat.offen);
    expect(
      within(screen.getByRole('table', { name: 'Angebote in Arbeit' })).getAllByRole('row').length,
    ).toBe(imMonat.tafel);
  });
});
