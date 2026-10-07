import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ArbeitszeitPage from './ArbeitszeitPage';
import { KopfPfadProvider } from '../components/KopfPfad';
import { fetchNachPfad, json, leer, problem } from '../test/fetchNachPfad';
import type { Routen } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';

const OHNE_MONAT = 'GET /api/arbeitszeit';
const WEG_NOVEMBER = '/api/arbeitszeit?monat=2026-11';
const NOVEMBER = `GET ${WEG_NOVEMBER}`;
const OKTOBER = 'GET /api/arbeitszeit?monat=2026-10';
const DEZEMBER = 'GET /api/arbeitszeit?monat=2026-12';
const POSITIONEN = 'GET /api/arbeitszeit/buchbare-positionen';
const ANLEGEN = 'POST /api/arbeitszeit';
const LOESCHEN = 'DELETE /api/arbeitszeit/7';

const KONZEPTION = {
  id: 101,
  bezeichnung: 'Konzeption',
  angebotId: 11,
  angebotDatum: '2026-09-24',
  intern: false,
  firmaName: 'IT Bildungshaus',
};

const SCHULUNG = {
  id: 102,
  bezeichnung: 'Schulung',
  angebotId: 12,
  angebotDatum: '2026-09-28',
  intern: false,
  firmaName: 'Adler AG',
};

/** Eine Position an einem internen Angebot (Issue #236, Kriterium 2). */
const EIGENE_WEBSEITE = {
  id: 301,
  bezeichnung: 'Eigene Webseite',
  angebotId: 41,
  angebotDatum: '2026-09-30',
  intern: true,
  firmaName: 'Manfred Wolff',
};

/** Zwei Tage mit drei Eintraegen: 1,75 + 2,00 Std. am 12., 2,00 Std. am 20. November. */
const MONATSLISTE = {
  monat: '2026-11',
  tage: [
    {
      tag: '2026-11-12',
      eintraege: [
        {
          id: 7,
          tag: '2026-11-12',
          von: '09:00:00',
          bis: '10:45:00',
          stunden: 1.75,
          position: KONZEPTION,
        },
        {
          id: 8,
          tag: '2026-11-12',
          von: '13:00:00',
          bis: '15:00:00',
          stunden: 2,
          position: SCHULUNG,
        },
      ],
      stunden: 3.75,
    },
    {
      tag: '2026-11-20',
      eintraege: [
        {
          id: 9,
          tag: '2026-11-20',
          von: '08:00:00',
          bis: '10:00:00',
          stunden: 2,
          position: KONZEPTION,
        },
      ],
      stunden: 2,
    },
  ],
  stunden: 5.75,
  stundenFuerKunden: 5.75,
  stundenIntern: 0,
};

/** Ein Monat ohne einen einzigen Eintrag. */
const LEERER_MONAT = {
  monat: '2026-11',
  tage: [],
  stunden: 0,
  stundenFuerKunden: 0,
  stundenIntern: 0,
};

/** Die Antwort auf das Erfassen — die Ansicht liest daraus nichts, sie laedt den Monat neu. */
const ANGELEGT = {
  id: 12,
  angebotPositionId: 101,
  tag: '2026-11-12',
  von: '09:00:00',
  bis: '10:45:00',
  stunden: 1.75,
};

type Nutzer = ReturnType<typeof userEvent.setup>;

/** Wie oft die Monatsliste gefragt wurde — der Dialog fragt daneben nach seinen Positionen. */
function monatsaufrufe(aufruf: ReturnType<typeof fetchNachPfad>): number {
  // Der Weg kommt als Zeichenkette heraus — `api/client.ts` ruft `fetch` nie mit einem `Request`.
  return aufruf.mock.calls.filter(([ziel]) => ziel === WEG_NOVEMBER).length;
}

/** Oeffnet das ⋯-Menue des ersten Eintrags und waehlt „Löschen". */
async function waehleLoeschen(nutzer: Nutzer) {
  await nutzer.click(
    await screen.findByRole('button', { name: 'Aktionen für 12.11.2026, 9:00 bis 10:45' }),
  );
  await nutzer.click(await screen.findByRole('menuitem', { name: 'Löschen' }));
}

/** Die Adresse — daran haengt, was der Monatswechsel in `?monat=` geschrieben hat. */
function Adresse() {
  const ort = useLocation();
  return <p data-testid="adresse">{`${ort.pathname}${ort.search}`}</p>;
}

function renderSeite(adresse = '/arbeitszeit') {
  return renderMitTheme(
    <MemoryRouter initialEntries={[adresse]}>
      <KopfPfadProvider>
        <ArbeitszeitPage />
        <Adresse />
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

function mitRouten(routen: Routen) {
  return fetchNachPfad(routen);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('ArbeitszeitPage (Kriterium 5, Plan A13, A20)', () => {
  it('nennt sich „Arbeitszeit" und zeigt den Ladehinweis, solange nichts da ist', () => {
    mitRouten({ [OHNE_MONAT]: json(200, MONATSLISTE) });

    renderSeite();

    expect(screen.getByRole('heading', { level: 1, name: 'Arbeitszeit' })).toBeInTheDocument();
    expect(screen.getByText('Die Arbeitszeit wird geladen …')).toBeInTheDocument();
  });

  it('fragt ohne Monat, wo die Adresse keinen nennt — dann gilt der laufende (E4)', async () => {
    const aufruf = mitRouten({ [OHNE_MONAT]: json(200, MONATSLISTE) });

    renderSeite();

    expect(await screen.findByText('November 2026')).toBeInTheDocument();
    expect(aufruf).toHaveBeenCalledTimes(1);
  });

  it('fragt ohne Monat, wo die Adresse keinen gueltigen nennt', async () => {
    mitRouten({ [OHNE_MONAT]: json(200, MONATSLISTE) });

    renderSeite('/arbeitszeit?monat=2026-13');

    expect(await screen.findByText('November 2026')).toBeInTheDocument();
  });

  it('fragt mit dem Monat aus der Adresse', async () => {
    mitRouten({ [NOVEMBER]: json(200, MONATSLISTE) });

    renderSeite('/arbeitszeit?monat=2026-11');

    expect(await screen.findByText('November 2026')).toBeInTheDocument();
  });

  it('zeigt die Tage als Gruppen mit Tagessumme, Monatssumme und je Zeile Spanne, Dauer, Position', async () => {
    mitRouten({ [NOVEMBER]: json(200, MONATSLISTE) });

    renderSeite('/arbeitszeit?monat=2026-11');

    const tafel = within(await screen.findByRole('table', { name: 'Arbeitszeit November 2026' }));
    // Je Tag eine Gruppenzeile mit seiner Summe …
    expect(tafel.getByText('12.11.2026')).toBeInTheDocument();
    expect(tafel.getByText('20.11.2026')).toBeInTheDocument();
    expect(tafel.getByText('3,75 Std.')).toBeInTheDocument();
    // … und am Fuss die Summe des Monats.
    expect(tafel.getByText('Summe November 2026')).toBeInTheDocument();
    expect(tafel.getByText('5,75 Std.')).toBeInTheDocument();
    // Je Zeile Spanne, Dauer und die Position mit ihrer Firma.
    expect(tafel.getByText('9:00 bis 10:45')).toBeInTheDocument();
    expect(tafel.getByText('1,75 Std.')).toBeInTheDocument();
    expect(tafel.getByText('13:00 bis 15:00')).toBeInTheDocument();
    expect(tafel.getAllByText('Konzeption')).toHaveLength(2);
    expect(tafel.getAllByText('IT Bildungshaus')).toHaveLength(2);
    expect(tafel.getByText('Schulung')).toBeInTheDocument();
    expect(tafel.getByText('Adler AG')).toBeInTheDocument();
  });

  it('stellt den Kopf einer Zahlenspalte rechtsbuendig und die uebrigen links (Issue #286)', async () => {
    mitRouten({ [NOVEMBER]: json(200, MONATSLISTE) });

    renderSeite('/arbeitszeit?monat=2026-11');

    const tafel = within(await screen.findByRole('table', { name: 'Arbeitszeit November 2026' }));
    expect(tafel.getByRole('columnheader', { name: 'Dauer' })).toHaveStyle({ textAlign: 'right' });
    for (const name of ['Zeit', 'Position', 'Aktionen']) {
      expect(tafel.getByRole('columnheader', { name })).toHaveStyle({ textAlign: 'left' });
    }
  });

  it('laedt den Vormonat und schreibt ihn in die Adresse', async () => {
    mitRouten({ [NOVEMBER]: json(200, MONATSLISTE), [OKTOBER]: json(200, LEERER_MONAT) });
    const nutzer = userEvent.setup();

    renderSeite('/arbeitszeit?monat=2026-11');
    await nutzer.click(await screen.findByRole('button', { name: 'Vorheriger Monat' }));

    expect(await screen.findByTestId('adresse')).toHaveTextContent('/arbeitszeit?monat=2026-10');
  });

  it('laedt den Folgemonat und schreibt ihn in die Adresse', async () => {
    mitRouten({ [NOVEMBER]: json(200, MONATSLISTE), [DEZEMBER]: json(200, LEERER_MONAT) });
    const nutzer = userEvent.setup();

    renderSeite('/arbeitszeit?monat=2026-11');
    await nutzer.click(await screen.findByRole('button', { name: 'Nächster Monat' }));

    expect(await screen.findByTestId('adresse')).toHaveTextContent('/arbeitszeit?monat=2026-12');
  });

  it('wechselt den Monat auch, wo die Adresse noch keinen nennt — er kommt aus der Antwort', async () => {
    mitRouten({ [OHNE_MONAT]: json(200, MONATSLISTE), [OKTOBER]: json(200, LEERER_MONAT) });
    const nutzer = userEvent.setup();

    renderSeite();
    await nutzer.click(await screen.findByRole('button', { name: 'Vorheriger Monat' }));

    expect(await screen.findByTestId('adresse')).toHaveTextContent('/arbeitszeit?monat=2026-10');
  });

  it('laedt den Monat neu, wenn die Adresse ihn wechselt', async () => {
    const aufruf = mitRouten({
      [NOVEMBER]: json(200, MONATSLISTE),
      [OKTOBER]: json(200, LEERER_MONAT),
    });
    const nutzer = userEvent.setup();

    renderSeite('/arbeitszeit?monat=2026-11');
    await nutzer.click(await screen.findByRole('button', { name: 'Vorheriger Monat' }));

    expect(await screen.findByText('Oktober 2026')).toBeInTheDocument();
    expect(aufruf).toHaveBeenCalledTimes(2);
  });

  it('laedt im leeren Monat eine Einladung statt einer Entschuldigung', async () => {
    mitRouten({ [NOVEMBER]: json(200, LEERER_MONAT) });

    renderSeite('/arbeitszeit?monat=2026-11');

    const leer = await screen.findByRole('status');
    expect(leer).toHaveTextContent('Noch keine Arbeitszeit in diesem Monat.');
    expect(leer).toHaveTextContent('Zeit erfassen');
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('meldet den Ausfall der Schnittstelle, ohne eine leere Liste zu behaupten', async () => {
    mitRouten({ [NOVEMBER]: problem(500, 'Kaputt.') });

    renderSeite('/arbeitszeit?monat=2026-11');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Die Arbeitszeit ist gerade nicht zu erreichen. Bitte später erneut versuchen.',
    );
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('traegt „Zeit erfassen" als Kupfertaste', async () => {
    mitRouten({ [NOVEMBER]: json(200, MONATSLISTE) });

    renderSeite('/arbeitszeit?monat=2026-11');

    expect(await screen.findByRole('button', { name: 'Zeit erfassen' })).toBeEnabled();
  });
});

describe('ArbeitszeitPage — erfassen und aendern (Kriterien 1 und 6, Plan A14)', () => {
  it('oeffnet den Dialog zum Erfassen und laedt den Monat nach dem Speichern neu', async () => {
    const nutzer = userEvent.setup();
    const aufruf = mitRouten({
      [NOVEMBER]: json(200, MONATSLISTE),
      [POSITIONEN]: json(200, [KONZEPTION]),
      [ANLEGEN]: json(201, ANGELEGT),
    });

    renderSeite('/arbeitszeit?monat=2026-11');
    await nutzer.click(await screen.findByRole('button', { name: 'Zeit erfassen' }));
    await nutzer.type(await screen.findByLabelText(/^von/), '09:00');
    await nutzer.type(screen.getByLabelText(/^bis/), '10:45');
    await nutzer.selectOptions(screen.getByRole('combobox', { name: 'Position' }), '101');
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    // Die Monatsliste ist ein zweites Mal gefragt worden, und der Dialog ist weg.
    await vi.waitFor(() => {
      expect(monatsaufrufe(aufruf)).toBe(2);
    });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('schliesst den Dialog beim Abbrechen, ohne den Monat neu zu laden', async () => {
    const nutzer = userEvent.setup();
    const aufruf = mitRouten({
      [NOVEMBER]: json(200, MONATSLISTE),
      [POSITIONEN]: json(200, [KONZEPTION]),
    });

    renderSeite('/arbeitszeit?monat=2026-11');
    await nutzer.click(await screen.findByRole('button', { name: 'Zeit erfassen' }));
    await nutzer.click(await screen.findByRole('button', { name: 'Abbrechen' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(monatsaufrufe(aufruf)).toBe(1);
  });

  it('oeffnet das Aendern ueber die Zeitspanne der Zeile (A14)', async () => {
    const nutzer = userEvent.setup();
    mitRouten({ [NOVEMBER]: json(200, MONATSLISTE), [POSITIONEN]: json(200, [KONZEPTION]) });

    renderSeite('/arbeitszeit?monat=2026-11');
    await nutzer.click(await screen.findByRole('button', { name: '9:00 bis 10:45' }));

    expect(
      await screen.findByRole('heading', { name: 'Arbeitszeit ändern' }),
    ).toBeInTheDocument();
    // Die Werte stammen aus der Zeile, auf die geklickt wurde.
    expect(screen.getByLabelText(/^Tag/)).toHaveValue('2026-11-12');
    expect(screen.getByLabelText(/^von/)).toHaveValue('09:00');
    expect(screen.getByRole('combobox', { name: 'Position' })).toHaveValue('101');
  });

  it('macht die Zeile nicht klickbar — die Spanne und das ⋯-Menue sind ihre Schalter', async () => {
    mitRouten({ [NOVEMBER]: json(200, MONATSLISTE) });

    renderSeite('/arbeitszeit?monat=2026-11');
    const tafel = within(await screen.findByRole('table'));

    // Je Eintrag genau zwei Schalter in dieser Reihenfolge: die Spanne, dann das ⋯-Menue.
    expect(tafel.getAllByRole('button').map((taste) => taste.textContent)).toEqual([
      '9:00 bis 10:45',
      '',
      '13:00 bis 15:00',
      '',
      '8:00 bis 10:00',
      '',
    ]);
    // Und kein Verweis, der die Zeile als Ganzes anfasst.
    expect(tafel.queryAllByRole('link')).toHaveLength(0);
  });
});

describe('ArbeitszeitPage — loeschen (Kriterium 6, Antwort 4)', () => {
  it('fragt vor dem Loeschen zurueck und tut nichts, wenn der Mensch abbricht', async () => {
    const nutzer = userEvent.setup();
    const aufruf = mitRouten({ [NOVEMBER]: json(200, MONATSLISTE) });

    renderSeite('/arbeitszeit?monat=2026-11');
    await waehleLoeschen(nutzer);
    expect(
      await screen.findByText('Der Eintrag wird gelöscht. Das lässt sich nicht zurücknehmen.'),
    ).toBeInTheDocument();
    await nutzer.click(screen.getByRole('button', { name: 'Abbrechen' }));

    // Nur der Leseweg des Monats, kein DELETE.
    expect(aufruf).toHaveBeenCalledTimes(1);
  });

  it('loescht nach der Rueckfrage und laedt den Monat neu', async () => {
    const nutzer = userEvent.setup();
    const aufruf = mitRouten({ [NOVEMBER]: json(200, MONATSLISTE), [LOESCHEN]: leer(204) });

    renderSeite('/arbeitszeit?monat=2026-11');
    await waehleLoeschen(nutzer);
    await nutzer.click(await screen.findByRole('button', { name: 'Löschen' }));

    await vi.waitFor(() => {
      expect(monatsaufrufe(aufruf)).toBe(2);
    });
  });

  it('meldet den Fehlschlag des Loeschens, statt ihn zu verschweigen', async () => {
    const nutzer = userEvent.setup();
    mitRouten({ [NOVEMBER]: json(200, MONATSLISTE), [LOESCHEN]: problem(500, 'Kaputt.') });

    renderSeite('/arbeitszeit?monat=2026-11');
    await waehleLoeschen(nutzer);
    await nutzer.click(await screen.findByRole('button', { name: 'Löschen' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Der Eintrag wurde nicht gelöscht. Bitte später erneut versuchen.',
    );
  });
});

describe('ArbeitszeitPage — Kennzeichen intern und die Aufteilung (Issue #236, Kriterien 2 und 10)', () => {
  /** Derselbe Monat, aber der Eintrag am 20. liegt auf einem internen Angebot. */
  const GEMISCHT = {
    ...MONATSLISTE,
    tage: [
      MONATSLISTE.tage[0],
      {
        ...MONATSLISTE.tage[1],
        eintraege: [{ ...MONATSLISTE.tage[1].eintraege[0], position: EIGENE_WEBSEITE }],
      },
    ],
    stundenFuerKunden: 3.75,
    stundenIntern: 2,
  };

  it('zeigt den Chip „Intern" nur in der Zeile zu einer Position eines internen Angebots', async () => {
    mitRouten({ [NOVEMBER]: json(200, GEMISCHT) });

    renderSeite('/arbeitszeit?monat=2026-11');

    const tafel = within(await screen.findByRole('table', { name: 'Arbeitszeit November 2026' }));
    expect(tafel.getAllByText('Intern')).toHaveLength(1);
    // Der Chip steht in der Zeile der internen Position, nicht in der der externen.
    const interne = tafel.getByRole('row', { name: /Eigene Webseite/ });
    expect(within(interne).getByText('Intern')).toBeInTheDocument();
    const externe = tafel.getAllByRole('row', { name: /Konzeption/ })[0];
    expect(within(externe).queryByText('Intern')).not.toBeInTheDocument();
  });

  it('nennt unter der Monatssumme die Aufteilung mit beiden Werten der Antwort', async () => {
    mitRouten({ [NOVEMBER]: json(200, GEMISCHT) });

    renderSeite('/arbeitszeit?monat=2026-11');

    const tafel = within(await screen.findByRole('table', { name: 'Arbeitszeit November 2026' }));
    expect(
      tafel.getByText('davon für Kunden 3,75 Std., intern 2,00 Std.'),
    ).toBeInTheDocument();
  });

  it('zeigt die Aufteilung auch in einem Monat mit nur einer Art, mit 0,00 Std. im anderen Teil', async () => {
    mitRouten({ [NOVEMBER]: json(200, MONATSLISTE) });

    renderSeite('/arbeitszeit?monat=2026-11');

    const tafel = within(await screen.findByRole('table', { name: 'Arbeitszeit November 2026' }));
    expect(
      tafel.getByText('davon für Kunden 5,75 Std., intern 0,00 Std.'),
    ).toBeInTheDocument();
  });

  it('zeigt im leeren Monat weiter die Leermeldung und keine Aufteilung', async () => {
    mitRouten({ [NOVEMBER]: json(200, LEERER_MONAT) });

    renderSeite('/arbeitszeit?monat=2026-11');

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Noch keine Arbeitszeit in diesem Monat.',
    );
    expect(screen.queryByText(/davon für Kunden/)).not.toBeInTheDocument();
  });
});
