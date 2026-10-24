import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';

import type { Rolle } from '../api/auth';
import App from '../App';
import { fetchNachPfad, json } from './fetchNachPfad';
import type { Routen } from './fetchNachPfad';
import { renderMitTheme } from './render';

/**
 * Rendert die ganze Anwendung unter einer Adresse, angemeldet mit einer Rolle, und gibt das
 * fetch-Doppel zurueck.
 *
 * Fuer die Proben, ob eine Route in `App` hinter {@link RollenRoute} liegt (Plan #414, A11): Das
 * zeigt nur der echte Routenbaum mit echtem `AuthProvider` — eine Maske allein weiss nichts von
 * der Route, unter der sie steht. Sitzung und Versionsangabe des Rahmens antworten immer; was die
 * Ansicht darueber hinaus liest, gibt der Aufrufer in `routen` mit.
 *
 * Das Fenster ist breit: Der Rahmen fragt `matchMedia`, und jsdom kennt es nicht.
 */
export function appAlsRolle(rolle: Rolle, adresse: string, routen: Routen = {}) {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: (abfrage: string) => ({
      matches: true,
      media: abfrage,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
    }),
  });
  const aufruf = fetchNachPfad({
    'GET /api/auth/me': json(200, {
      id: 1,
      displayName: 'Ben Krause',
      email: 'ben@firma.de',
      role: rolle,
    }),
    'GET /api/instance': json(200, { version: '0.1.3' }),
    ...routen,
  });
  renderMitTheme(
    <MemoryRouter initialEntries={[adresse]}>
      <App />
    </MemoryRouter>,
  );
  return aufruf;
}
