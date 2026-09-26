import { screen, within } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { renderMitTheme } from '../test/render';
import { KopfAktionProvider } from './KopfAktion';
import { KopfPfadProvider, useKopfPfad } from './KopfPfad';
import TopBar from './TopBar';

const FIRMEN = [{ titel: 'Firmen', ziel: '/firmen' }] as const;

/** Eine Ansicht unter dem Kopf, die ihren Pfad meldet. */
function Ansicht() {
  useKopfPfad(FIRMEN, 'IT Bildungshaus');
  return <p>Inhalt</p>;
}

function renderKopf({
  schalter,
  ansicht = false,
}: { readonly schalter?: ReactNode; readonly ansicht?: boolean } = {}) {
  return renderMitTheme(
    <MemoryRouter>
      <KopfAktionProvider>
        <KopfPfadProvider>
          <TopBar schalter={schalter} />
          {ansicht ? <Ansicht /> : null}
        </KopfPfadProvider>
      </KopfAktionProvider>
    </MemoryRouter>,
  );
}

describe('TopBar', () => {
  it('traegt kein Nutzer-Mal mehr — es steht im Fuss der Schiene (E7)', () => {
    renderKopf();

    const kopf = within(screen.getByRole('banner'));
    expect(kopf.queryByRole('button', { name: /Nutzermenü/ })).not.toBeInTheDocument();
    expect(kopf.queryAllByRole('button')).toHaveLength(0);
  });

  it('bleibt ohne gemeldeten Pfad und ohne Hauptaktion leer (E6)', () => {
    renderKopf();

    expect(screen.getByTestId('kopf-links')).toBeEmptyDOMElement();
    // Ohne Ansicht, die eine Hauptaktion mitbringt, bleibt ihr Platz leer.
    expect(screen.getByTestId('kopf-aktion')).toBeEmptyDOMElement();
  });

  it('zeigt den Pfad der Ansicht links im Kopf (E6)', async () => {
    renderKopf({ ansicht: true });

    const pfad = await screen.findByRole('navigation', { name: 'Pfad' });
    expect(screen.getByTestId('kopf-links')).toContainElement(pfad);
    expect(within(pfad).getByRole('link', { name: 'Firmen' })).toHaveAttribute('href', '/firmen');
    expect(within(pfad).getByText('IT Bildungshaus')).toHaveAttribute('aria-current', 'page');
  });

  it('nimmt die Schaltflaeche der Schiene links auf (E18)', () => {
    renderKopf({ schalter: <button type="button">Navigation öffnen</button> });

    expect(within(screen.getByTestId('kopf-links')).getByRole('button')).toHaveAccessibleName(
      'Navigation öffnen',
    );
  });

  it('traegt keine Suche, keine Reiterleiste und keine Kennzahl (A3)', () => {
    renderKopf();

    expect(screen.queryByRole('searchbox')).not.toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument();
    expect(screen.queryByText(/\d/)).not.toBeInTheDocument();
    expect(screen.queryByText('/')).not.toBeInTheDocument();
  });
});
