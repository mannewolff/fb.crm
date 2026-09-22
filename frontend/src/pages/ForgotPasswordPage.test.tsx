import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { fetchNachPfad, leer, problem } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';
import ForgotPasswordPage from './ForgotPasswordPage';

function renderSeite() {
  return renderMitTheme(
    <MemoryRouter initialEntries={['/passwort-vergessen']}>
      <ForgotPasswordPage />
    </MemoryRouter>,
  );
}

async function anfordern(email: string) {
  const nutzer = userEvent.setup();
  await nutzer.type(screen.getByLabelText(/^E-Mail-Adresse/), email);
  await nutzer.click(screen.getByRole('button', { name: 'Link anfordern' }));
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('Passwort vergessen', () => {
  it('hat genau ein beschriftetes Feld und den Weg zurueck', () => {
    renderSeite();

    expect(screen.getByLabelText(/^E-Mail-Adresse/)).toHaveAttribute('type', 'email');
    expect(screen.getAllByRole('textbox')).toHaveLength(1);
    expect(screen.getByRole('link', { name: /Zur Anmeldung/ })).toHaveAttribute('href', '/anmelden');
  });

  it('zeigt fuer bekannte und unbekannte Adresse denselben Text (K7)', async () => {
    // Der Server antwortet in beiden Faellen gleich (202) — der Test prueft, dass die Seite
    // daraus keinen Unterschied macht und den Text nicht aus der Eingabe zusammensetzt.
    fetchNachPfad({ 'POST /api/auth/password-reset': leer(202) });

    const { unmount } = renderSeite();
    await anfordern('info@mwolff.org');
    const ersterText = (await screen.findByRole('status')).textContent;
    unmount();

    renderSeite();
    await anfordern('niemand@example.org');
    const zweiterText = (await screen.findByRole('status')).textContent;

    expect(zweiterText).toBe(ersterText);
    expect(ersterText).not.toContain('info@mwolff.org');
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('zeigt eine Feldmeldung des Servers am Feld', async () => {
    fetchNachPfad({
      'POST /api/auth/password-reset': problem(400, 'Ungueltige Eingabe', {
        email: ['muss eine gueltige E-Mail-Adresse sein'],
      }),
    });

    renderSeite();
    await anfordern('a@b');

    expect(await screen.findByLabelText(/^E-Mail-Adresse/)).toHaveAccessibleDescription(
      'muss eine gueltige E-Mail-Adresse sein',
    );
  });

  it('reicht eine Meldung ohne Feldbezug durch', async () => {
    fetchNachPfad({ 'POST /api/auth/password-reset': problem(429, 'Zu viele Anfragen.') });

    renderSeite();
    await anfordern('info@mwolff.org');

    expect(await screen.findByRole('alert')).toHaveTextContent('Zu viele Anfragen.');
  });

  it('sagt ohne technische Einzelheiten, wenn die Schnittstelle nicht antwortet', async () => {
    fetchNachPfad({});

    renderSeite();
    await anfordern('info@mwolff.org');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Die Anfrage ist gerade nicht möglich. Bitte später erneut versuchen.',
    );
  });
});
