import { screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import Angebotsliste from './Angebotsliste';
import type { AngebotZeile } from '../api/angebote';
import { renderMitTheme } from '../test/render';

const JUENGER: AngebotZeile = {
  id: 12,
  angebotDatum: '2026-09-26',
  status: 'ANGELEGT',
  intern: false,
  summeInCent: 120000,
};

const AELTER: AngebotZeile = {
  id: 9,
  angebotDatum: '2026-09-24',
  status: 'BESTELLT',
  intern: false,
  summeInCent: 250003,
};

function renderListe(angebote: readonly AngebotZeile[]) {
  return renderMitTheme(
    <MemoryRouter>
      <Angebotsliste angebote={angebote} />
    </MemoryRouter>,
  );
}

describe('Angebotsliste (Kriterium 7)', () => {
  it('traegt Datum, Status und Summe je Zeile', () => {
    renderListe([AELTER]);

    const zeile = within(screen.getAllByRole('row')[1]);
    expect(zeile.getByRole('link', { name: '24.09.2026' })).toBeInTheDocument();
    expect(zeile.getByText('Bestellt')).toBeInTheDocument();
    expect(zeile.getByText('2.500,03 €')).toBeInTheDocument();
  });

  it('gibt die Reihenfolge der Antwort wieder — neueste zuerst', () => {
    // Sortiert wird am Server; die Liste ordnet nicht um.
    renderListe([JUENGER, AELTER]);

    expect(screen.getAllByRole('link').map((weg) => weg.textContent)).toEqual([
      '26.09.2026',
      '24.09.2026',
    ]);
  });

  it('oeffnet aus jeder Zeile das Angebot', () => {
    renderListe([JUENGER, AELTER]);

    expect(screen.getByRole('link', { name: '26.09.2026' })).toHaveAttribute(
      'href',
      '/angebote/12',
    );
    expect(screen.getByRole('link', { name: '24.09.2026' })).toHaveAttribute(
      'href',
      '/angebote/9',
    );
  });

  it('nennt die Spalten der Tafel', () => {
    renderListe([AELTER]);

    expect(screen.getAllByRole('columnheader').map((kopf) => kopf.textContent)).toEqual([
      'Datum',
      'Status',
      'Summe',
    ]);
  });

  it('sagt es, wenn die Firma noch kein Angebot hat, statt eine leere Tafel zu zeigen', () => {
    renderListe([]);

    expect(screen.getByRole('status')).toHaveTextContent('Noch kein Angebot');
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
});
