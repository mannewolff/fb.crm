import { screen, within } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '../auth/AuthContext';
import { fetchNachPfad, json } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';
import { KopfAktionProvider } from './KopfAktion';
import TopBar from './TopBar';

const KONTO = { id: 1, displayName: 'Manfred Wolff', email: 'info@mwolff.org' };

function renderKopf(schalter?: ReactNode) {
  fetchNachPfad({ 'GET /api/auth/me': json(200, KONTO) });
  return renderMitTheme(
    <MemoryRouter>
      <AuthProvider>
        <KopfAktionProvider>
          <TopBar schalter={schalter} />
        </KopfAktionProvider>
      </AuthProvider>
    </MemoryRouter>,
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('TopBar', () => {
  it('ist der Kopf der Seite, links leer und rechts allein das Nutzer-Mal (K13)', async () => {
    renderKopf();

    const kopf = within(screen.getByRole('banner'));
    expect(screen.getByTestId('kopf-links')).toBeEmptyDOMElement();
    // Ohne Ansicht, die eine Hauptaktion mitbringt, bleibt ihr Platz leer.
    expect(screen.getByTestId('kopf-aktion')).toBeEmptyDOMElement();
    expect(await kopf.findByRole('button', { name: /Nutzermenü/ })).toBeInTheDocument();
    expect(kopf.getAllByRole('button')).toHaveLength(1);
    expect(kopf.queryAllByRole('link')).toHaveLength(0);
  });

  it('nimmt die Schaltflaeche der Schiene links auf (E18)', async () => {
    renderKopf(<button type="button">Navigation öffnen</button>);
    await screen.findByRole('button', { name: /Nutzermenü/ });

    expect(within(screen.getByTestId('kopf-links')).getByRole('button')).toHaveAccessibleName(
      'Navigation öffnen',
    );
  });

  it('traegt keine Suche, keine Reiterleiste und keine Kennzahl', async () => {
    renderKopf();
    await screen.findByRole('button', { name: /Nutzermenü/ });

    expect(screen.queryByRole('searchbox')).not.toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument();
    expect(screen.queryByText(/\d/)).not.toBeInTheDocument();
    expect(screen.queryByText(/⌘K/)).not.toBeInTheDocument();
  });
});
