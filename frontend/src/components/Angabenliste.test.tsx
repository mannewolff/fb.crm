import { screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { renderMitTheme } from '../test/render';
import Angabenliste from './Angabenliste';

describe('Angabenliste', () => {
  it('setzt je Zeile ein Paar aus Bezeichnung und Wert, in der Reihenfolge der Eingabe', () => {
    renderMitTheme(
      <Angabenliste
        zeilen={[
          { name: 'Steuernummer', wert: '12/345/67890' },
          { name: 'Status', wert: <strong>bestellt</strong> },
        ]}
      />,
    );

    // `dt` traegt die Rolle „term", `dd` die Rolle „definition".
    expect(screen.getAllByRole('term').map((bezeichnung) => bezeichnung.textContent)).toEqual([
      'Steuernummer',
      'Status',
    ]);
    const werte = screen.getAllByRole('definition');
    expect(werte.map((wert) => wert.textContent)).toEqual(['12/345/67890', 'bestellt']);
    expect(within(werte[1]).getByText('bestellt').tagName).toBe('STRONG');
  });

  it('richtet Bezeichnung und Wert mittig aus, wenn ein Wert hoeher als eine Zeile ist', () => {
    renderMitTheme(<Angabenliste zeilen={[{ name: 'A', wert: 'a' }]} mittig testId="liste" />);

    expect(screen.getByTestId('liste')).toHaveStyle({ alignItems: 'center' });
  });

  it('laesst die Ausrichtung ohne „mittig" beim Raster', () => {
    renderMitTheme(<Angabenliste zeilen={[{ name: 'A', wert: 'a' }]} testId="liste" />);

    expect(screen.getByTestId('liste')).not.toHaveStyle({ alignItems: 'center' });
  });
});
