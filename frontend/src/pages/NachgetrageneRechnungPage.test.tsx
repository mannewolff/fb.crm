import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import NachgetrageneRechnungPage from './NachgetrageneRechnungPage';
import { KopfPfadProvider } from '../components/KopfPfad';
import { fetchNachPfad, json, leer, problem } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';

/**
 * Die Einzelansicht der nachgetragenen Rechnung (#254, Kriterien 6, 7, 8, 10, 11; Plan #259, E21,
 * E22, E24).
 *
 * Geprueft wird wie in `RechnungPage.test.tsx` zweierlei: <b>was dasteht</b> — Nummer, beide Chips,
 * die Firma als Verweis, Datum und Betraege, der Download oder der Satz ohne Dokument — und <b>was
 * hinausgeht</b>: der eigene Weg fuer den Zustand und das Loeschen erst nach der Rueckfrage.
 */

const WEG = 'GET /api/nachgetragene-rechnungen/7';
const WEG_ZUSTAND = 'PUT /api/nachgetragene-rechnungen/7/zustand';
const WEG_LOESCHEN = 'DELETE /api/nachgetragene-rechnungen/7';

const GESTELLT = {
  id: 7,
  firmaId: 5,
  firmaName: 'Adler AG',
  nummer: 'RE-2026-014',
  rechnungDatum: '2026-03-12',
  netto: 1000,
  brutto: 1190,
  zustand: 'GESTELLT',
  dokument: true,
};

const OHNE_DOKUMENT = { ...GESTELLT, dokument: false };
const BEZAHLT = { ...GESTELLT, zustand: 'BEZAHLT' };
const ABGESCHRIEBEN = { ...GESTELLT, zustand: 'ABGESCHRIEBEN' };

/** Die Adresse — daran haengt, ob das Loeschen zur Liste gefuehrt hat. */
function Adresse() {
  const ort = useLocation();
  return <p data-testid="adresse">{ort.pathname}</p>;
}

function renderSeite(adresse = '/rechnungen/nachgetragen/7') {
  return renderMitTheme(
    <MemoryRouter initialEntries={[adresse]}>
      <KopfPfadProvider>
        <Routes>
          <Route path="/rechnungen" element={<p>Die Liste</p>} />
          <Route path="/rechnungen/nachgetragen/:id" element={<NachgetrageneRechnungPage />} />
        </Routes>
        <Adresse />
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

/** Wartet, bis die Rechnung dasteht. */
async function geladen() {
  return screen.findByRole('heading', { level: 1, name: 'Rechnung RE-2026-014' });
}

/** Die Aufschriften der Schalter in der Kopfkarte — ohne Verweise und ohne das ⋯-Menue. */
function tasten(): readonly string[] {
  return within(screen.getByTestId('kopfkarte-aktionen'))
    .queryAllByRole('button')
    .filter((taste) => taste.getAttribute('aria-haspopup') !== 'menu')
    .map((taste) => taste.textContent);
}

/** Oeffnet das ⋯-Menue der Rechnung und waehlt einen Eintrag. */
async function waehle(nutzer: ReturnType<typeof userEvent.setup>, eintrag: string) {
  await nutzer.click(screen.getByRole('button', { name: 'Aktionen für diese Rechnung' }));
  await nutzer.click(await screen.findByRole('menuitem', { name: eintrag }));
}

/** Die Eintraege des ⋯-Menues. */
async function menueeintraege(nutzer: ReturnType<typeof userEvent.setup>) {
  await nutzer.click(screen.getByRole('button', { name: 'Aktionen für diese Rechnung' }));
  const eintraege = (await screen.findAllByRole('menuitem')).map((eintrag) => eintrag.textContent);
  await nutzer.keyboard('{Escape}');
  return eintraege;
}

/** Ein Weg, der haengt, bis der Test ihn freigibt — nur so ist „es laeuft" messbar. */
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

afterEach(() => {
  vi.restoreAllMocks();
});

describe('NachgetrageneRechnungPage — Laden, Fehler, Leer', () => {
  it('zeigt waehrend des Ladens einen Hinweis', () => {
    fetchNachPfad({ [WEG]: () => new Promise<Response>(() => undefined) });

    renderSeite();

    expect(screen.getByText('Die Rechnung wird geladen …')).toBeInTheDocument();
  });

  it('meldet eine Rechnung, die es nicht gibt', async () => {
    fetchNachPfad({ [WEG]: problem(404, 'Nicht gefunden.') });

    renderSeite();

    expect(
      await screen.findByText('Diese nachgetragene Rechnung gibt es nicht.'),
    ).toBeInTheDocument();
  });

  it('fragt bei einer Kennung, die keine ist, gar nicht erst nach', async () => {
    const fetchMock = fetchNachPfad({});

    renderSeite('/rechnungen/nachgetragen/abc');

    expect(
      await screen.findByText('Diese nachgetragene Rechnung gibt es nicht.'),
    ).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('meldet einen Ausfall mit eigenem Satz', async () => {
    fetchNachPfad({ [WEG]: () => Promise.reject(new Error('weg')) });

    renderSeite();

    expect(
      await screen.findByText(
        'Die Rechnung ist gerade nicht zu erreichen. Bitte später erneut versuchen.',
      ),
    ).toBeInTheDocument();
  });
});

describe('NachgetrageneRechnungPage — Erfolg', () => {
  it('zeigt Nummer, Zustand und daneben die Kennzeichnung „nachgetragen"', async () => {
    fetchNachPfad({ [WEG]: json(200, GESTELLT) });

    renderSeite();
    await geladen();

    const chips = screen.getByTestId('kopfkarte-chips');
    expect(within(chips).getByText('Gestellt')).toBeInTheDocument();
    expect(within(chips).getByText('nachgetragen')).toBeInTheDocument();
  });

  it('fuehrt die Firma als Verweis auf ihre Seite', async () => {
    fetchNachPfad({ [WEG]: json(200, GESTELLT) });

    renderSeite();
    await geladen();

    expect(screen.getByRole('link', { name: 'Adler AG' })).toHaveAttribute('href', '/firmen/5');
  });

  it('zeigt Rechnungsdatum, Netto- und Bruttobetrag', async () => {
    fetchNachPfad({ [WEG]: json(200, GESTELLT) });

    renderSeite();
    await geladen();

    const angaben = screen.getByTestId('nachtrag-angaben');
    expect(within(angaben).getByText('12.03.2026')).toBeInTheDocument();
    expect(within(angaben).getByText('1.000,00 €')).toBeInTheDocument();
    expect(within(angaben).getByText('1.190,00 €')).toBeInTheDocument();
  });

  it('bietet mit hinterlegtem Dokument genau dieses zum Herunterladen an', async () => {
    fetchNachPfad({ [WEG]: json(200, GESTELLT) });

    renderSeite();
    await geladen();

    const verweis = screen.getByRole('link', { name: 'Herunterladen' });
    expect(verweis).toHaveAttribute('href', '/api/nachgetragene-rechnungen/7/dokument');
    expect(verweis).toHaveAttribute('download');
    expect(screen.queryByText('Kein Dokument hinterlegt')).not.toBeInTheDocument();
  });

  it('sagt ohne Dokument „Kein Dokument hinterlegt" und bietet keinen Verweis an', async () => {
    fetchNachPfad({ [WEG]: json(200, OHNE_DOKUMENT) });

    renderSeite();
    await geladen();

    expect(screen.getByText('Kein Dokument hinterlegt')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Herunterladen' })).not.toBeInTheDocument();
  });

  it('fuehrt „Bearbeiten" auf die Maske der nachgetragenen Rechnung', async () => {
    fetchNachPfad({ [WEG]: json(200, GESTELLT) });

    renderSeite();
    await geladen();

    expect(screen.getByRole('link', { name: 'Bearbeiten' })).toHaveAttribute(
      'href',
      '/rechnungen/nachgetragen/7/bearbeiten',
    );
  });
});

describe('NachgetrageneRechnungPage — Zustand (Kriterium 8, E24)', () => {
  it('bietet einer gestellten Rechnung „Als bezahlt markieren" und im Menue „Abschreiben"', async () => {
    fetchNachPfad({ [WEG]: json(200, GESTELLT) });
    const nutzer = userEvent.setup();

    renderSeite();
    await geladen();

    expect(tasten()).toEqual(['Als bezahlt markieren']);
    expect(await menueeintraege(nutzer)).toEqual(['Abschreiben', 'Löschen']);
  });

  it.each([
    ['BEZAHLT', BEZAHLT],
    ['ABGESCHRIEBEN', ABGESCHRIEBEN],
  ])('bietet einer %s-Rechnung nur „Zurück auf gestellt"', async (_zustand, antwort) => {
    fetchNachPfad({ [WEG]: json(200, antwort) });
    const nutzer = userEvent.setup();

    renderSeite();
    await geladen();

    expect(tasten()).toEqual(['Zurück auf gestellt']);
    expect(await menueeintraege(nutzer)).toEqual(['Löschen']);
  });

  it('setzt sie ueber den eigenen Weg auf bezahlt und zeigt danach den neuen Zustand', async () => {
    const fetchMock = fetchNachPfad({
      [WEG]: json(200, GESTELLT),
      [WEG_ZUSTAND]: json(200, BEZAHLT),
    });
    const nutzer = userEvent.setup();

    renderSeite();
    await geladen();
    await nutzer.click(screen.getByRole('button', { name: 'Als bezahlt markieren' }));

    expect(
      await within(screen.getByTestId('kopfkarte-chips')).findByText('Bezahlt'),
    ).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/nachgetragene-rechnungen/7/zustand',
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
    await geladen();
    await waehle(nutzer, 'Abschreiben');
    const frage = await screen.findByRole('dialog');

    expect(fetchMock).not.toHaveBeenCalledWith(
      '/api/nachgetragene-rechnungen/7/zustand',
      expect.anything(),
    );

    await nutzer.click(within(frage).getByRole('button', { name: 'Abschreiben' }));

    expect(
      await within(screen.getByTestId('kopfkarte-chips')).findByText('Abgeschrieben'),
    ).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/nachgetragene-rechnungen/7/zustand',
      expect.objectContaining({ body: JSON.stringify({ zustand: 'ABGESCHRIEBEN' }) }),
    );
  });

  it.each([
    ['BEZAHLT', BEZAHLT],
    ['ABGESCHRIEBEN', ABGESCHRIEBEN],
  ])('stellt eine %s-Rechnung zurueck auf gestellt', async (_zustand, antwort) => {
    const fetchMock = fetchNachPfad({
      [WEG]: json(200, antwort),
      [WEG_ZUSTAND]: json(200, GESTELLT),
    });
    const nutzer = userEvent.setup();

    renderSeite();
    await geladen();
    await nutzer.click(screen.getByRole('button', { name: 'Zurück auf gestellt' }));

    expect(
      await within(screen.getByTestId('kopfkarte-chips')).findByText('Gestellt'),
    ).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/nachgetragene-rechnungen/7/zustand',
      expect.objectContaining({ method: 'PUT', body: JSON.stringify({ zustand: 'GESTELLT' }) }),
    );
  });

  it('zeigt einen abgewiesenen Uebergang als Meldung und bleibt stehen', async () => {
    fetchNachPfad({
      [WEG]: json(200, BEZAHLT),
      [WEG_ZUSTAND]: problem(409, 'Der Zustand der Rechnung laesst diesen Schritt nicht zu.'),
    });
    const nutzer = userEvent.setup();

    renderSeite();
    await geladen();
    await nutzer.click(screen.getByRole('button', { name: 'Zurück auf gestellt' }));

    expect(
      await screen.findByText('Der Zustand der Rechnung laesst diesen Schritt nicht zu.'),
    ).toBeInTheDocument();
    expect(within(screen.getByTestId('kopfkarte-chips')).getByText('Bezahlt')).toBeInTheDocument();
  });

  it('meldet einen Ausfall beim Umstellen mit eigenem Satz', async () => {
    fetchNachPfad({
      [WEG]: json(200, GESTELLT),
      [WEG_ZUSTAND]: () => Promise.reject(new Error('weg')),
    });
    const nutzer = userEvent.setup();

    renderSeite();
    await geladen();
    await nutzer.click(screen.getByRole('button', { name: 'Als bezahlt markieren' }));

    expect(
      await screen.findByText('Der Zustand wurde nicht geändert. Bitte später erneut versuchen.'),
    ).toBeInTheDocument();
  });
});

describe('NachgetrageneRechnungPage — Deaktiviert', () => {
  it('sperrt die Zustandstaste, solange das Umstellen laeuft', async () => {
    const halt = haltenderWeg();
    fetchNachPfad({ [WEG]: json(200, GESTELLT), [WEG_ZUSTAND]: halt.weg });
    const nutzer = userEvent.setup();

    renderSeite();
    await geladen();
    await nutzer.click(screen.getByRole('button', { name: 'Als bezahlt markieren' }));

    expect(screen.getByRole('button', { name: 'Als bezahlt markieren' })).toBeDisabled();

    halt.freigeben(json(200, BEZAHLT));

    expect(await screen.findByRole('button', { name: 'Zurück auf gestellt' })).toBeEnabled();
  });
});

describe('NachgetrageneRechnungPage — Löschen (Kriterium 10)', () => {
  it('fragt vorher nach, loescht erst nach der Bestaetigung und fuehrt auf die Liste', async () => {
    const fetchMock = fetchNachPfad({ [WEG]: json(200, GESTELLT), [WEG_LOESCHEN]: leer(204) });
    const nutzer = userEvent.setup();

    renderSeite();
    await geladen();
    await waehle(nutzer, 'Löschen');
    const frage = await screen.findByRole('dialog');

    expect(fetchMock).not.toHaveBeenCalledWith(
      '/api/nachgetragene-rechnungen/7',
      expect.objectContaining({ method: 'DELETE' }),
    );

    await nutzer.click(within(frage).getByRole('button', { name: 'Löschen' }));

    expect(await screen.findByText('Die Liste')).toBeInTheDocument();
    expect(screen.getByTestId('adresse')).toHaveTextContent(/^\/rechnungen$/);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/nachgetragene-rechnungen/7',
      expect.objectContaining({ method: 'DELETE' }),
    );
  });

  it('loescht nichts, wenn die Rueckfrage abgebrochen wird', async () => {
    const fetchMock = fetchNachPfad({ [WEG]: json(200, GESTELLT) });
    const nutzer = userEvent.setup();

    renderSeite();
    await geladen();
    await waehle(nutzer, 'Löschen');
    const frage = await screen.findByRole('dialog');
    await nutzer.click(within(frage).getByRole('button', { name: 'Abbrechen' }));

    expect(fetchMock).not.toHaveBeenCalledWith(
      '/api/nachgetragene-rechnungen/7',
      expect.objectContaining({ method: 'DELETE' }),
    );
    expect(screen.getByTestId('adresse')).toHaveTextContent('/rechnungen/nachgetragen/7');
  });

  it('meldet einen Ausfall beim Loeschen und bleibt stehen', async () => {
    fetchNachPfad({
      [WEG]: json(200, GESTELLT),
      [WEG_LOESCHEN]: () => Promise.reject(new Error('weg')),
    });
    const nutzer = userEvent.setup();

    renderSeite();
    await geladen();
    await waehle(nutzer, 'Löschen');
    await nutzer.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Löschen' }));

    expect(
      await screen.findByText(
        'Die Rechnung wurde nicht gelöscht. Bitte später erneut versuchen.',
      ),
    ).toBeInTheDocument();
    expect(screen.getByTestId('adresse')).toHaveTextContent('/rechnungen/nachgetragen/7');
  });
});
