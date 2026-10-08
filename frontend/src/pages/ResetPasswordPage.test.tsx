import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { fetchNachPfad, leer, problem } from '../test/fetchNachPfad';
import type { Routen } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';
import ResetPasswordPage from './ResetPasswordPage';

const PRUEFUNG = 'GET /api/auth/password-reset/gueltig';
const EINLOESUNG = 'POST /api/auth/password-reset/confirm';

/** Zeigt, wohin die Seite gefuehrt hat, und den mitgegebenen Zustand. */
function Anmeldung() {
  const zustand: unknown = useLocation().state;
  return <p>Anmeldeseite: {JSON.stringify(zustand)}</p>;
}

function renderSeite(adresse: string) {
  return renderMitTheme(
    <MemoryRouter initialEntries={[adresse]}>
      <Routes>
        <Route path="/passwort-neu" element={<ResetPasswordPage />} />
        <Route path="/anmelden" element={<Anmeldung />} />
      </Routes>
    </MemoryRouter>,
  );
}

function mitGueltigemToken(weitere: Routen = {}) {
  return fetchNachPfad({ [PRUEFUNG]: leer(204), ...weitere });
}

async function neuSetzen(passwort: string, wiederholung: string) {
  const nutzer = userEvent.setup();
  await nutzer.type(await screen.findByLabelText(/^Neues Passwort/), passwort);
  await nutzer.type(screen.getByLabelText(/^Wiederholung des Passworts/), wiederholung);
  await nutzer.click(screen.getByRole('button', { name: 'Passwort setzen' }));
}

function passwortFelder(container: HTMLElement) {
  return container.querySelectorAll('input[type="password"]');
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('Neues Passwort setzen', () => {
  it('zeigt ohne Token eine Meldung und kein Passwortfeld (K7)', () => {
    const fetchMock = fetchNachPfad({});

    const { container } = renderSeite('/passwort-neu');

    expect(screen.getByText(/Der Link gilt nicht/)).toBeInTheDocument();
    expect(passwortFelder(container)).toHaveLength(0);
    expect(screen.getByRole('link', { name: /Neuen Link anfordern/ })).toHaveAttribute(
      'href',
      '/passwort-vergessen',
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each([
    ['verbrauchtem oder abgelaufenem Link (410)', 410],
    ['unbekanntem Pfad (404)', 404],
  ])('zeigt bei %s eine Meldung statt des Formulars (K7, E24)', async (_fall, status) => {
    fetchNachPfad({ 'GET /api/auth/password-reset/alt': leer(status) });

    const { container } = renderSeite('/passwort-neu?token=alt');

    expect(await screen.findByText(/Der Link gilt nicht/)).toBeInTheDocument();
    expect(passwortFelder(container)).toHaveLength(0);
  });

  it('sagt, wenn der Link gerade nicht zu pruefen ist — ohne ihn fuer ungueltig zu erklaeren', async () => {
    fetchNachPfad({});

    const { container } = renderSeite('/passwort-neu?token=gueltig');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Der Link lässt sich gerade nicht prüfen. Bitte später erneut versuchen.',
    );
    expect(passwortFelder(container)).toHaveLength(0);
  });

  it('zeigt bei gueltigem Link das Formular mit beschrifteten Feldern', async () => {
    mitGueltigemToken();

    renderSeite('/passwort-neu?token=gueltig');

    expect(await screen.findByLabelText(/^Neues Passwort/)).toHaveAttribute('type', 'password');
    expect(screen.getByLabelText(/^Wiederholung des Passworts/)).toHaveAttribute('type', 'password');
  });

  it('meldet eine abweichende Wiederholung am Feld und schickt nichts ab', async () => {
    const fetchMock = mitGueltigemToken();

    renderSeite('/passwort-neu?token=gueltig');
    await neuSetzen('neues-passwort', 'neues-passwrot');

    expect(screen.getByLabelText(/^Wiederholung des Passworts/)).toHaveAccessibleDescription(
      /stimmt nicht mit dem neuen Passwort überein/,
    );
    expect(fetchMock.mock.calls.filter(([ziel]) => ziel === '/api/auth/password-reset/confirm')).toHaveLength(0);
  });

  it('fuehrt nach dem Setzen auf die Anmeldeseite, mit Hinweis', async () => {
    const fetchMock = mitGueltigemToken({ [EINLOESUNG]: leer(204) });

    renderSeite('/passwort-neu?token=gueltig');
    await neuSetzen('neues-passwort', 'neues-passwort');

    expect(await screen.findByText(/Anmeldeseite: .*Das neue Passwort ist gesetzt/)).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/password-reset/confirm',
      expect.objectContaining({
        body: JSON.stringify({ token: 'gueltig', password: 'neues-passwort' }),
      }),
    );
  });

  it('zeigt die Meldung des Servers zu einem kurzen Passwort am Feld (K6)', async () => {
    mitGueltigemToken({
      [EINLOESUNG]: problem(400, 'Ungueltige Eingabe', {
        password: ['muss mindestens 8 Zeichen lang sein'],
      }),
    });

    renderSeite('/passwort-neu?token=gueltig');
    await neuSetzen('sieben7', 'sieben7');

    expect(await screen.findByLabelText(/^Neues Passwort/)).toHaveAccessibleDescription(
      'muss mindestens 8 Zeichen lang sein',
    );
  });

  it('zeigt die Meldung statt des Formulars, wenn der Link beim Einloesen verbraucht ist', async () => {
    mitGueltigemToken({ [EINLOESUNG]: leer(410) });

    const { container } = renderSeite('/passwort-neu?token=gueltig');
    await neuSetzen('neues-passwort', 'neues-passwort');

    expect(await screen.findByText(/Der Link gilt nicht/)).toBeInTheDocument();
    expect(passwortFelder(container)).toHaveLength(0);
  });

  it('reicht eine andere Meldung des Servers ohne Feldbezug durch', async () => {
    mitGueltigemToken({ [EINLOESUNG]: problem(500, 'Gerade nicht moeglich.') });

    renderSeite('/passwort-neu?token=gueltig');
    await neuSetzen('neues-passwort', 'neues-passwort');

    expect(await screen.findByRole('alert')).toHaveTextContent('Gerade nicht moeglich.');
  });

  it('sagt ohne technische Einzelheiten, wenn das Einloesen nicht ankommt', async () => {
    mitGueltigemToken();

    renderSeite('/passwort-neu?token=gueltig');
    await neuSetzen('neues-passwort', 'neues-passwort');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Das Passwort lässt sich gerade nicht setzen. Bitte später erneut versuchen.',
    );
  });
});
