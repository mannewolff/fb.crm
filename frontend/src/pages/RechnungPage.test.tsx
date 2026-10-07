import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import RechnungPage from './RechnungPage';
import { KopfPfadProvider } from '../components/KopfPfad';
import { alsJson, fetchNachPfad, json, leer, problem } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';

/**
 * Die Entwurfsmaske der Rechnung (Issue #185), das Stellen und die gestellte Rechnung (Issue #186).
 *
 * Die Proben gehen den Weg des Freiberuflers: Er oeffnet den Entwurf, traegt an einer Position eine
 * Menge ein, sieht Betrag und Summen mitlaufen, speichert — und stellt die Rechnung, oder loescht
 * den Entwurf, wenn er ihn nicht braucht. Geprueft wird dabei zweierlei, was sich mit einem Blick
 * auf die Seite nicht unterscheiden laesst: <b>was dasteht</b> und <b>was hinausgeht</b>.
 *
 * Die Mengen und Betraege der Proben sind die des Akzeptanzkriteriums: 160 Stunden zu 120,00
 * angeboten, davon 80 abgerechnet — 9.600,00 netto, 1.824,00 Steuer, 11.424,00 brutto.
 */

const WEG = 'GET /api/rechnungen/4';
const WEG_AENDERN = 'PUT /api/rechnungen/4';
const WEG_LOESCHEN = 'DELETE /api/rechnungen/4';
const WEG_STELLEN = 'POST /api/rechnungen/4/stellen';
const WEG_ZUSTAND = 'PUT /api/rechnungen/4/zustand';

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

/** Dieselbe Rechnung, bezahlt beziehungsweise abgeschrieben (Issue #253). */
const BEZAHLT = { ...GESTELLT, zustand: 'BEZAHLT' };
const ABGESCHRIEBEN = { ...GESTELLT, zustand: 'ABGESCHRIEBEN' };

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

/** Die Taste „Rechnung stellen" der Seite — nicht die gleichnamige in der Rueckfrage. */
function stellenTaste(): HTMLElement {
  return within(screen.getByTestId('rechnung-aktionen')).getByRole('button', {
    name: 'Rechnung stellen',
  });
}

/** Oeffnet die Rueckfrage vor dem Stellen und gibt sie heraus. */
async function frageOeffnen(
  nutzer: ReturnType<typeof userEvent.setup>,
): Promise<HTMLElement> {
  await nutzer.click(stellenTaste());
  return screen.findByRole('dialog');
}

/** Oeffnet die Rueckfrage und bestaetigt sie. */
async function stelle(nutzer: ReturnType<typeof userEvent.setup>) {
  const frage = await frageOeffnen(nutzer);
  await nutzer.click(within(frage).getByRole('button', { name: 'Rechnung stellen' }));
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

  it('stellt den Kopf einer Zahlenspalte rechtsbuendig und die uebrigen links (Issue #286)', async () => {
    fetchNachPfad({ [WEG]: json(200, ENTWURF) });

    renderSeite();
    const tafel = within(await screen.findByRole('table', { name: 'Positionen' }));

    for (const name of ['Einzelpreis', 'Angeboten', 'Abgerechnet', 'Offen', 'Betrag']) {
      expect(tafel.getByRole('columnheader', { name })).toHaveStyle({ textAlign: 'right' });
    }
    // „Jetzt abrechnen" haelt ein Eingabefeld und keine rechtsbuendige Zahl.
    for (const name of ['Leistung', 'Einheit', 'Jetzt abrechnen']) {
      expect(tafel.getByRole('columnheader', { name })).toHaveStyle({ textAlign: 'left' });
    }
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

    // „Rechnung stellen" ist die Hauptaktion des Entwurfs; „Speichern" tritt daneben zurueck.
    expect(kupfertasten().map((taste) => taste.textContent)).toEqual(['Rechnung stellen']);
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

describe('RechnungPage — die Rechnung stellen (Kriterien 5 und 13, Issue #186)', () => {
  it('fragt vor dem Stellen nach und nennt den Bruttobetrag', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({ [WEG]: json(200, GESPEICHERT) });

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });
    const frage = await frageOeffnen(nutzer);

    expect(frage).toHaveAccessibleName('Rechnung stellen');
    expect(frage).toHaveTextContent('11.424,00 €');
    expect(frage).toHaveTextContent('weder geändert noch gelöscht');
  });

  it('stellt nicht, wenn die Rueckfrage abgebrochen wird, und gibt den Fokus zurueck', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ [WEG]: json(200, GESPEICHERT) });

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });
    const frage = await frageOeffnen(nutzer);
    await nutzer.click(within(frage).getByRole('button', { name: 'Abbrechen' }));

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(stellenTaste()).toHaveFocus();
  });

  it('stellt nicht, wenn die Rueckfrage mit Escape geschlossen wird', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ [WEG]: json(200, GESPEICHERT) });

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });
    await frageOeffnen(nutzer);
    await nutzer.keyboard('{Escape}');

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(stellenTaste()).toHaveFocus();
  });

  it('zeigt nach der Bestaetigung die gestellte Rechnung mit ihrer Nummer', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({ [WEG]: json(200, GESPEICHERT), [WEG_STELLEN]: json(200, GESTELLT) });

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });
    await stelle(nutzer);

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Rechnung 0001-2026' }),
    ).toBeInTheDocument();
  });

  it('schickt ungespeicherte Aenderungen vor dem Stellen hinaus', async () => {
    const nutzer = userEvent.setup();
    const reihenfolge: string[] = [];
    let gesendet: unknown;
    fetchNachPfad({
      [WEG]: json(200, ENTWURF),
      [WEG_AENDERN]: (rumpf) => {
        gesendet = alsJson(rumpf);
        reihenfolge.push('PUT');
        return json(200, GESPEICHERT)();
      },
      [WEG_STELLEN]: () => {
        reihenfolge.push('POST');
        return json(200, GESTELLT)();
      },
    });

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });
    await trageEin(nutzer, 1, '80');
    await stelle(nutzer);

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Rechnung 0001-2026' }),
    ).toBeInTheDocument();
    expect(reihenfolge).toEqual(['PUT', 'POST']);
    expect(gesendet).toEqual({
      rechnungDatum: '2026-10-01',
      leistungszeitraum: 'Oktober 2026',
      positionen: [
        { angebotPositionId: 11, bezeichnung: 'Entwicklung', menge: '80.00' },
        { angebotPositionId: 12, bezeichnung: 'Konzept', menge: '0.00' },
      ],
    });
  });

  it('stellt nicht, wenn das Speichern davor scheitert', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      [WEG]: json(200, ENTWURF),
      [WEG_AENDERN]: problem(503, 'kaputt'),
    });

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });
    await trageEin(nutzer, 1, '80');
    await nutzer.click(stellenTaste());

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Die Rechnung wurde nicht gespeichert.',
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    // Nur das Lesen und das gescheiterte Speichern — kein Stellen.
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('zeigt bei 422 die fehlenden Angaben mit den zwei Verweisen', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [WEG]: json(200, GESPEICHERT),
      [WEG_STELLEN]: problem(422, 'Fuer das Stellen fehlen Pflichtangaben.', {
        strasse: ['Diese Angabe ist fuer eine Rechnung noetig.'],
        'firma.ort': ['Diese Angabe ist fuer eine Rechnung noetig.'],
        zukunft: ['Etwas Neues fehlt.'],
      }),
    });

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });
    await stelle(nutzer);
    const hinweis = await screen.findByTestId('rechnung-pflichtangaben');

    expect(within(hinweis).getByText(/^Straße und Hausnummer —/u)).toBeInTheDocument();
    expect(within(hinweis).getByText(/^Ort der Firma —/u)).toBeInTheDocument();
    // Ein Feld, das diese Oberflaeche nicht kennt, steht mit seinem Namen da statt zu fehlen.
    expect(within(hinweis).getByText('zukunft — Etwas Neues fehlt.')).toBeInTheDocument();
    expect(within(hinweis).getByRole('link', { name: 'Eigene Angaben' })).toHaveAttribute(
      'href',
      '/eigene-angaben',
    );
    expect(within(hinweis).getByRole('link', { name: 'Adler AG' })).toHaveAttribute(
      'href',
      '/firmen/5',
    );
  });

  it('zeigt bei 409 die Meldung des Servers', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [WEG]: json(200, GESPEICHERT),
      [WEG_STELLEN]: problem(409, 'Der Zustand der Rechnung lässt diesen Schritt nicht zu.'),
    });

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });
    await stelle(nutzer);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Der Zustand der Rechnung lässt diesen Schritt nicht zu.',
    );
  });

  it('meldet einen Ausfall des Weges beim Stellen', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [WEG]: json(200, GESPEICHERT),
      [WEG_STELLEN]: () => Promise.reject(new Error('Leitung weg')),
    });

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });
    await stelle(nutzer);

    expect(await screen.findByRole('alert')).toHaveTextContent('Die Rechnung wurde nicht gestellt.');
  });

  it('sperrt das Stellen ohne Position mit einer Menge ueber 0 und sagt warum', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({ [WEG]: json(200, ENTWURF) });

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });

    expect(stellenTaste()).toBeDisabled();
    expect(screen.getByTestId('stellen-grund')).toHaveTextContent(
      'Ohne eine Position mit einer Menge über 0',
    );

    await trageEin(nutzer, 1, '80');

    expect(stellenTaste()).toBeEnabled();
    expect(screen.queryByTestId('stellen-grund')).not.toBeInTheDocument();
  });

  it('sperrt die Tasten, solange das Stellen laeuft', async () => {
    const nutzer = userEvent.setup();
    const haltend = haltenderWeg();
    fetchNachPfad({ [WEG]: json(200, GESPEICHERT), [WEG_STELLEN]: haltend.weg });

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });
    await stelle(nutzer);

    expect(stellenTaste()).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled();

    haltend.freigeben(json(200, GESTELLT));

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Rechnung 0001-2026' }),
    ).toBeInTheDocument();
  });
});

describe('RechnungPage — die gestellte Rechnung (Kriterien 14, 15 und 24)', () => {
  it('nennt sie mit ihrer Nummer und zeigt Datum, Zeitraum, Steuersatz und Zahlungsziel', async () => {
    fetchNachPfad({ [WEG]: json(200, GESTELLT) });

    renderSeite();

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Rechnung 0001-2026' }),
    ).toBeInTheDocument();
    const stammdaten = screen.getByTestId('rechnung-stammdaten');
    expect(stammdaten).toHaveTextContent('01.10.2026');
    expect(stammdaten).toHaveTextContent('Oktober 2026');
    expect(stammdaten).toHaveTextContent('19,00 %');
    expect(stammdaten).toHaveTextContent('14 Tage');
  });

  it('stellt im Beleg den Kopf einer Zahlenspalte rechtsbuendig und die uebrigen links (Issue #286)', async () => {
    fetchNachPfad({ [WEG]: json(200, GESTELLT) });

    renderSeite();
    const tafel = within(await screen.findByRole('table', { name: 'Positionen' }));

    for (const name of ['Anzahl', 'Einzelpreis', 'Gesamtpreis']) {
      expect(tafel.getByRole('columnheader', { name })).toHaveStyle({ textAlign: 'right' });
    }
    for (const name of ['Einheit', 'Leistung']) {
      expect(tafel.getByRole('columnheader', { name })).toHaveStyle({ textAlign: 'left' });
    }
  });

  it('zeigt nur die abgerechneten Positionen mit Anzahl, Einheit, Text und Preisen', async () => {
    fetchNachPfad({ [WEG]: json(200, GESTELLT) });

    renderSeite();
    const tafel = await screen.findByRole('table', { name: 'Positionen' });
    const zeilen = within(tafel).getAllByRole('row');

    // Kopfzeile und genau eine Position: „Konzept" traegt die Menge 0 und gehoert nicht dazu.
    expect(zeilen).toHaveLength(2);
    expect(zeilen[1]).toHaveTextContent('80,00');
    expect(zeilen[1]).toHaveTextContent('Stunde');
    expect(zeilen[1]).toHaveTextContent('Entwicklung');
    expect(zeilen[1]).toHaveTextContent('120,00 €');
    expect(zeilen[1]).toHaveTextContent('9.600,00 €');
    expect(screen.getByTestId('rechnung-netto')).toHaveTextContent('9.600,00 €');
    expect(screen.getByTestId('rechnung-steuer')).toHaveTextContent('1.824,00 €');
    expect(screen.getByTestId('rechnung-steuer')).toHaveTextContent('19,00 %');
    expect(screen.getByTestId('rechnung-brutto')).toHaveTextContent('11.424,00 €');
  });

  it('bietet genau eine Kupfertaste „Herunterladen" auf das Dokument', async () => {
    fetchNachPfad({ [WEG]: json(200, GESTELLT) });

    renderSeite();
    const verweis = await screen.findByRole('link', { name: 'Herunterladen' });

    expect(verweis).toHaveAttribute('href', '/api/rechnungen/4/dokument');
    expect(verweis).toHaveAttribute('download');
    expect(kupfertasten().map((taste) => taste.textContent)).toEqual(['Herunterladen']);
  });

  it('zeigt keine Eingabefelder, kein Speichern und kein Loeschen', async () => {
    fetchNachPfad({ [WEG]: json(200, GESTELLT) });

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });

    expect(screen.queryAllByRole('textbox')).toEqual([]);
    expect(screen.queryByRole('button', { name: 'Speichern' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Rechnung stellen' })).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Aktionen für diese Rechnung' }),
    ).not.toBeInTheDocument();
  });

  it('nennt fehlenden Zeitraum und fehlendes Zahlungsziel als Wort', async () => {
    fetchNachPfad({
      [WEG]: json(200, { ...GESTELLT, leistungszeitraum: null, zahlungszielTage: null }),
    });

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });
    const stammdaten = screen.getByTestId('rechnung-stammdaten');

    expect(stammdaten).toHaveTextContent('nicht angegeben');
    expect(stammdaten).toHaveTextContent('nicht festgelegt');
  });
});

describe('RechnungPage — der Ausgang der Forderung (Issue #253)', () => {
  /** Die Tasten der Kopfkarte in ihrer Reihenfolge. */
  function aktionen(): readonly (string | null)[] {
    return within(screen.getByTestId('rechnung-aktionen'))
      .getAllByRole('button')
      .map((taste) => taste.textContent);
  }

  it('bietet an der gestellten Rechnung „Als bezahlt markieren" und „Abschreiben"', async () => {
    fetchNachPfad({ [WEG]: json(200, GESTELLT) });

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });

    expect(aktionen()).toEqual(['Als bezahlt markieren', 'Abschreiben']);
    expect(screen.queryByRole('button', { name: 'Zurück auf gestellt' })).not.toBeInTheDocument();
  });

  it('setzt sie ohne Rueckfrage auf bezahlt und zeigt danach den neuen Zustand', async () => {
    const fetchMock = fetchNachPfad({ [WEG]: json(200, GESTELLT), [WEG_ZUSTAND]: json(200, BEZAHLT) });
    const nutzer = userEvent.setup();

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });
    await nutzer.click(screen.getByRole('button', { name: 'Als bezahlt markieren' }));

    expect(await screen.findByText('Bezahlt')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/rechnungen/4/zustand',
      expect.objectContaining({ method: 'PUT', body: JSON.stringify({ zustand: 'BEZAHLT' }) }),
    );
  });

  it('fragt vor dem Abschreiben nach und schickt erst nach der Bestaetigung', async () => {
    const fetchMock = fetchNachPfad({
      [WEG]: json(200, GESTELLT),
      [WEG_ZUSTAND]: json(200, ABGESCHRIEBEN),
    });
    const nutzer = userEvent.setup();

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });
    await nutzer.click(screen.getByRole('button', { name: 'Abschreiben' }));
    const frage = await screen.findByRole('dialog');

    // Solange die Rueckfrage offen ist, ging nichts hinaus.
    expect(fetchMock).not.toHaveBeenCalledWith('/api/rechnungen/4/zustand', expect.anything());

    await nutzer.click(within(frage).getByRole('button', { name: 'Abschreiben' }));

    expect(await screen.findByText('Abgeschrieben')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/rechnungen/4/zustand',
      expect.objectContaining({ body: JSON.stringify({ zustand: 'ABGESCHRIEBEN' }) }),
    );
  });

  it('schickt nichts, wenn die Rueckfrage vor dem Abschreiben abgebrochen wird', async () => {
    const fetchMock = fetchNachPfad({ [WEG]: json(200, GESTELLT) });
    const nutzer = userEvent.setup();

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });
    await nutzer.click(screen.getByRole('button', { name: 'Abschreiben' }));
    const frage = await screen.findByRole('dialog');
    await nutzer.click(within(frage).getByRole('button', { name: 'Abbrechen' }));

    expect(fetchMock).not.toHaveBeenCalledWith('/api/rechnungen/4/zustand', expect.anything());
  });

  it.each([
    ['BEZAHLT', BEZAHLT],
    ['ABGESCHRIEBEN', ABGESCHRIEBEN],
  ])('bietet an einer %s-Rechnung „Zurück auf gestellt"', async (_zustand, antwort) => {
    const fetchMock = fetchNachPfad({ [WEG]: json(200, antwort), [WEG_ZUSTAND]: json(200, GESTELLT) });
    const nutzer = userEvent.setup();

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });

    expect(aktionen()).toEqual(['Zurück auf gestellt']);

    await nutzer.click(screen.getByRole('button', { name: 'Zurück auf gestellt' }));

    expect(await screen.findByText('Gestellt')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/rechnungen/4/zustand',
      expect.objectContaining({ body: JSON.stringify({ zustand: 'GESTELLT' }) }),
    );
  });

  it('zeigt die Leseansicht samt Download auch an einer bezahlten Rechnung', async () => {
    fetchNachPfad({ [WEG]: json(200, BEZAHLT) });

    renderSeite();

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Rechnung 0001-2026' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Herunterladen' })).toHaveAttribute(
      'href',
      '/api/rechnungen/4/dokument',
    );
    expect(screen.queryAllByRole('textbox')).toEqual([]);
  });

  it('zeigt einen abgewiesenen Uebergang als Meldung und bleibt stehen', async () => {
    fetchNachPfad({
      [WEG]: json(200, BEZAHLT),
      [WEG_ZUSTAND]: problem(409, 'Der Zustand der Rechnung laesst diesen Schritt nicht zu.'),
    });
    const nutzer = userEvent.setup();

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });
    await nutzer.click(screen.getByRole('button', { name: 'Zurück auf gestellt' }));

    expect(
      await screen.findByText('Der Zustand der Rechnung laesst diesen Schritt nicht zu.'),
    ).toBeInTheDocument();
    // Die Seite steht weiter, der Zustand ist unveraendert.
    expect(screen.getByText('Bezahlt')).toBeInTheDocument();
  });

  it('meldet einen Ausfall beim Umstellen mit eigenem Satz', async () => {
    fetchNachPfad({ [WEG]: json(200, GESTELLT), [WEG_ZUSTAND]: () => Promise.reject(new Error('weg')) });
    const nutzer = userEvent.setup();

    renderSeite();
    await screen.findByRole('table', { name: 'Positionen' });
    await nutzer.click(screen.getByRole('button', { name: 'Als bezahlt markieren' }));

    expect(
      await screen.findByText('Der Zustand wurde nicht geändert. Bitte später erneut versuchen.'),
    ).toBeInTheDocument();
  });
});
