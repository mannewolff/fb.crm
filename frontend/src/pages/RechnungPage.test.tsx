import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import RechnungPage from './RechnungPage';
import { KopfPfadProvider } from '../components/KopfPfad';
import { alsJson, fetchNachPfad, json, leer, problem } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';

/**
 * Die Entwurfsmaske der Rechnung (Issue #185).
 *
 * Die Proben gehen den Weg des Freiberuflers: Er oeffnet den Entwurf, traegt an einer Position eine
 * Menge ein, sieht Betrag und Summen mitlaufen, speichert — und loescht den Entwurf, wenn er ihn
 * nicht braucht. Geprueft wird dabei zweierlei, was sich mit einem Blick auf die Seite nicht
 * unterscheiden laesst: <b>was dasteht</b> und <b>was hinausgeht</b>.
 *
 * Die Mengen und Betraege der Proben sind die des Akzeptanzkriteriums: 160 Stunden zu 120,00
 * angeboten, davon 80 abgerechnet — 9.600,00 netto, 1.824,00 Steuer, 11.424,00 brutto.
 */

const WEG = 'GET /api/rechnungen/4';
const WEG_AENDERN = 'PUT /api/rechnungen/4';
const WEG_LOESCHEN = 'DELETE /api/rechnungen/4';

/** Eine Position mit 160 angebotenen Stunden zu 120,00, noch nichts abgerechnet. */
const ZEILE_STUNDEN = {
  angebotPositionId: 11,
  bezeichnung: 'Entwicklung',
  einheit: 'STUNDE',
  einzelpreis: 120,
  angeboten: 160,
  abgerechnet: 0,
  offen: 160,
  menge: 0,
  ueberschreitung: 0,
};

/** Eine Position, die bereits vollstaendig abgerechnet ist — sie steht trotzdem in der Maske. */
const ZEILE_PAUSCHAL = {
  angebotPositionId: 12,
  bezeichnung: 'Konzept',
  einheit: 'PAUSCHAL',
  einzelpreis: 2500,
  angeboten: 1,
  abgerechnet: 1,
  offen: 0,
  menge: 0,
  ueberschreitung: 0,
};

const ENTWURF = {
  id: 4,
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
  zeilen: [ZEILE_STUNDEN, ZEILE_PAUSCHAL],
};

/** Derselbe Entwurf, nachdem 80 Stunden eingetragen und gespeichert wurden. */
const GESPEICHERT = {
  ...ENTWURF,
  netto: 9600,
  steuer: 1824,
  brutto: 11424,
  zeilen: [{ ...ZEILE_STUNDEN, menge: 80 }, ZEILE_PAUSCHAL],
};

const GESTELLT = {
  ...GESPEICHERT,
  zustand: 'GESTELLT',
  nummer: '0001-2026',
  zahlungszielTage: 14,
};

/** Die Adresse — daran haengt, ob das Loeschen zur Liste gefuehrt hat. */
function Adresse() {
  const ort = useLocation();
  return <p data-testid="adresse">{ort.pathname}</p>;
}

function renderSeite(adresse = '/rechnungen/4') {
  return renderMitTheme(
    <MemoryRouter initialEntries={[adresse]}>
      <KopfPfadProvider>
        <Routes>
          <Route path="/rechnungen" element={<p>Die Liste</p>} />
          <Route path="/rechnungen/:rechnungId" element={<RechnungPage />} />
        </Routes>
        <Adresse />
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

/**
 * Die Kupfertasten der Seite.
 *
 * Sie tragen den Kupferverlauf, die weichen eine Flaeche (CLAUDE-design.md, „Tasten") — die Rolle
 * unterscheidet beide nicht. Gezaehlt wird darum am Verlauf, wie in `RechnungenPage.test.tsx`.
 */
function kupfertasten(): readonly HTMLElement[] {
  return [...screen.queryAllByRole('link'), ...screen.queryAllByRole('button')].filter((taste) =>
    getComputedStyle(taste).background.includes('linear-gradient'),
  );
}

/** Das Feld „jetzt abrechnen" der n-ten Position. */
function mengenfeld(nummer: number): HTMLElement {
  return screen.getByRole('textbox', { name: `Jetzt abrechnen, Position ${String(nummer)}` });
}

/** Das Textfeld der n-ten Position. */
function leistungsfeld(nummer: number): HTMLElement {
  return screen.getByRole('textbox', { name: `Leistung, Position ${String(nummer)}` });
}

/** Traegt eine Menge in das Feld der n-ten Position ein — das Feld ist vorbelegt. */
async function trageEin(nutzer: ReturnType<typeof userEvent.setup>, nummer: number, wert: string) {
  const feld = mengenfeld(nummer);
  await nutzer.clear(feld);
  await nutzer.type(feld, wert);
}

/**
 * Ein Weg, der haengt, bis der Test ihn freigibt.
 *
 * Nur so ist der Zustand „es laeuft" messbar: Ein Weg, der sofort antwortet, ist schon fertig,
 * bevor die Erwartung ihn ansieht. Die Wartenden stehen in einer Liste und nicht in einer
 * Einzelvariablen mit Ersatzwert — ein nie gerufener Ersatzwert waere eine Funktion ohne Abdeckung.
 */
function haltenderWeg(): {
  readonly weg: () => Promise<Response>;
  readonly freigeben: (antwort: () => Response) => void;
} {
  const wartende: ((antwort: Response) => void)[] = [];
  return {
    weg: () =>
      new Promise<Response>((loesen) => {
        wartende.push(loesen);
      }),
    freigeben: (antwort) => {
      for (const loesen of wartende) {
        loesen(antwort());
      }
    },
  };
}

/** Oeffnet das ⋯-Menue der Rechnung und waehlt einen Eintrag. */
async function waehle(nutzer: ReturnType<typeof userEvent.setup>, eintrag: string) {
  await nutzer.click(screen.getByRole('button', { name: 'Aktionen für diese Rechnung' }));
  await nutzer.click(await screen.findByRole('menuitem', { name: eintrag }));
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('RechnungPage — der Kopf (Issue #185)', () => {
  it('zeigt waehrend des Ladens einen Hinweis', () => {
    fetchNachPfad({ [WEG]: json(200, ENTWURF) });

    renderSeite();

    expect(screen.getByText('Die Rechnung wird geladen …')).toBeInTheDocument();
  });

  it('nennt den Entwurf „Rechnung (Entwurf)" und verweist auf Firma und Angebot', async () => {
    fetchNachPfad({ [WEG]: json(200, ENTWURF) });

    renderSeite();

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Rechnung (Entwurf)' }),
    ).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    expect(screen.getByRole('link', { name: 'Adler AG' })).toHaveAttribute('href', '/firmen/5');
    expect(screen.getByRole('link', { name: 'Zum Angebot' })).toHaveAttribute(
      'href',
      '/angebote/9',
    );
    expect(within(screen.getByTestId('rechnung-angaben')).getByText('Entwurf')).toBeInTheDocument();
  });

  it('meldet eine Rechnung, die es nicht gibt (404)', async () => {
    fetchNachPfad({ [WEG]: problem(404, 'weg') });

    renderSeite();

    expect(await screen.findByRole('alert')).toHaveTextContent('Diese Rechnung gibt es nicht.');
  });

  it('meldet eine Kennung, die keine ist, ohne das Netz zu bemuehen', async () => {
    const fetchMock = fetchNachPfad({});

    renderSeite('/rechnungen/keine-zahl');

    expect(await screen.findByRole('alert')).toHaveTextContent('Diese Rechnung gibt es nicht.');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('meldet den Ausfall des Weges', async () => {
    fetchNachPfad({ [WEG]: problem(500, 'kaputt') });

    renderSeite();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Die Rechnung ist gerade nicht zu erreichen.',
    );
  });
});

describe('RechnungPage — die Maske des Entwurfs (Kriterien 4, 6 bis 8)', () => {
  it('zeigt jede Position des Angebots, auch eine ohne Menge', async () => {
    fetchNachPfad({ [WEG]: json(200, ENTWURF) });

    renderSeite();
    const tafel = await screen.findByRole('table', { name: 'Positionen' });

    expect(within(tafel).getAllByRole('row')).toHaveLength(3);
    expect(leistungsfeld(1)).toHaveValue('Entwicklung');
    expect(leistungsfeld(2)).toHaveValue('Konzept');
    expect(mengenfeld(1)).toHaveValue('0,00');
    expect(mengenfeld(2)).toHaveValue('0,00');
    // Die Position, die schon abgerechnet ist, steht mit „offen 0,00" da (Kriterium 7).
    const zeile = within(tafel).getAllByRole('row')[2];
    expect(within(zeile).getByTestId('zeile-abgerechnet')).toHaveTextContent('1,00');
    expect(within(zeile).getByTestId('zeile-offen')).toHaveTextContent('0,00');
  });

  it('zeigt Rechnungsdatum und Leistungszeitraum als Felder', async () => {
    fetchNachPfad({ [WEG]: json(200, ENTWURF) });

    renderSeite();

    expect(await screen.findByLabelText(/^Rechnungsdatum/u)).toHaveValue('2026-10-01');
    expect(screen.getByLabelText(/^Leistungszeitraum/u)).toHaveValue('Oktober 2026');
  });

  it('zeigt ein leeres Feld, wenn die Rechnung keinen Leistungszeitraum traegt', async () => {
    fetchNachPfad({ [WEG]: json(200, { ...ENTWURF, leistungszeitraum: null }) });

    renderSeite();

    // Kein „null" im Feld: Eine fehlende Angabe ist ein leeres Feld, kein Platzhaltertext.
    expect(await screen.findByLabelText(/^Leistungszeitraum/u)).toHaveValue('');
  });

  it('rechnet Betrag, Netto, Steuer und Brutto bei jeder Aenderung neu', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({ [WEG]: json(200, ENTWURF) });

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });
    expect(screen.getByTestId('rechnung-netto')).toHaveTextContent('0,00 €');

    await trageEin(nutzer, 1, '80');

    expect(screen.getByTestId('zeile-betrag-11')).toHaveTextContent('9.600,00 €');
    expect(screen.getByTestId('rechnung-netto')).toHaveTextContent('9.600,00 €');
    expect(screen.getByTestId('rechnung-steuer')).toHaveTextContent('1.824,00 €');
    expect(screen.getByTestId('rechnung-brutto')).toHaveTextContent('11.424,00 €');
    expect(screen.getByTestId('rechnung-steuer')).toHaveTextContent('19,00 %');
  });

  it('zeigt die Ueberschreitung als Hinweis und laesst das Speichern zu', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({ [WEG]: json(200, ENTWURF) });

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });
    await trageEin(nutzer, 1, '200');

    expect(mengenfeld(1)).toHaveAccessibleDescription('40,00 über dem Angebot');
    expect(screen.getByTestId('zeile-hinweis-11')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Speichern' })).toBeEnabled();
  });

  it('meldet eine negative Menge am Feld und schickt nichts hinaus', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ [WEG]: json(200, ENTWURF) });

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });
    await trageEin(nutzer, 1, '-5');
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(mengenfeld(1)).toHaveAccessibleDescription(
      'Bitte eine Menge mit höchstens zwei Nachkommastellen angeben, nicht negativ.',
    );
    expect(screen.getByTestId('rechnung-netto')).toHaveTextContent('—');
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('meldet drei Nachkommastellen am Feld und schickt nichts hinaus', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ [WEG]: json(200, ENTWURF) });

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });
    await trageEin(nutzer, 1, '1,234');
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(mengenfeld(1)).toHaveAccessibleDescription(
      'Bitte eine Menge mit höchstens zwei Nachkommastellen angeben, nicht negativ.',
    );
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('meldet einen leeren Leistungszeitraum am Feld und schickt nichts hinaus', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ [WEG]: json(200, ENTWURF) });

    renderSeite();
    await screen.findByLabelText(/^Leistungszeitraum/u);
    await nutzer.clear(screen.getByLabelText(/^Leistungszeitraum/u));
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(screen.getByLabelText(/^Leistungszeitraum/u)).toHaveAccessibleDescription(
      'Die Rechnung braucht einen Leistungszeitraum.',
    );
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('meldet ein fehlendes Rechnungsdatum am Feld und schickt nichts hinaus', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ [WEG]: json(200, ENTWURF) });

    renderSeite();
    await screen.findByLabelText(/^Rechnungsdatum/u);
    await nutzer.clear(screen.getByLabelText(/^Rechnungsdatum/u));
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(screen.getByLabelText(/^Rechnungsdatum/u)).toHaveAccessibleDescription(
      'Bitte das Datum der Rechnung angeben.',
    );
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('meldet eine leere Leistung an ihrem Feld und schickt nichts hinaus', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ [WEG]: json(200, ENTWURF) });

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });
    await nutzer.clear(leistungsfeld(1));
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(leistungsfeld(1)).toHaveAccessibleDescription('Jede Position braucht eine Bezeichnung.');
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('traegt im Entwurf genau eine Kupfertaste', async () => {
    fetchNachPfad({ [WEG]: json(200, ENTWURF) });

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });

    expect(kupfertasten().map((taste) => taste.textContent)).toEqual(['Speichern']);
  });
});

describe('RechnungPage — speichern (Kriterium 4)', () => {
  it('schickt Datum, Leistungszeitraum und je Position Kennung, Text und Menge', async () => {
    const nutzer = userEvent.setup();
    let gesendet: unknown;
    fetchNachPfad({
      [WEG]: json(200, ENTWURF),
      [WEG_AENDERN]: (rumpf) => {
        gesendet = alsJson(rumpf);
        return json(200, GESPEICHERT)();
      },
    });

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });
    await trageEin(nutzer, 1, '80');
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(await screen.findByText('Gespeichert.')).toBeInTheDocument();
    expect(gesendet).toEqual({
      rechnungDatum: '2026-10-01',
      leistungszeitraum: 'Oktober 2026',
      positionen: [
        { angebotPositionId: 11, bezeichnung: 'Entwicklung', menge: '80.00' },
        { angebotPositionId: 12, bezeichnung: 'Konzept', menge: '0.00' },
      ],
    });
  });

  it('uebernimmt die Werte der Antwort', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [WEG]: json(200, ENTWURF),
      [WEG_AENDERN]: json(200, GESPEICHERT),
    });

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });
    await trageEin(nutzer, 1, '79');
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(await screen.findByText('Gespeichert.')).toBeInTheDocument();
    expect(mengenfeld(1)).toHaveValue('80,00');
    expect(screen.getByTestId('rechnung-brutto')).toHaveTextContent('11.424,00 €');
  });

  it('zeigt einen Feldfehler des Servers am Feld', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [WEG]: json(200, ENTWURF),
      [WEG_AENDERN]: problem(422, 'Bitte prüfen', {
        leistungszeitraum: ['Der Zeitraum ist zu lang.'],
        'positionen[0].bezeichnung': ['Die Leistung ist zu lang.'],
      }),
    });

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(await screen.findByText('Der Zeitraum ist zu lang.')).toBeInTheDocument();
    expect(screen.getByLabelText(/^Leistungszeitraum/u)).toHaveAccessibleDescription(
      'Der Zeitraum ist zu lang.',
    );
    expect(leistungsfeld(1)).toHaveAccessibleDescription('Die Leistung ist zu lang.');
  });

  it('meldet einen Ausfall beim Speichern und behaelt die Eingaben', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [WEG]: json(200, ENTWURF),
      [WEG_AENDERN]: problem(503, 'kaputt'),
    });

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });
    await trageEin(nutzer, 1, '80');
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Die Rechnung wurde nicht gespeichert.',
    );
    expect(mengenfeld(1)).toHaveValue('80');
  });

  it('sperrt die Taste, solange das Speichern laeuft', async () => {
    const nutzer = userEvent.setup();
    const haltend = haltenderWeg();
    fetchNachPfad({ [WEG]: json(200, ENTWURF), [WEG_AENDERN]: haltend.weg });

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled();

    haltend.freigeben(json(200, GESPEICHERT));

    expect(await screen.findByText('Gespeichert.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Speichern' })).toBeEnabled();
  });
});

describe('RechnungPage — den Entwurf loeschen (Kriterium 12)', () => {
  it('fragt nach, loescht und fuehrt zur Liste', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      [WEG]: json(200, ENTWURF),
      [WEG_LOESCHEN]: leer(204),
    });

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });
    await waehle(nutzer, 'Entwurf löschen');
    const frage = await screen.findByRole('dialog');
    expect(frage).toHaveAccessibleName('Entwurf löschen: Rechnung (Entwurf)');
    await nutzer.click(within(frage).getByRole('button', { name: 'Entwurf löschen' }));

    expect(await screen.findByText('Die Liste')).toBeInTheDocument();
    expect(screen.getByTestId('adresse')).toHaveTextContent('/rechnungen');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/rechnungen/4',
      expect.objectContaining({ method: 'DELETE' }),
    );
  });

  it('loescht nicht, wenn die Rueckfrage abgebrochen wird', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ [WEG]: json(200, ENTWURF) });

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });
    await waehle(nutzer, 'Entwurf löschen');
    const frage = await screen.findByRole('dialog');
    await nutzer.click(within(frage).getByRole('button', { name: 'Abbrechen' }));

    expect(screen.getByTestId('adresse')).toHaveTextContent('/rechnungen/4');
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('meldet einen Ausfall beim Loeschen und bleibt auf der Rechnung', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [WEG]: json(200, ENTWURF),
      [WEG_LOESCHEN]: problem(503, 'kaputt'),
    });

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });
    await waehle(nutzer, 'Entwurf löschen');
    const frage = await screen.findByRole('dialog');
    await nutzer.click(within(frage).getByRole('button', { name: 'Entwurf löschen' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Der Entwurf wurde nicht gelöscht.',
    );
    expect(screen.getByTestId('adresse')).toHaveTextContent('/rechnungen/4');
  });
});

describe('RechnungPage — die gestellte Rechnung in diesem Stand (Issue #186 folgt)', () => {
  it('nennt sie mit ihrer Nummer und zeigt keine Eingabefelder', async () => {
    fetchNachPfad({ [WEG]: json(200, GESTELLT) });

    renderSeite();

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Rechnung 0001-2026' }),
    ).toBeInTheDocument();
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Die Ansicht der gestellten Rechnung folgt.',
    );
    expect(screen.queryAllByRole('textbox')).toEqual([]);
    expect(screen.queryByRole('button', { name: 'Speichern' })).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Aktionen für diese Rechnung' }),
    ).not.toBeInTheDocument();
    expect(kupfertasten()).toEqual([]);
  });
});
