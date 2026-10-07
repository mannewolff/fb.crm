import { screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import JahresabschlussPage from './JahresabschlussPage';
import { KopfPfadProvider } from '../components/KopfPfad';
import type { ToenungName } from '../theme';
import { theme } from '../theme';
import { ohneRueckfall } from '../test/cssvar';
import { fetchNachPfad, json, problem } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';

const WEG = 'GET /api/jahresabschluesse/2025';

/** Ein abgelaufenes Jahr, wie das Backend es schreibt — mit zwei Saetzen und Nachtragszeile. */
const ABSCHLUSS = {
  jahr: '2025',
  laeuftNoch: false,
  einnahmen: { netto: 1000, brutto: 1170, umsatzsteuer: 170 },
  rechnungsstand: {
    anzahl: 3,
    offenAnzahl: 1,
    offenNetto: 250.25,
    abgeschriebenAnzahl: 1,
    abgeschriebenNetto: 50,
  },
  steuerzeilen: [
    { satz: 19, netto: 800, umsatzsteuer: 152 },
    { satz: 7, netto: 100, umsatzsteuer: 7 },
    { satz: null, netto: 100, umsatzsteuer: 11 },
  ],
  angebotsbilanz: {
    abgegeben: 4,
    angenommen: 3,
    offen: 1,
    annahmequote: 75,
    volumenAbgegeben: 2000,
    volumenAngenommen: 1500.5,
  },
  kunden: [{ firmaName: 'Muster GmbH', netto: 1000, anteil: 100 }],
  arbeitszeit: { kundenStunden: 10, interneStunden: 2, erloesJeStunde: 100 },
};

function renderSeite(jahr = '2025') {
  return renderMitTheme(
    <MemoryRouter initialEntries={[`/jahresabschluesse/${jahr}`]}>
      <KopfPfadProvider>
        <Routes>
          <Route path="/jahresabschluesse/:jahr" element={<JahresabschlussPage />} />
        </Routes>
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

/** Die Kachel mit dieser Beschriftung. */
async function kachel(beschriftung: string): Promise<HTMLElement> {
  const kacheln = await screen.findAllByTestId('kennzahlkachel');
  const treffer = kacheln.filter((element) => within(element).queryByText(beschriftung) !== null);
  expect(treffer).toHaveLength(1);
  return treffer[0];
}

/** Die Datenzeilen einer Tafel, ohne die Kopfzeile. */
async function datenzeilen(name: string): Promise<HTMLElement[]> {
  const tafel = within(await screen.findByRole('table', { name }));
  return tafel.getAllByRole('row').slice(1);
}

/** Die Zellen einer Zeile als Text. */
function zellen(zeile: HTMLElement): (string | null)[] {
  return Array.from(zeile.querySelectorAll('th, td'), (zelle) => zelle.textContent);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('JahresabschlussPage — Kopf und Zustaende', () => {
  it('zeigt waehrend des Ladens einen Hinweis und danach das Jahr als die eine Ueberschrift', async () => {
    fetchNachPfad({ [WEG]: json(200, ABSCHLUSS) });

    renderSeite();

    expect(screen.getByText('Der Jahresabschluss wird geladen …')).toBeInTheDocument();
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Jahresabschluss 2025' }),
    ).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
  });

  it('traegt den Hinweis „läuft noch" nur am laufenden Jahr', async () => {
    fetchNachPfad({
      'GET /api/jahresabschluesse/2026': json(200, { ...ABSCHLUSS, jahr: '2026', laeuftNoch: true }),
    });

    renderSeite('2026');

    expect(await screen.findByText('läuft noch')).toBeInTheDocument();
  });

  it('traegt am abgelaufenen Jahr keinen Hinweis „läuft noch"', async () => {
    fetchNachPfad({ [WEG]: json(200, ABSCHLUSS) });

    renderSeite();

    await screen.findByRole('heading', { level: 1, name: 'Jahresabschluss 2025' });
    expect(screen.queryByText('läuft noch')).not.toBeInTheDocument();
  });

  it('meldet bei 404, dass es fuer das Jahr keinen Abschluss gibt — statt eines aus Nullen (E3)', async () => {
    fetchNachPfad({ [WEG]: problem(404, 'Kein Abschluss') });

    renderSeite();

    expect(
      await screen.findByText('Für 2025 gibt es keinen Jahresabschluss.'),
    ).toBeInTheDocument();
    expect(screen.queryByTestId('kennzahlkachel')).not.toBeInTheDocument();
    expect(screen.queryByText('0,00 €')).not.toBeInTheDocument();
  });

  it('meldet bei einem anderen Fehler den Ausfall', async () => {
    fetchNachPfad({ [WEG]: problem(500, 'kaputt') });

    renderSeite();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Der Jahresabschluss ist gerade nicht zu erreichen. Bitte später erneut versuchen.',
    );
    expect(screen.queryByTestId('kennzahlkachel')).not.toBeInTheDocument();
  });

  it('fragt fuer eine Adresse, die kein Jahr ist, nicht beim Server und meldet den fehlenden Abschluss', async () => {
    const abruf = fetchNachPfad({});

    renderSeite('abc');

    expect(
      await screen.findByText('Für abc gibt es keinen Jahresabschluss.'),
    ).toBeInTheDocument();
    expect(abruf).not.toHaveBeenCalled();
  });
});

describe('JahresabschlussPage — Einnahmen (#287, Kriterium 4)', () => {
  it.each<[string, string, ToenungName]>([
    ['Einnahmen netto', '1.000,00 €', 'salbei'],
    ['Einnahmen brutto', '1.170,00 €', 'himmel'],
    ['Enthaltene Umsatzsteuer', '170,00 €', 'flieder'],
  ])('zeigt die Kachel „%s" mit %s auf %s', async (beschriftung, betrag, toenung) => {
    fetchNachPfad({ [WEG]: json(200, ABSCHLUSS) });

    renderSeite();

    const element = await kachel(beschriftung);
    expect(element).toHaveTextContent(betrag);
    expect(element).toHaveStyle({
      color: ohneRueckfall(theme.vars.palette.kupferwolke.toenung[toenung].schrift),
    });
  });

  it('zeigt genau drei Kacheln', async () => {
    fetchNachPfad({ [WEG]: json(200, ABSCHLUSS) });

    renderSeite();

    expect(await screen.findAllByTestId('kennzahlkachel')).toHaveLength(3);
  });

  it('sagt unter den Kacheln im Wortlaut, wonach gezaehlt ist', async () => {
    fetchNachPfad({ [WEG]: json(200, ABSCHLUSS) });

    renderSeite();

    expect(
      await screen.findByText('Gezählt nach Rechnungsdatum, nicht nach Zahlungseingang.'),
    ).toBeInTheDocument();
  });
});

describe('JahresabschlussPage — Rechnungen (#287, Kriterium 5)', () => {
  it('nennt die gestellten und davon die offenen und abgeschriebenen je mit Anzahl und Netto', async () => {
    fetchNachPfad({ [WEG]: json(200, ABSCHLUSS) });

    renderSeite();

    expect(
      await screen.findByRole('heading', { level: 2, name: 'Rechnungen' }),
    ).toBeInTheDocument();
    expect((await datenzeilen('Rechnungen')).map(zellen)).toEqual([
      ['Gestellt', '3', '1.000,00 €'],
      ['davon heute offen', '1', '250,25 €'],
      ['davon abgeschrieben', '1', '50,00 €'],
    ]);
  });

  it('stellt den Kopf der beiden Zahlenspalten rechts', async () => {
    fetchNachPfad({ [WEG]: json(200, ABSCHLUSS) });

    renderSeite();

    const tafel = within(await screen.findByRole('table', { name: 'Rechnungen' }));
    for (const name of ['Anzahl', 'Netto']) {
      expect(tafel.getByRole('columnheader', { name })).toHaveStyle({ textAlign: 'right' });
    }
  });

  it('nennt ohne Offenes und Abgeschriebenes 0 und 0,00 €', async () => {
    fetchNachPfad({
      [WEG]: json(200, {
        ...ABSCHLUSS,
        rechnungsstand: {
          anzahl: 3,
          offenAnzahl: 0,
          offenNetto: 0,
          abgeschriebenAnzahl: 0,
          abgeschriebenNetto: 0,
        },
      }),
    });

    renderSeite();

    const zeilen = await datenzeilen('Rechnungen');
    expect(zellen(zeilen[1])).toEqual(['davon heute offen', '0', '0,00 €']);
    expect(zellen(zeilen[2])).toEqual(['davon abgeschrieben', '0', '0,00 €']);
  });
});

describe('JahresabschlussPage — Umsatzsteuer je Steuersatz (#287, Kriterium 6)', () => {
  it('zeigt zwei Saetze und die Zeile „Steuersatz nicht erfasst" zuletzt', async () => {
    fetchNachPfad({ [WEG]: json(200, ABSCHLUSS) });

    renderSeite();

    expect(
      await screen.findByRole('heading', { level: 2, name: 'Umsatzsteuer je Steuersatz' }),
    ).toBeInTheDocument();
    expect((await datenzeilen('Umsatzsteuer je Steuersatz')).map(zellen)).toEqual([
      ['19,0 %', '800,00 €', '152,00 €'],
      ['7,0 %', '100,00 €', '7,00 €'],
      ['Steuersatz nicht erfasst', '100,00 €', '11,00 €'],
    ]);
  });

  it('stellt den Kopf der Zahlenspalten rechts und den des Satzes links', async () => {
    fetchNachPfad({ [WEG]: json(200, ABSCHLUSS) });

    renderSeite();

    const tafel = within(await screen.findByRole('table', { name: 'Umsatzsteuer je Steuersatz' }));
    expect(tafel.getByRole('columnheader', { name: 'Steuersatz' })).toHaveStyle({
      textAlign: 'left',
    });
    for (const name of ['Netto', 'Umsatzsteuer']) {
      expect(tafel.getByRole('columnheader', { name })).toHaveStyle({ textAlign: 'right' });
    }
  });

  it('laesst die ganze Karte weg, wenn die Liste der Steuerzeilen leer ist', async () => {
    fetchNachPfad({ [WEG]: json(200, { ...ABSCHLUSS, steuerzeilen: [] }) });

    renderSeite();

    await screen.findByRole('heading', { level: 2, name: 'Rechnungen' });
    expect(
      screen.queryByRole('heading', { name: 'Umsatzsteuer je Steuersatz' }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('table', { name: 'Umsatzsteuer je Steuersatz' }),
    ).not.toBeInTheDocument();
  });
});
