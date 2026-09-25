import { lazy, Suspense } from 'react';
import { Navigate, Outlet, Route, Routes } from 'react-router-dom';

import { AuthProvider } from './auth/AuthContext';
import AppShell from './components/AppShell';
import ProtectedRoute from './routes/ProtectedRoute';

// Route-Level Lazy Loading ist Pflicht fuer alle Top-Level-Routen (CLAUDE-react.md).
const LoginPage = lazy(async () => import('./pages/LoginPage'));
const SetupPage = lazy(async () => import('./pages/SetupPage'));
const ForgotPasswordPage = lazy(async () => import('./pages/ForgotPasswordPage'));
const ResetPasswordPage = lazy(async () => import('./pages/ResetPasswordPage'));
const EmptyPanel = lazy(async () => import('./pages/EmptyPanel'));
const FirmenPage = lazy(async () => import('./pages/FirmenPage'));
const FirmaMaske = lazy(async () => import('./pages/FirmaMaske'));
const FirmaPage = lazy(async () => import('./pages/FirmaPage'));
const AnsprechpartnerMaske = lazy(async () => import('./pages/AnsprechpartnerMaske'));

/**
 * Der Routenbaum. Offen sind die Anmeldeseite, die Einrichtung und die beiden Seiten zum
 * Passwort; alles andere liegt hinter der Sitzung.
 *
 * Die geschuetzten Adressen teilen sich einen Rahmen ({@link AppShell}): Er steht einmal um
 * das `Outlet` und bleibt beim Wechsel zwischen ihnen stehen, statt je Ansicht neu zu entstehen.
 * `/`, `/administration` und `/dokumentation` zeigen in diesem Stand dasselbe leere Panel;
 * `/firmen` traegt die erste fachliche Ansicht.
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
          <Route path="/einrichten" element={<SetupPage />} />
          <Route path="/passwort-vergessen" element={<ForgotPasswordPage />} />
          <Route path="/passwort-neu" element={<ResetPasswordPage />} />
          <Route
            element={
              <ProtectedRoute>
                <AppShell>
                  <Suspense fallback={null}>
                    <Outlet />
                  </Suspense>
                </AppShell>
              </ProtectedRoute>
            }
          >
            <Route path="/" element={<EmptyPanel />} />
            <Route path="/firmen" element={<FirmenPage />} />
            {/* Statisch vor dynamisch: `/firmen/neu` ist die Maske, nicht die Firma „neu". */}
            <Route path="/firmen/neu" element={<FirmaMaske />} />
            <Route path="/firmen/:id" element={<FirmaPage />} />
            <Route path="/firmen/:id/bearbeiten" element={<FirmaMaske />} />
            <Route
              path="/firmen/:id/ansprechpartner/neu"
              element={<AnsprechpartnerMaske />}
            />
            <Route
              path="/firmen/:id/ansprechpartner/:ansprechpartnerId/bearbeiten"
              element={<AnsprechpartnerMaske />}
            />
            <Route path="/administration" element={<EmptyPanel />} />
            <Route path="/dokumentation" element={<EmptyPanel />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
      </Suspense>
    </AuthProvider>
  );
}
