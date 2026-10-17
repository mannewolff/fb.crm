import { screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import Angebotsweg from './Angebotsweg';
import { renderMitTheme } from '../test/render';

describe('Angebotsweg', () => {
  it('fuehrt nach /angebote/:id und traegt das Datum in seiner Wortform', () => {
    renderMitTheme(
      <MemoryRouter>
        <Angebotsweg angebotId={12} tag="2026-09-26" titel={null} />
      </MemoryRouter>,
    );

    const verweis = screen.getByRole('link', { name: '26.09.2026' });
    expect(verweis).toHaveAttribute('href', '/angebote/12');
    expect(verweis).toHaveClass('zahlen');
  });

  it('traegt mit Titel den Titel statt des Datums und fuehrt an dasselbe Ziel (Issue #324, E2)', () => {
    renderMitTheme(
      <MemoryRouter>
        <Angebotsweg angebotId={12} tag="2026-09-26" titel="Java-Grundkurs" />
      </MemoryRouter>,
    );

    const verweis = screen.getByRole('link', { name: 'Java-Grundkurs' });
    expect(verweis).toHaveAttribute('href', '/angebote/12');
    expect(verweis).not.toHaveClass('zahlen');
    expect(screen.queryByText('26.09.2026')).not.toBeInTheDocument();
  });

  it('bricht einen langen Titel ohne Leerzeichen um und kuerzt ihn nicht (Kriterium 8, E10)', () => {
    const lang = 'x'.repeat(200);

    renderMitTheme(
      <MemoryRouter>
        <Angebotsweg angebotId={12} tag="2026-09-26" titel={lang} />
      </MemoryRouter>,
    );

    const verweis = screen.getByRole('link', { name: lang });
    expect(verweis).toHaveStyle({ overflowWrap: 'anywhere' });
    expect(verweis).not.toHaveStyle({ whiteSpace: 'nowrap' });
  });
});
