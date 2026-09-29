import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
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

const ANGEBOT = {
  id: 9,
  firmaId: 5,
  firmaName: 'Adler AG',
  ansprechpartnerId: 8,
  ansprechpartnerName: 'Eva Adler',
  status: 'ABGEGEBEN',
  angebotDatum: '2026-09-24',
  beschreibung: 'Neue Website mit Redaktionssystem',
  positionen: [POSITION],
  summe: 2500.03,
};

const UEBERSCHRIFT = 'Angebot vom 24.09.2026';

function renderSeite(start = '/angebote/9') {
  return renderMitTheme(
    <MemoryRouter initialEntries={[start]}>
      <KopfPfadProvider>
        <Routes>
          <Route path="/angebote/:angebotId" element={<AngebotPage />} />
        </Routes>
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

/** Die Aktionen neben der Ueberschrift — dort wird die eine Kupfertaste gezaehlt. */
function aktionen() {
  return within(screen.getByTestId('angebot-aktionen'));
}

/**
 * Die Kupfertasten unter den Aktionen.
 *
 * Sie tragen den Kupferverlauf, die weichen eine Flaeche (CLAUDE-design.md, „Tasten"). Die Rolle
 * unterscheidet beide nicht: „Bearbeiten" ist ein Weg und damit ein Link, „Status weiter" eine
 * Handlung und damit ein Knopf.
 */
function kupfertasten(): readonly HTMLElement[] {
  const feld = screen.getByTestId('angebot-aktionen');
  return [...within(feld).queryAllByRole('link'), ...within(feld).queryAllByRole('button')].filter(
    (taste) => getComputedStyle(taste).background.includes('linear-gradient'),
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('AngebotPage — was das Angebot zeigt (Issue #127)', () => {
  it('traegt „Angebot vom <Datum>" als die eine Ueberschrift', async () => {
    fetchNachPfad({ 'GET /api/angebote/9': json(200, ANGEBOT) });

    renderSeite();

    expect(await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT })).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
  });

  it('nennt Firma, Ansprechpartner, Angebotsdatum und Status in den Angaben', async () => {
    fetchNachPfad({ 'GET /api/angebote/9': json(200, ANGEBOT) });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    const angaben = within(screen.getByTestId('angebot-angaben'));
    expect(angaben.getAllByRole('term').map((teil) => teil.textContent)).toEqual([
      'Firma',
      'Ansprechpartner',
      'Angebotsdatum',
      'Status',
    ]);
    expect(angaben.getByRole('link', { name: 'Adler AG' })).toHaveAttribute('href', '/firmen/5');
    expect(angaben.getByText('Eva Adler')).toBeInTheDocument();
    expect(angaben.getByText('24.09.2026')).toBeInTheDocument();
    // Das Wort traegt den Status, die Toenung stuetzt ihn (CLAUDE-design.md, „Zustandsformen").
    expect(angaben.getByTestId('chip')).toHaveTextContent('Abgegeben');
  });

  it('laesst die Zeile des Ansprechpartners weg, wo das Angebot keinen traegt', async () => {
    fetchNachPfad({
      'GET /api/angebote/9': json(200, {
        ...ANGEBOT,
        ansprechpartnerId: null,
        ansprechpartnerName: null,
      }),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    const angaben = within(screen.getByTestId('angebot-angaben'));
    expect(angaben.queryByText('Ansprechpartner')).not.toBeInTheDocument();
    expect(angaben.getByText('Adler AG')).toBeInTheDocument();
  });

  it('zeigt die Beschreibung', async () => {
    fetchNachPfad({ 'GET /api/angebote/9': json(200, ANGEBOT) });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    expect(screen.getByRole('heading', { name: 'Beschreibung' })).toBeInTheDocument();
    expect(screen.getByText('Neue Website mit Redaktionssystem')).toBeInTheDocument();
  });

  it('laesst die Karte der Beschreibung weg, wo keine steht — ohne Platzhalter', async () => {
    fetchNachPfad({ 'GET /api/angebote/9': json(200, { ...ANGEBOT, beschreibung: null }) });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    expect(screen.queryByRole('heading', { name: 'Beschreibung' })).not.toBeInTheDocument();
    expect(screen.queryByText('—')).not.toBeInTheDocument();
  });

  it('stellt die Positionen als Tafel mit Menge, Einheit, Einzelpreis und Betrag', async () => {
    fetchNachPfad({ 'GET /api/angebote/9': json(200, ANGEBOT) });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

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
    fetchNachPfad({ 'GET /api/angebote/9': json(200, ANGEBOT) });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    expect(screen.getByTestId('angebot-summe')).toHaveTextContent('2.500,03 €');
    expect(
      screen.getByText('Alle Beträge netto, zzgl. gesetzlicher Umsatzsteuer'),
    ).toBeInTheDocument();
  });

  it('sagt es, wenn das Angebot noch keine Position hat', async () => {
    fetchNachPfad({
      'GET /api/angebote/9': json(200, { ...ANGEBOT, positionen: [], summe: 0 }),
    });

    renderSeite();

    expect(await screen.findByRole('status')).toHaveTextContent('Noch keine Position');
  });
});

describe('AngebotPage — die Aktionen (Issue #127, Kriterien 4, 5)', () => {
  it('traegt „Status weiter" als einzige Kupfertaste, „Status zurück" und „Bearbeiten" weich', async () => {
    fetchNachPfad({ 'GET /api/angebote/9': json(200, ANGEBOT) });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    // Genau eine Kupfertaste je Ansicht (CLAUDE-design.md, Leitgedanke 2).
    expect(kupfertasten().map((taste) => taste.textContent)).toEqual(['Status weiter']);
    expect(aktionen().getByRole('button', { name: 'Status zurück' })).toBeInTheDocument();
    expect(aktionen().getByRole('link', { name: 'Bearbeiten' })).toHaveAttribute(
      'href',
      '/angebote/9/bearbeiten',
    );
  });

  it('bietet bei „angelegt" kein „Status zurück" an', async () => {
    fetchNachPfad({ 'GET /api/angebote/9': json(200, { ...ANGEBOT, status: 'ANGELEGT' }) });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    expect(aktionen().queryByRole('button', { name: 'Status zurück' })).not.toBeInTheDocument();
    expect(aktionen().getByRole('button', { name: 'Status weiter' })).toBeInTheDocument();
  });

  it('bietet bei „abgerechnet" kein „Status weiter" an, Bearbeiten aber schon', async () => {
    fetchNachPfad({ 'GET /api/angebote/9': json(200, { ...ANGEBOT, status: 'ABGERECHNET' }) });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });

    expect(aktionen().queryByRole('button', { name: 'Status weiter' })).not.toBeInTheDocument();
    expect(kupfertasten()).toEqual([]);
    expect(aktionen().getByRole('button', { name: 'Status zurück' })).toBeInTheDocument();
    expect(aktionen().getByRole('link', { name: 'Bearbeiten' })).toBeInTheDocument();
  });

  it.each([
    ['Status weiter', 'POST /api/angebote/9/status/weiter', 'BESTELLT', 'Bestellt'],
    ['Status zurück', 'POST /api/angebote/9/status/zurueck', 'ANGELEGT', 'Angelegt'],
  ])('schaltet mit „%s" und nimmt den Status aus der Antwort', async (
    taste,
    schluessel,
    status,
    wort,
  ) => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      'GET /api/angebote/9': json(200, ANGEBOT),
      [schluessel]: json(200, { ...ANGEBOT, status }),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });
    await nutzer.click(aktionen().getByRole('button', { name: taste }));

    expect(await screen.findByText(wort)).toBeInTheDocument();
    expect(within(screen.getByTestId('angebot-angaben')).getByTestId('chip')).toHaveTextContent(
      wort,
    );
    expect(fetchMock).toHaveBeenCalledWith(
      schluessel.slice('POST '.length),
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('meldet, wenn der Statuswechsel nicht durchgeht, und behaelt den bisherigen Status', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      'GET /api/angebote/9': json(200, ANGEBOT),
      'POST /api/angebote/9/status/weiter': problem(409, 'In diese Richtung gibt es keinen weiteren Status.'),
    });

    renderSeite();
    await screen.findByRole('heading', { level: 1, name: UEBERSCHRIFT });
    await nutzer.click(aktionen().getByRole('button', { name: 'Status weiter' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht geändert');
    expect(within(screen.getByTestId('angebot-angaben')).getByTestId('chip')).toHaveTextContent(
      'Abgegeben',
    );
  });
});

describe('AngebotPage — unsinnige Kennung, unbekanntes Angebot, Ausfall', () => {
  it('faengt eine nicht numerische Kennung ab, bevor sie an die Schnittstelle geht', async () => {
    const fetchMock = fetchNachPfad({});

    renderSeite('/angebote/keine-zahl');

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
    fetchNachPfad({ 'GET /api/angebote/9': json(200, ANGEBOT) });

    renderSeite();

    expect(screen.getByText('Das Angebot wird geladen …')).toBeInTheDocument();
  });
});
