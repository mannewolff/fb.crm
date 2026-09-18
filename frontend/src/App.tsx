import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';

import { AuthProvider } from './auth/AuthContext';
import ProtectedRoute from './routes/ProtectedRoute';

// Route-Level Lazy Loading ist Pflicht fuer alle Top-Level-Routen (CLAUDE-react.md).
const LoginPage = lazy(async () => import('./pages/LoginPage'));
const StartPage = lazy(async () => import('./pages/StartPage'));

/**
 * Der Routenbaum. Offen ist genau die Anmeldeseite; alles andere liegt hinter der Sitzung.
 *
 * Die unbekannte Adresse bekommt keine eigene Ansicht: Mit Sitzung fuehrt sie auf die
 * Startadresse, ohne Sitzung uebernimmt {@link ProtectedRoute} und fuehrt auf die
 * Anmeldeseite — beides mit `replace` (E21).
 */
export default function App() {
  return (
    <AuthProvider>
      <Suspense fallback={null}>
        <Routes>
          <Route path="/anmelden" element={<LoginPage />} />
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <StartPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="*"
            element={
              <ProtectedRoute>
                <Navigate to="/" replace />
              </ProtectedRoute>
            }
          />
        </Routes>
      </Suspense>
    </AuthProvider>
  );
}
