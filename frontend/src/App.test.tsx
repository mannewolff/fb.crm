import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useNavigate } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import App from './App';
import { renderMitTheme } from './test/render';

function ohneSitzung() {
  return vi
    .spyOn(globalThis, 'fetch')
    .mockImplementation(() => Promise.resolve(new Response(null, { status: 401 })));
}

/** Der Zurueck-Knopf des Browsers, nachgebildet ueber dieselbe Verlaufs-Schnittstelle. */
function Zurueck() {
  const navigate = useNavigate();
  return (
    <button
      type="button"
      onClick={() => {
        navigate(-1);
      }}
    >
      zurueck
    </button>
  );
}

function renderApp(verlauf: string[], stand: number) {
  return renderMitTheme(
    <MemoryRouter initialEntries={verlauf} initialIndex={stand}>
      <Zurueck />
      <App />
    </MemoryRouter>,
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('App', () => {
  it('zeigt ohne Sitzung die Anmeldeseite', async () => {
    ohneSitzung();

    renderApp(['/anmelden'], 0);

    expect(await screen.findByLabelText(/^E-Mail-Adresse/)).toBeInTheDocument();
  });

  it('fuehrt einen Schritt zurueck auf eine geschuetzte Adresse wieder auf die Anmeldeseite', async () => {
    // K9: Nach dem Abmelden liegt die Anwendung im Verlauf hinter der Anmeldeseite. Der
    // Zurueck-Knopf darf sie nicht wieder hervorholen — die Zugangsregel haengt an der
    // Sitzung, nicht am Verlauf.
    ohneSitzung();
    const nutzer = userEvent.setup();

    renderApp(['/', '/anmelden'], 1);
    await screen.findByLabelText(/^E-Mail-Adresse/);
    await nutzer.click(screen.getByRole('button', { name: 'zurueck' }));

    expect(await screen.findByLabelText(/^E-Mail-Adresse/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Anmelden' })).toBeInTheDocument();
  });
});
