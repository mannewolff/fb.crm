import { screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import Angebotsweg from './Angebotsweg';
import { renderMitTheme } from '../test/render';

describe('Angebotsweg', () => {
  it('fuehrt nach /angebote/:id und traegt das Datum in seiner Wortform', () => {
    renderMitTheme(
      <MemoryRouter>
        <Angebotsweg angebotId={12} tag="2026-09-26" />
      </MemoryRouter>,
    );

    const verweis = screen.getByRole('link', { name: '26.09.2026' });
    expect(verweis).toHaveAttribute('href', '/angebote/12');
  });
});
