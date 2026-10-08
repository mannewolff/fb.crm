import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';

import type { FieldErrors } from '../api/client';
import { renderMitTheme } from '../test/render';
import EmailFeld from './EmailFeld';

function Probe({
  feldFehler = {},
  autoComplete = 'email',
}: {
  readonly feldFehler?: FieldErrors;
  readonly autoComplete?: 'email' | 'username';
}) {
  const [email, setzeEmail] = useState('');
  return (
    <>
      <EmailFeld
        wert={email}
        setzeWert={setzeEmail}
        feldFehler={feldFehler}
        autoComplete={autoComplete}
      />
      <output aria-label="wert">{email}</output>
    </>
  );
}

describe('EmailFeld', () => {
  it('ist ein Pflichtfeld vom Typ E-Mail mit der Beschriftung „E-Mail-Adresse"', () => {
    renderMitTheme(<Probe />);

    const feld = screen.getByLabelText(/^E-Mail-Adresse/);
    expect(feld).toHaveAttribute('type', 'email');
    expect(feld).toBeRequired();
    expect(feld).toHaveAttribute('autocomplete', 'email');
  });

  it('reicht die Art der Vervollstaendigung durch', () => {
    renderMitTheme(<Probe autoComplete="username" />);

    expect(screen.getByLabelText(/^E-Mail-Adresse/)).toHaveAttribute('autocomplete', 'username');
  });

  it('gibt jede Eingabe weiter', async () => {
    const nutzer = userEvent.setup();
    renderMitTheme(<Probe />);

    await nutzer.type(screen.getByLabelText(/^E-Mail-Adresse/), 'eva@adler.de');

    expect(screen.getByLabelText('wert')).toHaveTextContent('eva@adler.de');
  });

  it('zeigt die Meldung des Servers zum Feld „email"', () => {
    renderMitTheme(<Probe feldFehler={{ email: ['ist keine E-Mail-Adresse'] }} />);

    const feld = screen.getByLabelText(/^E-Mail-Adresse/);
    expect(feld).toHaveAttribute('aria-invalid', 'true');
    expect(feld).toHaveAccessibleDescription('ist keine E-Mail-Adresse');
  });
});
