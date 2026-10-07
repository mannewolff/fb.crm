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
  abgerechnet: {
    netto: 9600,
    brutto: 11424,
    anzahl: 3,
    offenNetto: 360,
    offenAnzahl: 1,
    monate: [],
  },
  interneStundenImZeitraum: 12.5,
};

/** Derselbe Monat, aber nichts darin — alle drei Kennzahlen stehen auf null. */
const LEERER_STAND = {
  zeitraum: { art: 'MONAT', wert: '2026-10' },
  waehlbar: { jahre: ['2026', '2025'], monate: MONATE },
  inArbeit: [],
  nichtAbgerechnet: { netto: 0, erfasstImZeitraum: 0, angebote: [] },
  abgerechnet: { netto: 0, brutto: 0, anzahl: 0, offenNetto: 0, offenAnzahl: 0, monate: [] },
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
    offenNetto: 360,
    offenAnzahl: 1,
    monate: [
      { monat: '2026-03', anzahl: 1, netto: 3600, brutto: 4284, offenNetto: 0, offenAnzahl: 0 },
      { monat: '2026-09', anzahl: 2, netto: 6000, brutto: 7140, offenNetto: 360, offenAnzahl: 1 },
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

const WEG_OKTOBER = '/api/startseite?zeitraum=2026-10';
const OKTOBER = `GET ${WEG_OKTOBER}`;

/**
 * Der Anlass von Issue #284: eine gestellte, nicht bezahlte Rechnung im Oktober 2026 ueber 360,00 €
 * netto, bei Wahl des Jahres 2026. Der Oktober traegt sie, der Maerz nichts Offenes.
 */
const JAHR_MIT_OFFENEM = {
  ...LEERER_STAND,
  zeitraum: { art: 'JAHR', wert: '2026' },
  abgerechnet: {
    netto: 3960,
    brutto: 4712.4,
    anzahl: 2,
    offenNetto: 360,
    offenAnzahl: 1,
    monate: [
      { monat: '2026-03', anzahl: 1, netto: 3600, brutto: 4284, offenNetto: 0, offenAnzahl: 0 },
      { monat: '2026-10', anzahl: 1, netto: 360, brutto: 428.4, offenNetto: 360, offenAnzahl: 1 },
    ],
  },
};

/** Derselbe Bestand, aber der Oktober gewaehlt — dann steht keine Monatsliste da. */
const MONAT_MIT_OFFENEM = {
  ...LEERER_STAND,
  zeitraum: { art: 'MONAT', wert: '2026-10' },
  abgerechnet: {
    netto: 360,
    brutto: 428.4,
    anzahl: 1,
    offenNetto: 360,
    offenAnzahl: 1,
    monate: [],
  },
};

/** Dasselbe Jahr, nachdem die Oktober-Rechnung auf bezahlt gestellt wurde (#284). */
const JAHR_ALLES_BEZAHLT = {
  ...JAHR_MIT_OFFENEM,
  abgerechnet: {
    ...JAHR_MIT_OFFENEM.abgerechnet,
    offenNetto: 0,
    offenAnzahl: 0,
    monate: JAHR_MIT_OFFENEM.abgerechnet.monate.map((zeile) => ({
      ...zeile,
      offenNetto: 0,
      offenAnzahl: 0,
    })),
  },
};

/** Zwei offene Rechnungen — die Mehrzahl in der Kachelzeile. */
const JAHR_MIT_ZWEI_OFFENEN = {
  ...JAHR_MIT_OFFENEM,
  abgerechnet: { ...JAHR_MIT_OFFENEM.abgerechnet, offenNetto: 3960, offenAnzahl: 2 },
};

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

  it('nennt in „Abgerechnet" den noch offenen Betrag netto (#284)', async () => {
    mitRouten({ [OHNE_MONAT]: json(200, STAND) });

    renderSeite();

    expect(within(await kachel(ABGERECHNET)).getByTestId('kennzahlkachel-drittzeile'))
      .toHaveTextContent('davon offen: 360,00 € (1 Rechnung)');
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
    ).toEqual(['Monat', 'Rechnungen', 'Netto', 'Brutto', 'Offen']);
    const zeilen = within(tafel).getAllByRole('row').slice(1);
    expect(zeilen.map((zeile) => zeile.textContent)).toEqual([
      'März 202613.600,00 €4.284,00 €—',
      'September 202626.000,00 €7.140,00 €360,00 €',
      'Summe 202639.600,00 €11.424,00 €360,00 €',
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

/**
 * Das Offene in Kachel und Monatsliste (Issue #284).
 *
 * Der Anlass ist der Oktober 2026 mit einer gestellten, nicht bezahlten Rechnung ueber 360,00 €
 * netto. Geprueft wird, dass er in beiden Ansichten zu sehen ist, dass er mit dem Bezahlen
 * verschwindet und dass „Abgerechnet" dabei unveraendert bleibt.
 */
describe('StartseitePage: offene Rechnungen (Issue #284)', () => {
  /**
   * Die Zelle der Spalte „Offen" einer Zeile — die letzte.
   *
   * Von hinten gezaehlt und nicht mit einer festen Nummer: In der Summenzeile ist die erste Zelle
   * eine Kopfzelle und faellt aus der Rolle `cell` heraus.
   */
  function offenZelle(zeile: HTMLElement): HTMLElement {
    const zellen = within(zeile).getAllByRole('cell');
    return zellen[zellen.length - 1];
  }

  it('zeigt bei Jahreswahl „davon offen" in der Kachel, in der Monatszeile und in der Summe', async () => {
    mitRouten({ [JAHR_2026]: json(200, JAHR_MIT_OFFENEM) });

    renderSeite('/?zeitraum=2026');

    expect(within(await kachel(ABGERECHNET)).getByTestId('kennzahlkachel-drittzeile'))
      .toHaveTextContent('davon offen: 360,00 € (1 Rechnung)');
    const tafel = screen.getByRole('table', { name: 'Abgerechnet' });
    const zeilen = within(tafel).getAllByRole('row').slice(1);
    expect(zeilen.map((zeile) => zeile.textContent)).toEqual([
      'März 202613.600,00 €4.284,00 €—',
      'Oktober 20261360,00 €428,40 €360,00 €',
      'Summe 202623.960,00 €4.712,40 €360,00 €',
    ]);
  });

  it('zeigt bei Monatswahl dieselbe Kachelzeile und keine Monatsliste', async () => {
    mitRouten({ [OKTOBER]: json(200, MONAT_MIT_OFFENEM) });

    renderSeite('/?zeitraum=2026-10');

    expect(within(await kachel(ABGERECHNET)).getByTestId('kennzahlkachel-drittzeile'))
      .toHaveTextContent('davon offen: 360,00 € (1 Rechnung)');
    expect(screen.queryByRole('table', { name: 'Abgerechnet' })).not.toBeInTheDocument();
  });

  it('hebt den offenen Betrag hervor und laesst den Gedankenstrich matt', async () => {
    mitRouten({ [JAHR_2026]: json(200, JAHR_MIT_OFFENEM) });

    renderSeite('/?zeitraum=2026');

    const tafel = await screen.findByRole('table', { name: 'Abgerechnet' });
    const zeilen = within(tafel).getAllByRole('row').slice(1);
    // Die Hervorhebung ist eine Aussage und keine Zierde: Ohne sie unterschiede sich der offene
    // Betrag in nichts von Netto und Brutto daneben.
    expect(offenZelle(zeilen[1])).toHaveAttribute('data-offen', 'ja');
    expect(offenZelle(zeilen[0])).toHaveAttribute('data-offen', 'nein');
  });

  it('laesst Kachelzeile und Betrag weg, nachdem die Rechnung bezahlt ist', async () => {
    mitRouten({ [JAHR_2026]: json(200, JAHR_ALLES_BEZAHLT) });

    renderSeite('/?zeitraum=2026');

    const tafel = await screen.findByRole('table', { name: 'Abgerechnet' });
    expect(screen.queryByTestId('kennzahlkachel-drittzeile')).not.toBeInTheDocument();
    expect(screen.queryByText(/davon offen/u)).not.toBeInTheDocument();
    const zeilen = within(tafel).getAllByRole('row').slice(1);
    // „Abgerechnet" bleibt unveraendert: Netto und Brutto zaehlen die bezahlte Rechnung weiter mit.
    expect(zeilen.map((zeile) => zeile.textContent)).toEqual([
      'März 202613.600,00 €4.284,00 €—',
      'Oktober 20261360,00 €428,40 €—',
      'Summe 202623.960,00 €4.712,40 €—',
    ]);
    expect(await kachel(ABGERECHNET)).toHaveTextContent('3.960,00 €');
    expect(await kachel(ABGERECHNET)).toHaveTextContent('4.712,40 € brutto');
  });

  it('setzt die Mehrzahl, wo mehr als eine Rechnung offen ist', async () => {
    mitRouten({ [JAHR_2026]: json(200, JAHR_MIT_ZWEI_OFFENEN) });

    renderSeite('/?zeitraum=2026');

    expect(within(await kachel(ABGERECHNET)).getByTestId('kennzahlkachel-drittzeile'))
      .toHaveTextContent('davon offen: 3.960,00 € (2 Rechnungen)');
  });

  it('nennt im leeren Zeitraum nichts Offenes', async () => {
    mitRouten({ [OHNE_MONAT]: json(200, LEERER_STAND) });

    renderSeite();

    await screen.findByText('Keine Rechnung in diesem Monat.');
    expect(screen.queryByTestId('kennzahlkachel-drittzeile')).not.toBeInTheDocument();
  });
});
