import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import AngebotPage from './AngebotPage';
import { KopfPfadProvider } from '../components/KopfPfad';
import { fetchNachPfad, json, leer, problem } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';

const POSITION = {
  bezeichnung: 'Konzeption',
  abrechnungsmodus: 'AUFWAND',
  menge: 2.5,
  einheit: 'PERSONENTAG',
  einzelpreis: 1000.01,
  betrag: 2500.03,
};

const ENTWURF = {
  id: 9,
  vorgangId: 5,
  nummer: null,
  stand: 'ENTWURF',
  angebotDatum: '2026-09-24',
  gueltigBis: '2026-10-24',
  leistungsbeschreibung: 'Neue Website mit Redaktionssystem',
  zahlungsbedingungen: 'Zahlbar in 14 Tagen ohne Abzug',
  versendetAm: null,
  reaktionAm: null,
  positionen: [POSITION],
  summe: 2500.03,
};

const VERSENDET = {
  ...ENTWURF,
  nummer: 'A-2026-001',
  stand: 'VERSENDET',
  versendetAm: '2026-09-24T08:00:00Z',
};

/** Die Adresse, an der sich ablesen laesst, wohin ein Weg gefuehrt hat. */
function Adresse() {
  const ort = useLocation();
  return <p data-testid="adresse">{ort.pathname}</p>;
}

function renderSeite(start = '/vorgaenge/5/angebote/9') {
  return renderMitTheme(
    <MemoryRouter initialEntries={[start]}>
      <KopfPfadProvider>
        <Routes>
          <Route path="/vorgaenge/:id/angebote/:angebotId" element={<AngebotPage />} />
          <Route path="/vorgaenge/:id/angebote/:angebotId/bearbeiten" element={<p>Maske</p>} />
          <Route path="/vorgaenge/:id" element={<p>Vorgangsseite</p>} />
        </Routes>
        <Adresse />
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

/** Die Aktionen neben dem Inhalt — dort wird die eine Kupfertaste gezaehlt (E18). */
function aktionen() {
  return within(screen.getByTestId('angebot-aktionen'));
}

/**
 * Die Kupfertasten unter den Aktionen.
 *
 * Sie tragen den Kupferverlauf, die weichen eine Flaeche (CLAUDE-design.md, „Tasten"). Die Rolle
 * unterscheidet beide nicht: „Bearbeiten" ist ein Weg und damit ein Link, „Versenden" eine Handlung
 * und damit ein Knopf.
 */
function kupfertasten(): readonly HTMLElement[] {
  const felder = screen.queryAllByTestId('angebot-aktionen');
  return felder
    .flatMap((feld) => [
      ...within(feld).queryAllByRole('link'),
      ...within(feld).queryAllByRole('button'),
    ])
    .filter((taste) => getComputedStyle(taste).background.includes('linear-gradient'));
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('AngebotPage — was das Angebot zeigt (Kriterien 5, 18)', () => {
  it('traegt Nummer und Stand als die eine Ueberschrift samt Chip', async () => {
    fetchNachPfad({ 'GET /api/angebote/9': json(200, VERSENDET) });

    renderSeite();

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Angebot A-2026-001' }),
    ).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    // Das Wort traegt den Stand, die Toenung stuetzt ihn (CLAUDE-design.md, „Zustandsformen").
    expect(screen.getByTestId('chip')).toHaveTextContent('Versendet');
  });

  it('nennt einen Entwurf „Angebotsentwurf", weil er noch keine Nummer hat (Kriterium 11)', async () => {
    fetchNachPfad({ 'GET /api/angebote/9': json(200, ENTWURF) });

    renderSeite();

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Angebotsentwurf' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('chip')).toHaveTextContent('Entwurf');
  });

  it('stellt die Positionen als Tafel mit Menge, Einheit, Einzelpreis und Betrag', async () => {
    fetchNachPfad({ 'GET /api/angebote/9': json(200, VERSENDET) });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: 'Angebot A-2026-001' });

    expect(screen.getAllByRole('columnheader').map((kopf) => kopf.textContent)).toEqual([
      'Bezeichnung',
      'Abrechnung',
      'Menge',
      'Einheit',
      'Einzelpreis',
      'Betrag',
    ]);
    const zeile = within(screen.getAllByRole('row')[1]);
    expect(zeile.getByText('Konzeption')).toBeInTheDocument();
    expect(zeile.getByText('Aufwand')).toBeInTheDocument();
    expect(zeile.getByText('2,50')).toBeInTheDocument();
    expect(zeile.getByText('Personentag')).toBeInTheDocument();
    expect(zeile.getByText('1.000,01 €')).toBeInTheDocument();
    expect(zeile.getByText('2.500,03 €')).toBeInTheDocument();
  });

  it('nennt die Summe mit dem Hinweis auf die Umsatzsteuer', async () => {
    fetchNachPfad({ 'GET /api/angebote/9': json(200, VERSENDET) });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: 'Angebot A-2026-001' });

    expect(screen.getByTestId('angebot-summe')).toHaveTextContent('2.500,03 €');
    expect(
      screen.getByText('Alle Beträge netto, zzgl. gesetzlicher Umsatzsteuer'),
    ).toBeInTheDocument();
  });

  it('sagt es, wenn das Angebot noch keine Position hat', async () => {
    fetchNachPfad({
      'GET /api/angebote/9': json(200, { ...ENTWURF, positionen: [], summe: 0 }),
    });

    renderSeite();

    expect(await screen.findByRole('status')).toHaveTextContent('Noch keine Position');
  });

  it('nennt Angebotsdatum und Gueltigkeit in den Angaben', async () => {
    fetchNachPfad({ 'GET /api/angebote/9': json(200, VERSENDET) });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: 'Angebot A-2026-001' });

    const angaben = within(screen.getByTestId('angebot-angaben'));
    expect(angaben.getAllByRole('term').map((teil) => teil.textContent)).toEqual([
      'Nummer',
      'Angebotsdatum',
      'Gültig bis',
      'Dokument',
    ]);
    expect(angaben.getByText('24.09.2026')).toBeInTheDocument();
    expect(angaben.getByText('24.10.2026')).toBeInTheDocument();
  });

  it('zeigt Leistungsbeschreibung und Zahlungsbedingungen', async () => {
    fetchNachPfad({ 'GET /api/angebote/9': json(200, VERSENDET) });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: 'Angebot A-2026-001' });

    expect(screen.getByText('Neue Website mit Redaktionssystem')).toBeInTheDocument();
    expect(screen.getByText('Zahlbar in 14 Tagen ohne Abzug')).toBeInTheDocument();
  });

  it('laesst die Karte der Texte weg, wo keiner steht — ohne Platzhalter', async () => {
    fetchNachPfad({
      'GET /api/angebote/9': json(200, {
        ...ENTWURF,
        leistungsbeschreibung: null,
        zahlungsbedingungen: null,
      }),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: 'Angebotsentwurf' });

    expect(screen.queryByRole('heading', { name: 'Leistungsbeschreibung' })).not.toBeInTheDocument();
    expect(screen.queryByText('—')).not.toBeInTheDocument();
  });
});

describe('AngebotPage — der Beleg (Kriterium 14, E17)', () => {
  it('verweist auf das PDF zum Oeffnen im neuen Reiter', async () => {
    fetchNachPfad({ 'GET /api/angebote/9': json(200, VERSENDET) });

    renderSeite();

    const weg = await screen.findByRole('link', { name: 'PDF öffnen' });
    expect(weg).toHaveAttribute('href', '/api/angebote/9/pdf');
    expect(weg).toHaveAttribute('target', '_blank');
    expect(weg).toHaveAttribute('rel', 'noopener');
  });

  it('nennt beim Entwurf kein Dokument — es entsteht erst beim Versenden (Kriterium 10)', async () => {
    fetchNachPfad({ 'GET /api/angebote/9': json(200, ENTWURF) });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: 'Angebotsentwurf' });

    expect(screen.queryByRole('link', { name: 'PDF öffnen' })).not.toBeInTheDocument();
    expect(within(screen.getByTestId('angebot-angaben')).getByText('noch keines'))
      .toBeInTheDocument();
  });
});

describe('AngebotPage — die Aktionen am Entwurf (Kriterien 6, 7, 10)', () => {
  it('traegt „Versenden" als einzige Kupfertaste und „Bearbeiten" daneben', async () => {
    fetchNachPfad({ 'GET /api/angebote/9': json(200, ENTWURF) });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: 'Angebotsentwurf' });

    // Genau eine Kupfertaste je Ansicht (CLAUDE-design.md, Leitgedanke 2): Versenden ist die
    // Hauptsache am Entwurf, Bearbeiten tritt als weiche Taste daneben zurueck.
    expect(aktionen().getByRole('button', { name: 'Versenden' })).toBeInTheDocument();
    expect(aktionen().getByRole('link', { name: 'Bearbeiten' })).toHaveAttribute(
      'href',
      '/vorgaenge/5/angebote/9/bearbeiten',
    );
    expect(aktionen().queryByRole('button', { name: 'Annehmen' })).not.toBeInTheDocument();
  });

  it('versendet und nimmt Nummer und Stand aus der Antwort (Kriterien 10, 11)', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/angebote/9': json(200, ENTWURF),
      'POST /api/angebote/9/versenden': json(200, VERSENDET),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: 'Angebotsentwurf' });
    await nutzer.click(screen.getByRole('button', { name: 'Versenden' }));

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Angebot A-2026-001' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('chip')).toHaveTextContent('Versendet');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/9/versenden',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('nennt alle fehlenden Angaben, wenn der Versand abgewiesen wird (Kriterium 12, E22)', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'GET /api/angebote/9': json(200, ENTWURF),
      'POST /api/angebote/9/versenden': problem(409, 'Zum Versenden fehlen Angaben.', {
        positionen: ['Das Angebot braucht mindestens eine Position.'],
        gueltigBis: ['Die Gueltigkeit darf nicht vor dem Angebotsdatum liegen.'],
        firma: ['Die Firma des Vorgangs braucht Strasse, PLZ und Ort.'],
        eigeneAngaben: ['Unter „Eigene Angaben" fehlt der Name.', 'Unter „Eigene Angaben" fehlen Strasse, PLZ oder Ort.'],
      }),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: 'Angebotsentwurf' });
    await nutzer.click(screen.getByRole('button', { name: 'Versenden' }));

    const meldung = within(await screen.findByRole('alert'));
    expect(meldung.getByText(/Zum Versenden dieses Angebots fehlen Angaben/)).toBeInTheDocument();
    // Alle vier Schluessel auf einmal, in der Leserichtung der Antwort (E22).
    expect(meldung.getAllByRole('listitem').map((zeile) => zeile.textContent)).toEqual([
      'Das Angebot braucht mindestens eine Position.',
      'Die Gueltigkeit darf nicht vor dem Angebotsdatum liegen.',
      'Die Firma des Vorgangs braucht Strasse, PLZ und Ort.',
      'Unter „Eigene Angaben" fehlt der Name.',
      'Unter „Eigene Angaben" fehlen Strasse, PLZ oder Ort.',
    ]);
  });

  it('meldet einen Fehlschlag ohne Feldliste als Ausfall', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'GET /api/angebote/9': json(200, ENTWURF),
      'POST /api/angebote/9/versenden': leer(500),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: 'Angebotsentwurf' });
    await nutzer.click(screen.getByRole('button', { name: 'Versenden' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht ausgeführt');
  });

  it('verwirft den Entwurf erst nach der Rueckfrage und fuehrt dann auf den Vorgang', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/angebote/9': json(200, ENTWURF),
      'DELETE /api/angebote/9': leer(204),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: 'Angebotsentwurf' });
    await nutzer.click(aktionen().getByRole('button', { name: 'Weitere Aktionen' }));
    await nutzer.click(screen.getByRole('menuitem', { name: 'Verwerfen' }));

    // Die Rueckfrage ist ein eigener Dialog und kein `confirm` des Browsers (E19).
    const dialog = within(await screen.findByRole('dialog'));
    expect(dialog.getByText(/verschwindet dann vollständig/)).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalledWith(
      '/api/angebote/9',
      expect.objectContaining({ method: 'DELETE' }),
    );

    await nutzer.click(dialog.getByRole('button', { name: 'Verwerfen' }));

    expect(await screen.findByText('Vorgangsseite')).toBeInTheDocument();
    expect(screen.getByTestId('adresse')).toHaveTextContent('/vorgaenge/5');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/9',
      expect.objectContaining({ method: 'DELETE' }),
    );
  });

  it('meldet, wenn das Verwerfen nicht durchgeht', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'GET /api/angebote/9': json(200, ENTWURF),
      'DELETE /api/angebote/9': leer(500),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: 'Angebotsentwurf' });
    await nutzer.click(aktionen().getByRole('button', { name: 'Weitere Aktionen' }));
    await nutzer.click(screen.getByRole('menuitem', { name: 'Verwerfen' }));
    await nutzer.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Verwerfen' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht ausgeführt');
  });
});

describe('AngebotPage — die Reaktion des Kunden (Kriterien 17, 18, F13)', () => {
  it.each([['VERSENDET'], ['ABGELAUFEN'], ['ABGELOEST']])(
    'bietet am Stand %s „Annehmen" und „Ablehnen" an',
    async (stand) => {
      fetchNachPfad({ 'GET /api/angebote/9': json(200, { ...VERSENDET, stand }) });

      renderSeite();
      await screen.findByRole('heading', { level: 1, name: 'Angebot A-2026-001' });

      expect(aktionen().getByRole('button', { name: 'Annehmen' })).toBeInTheDocument();
      expect(aktionen().getByRole('button', { name: 'Ablehnen' })).toBeInTheDocument();
      // Kein Bearbeiten und kein Versenden: Ein versendetes Angebot ist fest (Kriterium 13).
      expect(aktionen().queryByRole('link', { name: 'Bearbeiten' })).not.toBeInTheDocument();
      expect(aktionen().queryByRole('button', { name: 'Versenden' })).not.toBeInTheDocument();
    },
  );

  it.each([
    ['ENTWURF', 'Versenden'],
    ['VERSENDET', 'Annehmen'],
    ['ABGELAUFEN', 'Annehmen'],
    ['ABGELOEST', 'Annehmen'],
  ])('traegt am Stand %s genau eine Kupfertaste: „%s"', async (stand, aufschrift) => {
    fetchNachPfad({ 'GET /api/angebote/9': json(200, { ...VERSENDET, stand }) });

    renderSeite();
    await screen.findByTestId('angebot-aktionen');

    // Genau eine Kupfertaste je Ansicht (CLAUDE-design.md, Leitgedanke 2).
    expect(kupfertasten().map((taste) => taste.textContent)).toEqual([aufschrift]);
  });

  it.each([['ANGENOMMEN'], ['ABGELEHNT']])(
    'bietet am endgueltigen Stand %s keine Aktion mehr an',
    async (stand) => {
      fetchNachPfad({
        'GET /api/angebote/9': json(200, { ...VERSENDET, stand, reaktionAm: '2026-09-26T09:30:00Z' }),
      });

      renderSeite();
      await screen.findByRole('heading', { level: 1, name: 'Angebot A-2026-001' });

      expect(screen.queryByTestId('angebot-aktionen')).not.toBeInTheDocument();
      expect(kupfertasten()).toEqual([]);
      // Der Beleg bleibt erreichbar (Kriterium 14).
      expect(screen.getByRole('link', { name: 'PDF öffnen' })).toBeInTheDocument();
    },
  );

  it.each([
    ['Annehmen', 'ANGENOMMEN', 'Angenommen', 'POST /api/angebote/9/annehmen'],
    ['Ablehnen', 'ABGELEHNT', 'Abgelehnt', 'POST /api/angebote/9/ablehnen'],
  ])('haelt mit „%s" die Reaktion fest und nimmt den Stand aus der Antwort', async (
    taste,
    stand,
    wort,
    schluessel,
  ) => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'GET /api/angebote/9': json(200, VERSENDET),
      [schluessel]: json(200, { ...VERSENDET, stand, reaktionAm: '2026-09-26T09:30:00Z' }),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: 'Angebot A-2026-001' });
    await nutzer.click(screen.getByRole('button', { name: taste }));

    expect(await screen.findByText(wort)).toBeInTheDocument();
    expect(screen.queryByTestId('angebot-aktionen')).not.toBeInTheDocument();
  });
});

describe('AngebotPage — unsinnige Kennung, unbekanntes Angebot, Ausfall', () => {
  it('faengt eine nicht numerische Kennung ab, bevor sie an die Schnittstelle geht', async () => {
    const fetchMock = fetchNachPfad({});

    renderSeite('/vorgaenge/5/angebote/keine-zahl');

    expect(await screen.findByRole('alert')).toHaveTextContent('gibt es nicht');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('meldet ein unbekanntes Angebot', async () => {
    fetchNachPfad({ 'GET /api/angebote/9': leer(404) });

    renderSeite();

    expect(await screen.findByRole('alert')).toHaveTextContent('gibt es nicht');
  });

  it('meldet den Ausfall der Schnittstelle', async () => {
    fetchNachPfad({ 'GET /api/angebote/9': leer(503) });

    renderSeite();

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht zu erreichen');
  });

  it('zeigt waehrend des Ladens einen Hinweis statt einer leeren Seite', () => {
    fetchNachPfad({ 'GET /api/angebote/9': json(200, VERSENDET) });

    renderSeite();

    expect(screen.getByText('Das Angebot wird geladen …')).toBeInTheDocument();
  });
});

describe('AngebotPage — der Auftrag zum angenommenen Angebot (Kriterium 1, F9, Plan E3)', () => {
  const ANGENOMMEN = {
    ...VERSENDET,
    stand: 'ANGENOMMEN',
    reaktionAm: '2026-09-26T09:30:00Z',
  };

  const AUFTRAG = {
    id: 3,
    vorgangId: 5,
    angebotId: 9,
    angebotNummer: 'A-2026-001',
    nummer: 'AU-2026-001',
    status: 'OFFEN',
    auftragDatum: '2026-09-28',
    kundenbestellnummer: null,
    leistungAb: null,
    leistungBis: null,
    positionen: [],
    summe: 0,
  };

  it('zeigt „Auftrag anlegen" als Kupfertaste, solange anlegbar gilt und kein Auftrag steht', async () => {
    fetchNachPfad({
      'GET /api/angebote/9': json(200, ANGENOMMEN),
      'GET /api/angebote/9/auftrag': json(200, { auftrag: null, anlegbar: true }),
    });

    renderSeite();

    const taste = await screen.findByRole('link', { name: 'Auftrag anlegen' });
    expect(taste).toHaveAttribute('href', '/vorgaenge/5/angebote/9/auftrag/neu');
    expect(kupfertasten()).toEqual([taste]);
  });

  it('fuehrt mit der Taste in die Anlege-Maske unter dem Angebot', async () => {
    fetchNachPfad({
      'GET /api/angebote/9': json(200, ANGENOMMEN),
      'GET /api/angebote/9/auftrag': json(200, { auftrag: null, anlegbar: true }),
    });

    renderSeite();
    await userEvent.click(await screen.findByRole('link', { name: 'Auftrag anlegen' }));

    expect(screen.getByTestId('adresse')).toHaveTextContent('/vorgaenge/5/angebote/9/auftrag/neu');
  });

  it('zeigt keine Taste, wenn das Anlegen nicht zugelassen ist — etwa am abgeschlossenen Vorgang', async () => {
    fetchNachPfad({
      'GET /api/angebote/9': json(200, ANGENOMMEN),
      'GET /api/angebote/9/auftrag': json(200, { auftrag: null, anlegbar: false }),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: 'Angebot A-2026-001' });
    await vi.waitFor(() => {
      expect(fetch).toHaveBeenCalledTimes(2);
    });

    expect(screen.queryByRole('link', { name: 'Auftrag anlegen' })).not.toBeInTheDocument();
    expect(screen.queryByTestId('angebot-aktionen')).not.toBeInTheDocument();
    expect(within(screen.getByTestId('angebot-angaben')).queryByText('Auftrag')).not.toBeInTheDocument();
  });

  it('zeigt statt der Taste die Nummer des bestehenden Auftrags in den Angaben', async () => {
    fetchNachPfad({
      'GET /api/angebote/9': json(200, ANGENOMMEN),
      'GET /api/angebote/9/auftrag': json(200, { auftrag: AUFTRAG, anlegbar: false }),
    });

    renderSeite();

    const angaben = within(await screen.findByTestId('angebot-angaben'));
    expect(await angaben.findByRole('link', { name: 'AU-2026-001' })).toHaveAttribute(
      'href',
      '/vorgaenge/5/auftraege/3',
    );
    expect(angaben.getByText('Auftrag')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Auftrag anlegen' })).not.toBeInTheDocument();
  });

  it('zeigt auch dann keine Taste, wenn ein Auftrag steht und das Backend anlegbar meldete', async () => {
    fetchNachPfad({
      'GET /api/angebote/9': json(200, ANGENOMMEN),
      'GET /api/angebote/9/auftrag': json(200, { auftrag: AUFTRAG, anlegbar: true }),
    });

    renderSeite();

    await within(await screen.findByTestId('angebot-angaben')).findByText('AU-2026-001');
    expect(screen.queryByRole('link', { name: 'Auftrag anlegen' })).not.toBeInTheDocument();
  });

  it('hat einen eigenen Ladezustand und laesst die uebrige Ansicht stehen', async () => {
    fetchNachPfad({
      'GET /api/angebote/9': json(200, ANGENOMMEN),
      'GET /api/angebote/9/auftrag': () => new Promise<Response>(() => undefined),
    });

    renderSeite();

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Angebot A-2026-001' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('chip')).toHaveTextContent('Angenommen');
    expect(within(screen.getByTestId('angebot-angaben')).getByText('wird geladen …'))
      .toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Auftrag anlegen' })).not.toBeInTheDocument();
  });

  it('bleibt lesbar, wenn die Auskunft zum Auftrag scheitert', async () => {
    fetchNachPfad({
      'GET /api/angebote/9': json(200, ANGENOMMEN),
      'GET /api/angebote/9/auftrag': problem(500, 'Kaputt'),
    });

    renderSeite();

    const angaben = within(await screen.findByTestId('angebot-angaben'));
    expect(await angaben.findByText('gerade nicht zu erreichen')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: 'Angebot A-2026-001' })).toBeInTheDocument();
    expect(screen.getByTestId('angebot-summe')).toHaveTextContent('2.500,03 €');
    expect(screen.queryByRole('link', { name: 'Auftrag anlegen' })).not.toBeInTheDocument();
  });

  it('fragt an einem nicht angenommenen Angebot gar nicht nach dem Auftrag', async () => {
    const fetch = fetchNachPfad({ 'GET /api/angebote/9': json(200, VERSENDET) });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: 'Angebot A-2026-001' });

    expect(fetch).toHaveBeenCalledTimes(1);
    expect(within(screen.getByTestId('angebot-angaben')).queryByText('Auftrag')).not.toBeInTheDocument();
  });

  it('fragt nach dem Annehmen nach dem Auftrag und zeigt dann die Taste', async () => {
    fetchNachPfad({
      'GET /api/angebote/9': json(200, VERSENDET),
      'POST /api/angebote/9/annehmen': json(200, ANGENOMMEN),
      'GET /api/angebote/9/auftrag': json(200, { auftrag: null, anlegbar: true }),
    });

    renderSeite();
    await userEvent.click(await screen.findByRole('button', { name: 'Annehmen' }));

    expect(await screen.findByRole('link', { name: 'Auftrag anlegen' })).toBeInTheDocument();
  });
});
