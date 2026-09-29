import { screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import Angebotsliste from './Angebotsliste';
import type { AngebotZeile } from '../api/angebote';
import { renderMitTheme } from '../test/render';

const ENTWURF: AngebotZeile = {
  id: 12,
  nummer: null,
  stand: 'ENTWURF',
  angebotDatum: '2026-09-26',
  gueltigBis: '2026-10-26',
  summeInCent: 120000,
};

const VERSENDET: AngebotZeile = {
  id: 9,
  nummer: 'A-2026-001',
  stand: 'VERSENDET',
  angebotDatum: '2026-09-24',
  gueltigBis: '2026-10-24',
  summeInCent: 250003,
};

function renderListe(angebote: readonly AngebotZeile[]) {
  return renderMitTheme(
    <MemoryRouter>
      <Angebotsliste angebote={angebote} />
    </MemoryRouter>,
  );
}

describe('Angebotsliste (Kriterium 20)', () => {
  it('traegt Nummer, Datum, Gueltigkeit, Zustand und Summe je Zeile', () => {
    renderListe([VERSENDET]);

    const zeile = within(screen.getAllByRole('row')[1]);
    expect(zeile.getByRole('link', { name: 'A-2026-001' })).toBeInTheDocument();
    expect(zeile.getByText('24.09.2026')).toBeInTheDocument();
    expect(zeile.getByText('24.10.2026')).toBeInTheDocument();
    expect(zeile.getByText('Versendet')).toBeInTheDocument();
    expect(zeile.getByText('2.500,03 €')).toBeInTheDocument();
  });

  it('nennt ein Angebot ohne Nummer „Entwurf" (Kriterium 11)', () => {
    renderListe([ENTWURF]);

    expect(screen.getByRole('link', { name: 'Entwurf' })).toBeInTheDocument();
  });

  it('gibt die Reihenfolge der Antwort wieder — Entwuerfe oben (E25)', () => {
    // Sortiert wird am Server; die Liste ordnet nicht um. Zwei Sortierungen fuer dieselbe Liste
    // liefen beim naechsten Zustand auseinander.
    renderListe([ENTWURF, VERSENDET]);

    expect(screen.getAllByRole('link').map((weg) => weg.textContent)).toEqual([
      'Entwurf',
      'A-2026-001',
    ]);
  });

  it('oeffnet aus jeder Zeile das Angebot', () => {
    renderListe([ENTWURF, VERSENDET]);

    expect(screen.getByRole('link', { name: 'Entwurf' })).toHaveAttribute(
      'href',
      '/angebote/12',
    );
    expect(screen.getByRole('link', { name: 'A-2026-001' })).toHaveAttribute(
      'href',
      '/angebote/9',
    );
  });

  it('nennt die Spalten der Tafel', () => {
    renderListe([VERSENDET]);

    expect(screen.getAllByRole('columnheader').map((kopf) => kopf.textContent)).toEqual([
      'Nummer',
      'Datum',
      'Gültig bis',
      'Zustand',
      'Summe',
    ]);
  });

  it('sagt es, wenn die Firma noch kein Angebot hat, statt eine leere Tafel zu zeigen', () => {
    renderListe([]);

    expect(screen.getByRole('status')).toHaveTextContent('Noch kein Angebot');
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
});
