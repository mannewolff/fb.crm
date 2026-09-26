import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { renderMitTheme } from '../test/render';
import Kopfkarte from './Kopfkarte';
import ZustandsChip from './ZustandsChip';

describe('Kopfkarte', () => {
  it('stellt Titel, Zeile, Chips und Aktionen in dieser Reihenfolge', () => {
    renderMitTheme(
      <Kopfkarte
        malName="IT Bildungshaus"
        titel="IT Bildungshaus"
        zeile="Konsul-Smidt-Str. 24 · 28197 Bremen"
        chips={<ZustandsChip wort="Aktiv" toenung="salbei" />}
        aktionen={
          <button type="button" data-testid="probe-aktion">
            Bearbeiten
          </button>
        }
      />,
    );

    // Der Titel ist die Ueberschrift der Ansicht: Eine Seite traegt genau eine `h1`, und die
    // Kopfkarte ist der Ort, an dem sie steht.
    expect(screen.getByRole('heading', { level: 1, name: 'IT Bildungshaus' })).toBeInTheDocument();
    // Die Reihenfolge im DOM ist die Reihenfolge, in der der Screenreader vorliest — und die
    // Vorlage zeigt sie so (Z. 133–151).
    const abschnitte = screen.getAllByTestId(/^kopfkarte-/).map((teil) => teil.dataset.testid);
    expect(abschnitte).toEqual([
      'kopfkarte-titel',
      'kopfkarte-zeile',
      'kopfkarte-chips',
      'kopfkarte-aktionen',
    ]);
    expect(screen.getByText('Konsul-Smidt-Str. 24 · 28197 Bremen')).toBeInTheDocument();
    expect(screen.getByText('Aktiv')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Bearbeiten' })).toBeInTheDocument();
  });

  it('haelt das Mal aus dem Vorgelesenen heraus', () => {
    renderMitTheme(
      <Kopfkarte malName="IT Bildungshaus" malToenung="flieder" titel="IT Bildungshaus" />,
    );

    // Das Kuerzel steht neben dem Namen und doppelte ihn nur.
    const mal = screen.getByTestId('mal');
    expect(mal).toHaveAttribute('aria-hidden', 'true');
    expect(mal).toHaveTextContent('IB');
  });

  it('bleibt ohne Zeile, Chips und Aktionen eine Kopfkarte mit Titel', () => {
    renderMitTheme(<Kopfkarte malName="Gabi Rosenbaum" titel="Gabi Rosenbaum" />);

    const abschnitte = screen.getAllByTestId(/^kopfkarte-/).map((teil) => teil.dataset.testid);
    expect(abschnitte).toEqual(['kopfkarte-titel']);
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});
