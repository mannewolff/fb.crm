import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import { renderMitTheme } from '../test/render';
import KopfPfad, { KopfPfadProvider, useKopfPfad } from './KopfPfad';

const FIRMEN = [{ titel: 'Firmen', ziel: '/firmen' }] as const;

/** Eine Ansicht, die ihren Pfad meldet — so wie es jede Seite tut. */
function FirmaSeite() {
  useKopfPfad(FIRMEN, 'IT Bildungshaus');
  return <Link to="/ohne">Weiter</Link>;
}

/** Nur der Ruf, ohne Router: fuer den Fall ausserhalb des Rahmens. */
function NurMeldung() {
  useKopfPfad(FIRMEN, 'IT Bildungshaus');
  return null;
}

function renderSeiten() {
  return renderMitTheme(
    <MemoryRouter initialEntries={['/firmen/7']}>
      <KopfPfadProvider>
        <KopfPfad />
        <Routes>
          <Route path="/firmen/7" element={<FirmaSeite />} />
          <Route path="/ohne" element={<p>Seite ohne Pfad</p>} />
        </Routes>
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

describe('KopfPfad', () => {
  it('zeigt die gemeldeten Stufen als Pfad, die letzte als Text (E6)', async () => {
    renderSeiten();

    const pfad = within(await screen.findByRole('navigation', { name: 'Pfad' }));
    expect(pfad.getByRole('link', { name: 'Firmen' })).toHaveAttribute('href', '/firmen');
    const aktuell = pfad.getByText('IT Bildungshaus');
    expect(aktuell).toHaveAttribute('aria-current', 'page');
    // Die letzte Stufe ist kein Weg: Sie fuehrt auf die Seite, auf der man schon steht.
    expect(aktuell.tagName).toBe('SPAN');
  });

  it('bleibt ohne gemeldeten Pfad leer', () => {
    renderMitTheme(
      <KopfPfadProvider>
        <KopfPfad />
      </KopfPfadProvider>,
    );

    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
  });

  it('raeumt den Pfad, sobald die Seite verlassen wird', async () => {
    renderSeiten();
    await screen.findByRole('navigation', { name: 'Pfad' });

    await userEvent.click(screen.getByRole('link', { name: 'Weiter' }));

    expect(screen.getByText('Seite ohne Pfad')).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Pfad' })).not.toBeInTheDocument();
  });

  it('meldet die Endstufe nach, sobald die Ansicht ihre Daten hat', async () => {
    function SpaeterName() {
      const [name, setzeName] = useState('Firma');
      useKopfPfad(FIRMEN, name);
      return (
        <button
          type="button"
          onClick={() => {
            setzeName('IT Bildungshaus');
          }}
        >
          Geladen
        </button>
      );
    }
    renderMitTheme(
      <MemoryRouter>
        <KopfPfadProvider>
          <KopfPfad />
          <SpaeterName />
        </KopfPfadProvider>
      </MemoryRouter>,
    );
    expect(await screen.findByText('Firma')).toHaveAttribute('aria-current', 'page');

    await userEvent.click(screen.getByRole('button', { name: 'Geladen' }));

    expect(screen.getByText('IT Bildungshaus')).toHaveAttribute('aria-current', 'page');
    expect(screen.queryByText('Firma')).not.toBeInTheDocument();
  });

  it('scheitert laut, wenn eine Ansicht ausserhalb des Rahmens meldet', () => {
    // Ohne Provider gaebe es niemanden, der den Pfad annimmt — er verschwaende dann still.
    vi.spyOn(console, 'error').mockImplementation(() => {});

    expect(() => renderMitTheme(<NurMeldung />)).toThrow(/AppShell/);
  });
});
