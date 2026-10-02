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
const AngebotMaske = lazy(async () => import('./pages/AngebotMaske'));
const AngebotePage = lazy(async () => import('./pages/AngebotePage'));
const AngebotPage = lazy(async () => import('./pages/AngebotPage'));
const ArbeitszeitPage = lazy(async () => import('./pages/ArbeitszeitPage'));
const RechnungenPage = lazy(async () => import('./pages/RechnungenPage'));
const RechnungPage = lazy(async () => import('./pages/RechnungPage'));
const EigeneAngabenMaske = lazy(async () => import('./pages/EigeneAngabenMaske'));
const AdministrationPage = lazy(async () => import('./pages/AdministrationPage'));
const StartseitePage = lazy(async () => import('./pages/StartseitePage'));

/**
 * Der Routenbaum. Offen sind die Anmeldeseite, die Einrichtung und die beiden Seiten zum
 * Passwort; alles andere liegt hinter der Sitzung.
 *
 * Die geschuetzten Adressen teilen sich einen Rahmen ({@link AppShell}): Er steht einmal um
 * das `Outlet` und bleibt beim Wechsel zwischen ihnen stehen, statt je Ansicht neu zu entstehen.
 * `/` traegt die Startseite mit dem Geschaeftsstand (Issue #216); `/dokumentation` ist der
 * letzte Weg auf das leere Panel. `/firmen`, `/eigene-angaben` und `/administration` tragen die
 * fachlichen Ansichten. Ein Angebot
 * entsteht an der Firma (`/firmen/:id/angebote/neu`) und steht danach unter
 * `/angebote/:angebotId` — es braucht seine Firma nicht in der Adresse, denn es kennt sie
 * selbst (Issue #126). Dasselbe gilt fuer die Rechnung: Sie entsteht an ihrem Angebot und steht
 * danach unter `/rechnungen/:rechnungId`, die Liste aller Rechnungen unter `/rechnungen`
 * (Issue #184). Die Arbeitszeit steht quer dazu unter `/arbeitszeit` — sie haengt an keinem
 * einzelnen Angebot, sondern zeigt einen Monat ueber alle (Issue #193).
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
            <Route path="/" element={<StartseitePage />} />
            <Route path="/angebote" element={<AngebotePage />} />
            <Route path="/angebote/:angebotId" element={<AngebotPage />} />
            <Route path="/angebote/:angebotId/bearbeiten" element={<AngebotMaske />} />
            {/* Die Arbeitszeit traegt ihren Monat als Parameter (`?monat=`) und nicht im Pfad:
                Er filtert eine Ansicht, er benennt kein eigenes Objekt (Plan #194, A13). */}
            <Route path="/arbeitszeit" element={<ArbeitszeitPage />} />
            <Route path="/rechnungen" element={<RechnungenPage />} />
            <Route path="/rechnungen/:rechnungId" element={<RechnungPage />} />
            <Route path="/firmen" element={<FirmenPage />} />
            {/* Statisch vor dynamisch: `/firmen/neu` ist die Maske, nicht die Firma „neu". */}
            <Route path="/firmen/neu" element={<FirmaMaske />} />
            <Route path="/firmen/:id" element={<FirmaPage />} />
            <Route path="/firmen/:id/bearbeiten" element={<FirmaMaske />} />
            <Route path="/firmen/:id/angebote/neu" element={<AngebotMaske />} />
            <Route
              path="/firmen/:id/ansprechpartner/neu"
              element={<AnsprechpartnerMaske />}
            />
            <Route
              path="/firmen/:id/ansprechpartner/:ansprechpartnerId/bearbeiten"
              element={<AnsprechpartnerMaske />}
            />
            <Route path="/eigene-angaben" element={<EigeneAngabenMaske />} />
            <Route path="/administration" element={<AdministrationPage />} />
            <Route path="/dokumentation" element={<EmptyPanel />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
      </Suspense>
    </AuthProvider>
  );
}
