import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { renderMitTheme } from '../test/render';
import Bereitschaftsrahmen from './Bereitschaftsrahmen';

describe('Bereitschaftsrahmen', () => {
  it('zeigt waehrend des Ladens den Ladetext ohne Alert', () => {
    renderMitTheme(<Bereitschaftsrahmen laedt="Die Rechnung wird geladen …" />);

    expect(screen.getByText('Die Rechnung wird geladen …')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('zeigt den Fehler als Alert der Stufe Fehler', () => {
    renderMitTheme(<Bereitschaftsrahmen fehler="Die Rechnung gibt es nicht." />);

    const meldung = screen.getByRole('alert');
    expect(meldung).toHaveTextContent('Die Rechnung gibt es nicht.');
    expect(meldung).toHaveClass('MuiAlert-standardError');
  });
});
