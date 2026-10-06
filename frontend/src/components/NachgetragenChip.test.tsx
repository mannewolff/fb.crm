import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { renderMitTheme } from '../test/render';
import NachgetragenChip from './NachgetragenChip';

describe('NachgetragenChip', () => {
  it('sagt die Art als Wort', () => {
    renderMitTheme(<NachgetragenChip />);

    // Farbe allein traegt nie eine Aussage (CLAUDE-design.md, „Zustandsformen").
    expect(screen.getByText('nachgetragen')).toBeInTheDocument();
  });

  it('haelt das stuetzende Symbol aus dem Vorgelesenen heraus', () => {
    renderMitTheme(<NachgetragenChip />);

    expect(screen.getByTestId('chip-symbol')).toHaveAttribute('aria-hidden', 'true');
  });
});
