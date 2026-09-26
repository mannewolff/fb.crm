import { screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { renderMitTheme } from '../test/render';
import Tafel from './Tafel';

function renderTafel() {
  return renderMitTheme(
    <Tafel beschriftung="Vorgänge" spalten={['Nr.', 'Vorgang', 'Phase']}>
      <tr>
        <td>#12</td>
        <td>Neue Website</td>
        <td>Anbahnung</td>
      </tr>
      <tr>
        <td>#13</td>
        <td>Wartungsvertrag</td>
        <td>Anbahnung</td>
      </tr>
    </Tafel>,
  );
}

describe('Tafel', () => {
  it('traegt den uebergebenen Namen', () => {
    renderTafel();

    expect(screen.getByRole('table', { name: 'Vorgänge' })).toBeInTheDocument();
  });

  it('macht aus jeder Spaltenbeschriftung eine Kopfzelle ihrer Spalte', () => {
    renderTafel();

    const kopf = screen.getAllByRole('columnheader');
    expect(kopf.map((zelle) => zelle.textContent)).toEqual(['Nr.', 'Vorgang', 'Phase']);
    // `scope="col"` ist die Zuordnung, aus der der Screenreader beim Vorlesen einer Zelle ihre
    // Spalte nennt. Ohne sie bleibt die Kopfzeile eine Zeile wie jede andere.
    expect(kopf.map((zelle) => zelle.getAttribute('scope'))).toEqual(['col', 'col', 'col']);
  });

  it('fuehrt die Kopfzeile und jede uebergebene Zeile als Zeile', () => {
    renderTafel();

    const zeilen = screen.getAllByRole('row');
    expect(zeilen).toHaveLength(3);
    expect(within(zeilen[0]).getAllByRole('columnheader')).toHaveLength(3);
    expect(within(zeilen[1]).getAllByRole('cell').map((zelle) => zelle.textContent)).toEqual([
      '#12',
      'Neue Website',
      'Anbahnung',
    ]);
    expect(within(zeilen[2]).getAllByRole('cell').map((zelle) => zelle.textContent)).toEqual([
      '#13',
      'Wartungsvertrag',
      'Anbahnung',
    ]);
  });

  it('schreibt die Spaltenkoepfe in Satzschreibung statt in Versalien', () => {
    renderTafel();

    // Satzschreibung ueberall, keine Versalien mit Laufweite (CLAUDE-design.md, Typografie).
    for (const zelle of screen.getAllByRole('columnheader')) {
      expect(zelle).not.toHaveStyle({ textTransform: 'uppercase' });
    }
  });

  it('gliedert die Zeilen ohne Linien', () => {
    renderTafel();

    // Karten statt Linien (E10): Die Zeilen trennt der Hover, nicht ein Strich.
    for (const zelle of screen.getAllByRole('cell')) {
      expect(zelle).not.toHaveStyle({ borderBottomStyle: 'solid' });
    }
  });
});
