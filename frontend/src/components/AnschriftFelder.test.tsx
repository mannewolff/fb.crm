import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';

import type { FieldErrors } from '../api/client';
import { renderMitTheme } from '../test/render';
import AnschriftFelder from './AnschriftFelder';
import type { Anschriftwerte } from './AnschriftFelder';

/** Eine Maske traegt neben der Anschrift weitere Felder — der Baustein laesst sie stehen. */
interface Werte extends Anschriftwerte {
  readonly name: string;
}

const START: Werte = { name: 'Adler AG', strasse: 'Hauptstr. 1', plz: '28195', ort: '', land: '' };

function Probe({ feldFehler = {} }: { readonly feldFehler?: FieldErrors }) {
  const [werte, setzeWerte] = useState<Werte>(START);
  return (
    <>
      <AnschriftFelder werte={werte} setzeWerte={setzeWerte} feldFehler={feldFehler} />
      <output aria-label="werte">{JSON.stringify(werte)}</output>
    </>
  );
}

describe('AnschriftFelder', () => {
  it('zeigt die vier Felder der Anschrift in dieser Reihenfolge mit ihren Werten', () => {
    renderMitTheme(<Probe />);

    expect(screen.getAllByRole('textbox').map((feld) => feld.getAttribute('value'))).toEqual([
      'Hauptstr. 1',
      '28195',
      '',
      '',
    ]);
    expect(screen.getByLabelText('Straße und Hausnummer')).toBeInTheDocument();
    expect(screen.getByLabelText('Postleitzahl')).toBeInTheDocument();
    expect(screen.getByLabelText('Ort')).toBeInTheDocument();
    expect(screen.getByLabelText('Land')).toBeInTheDocument();
  });

  it('uebernimmt eine Eingabe in genau ihr Feld und laesst die uebrigen Werte stehen', async () => {
    const nutzer = userEvent.setup();
    renderMitTheme(<Probe />);

    await nutzer.type(screen.getByLabelText('Ort'), 'Bremen');

    expect(screen.getByLabelText('werte')).toHaveTextContent(
      JSON.stringify({ ...START, ort: 'Bremen' }),
    );
  });

  it('zeigt die Meldung des Servers am Feld, zu dem sie gehoert', () => {
    renderMitTheme(<Probe feldFehler={{ plz: ['ist zu lang'] }} />);

    const plz = screen.getByLabelText('Postleitzahl');
    expect(plz).toHaveAttribute('aria-invalid', 'true');
    expect(plz).toHaveAccessibleDescription('ist zu lang');
    expect(screen.getByLabelText('Ort')).toHaveAttribute('aria-invalid', 'false');
  });
});
