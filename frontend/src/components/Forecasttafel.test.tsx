import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import Forecasttafel from './Forecasttafel';
import type { Forecast } from '../api/forecast';
import { renderMitTheme } from '../test/render';

/** Zwei Jahre: 2026 zugesagt, 2027 angeboten — das Angebot an IT Bildungshaus aus #302. */
const FORECAST: Forecast = {
  jahre: [
    {
      jahr: '2026',
      zugesagtInCent: 120000,
      angebotenInCent: 0,
      summeInCent: 120000,
      positionen: [
        {
          angebotId: 9,
          firmaName: 'Werkstatt Nord',
          angebotDatum: '2026-08-03',
          bezeichnung: 'Schulung',
          nettoInCent: 120000,
        },
      ],
    },
    {
      jahr: '2027',
      zugesagtInCent: 50000,
      angebotenInCent: 1782000,
      summeInCent: 1832000,
      positionen: [
        {
          angebotId: 14,
          firmaName: 'IT Bildungshaus',
          angebotDatum: '2026-10-15',
          bezeichnung: 'Aufwände Mai 2027',
          nettoInCent: 810000,
        },
        {
          angebotId: 14,
          firmaName: 'IT Bildungshaus',
          angebotDatum: '2026-10-15',
          bezeichnung: 'Aufwände Juni 2027',
          nettoInCent: 810000,
        },
      ],
    },
  ],
};

function zeige(forecast: Forecast) {
  return renderMitTheme(
    <MemoryRouter>
      <Forecasttafel forecast={forecast} />
    </MemoryRouter>,
  );
}

/** Die Zeile eines Jahres, gefunden ueber ihre Taste. */
function jahreszeile(jahr: string): HTMLElement {
  const treffer = screen
    .getAllByRole('row')
    .filter(
      (kandidat) =>
        within(kandidat).queryByRole('button', { name: new RegExp(`^Positionen ${jahr} `, 'u') }) !==
        null,
    );
  expect(treffer).toHaveLength(1);
  return treffer[0];
}

describe('Forecasttafel', () => {
  it('fuehrt die Spalten Jahr, Zugesagt, Angeboten und Summe', () => {
    zeige(FORECAST);

    const tafel = screen.getByRole('table', { name: 'Forecast' });
    const koepfe = within(tafel)
      .getAllByRole('columnheader')
      .map((kopf) => kopf.textContent);
    expect(koepfe).toEqual(['Jahr', 'Zugesagt', 'Angeboten', 'Summe']);
  });

  it('zeigt je Jahr die drei Betraege in Euro wie die uebrigen Tafeln (Kriterium 1)', () => {
    zeige(FORECAST);

    const zellen = within(jahreszeile('2027')).getAllByRole('cell');
    expect(zellen.map((zelle) => zelle.textContent)).toEqual([
      '2027',
      '500,00 €',
      '17.820,00 €',
      '18.320,00 €',
    ]);
    expect(zellen[1]).toHaveClass('zahlen');
    expect(zellen[3]).toHaveClass('zahlen');
    expect(within(jahreszeile('2026')).getAllByRole('cell')[2]).toHaveTextContent('0,00 €');
  });

  it('haelt die Positionen zu, bis die Taste sie aufklappt (Kriterium 5)', async () => {
    zeige(FORECAST);

    const taste = screen.getByRole('button', { name: 'Positionen 2027 anzeigen' });
    expect(taste).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByRole('table', { name: 'Positionen 2027' })).not.toBeInTheDocument();
    expect(screen.queryByText('Aufwände Mai 2027')).not.toBeInTheDocument();

    await userEvent.click(taste);

    const offen = screen.getByRole('button', { name: 'Positionen 2027 verbergen' });
    expect(offen).toBe(taste);
    expect(offen).toHaveAttribute('aria-expanded', 'true');
    const positionen = screen.getByRole('table', { name: 'Positionen 2027' });
    expect(
      within(positionen)
        .getAllByRole('columnheader')
        .map((kopf) => kopf.textContent),
    ).toEqual(['Firma', 'Angebot', 'Position', 'Restbetrag']);
    const zeilen = within(positionen).getAllByRole('row').slice(1);
    expect(zeilen).toHaveLength(2);
    const erste = within(zeilen[0]).getAllByRole('cell');
    expect(erste.map((zelle) => zelle.textContent)).toEqual([
      'IT Bildungshaus',
      '15.10.2026',
      'Aufwände Mai 2027',
      '8.100,00 €',
    ]);
    expect(within(zeilen[0]).getByRole('link', { name: '15.10.2026' })).toHaveAttribute(
      'href',
      '/angebote/14',
    );
  });

  it('klappt ein offenes Jahr wieder zu', async () => {
    zeige(FORECAST);

    await userEvent.click(screen.getByRole('button', { name: 'Positionen 2027 anzeigen' }));
    await userEvent.click(screen.getByRole('button', { name: 'Positionen 2027 verbergen' }));

    expect(screen.getByRole('button', { name: 'Positionen 2027 anzeigen' })).toHaveAttribute(
      'aria-expanded',
      'false',
    );
    expect(screen.queryByRole('table', { name: 'Positionen 2027' })).not.toBeInTheDocument();
  });

  it('laesst zwei Jahre gleichzeitig offen stehen', async () => {
    zeige(FORECAST);

    await userEvent.click(screen.getByRole('button', { name: 'Positionen 2026 anzeigen' }));
    await userEvent.click(screen.getByRole('button', { name: 'Positionen 2027 anzeigen' }));

    expect(screen.getByRole('table', { name: 'Positionen 2026' })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Positionen 2027' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Positionen 2026 verbergen' })).toHaveAttribute(
      'aria-expanded',
      'true',
    );
  });

  it('stellt die Positionen in einer Zeile ueber alle vier Spalten direkt unter ihr Jahr', async () => {
    zeige(FORECAST);

    await userEvent.click(screen.getByRole('button', { name: 'Positionen 2026 anzeigen' }));

    const zeilen = screen.getAllByRole('row');
    const folgende = zeilen[zeilen.indexOf(jahreszeile('2026')) + 1];
    const innen = within(folgende).getByRole('table', { name: 'Positionen 2026' });
    const weite = within(folgende)
      .getAllByRole('cell')
      .find((zelle) => within(zelle).queryByRole('table') !== null);
    expect(weite).toHaveAttribute('colspan', '4');
    // Die Zeilen stehen in Dokumentfolge: die weite Zeile, ihre inneren, dann das naechste Jahr.
    const innere = within(innen).getAllByRole('row').length;
    expect(zeilen.indexOf(jahreszeile('2027'))).toBe(zeilen.indexOf(folgende) + innere + 1);
  });

  it('zeigt keine Gesamtsumme ueber alle Jahre (E13)', async () => {
    zeige(FORECAST);

    const tafel = screen.getByRole('table', { name: 'Forecast' });
    // Eine Zeile je Jahr und nichts darueber hinaus — auch keine Summenzeile.
    expect(within(tafel).getAllByRole('row')).toHaveLength(1 + FORECAST.jahre.length);
    // Die Summe beider Jahre (1.200,00 € + 18.320,00 €) steht nirgends.
    expect(screen.queryByText('19.520,00 €')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Positionen 2026 anzeigen' }));
    expect(screen.queryByText('19.520,00 €')).not.toBeInTheDocument();
    expect(screen.queryByText(/gesamt/iu)).not.toBeInTheDocument();
  });

  it('sagt ohne ein einziges Jahr in einem Satz, dass nichts angeboten ist (Kriterium 6)', () => {
    zeige({ jahre: [] });

    expect(screen.getByText('Nichts angeboten — alle Positionen sind abgerechnet.')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
});
