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

  it('fuehrt den Titel als einzige Ueberschrift erster Ordnung', () => {
    renderMitTheme(
      <AuthCard titel="Anmelden">
        <p>Formular</p>
      </AuthCard>,
    );

    // Die Auth-Seiten stehen ausserhalb des Rahmens: Hier gibt es keine andere Ueberschrift,
    // die den Einstieg in die Seite benennen koennte.
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Anmelden');
  });

  it('haelt das Markenmal aus der Vorlesereihenfolge heraus', () => {
    renderMitTheme(
      <AuthCard titel="Anmelden">
        <p>Formular</p>
      </AuthCard>,
    );

    // Das Mal ist Zierat neben dem Namen „fb.crm"; vorgelesen traegt es nichts bei.
    expect(screen.getByTestId('marke-mal')).toHaveAttribute('aria-hidden');
  });

  it('stellt Marke, Titel und Inhalt in den Hauptbereich', () => {
    renderMitTheme(
      <AuthCard titel="Anmelden">
        <p>Formular</p>
      </AuthCard>,
    );

    const haupt = screen.getByRole('main');
    expect(haupt).toContainElement(screen.getByTestId('marke-mal'));
    expect(haupt).toContainElement(screen.getByRole('heading', { level: 1 }));
    expect(haupt).toContainElement(screen.getByText('Formular'));
  });

  it('zeigt keine Versionsnummer', () => {
    renderMitTheme(
      <AuthCard titel="Anmelden">
        <p>Formular</p>
      </AuthCard>,
    );

    // Die Version verlangt eine Sitzung (E10); vor der Anmeldung gibt es keine, und die
    // Karte darf auch keine erfinden (K15).
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
