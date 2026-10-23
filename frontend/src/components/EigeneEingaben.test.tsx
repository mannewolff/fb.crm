import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';

import { renderMitTheme } from '../test/render';
import EigeneEingaben, { type EigeneEingabe } from './EigeneEingaben';

const WERTE: readonly EigeneEingabe[] = [
  { bezeichnung: 'Titel', wert: 'Relaunch Website' },
  { bezeichnung: 'Notiz', wert: '' },
];

/** Haelt die Werte wie eine Ansicht: bis „Verwerfen" gewaehlt wird. */
function Ansicht() {
  const [werte, setWerte] = useState<readonly EigeneEingabe[] | null>(WERTE);
  return werte === null ? null : (
    <EigeneEingaben werte={werte} onVerwerfen={() => setWerte(null)} />
  );
}

describe('EigeneEingaben', () => {
  it('zeigt Bezeichnung und Wert jeder eigenen Eingabe', () => {
    renderMitTheme(<EigeneEingaben werte={WERTE} onVerwerfen={vi.fn()} />);

    const bereich = screen.getByRole('region', { name: 'Deine Eingaben' });
    expect(bereich).toHaveTextContent('Titel');
    expect(screen.getByText('Relaunch Website')).toBeInTheDocument();
    // Ein leeres Feld steht als leer da und nicht als fehlende Zeile.
    expect(bereich).toHaveTextContent('Notiz');
    expect(screen.getByText('(leer)')).toBeInTheDocument();
  });

  it('zeigt die Werte als Text und nicht als Eingabefeld', () => {
    // Schreibgeschuetzt und kopierbar: Ein zweites Feld gleichen Namens neben dem Formular
    // machte jede Beschriftung doppelt.
    renderMitTheme(<EigeneEingaben werte={WERTE} onVerwerfen={vi.fn()} />);

    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('blendet die Werte mit Verwerfen aus', async () => {
    renderMitTheme(<Ansicht />);

    await userEvent.click(screen.getByRole('button', { name: 'Verwerfen' }));

    expect(screen.queryByText('Relaunch Website')).not.toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Deine Eingaben' })).not.toBeInTheDocument();
  });
});
