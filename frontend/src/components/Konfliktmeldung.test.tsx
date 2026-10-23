import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import { ApiError } from '../api/client';
import { renderMitTheme } from '../test/render';
import Konfliktmeldung from './Konfliktmeldung';

// 30.09.2026, 18:00 in Berlin — der Augenblick, in dem die Meldung erscheint.
const JETZT = new Date('2026-09-30T16:00:00Z');

function abweisung(art: string): ApiError {
  return new ApiError(
    409,
    'Inzwischen geaendert.',
    {},
    {
      konflikt: {
        art,
        geaendertAm: '2026-09-30T12:05:00Z',
        geaendertVon: 'Anna Muster',
        eigenesFenster: false,
      },
    },
  );
}

function zeigen(ursache: unknown, neuLaden = vi.fn()) {
  renderMitTheme(
    <MemoryRouter>
      <Konfliktmeldung ursache={ursache} onNeuLaden={neuLaden} uebersicht="/angebote" jetzt={JETZT} />
    </MemoryRouter>,
  );
  return neuLaden;
}

describe('Konfliktmeldung', () => {
  it('nennt den Satz mit Urheber und Zeitpunkt in Wort und Symbol', () => {
    zeigen(abweisung('veraltet'));

    const meldung = screen.getByRole('alert');
    expect(meldung).toHaveTextContent('Nicht ausgeführt');
    expect(meldung).toHaveTextContent('Anna Muster hat diesen Eintrag inzwischen geändert (14:05).');
    // Wort und Symbol, nie Farbe allein (CLAUDE-design.md).
    expect(screen.getByTestId('konflikt-symbol')).toHaveAttribute('aria-hidden', 'true');
  });

  it('bietet bei einem geaenderten Eintrag Neu laden an', async () => {
    const neuLaden = zeigen(abweisung('veraltet'));

    await userEvent.click(screen.getByRole('button', { name: 'Neu laden' }));

    expect(neuLaden).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('link', { name: 'Zur Übersicht' })).not.toBeInTheDocument();
  });

  it('fuehrt bei einem geloeschten Eintrag zur Uebersicht statt neu zu laden', () => {
    zeigen(abweisung('gelöscht'));

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Anna Muster hat diesen Eintrag inzwischen gelöscht (14:05).',
    );
    expect(screen.getByRole('link', { name: 'Zur Übersicht' })).toHaveAttribute('href', '/angebote');
    expect(screen.queryByRole('button', { name: 'Neu laden' })).not.toBeInTheDocument();
  });

  it('zeigt nichts, wo kein Bearbeitungskonflikt vorliegt', () => {
    zeigen(new ApiError(409, 'Der Zustand passt nicht.', {}));

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('rechnet ohne Augenblick mit der Uhr des Rechners', () => {
    renderMitTheme(
      <MemoryRouter>
        <Konfliktmeldung ursache={abweisung('veraltet')} onNeuLaden={vi.fn()} uebersicht="/" />
      </MemoryRouter>,
    );

    // Der 30.09.2026 liegt vor dem Testlauf — also mit Datum.
    expect(screen.getByRole('alert')).toHaveTextContent('(30.09.2026, 14:05)');
  });
});
