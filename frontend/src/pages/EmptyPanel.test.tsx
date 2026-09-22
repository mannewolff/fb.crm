import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { renderMitTheme } from '../test/render';
import EmptyPanel from './EmptyPanel';

describe('EmptyPanel', () => {
  it('ist leer und traegt keine fachliche Ueberschrift', () => {
    renderMitTheme(<EmptyPanel />);

    const panel = screen.getByTestId('leeres-panel');
    expect(panel).toBeEmptyDOMElement();
    expect(screen.queryByRole('heading')).not.toBeInTheDocument();
  });
});
