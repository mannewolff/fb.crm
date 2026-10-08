import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { renderMitTheme } from '../test/render';
import InternChip from './InternChip';

describe('InternChip', () => {
  it('sagt die Art als Wort', () => {
    renderMitTheme(<InternChip />);

    // Farbe allein traegt nie eine Aussage (CLAUDE-design.md, „Zustandsformen").
    expect(screen.getByText('Intern')).toBeInTheDocument();
  });

  it('haelt das stuetzende Symbol aus dem Vorgelesenen heraus', () => {
    renderMitTheme(<InternChip />);

    expect(screen.getByTestId('chip-symbol')).toHaveAttribute('aria-hidden', 'true');
  });
});
