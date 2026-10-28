import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';

import type { FieldErrors } from '../api/client';
import type { Land } from '../api/laender';
import { renderMitTheme } from '../test/render';
import AnschriftFelder from './AnschriftFelder';
import type { Anschriftwerte } from './AnschriftFelder';

/** Eine Maske traegt neben der Anschrift weitere Felder — der Baustein laesst sie stehen. */
interface Werte extends Anschriftwerte {
  readonly name: string;
}

const START: Werte = { name: 'Adler AG', strasse: 'Hauptstr. 1', plz: '28195', ort: '', land: '' };

const LAENDER: readonly Land[] = [
  { kuerzel: 'DE', name: 'Deutschland' },
  { kuerzel: 'FR', name: 'Frankreich' },
  { kuerzel: 'AT', name: 'Österreich' },
];

interface ProbeProps {
  readonly feldFehler?: FieldErrors;
  readonly laender?: readonly Land[];
  readonly start?: Werte;
}

function Probe({ feldFehler = {}, laender, start = START }: ProbeProps) {
  const [werte, setzeWerte] = useState<Werte>(start);
  return (
    <>
      <AnschriftFelder
        werte={werte}
        setzeWerte={setzeWerte}
        feldFehler={feldFehler}
        laender={laender}
      />
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

  it('fragt das Land ohne Laenderliste als Textfeld ab', () => {
    renderMitTheme(<Probe />);

    expect(screen.getByRole('textbox', { name: 'Land' })).toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: 'Land' })).not.toBeInTheDocument();
  });
});

describe('AnschriftFelder — mit Laenderliste (Issue #463)', () => {
  it('bietet das Land als Auswahl mit derselben Beschriftung an und zeigt den Namen', () => {
    renderMitTheme(<Probe laender={LAENDER} start={{ ...START, land: 'FR' }} />);

    const land = screen.getByRole('combobox', { name: 'Land' });
    expect(land).toHaveValue('Frankreich');
    expect(screen.queryByRole('textbox', { name: 'Land' })).not.toBeInTheDocument();
    // Die uebrigen drei Felder bleiben Textfelder.
    expect(screen.getAllByRole('textbox')).toHaveLength(3);
  });

  it('gibt beim Waehlen das Kuerzel weiter, nicht den Namen', async () => {
    const nutzer = userEvent.setup();
    renderMitTheme(<Probe laender={LAENDER} start={{ ...START, land: 'DE' }} />);

    await nutzer.click(screen.getByRole('combobox', { name: 'Land' }));
    await nutzer.click(screen.getByRole('option', { name: 'Österreich' }));

    expect(screen.getByLabelText('werte')).toHaveTextContent(
      JSON.stringify({ ...START, land: 'AT' }),
    );
    expect(screen.getByRole('combobox', { name: 'Land' })).toHaveValue('Österreich');
  });

  it('findet ein Land ueber die Tastatur', async () => {
    const nutzer = userEvent.setup();
    renderMitTheme(<Probe laender={LAENDER} start={{ ...START, land: 'DE' }} />);

    const land = screen.getByRole('combobox', { name: 'Land' });
    await nutzer.clear(land);
    await nutzer.type(land, 'Frank');
    await nutzer.keyboard('{ArrowDown}{Enter}');

    expect(screen.getByLabelText('werte')).toHaveTextContent(
      JSON.stringify({ ...START, land: 'FR' }),
    );
  });

  it('laesst das Land leer, wenn die Auswahl geleert wird', async () => {
    const nutzer = userEvent.setup();
    renderMitTheme(<Probe laender={LAENDER} start={{ ...START, land: 'DE' }} />);

    await nutzer.clear(screen.getByRole('combobox', { name: 'Land' }));

    expect(screen.getByLabelText('werte')).toHaveTextContent(JSON.stringify({ ...START, land: '' }));
  });

  it('zeigt ein Kuerzel ausserhalb der Liste als leere Auswahl', () => {
    renderMitTheme(<Probe laender={LAENDER} start={{ ...START, land: 'XX' }} />);

    expect(screen.getByRole('combobox', { name: 'Land' })).toHaveValue('');
  });

  it('zeigt die Meldung des Servers an der Auswahl', () => {
    renderMitTheme(
      <Probe
        laender={LAENDER}
        start={{ ...START, land: 'DE' }}
        feldFehler={{ land: ['muss ein Land aus der Länderliste sein'] }}
      />,
    );

    const land = screen.getByRole('combobox', { name: 'Land' });
    expect(land).toHaveAttribute('aria-invalid', 'true');
    expect(land).toHaveAccessibleDescription('muss ein Land aus der Länderliste sein');
  });
});
