import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import { ApiError } from '../api/client';
import { renderMitTheme } from '../test/render';
import Maskenmeldungen from './Maskenmeldungen';
import type { MaskenmeldungenProps } from './Maskenmeldungen';

const KONFLIKT = new ApiError(409, 'Veraltet', {}, { konflikt: { art: 'veraltet' } });

function zeigen(angaben: Partial<MaskenmeldungenProps> = {}) {
  const props: MaskenmeldungenProps = {
    fehler: null,
    abweisung: null,
    onNeuLaden: vi.fn(),
    uebersicht: '/firmen',
    eigene: null,
    onVerwerfen: vi.fn(),
    ...angaben,
  };
  renderMitTheme(
    <MemoryRouter>
      <div data-testid="maske">
        <Maskenmeldungen {...props} />
      </div>
    </MemoryRouter>,
  );
  return props;
}

describe('Maskenmeldungen', () => {
  it('zeigt ohne Anlass nichts', () => {
    zeigen();

    expect(screen.getByTestId('maske')).toBeEmptyDOMElement();
  });

  it('zeigt Fehler, Erfolg, Konflikt und eigene Eingaben in dieser Reihenfolge', () => {
    zeigen({
      fehler: 'Speichern fehlgeschlagen.',
      erfolg: 'Gespeichert.',
      abweisung: KONFLIKT,
      eigene: [{ bezeichnung: 'Name', wert: 'Muster GmbH' }],
    });

    expect(screen.getByTestId('maske')).toHaveTextContent(
      /Speichern fehlgeschlagen\..*Gespeichert\..*Nicht ausgeführt.*Deine Eingaben.*Muster GmbH/,
    );
  });

  it('meldet den Fehler als Alert der Stufe Fehler und den Erfolg als Erfolg', () => {
    zeigen({ fehler: 'Kaputt.', erfolg: 'Gespeichert.' });

    const [fehler, erfolg] = screen.getAllByRole('alert');
    expect(fehler).toHaveClass('MuiAlert-standardError');
    expect(erfolg).toHaveClass('MuiAlert-standardSuccess');
  });

  it('reicht Neu laden der Konfliktmeldung durch', async () => {
    const props = zeigen({ abweisung: KONFLIKT });

    await userEvent.click(screen.getByRole('button', { name: 'Neu laden' }));

    expect(props.onNeuLaden).toHaveBeenCalledTimes(1);
  });

  it('zeigt ohne Weg zum Neuladen keine Konfliktmeldung', () => {
    zeigen({ abweisung: KONFLIKT, onNeuLaden: null });

    expect(screen.queryByText('Nicht ausgeführt')).not.toBeInTheDocument();
  });

  it('reicht Verwerfen der eigenen Eingaben durch', async () => {
    const props = zeigen({ eigene: [{ bezeichnung: 'Name', wert: 'Muster GmbH' }] });

    await userEvent.click(screen.getByRole('button', { name: 'Verwerfen' }));

    expect(props.onVerwerfen).toHaveBeenCalledTimes(1);
  });
});
