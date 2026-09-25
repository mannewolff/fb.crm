import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '../auth/AuthContext';
import { fetchNachPfad, json } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';
import AppShell from './AppShell';
import KopfAktion from './KopfAktion';
import KupferTaste from './KupferTaste';

const KONTO = { id: 1, displayName: 'Manfred Wolff', email: 'info@mwolff.org' };

function SeiteMitAktion() {
  return (
    <>
      <KopfAktion>
        <KupferTaste to="/firmen/neu">Neue Firma</KupferTaste>
      </KopfAktion>
      <Link to="/ohne">Weiter</Link>
    </>
  );
}

function renderSeiten() {
  fetchNachPfad({ 'GET /api/auth/me': json(200, KONTO) });
  return renderMitTheme(
    <MemoryRouter initialEntries={['/mit']}>
      <AuthProvider>
        <AppShell>
          <Routes>
            <Route path="/mit" element={<SeiteMitAktion />} />
            <Route path="/ohne" element={<p>Seite ohne Aktion</p>} />
          </Routes>
        </AppShell>
      </AuthProvider>
    </MemoryRouter>,
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('KopfAktion', () => {
  it('legt die Hauptaktion der Seite in den Kopf, vor das Nutzer-Mal', async () => {
    renderSeiten();

    const kopf = within(screen.getByRole('banner'));
    const aktion = kopf.getByRole('link', { name: 'Neue Firma' });
    const mal = await kopf.findByRole('button', { name: /Nutzermenü/ });
    expect(aktion).toHaveAttribute('href', '/firmen/neu');
    expect(aktion.compareDocumentPosition(mal) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('raeumt den Platz, sobald die Seite verlassen wird', async () => {
    renderSeiten();
    await screen.findByRole('button', { name: /Nutzermenü/ });

    await userEvent.click(screen.getByRole('link', { name: 'Weiter' }));

    expect(screen.getByText('Seite ohne Aktion')).toBeInTheDocument();
    expect(screen.getByTestId('kopf-aktion')).toBeEmptyDOMElement();
  });

  it('scheitert laut, wenn sie ausserhalb des Rahmens steht', () => {
    // Ohne Provider gaebe es keinen Platz — die Hauptaktion verschwaende dann still.
    vi.spyOn(console, 'error').mockImplementation(() => {});

    expect(() => renderMitTheme(<KopfAktion>Neue Firma</KopfAktion>)).toThrow(/AppShell/);
  });
});
