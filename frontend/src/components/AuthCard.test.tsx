import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { renderMitTheme } from '../test/render';
import AuthCard from './AuthCard';

describe('AuthCard', () => {
  it('traegt Marke, Titel und Inhalt', () => {
    renderMitTheme(
      <AuthCard titel="Anmelden">
        <p>Formular</p>
      </AuthCard>,
    );

    expect(screen.getByTestId('marke-mal')).toBeInTheDocument();
    expect(screen.getByText('fb.crm')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Anmelden' })).toBeInTheDocument();
    expect(screen.getByText('Formular')).toBeInTheDocument();
  });

  it('zeigt keine Versionsnummer', () => {
    renderMitTheme(
      <AuthCard titel="Anmelden">
        <p>Formular</p>
      </AuthCard>,
    );

    // Die Version verlangt eine Sitzung (E10); vor der Anmeldung gibt es keine, und die
    // Platte darf auch keine erfinden (K15).
    expect(screen.queryByTestId('marke-zusatz')).not.toBeInTheDocument();
    expect(screen.getByRole('main')).not.toHaveTextContent(/\d+\.\d+/);
  });

  it('nimmt Nebenwege in den Fuss auf', () => {
    renderMitTheme(
      <AuthCard titel="Anmelden" fuss={<a href="/passwort-vergessen">Passwort vergessen?</a>}>
        <p>Formular</p>
      </AuthCard>,
    );

    expect(screen.getByRole('link', { name: 'Passwort vergessen?' })).toBeInTheDocument();
  });

  it('bleibt ohne Nebenwege vollstaendig', () => {
    renderMitTheme(
      <AuthCard titel="Anmelden">
        <p>Formular</p>
      </AuthCard>,
    );

    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });
});
