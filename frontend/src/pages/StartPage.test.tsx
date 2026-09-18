import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import StartPage from './StartPage';

describe('StartPage', () => {
  it('traegt in diesem Stand einen leeren Inhaltsbereich', () => {
    render(<StartPage />);

    const inhalt = screen.getByRole('main');
    expect(inhalt).toBeInTheDocument();
    expect(inhalt).toBeEmptyDOMElement();
  });
});
