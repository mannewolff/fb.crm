import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ArbeitszeitPage from './ArbeitszeitPage';
import { KopfPfadProvider } from '../components/KopfPfad';
import { fetchNachPfad, json, problem } from '../test/fetchNachPfad';
import type { Routen } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';

const OHNE_MONAT = 'GET /api/arbeitszeit';
const NOVEMBER = 'GET /api/arbeitszeit?monat=2026-11';
const OKTOBER = 'GET /api/arbeitszeit?monat=2026-10';
const DEZEMBER = 'GET /api/arbeitszeit?monat=2026-12';

const KONZEPTION = {
  id: 101,
  bezeichnung: 'Konzeption',
  angebotId: 11,
  angebotDatum: '2026-09-24',
  firmaName: 'IT Bildungshaus',
};

const SCHULUNG = {
  id: 102,
  bezeichnung: 'Schulung',
  angebotId: 12,
  angebotDatum: '2026-09-28',
  firmaName: 'Adler AG',
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
};

/** Ein Monat ohne einen einzigen Eintrag. */
const LEERER_MONAT = { monat: '2026-11', tage: [], stunden: 0 };

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

  it('traegt „Zeit erfassen" als Kupfertaste, in diesem Stand noch gesperrt (Issue #202)', async () => {
    mitRouten({ [NOVEMBER]: json(200, MONATSLISTE) });

    renderSeite('/arbeitszeit?monat=2026-11');

    const taste = await screen.findByRole('button', { name: 'Zeit erfassen' });
    expect(taste).toBeDisabled();
  });
});
