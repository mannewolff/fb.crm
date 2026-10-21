import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';

import type { FieldErrors } from '../api/client';
import { renderMitTheme } from '../test/render';
import CodeFeld from './CodeFeld';

function Probe({ feldFehler = {} }: { readonly feldFehler?: FieldErrors }) {
  const [code, setzeCode] = useState('');
  return (
    <>
      <CodeFeld wert={code} setzeWert={setzeCode} feldFehler={feldFehler} />
      <output aria-label="wert">{code}</output>
    </>
  );
}

describe('CodeFeld', () => {
  it('ist ein sichtbar beschriftetes Pflichtfeld fuer sechs Ziffern aus der Mail', () => {
    renderMitTheme(<Probe />);

    const feld = screen.getByLabelText(/^Code/);
    expect(feld).toBeRequired();
    expect(feld).toHaveAttribute('inputmode', 'numeric');
    expect(feld).toHaveAttribute('autocomplete', 'one-time-code');
    expect(feld).toHaveAttribute('maxlength', '6');
    expect(feld).not.toHaveAttribute('type', 'password');
  });

  it('nimmt beim Erscheinen den Fokus', () => {
    renderMitTheme(<Probe />);

    expect(screen.getByLabelText(/^Code/)).toHaveFocus();
  });

  it('gibt jede Eingabe weiter', async () => {
    const nutzer = userEvent.setup();
    renderMitTheme(<Probe />);

    await nutzer.type(screen.getByLabelText(/^Code/), '042731');

    expect(screen.getByLabelText('wert')).toHaveTextContent('042731');
  });

  it('zeigt die Meldung des Servers zum Feld „code"', () => {
    renderMitTheme(<Probe feldFehler={{ code: ['darf nicht leer sein'] }} />);

    const feld = screen.getByLabelText(/^Code/);
    expect(feld).toHaveAttribute('aria-invalid', 'true');
    expect(feld).toHaveAccessibleDescription('darf nicht leer sein');
  });
});
