import { lazy, Suspense } from 'react';
import { Navigate, Outlet, Route, Routes } from 'react-router-dom';

import { AuthProvider } from './auth/AuthContext';
import AppShell from './components/AppShell';
import ProtectedRoute from './routes/ProtectedRoute';
import RollenRoute from './routes/RollenRoute';

// Route-Level Lazy Loading ist Pflicht fuer alle Top-Level-Routen (CLAUDE-react.md).
const LoginPage = lazy(async () => import('./pages/LoginPage'));
const SetupPage = lazy(async () => import('./pages/SetupPage'));
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
const NachtragMaske = lazy(async () => import('./pages/NachtragMaske'));
const NachgetrageneRechnungPage = lazy(async () => import('./pages/NachgetrageneRechnungPage'));
const EigeneAngabenMaske = lazy(async () => import('./pages/EigeneAngabenMaske'));
const AdministrationPage = lazy(async () => import('./pages/AdministrationPage'));
const MitarbeitendePage = lazy(async () => import('./pages/MitarbeitendePage'));
const MitarbeiterMaske = lazy(async () => import('./pages/MitarbeiterMaske'));
const JahresabschluessePage = lazy(async () => import('./pages/JahresabschluessePage'));
const JahresabschlussPage = lazy(async () => import('./pages/JahresabschlussPage'));
const StartseitePage = lazy(async () => import('./pages/StartseitePage'));
// Ein Stueck fuer beide Teile der Dokumentation: Der Wechsel laedt nichts nach (Plan #378, E11).
const DokumentationPage = lazy(async () => import('./pages/DokumentationPage'));

/**
 * Der Routenbaum. Offen sind die Anmeldeseite und die Einrichtung; alles andere liegt hinter
 * der Sitzung.
 *
 * Die geschuetzten Adressen teilen sich einen Rahmen ({@link AppShell}): Er steht einmal um
 * das `Outlet` und bleibt beim Wechsel zwischen ihnen stehen, statt je Ansicht neu zu entstehen.
 * `/` traegt die Startseite mit dem Geschaeftsstand (Issue #216). `/firmen`, `/eigene-angaben`
 * und `/administration` tragen die fachlichen Ansichten. Ein Angebot
 * entsteht an der Firma (`/firmen/:id/angebote/neu`) und steht danach unter
 * `/angebote/:angebotId` — es braucht seine Firma nicht in der Adresse, denn es kennt sie
 * selbst (Issue #126). Dasselbe gilt fuer die Rechnung: Sie entsteht an ihrem Angebot und steht
 * danach unter `/rechnungen/:rechnungId`, die Liste aller Rechnungen unter `/rechnungen`
 * (Issue #184). Die Arbeitszeit steht quer dazu unter `/arbeitszeit` — sie haengt an keinem
 * einzelnen Angebot, sondern zeigt einen Monat ueber alle (Issue #193). Die Dokumentation steht
 * unter `/dokumentation` mit dem Teil „Bedienung" und unter `/dokumentation/betrieb` mit dem Teil
 * „Betrieb" — beide aus einer Seite (Issue #387).
 *
 * Die Einstellungen — `/eigene-angaben` und `/administration` — liegen zusaetzlich in
 * {@link RollenRoute}: Sie stehen nur einer Rolle offen, die sie sehen darf (Plan #414, A11).
 * Ebenso `/mitarbeitende` mit seinen Masken, die nur einem C-Level offenstehen (A12), und die
 * Masken zu Firma, Ansprechpartner und Angebot, die nur einer Rolle offenstehen, die fachlich
 * schreiben darf (Kriterium 10 von #409).
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
            <Route
              path="/angebote/:angebotId/bearbeiten"
              element={
                <RollenRoute recht="darfFachlichSchreiben">
                  <AngebotMaske />
                </RollenRoute>
              }
            />
            {/* Die Arbeitszeit traegt ihren Monat als Parameter (`?monat=`) und nicht im Pfad:
                Er filtert eine Ansicht, er benennt kein eigenes Objekt (Plan #194, A13). */}
            <Route path="/arbeitszeit" element={<ArbeitszeitPage />} />
            <Route path="/rechnungen" element={<RechnungenPage />} />
            {/* Statisch vor dynamisch (Plan #259, E21): `nachtragen` ist die Maske der
                nachgetragenen Rechnung, nicht die Rechnung „nachtragen". Die nachgetragene
                Rechnung hat ihren eigenen Kennungsraum unter `/rechnungen/nachgetragen/`. */}
            <Route path="/rechnungen/nachtragen" element={<NachtragMaske />} />
            <Route path="/rechnungen/:rechnungId" element={<RechnungPage />} />
            <Route path="/rechnungen/nachgetragen/:id" element={<NachgetrageneRechnungPage />} />
            <Route
              path="/rechnungen/nachgetragen/:id/bearbeiten"
              element={<NachtragMaske />}
            />
            {/* Das Jahr steht spaeter im Pfad und nicht als Parameter (Plan #288, E16): Es benennt
                ein eigenes Objekt — den Abschluss — und filtert keine Ansicht. */}
            <Route path="/jahresabschluesse" element={<JahresabschluessePage />} />
            {/* Statisch vor dynamisch (E16): `/jahresabschluesse` ist die Uebersicht. */}
            <Route path="/jahresabschluesse/:jahr" element={<JahresabschlussPage />} />
            <Route path="/firmen" element={<FirmenPage />} />
            {/* Statisch vor dynamisch: `/firmen/neu` ist die Maske, nicht die Firma „neu". */}
            <Route
              path="/firmen/neu"
              element={
                <RollenRoute recht="darfFachlichSchreiben">
                  <FirmaMaske />
                </RollenRoute>
              }
            />
            <Route path="/firmen/:id" element={<FirmaPage />} />
            <Route
              path="/firmen/:id/bearbeiten"
              element={
                <RollenRoute recht="darfFachlichSchreiben">
                  <FirmaMaske />
                </RollenRoute>
              }
            />
            <Route
              path="/firmen/:id/angebote/neu"
              element={
                <RollenRoute recht="darfFachlichSchreiben">
                  <AngebotMaske />
                </RollenRoute>
              }
            />
            <Route
              path="/firmen/:id/ansprechpartner/neu"
              element={
                <RollenRoute recht="darfFachlichSchreiben">
                  <AnsprechpartnerMaske />
                </RollenRoute>
              }
            />
            <Route
              path="/firmen/:id/ansprechpartner/:ansprechpartnerId/bearbeiten"
              element={
                <RollenRoute recht="darfFachlichSchreiben">
                  <AnsprechpartnerMaske />
                </RollenRoute>
              }
            />
            <Route
              path="/eigene-angaben"
              element={
                <RollenRoute recht="darfEinstellungenSehen">
                  <EigeneAngabenMaske />
                </RollenRoute>
              }
            />
            <Route
              path="/administration"
              element={
                <RollenRoute recht="darfEinstellungenSehen">
                  <AdministrationPage />
                </RollenRoute>
              }
            />
            {/* Die Konten verwaltet nur ein C-Level (Plan #414, A12); statisch vor dynamisch. */}
            <Route
              path="/mitarbeitende"
              element={
                <RollenRoute recht="darfKontenVerwalten">
                  <MitarbeitendePage />
                </RollenRoute>
              }
            />
            <Route
              path="/mitarbeitende/neu"
              element={
                <RollenRoute recht="darfKontenVerwalten">
                  <MitarbeiterMaske />
                </RollenRoute>
              }
            />
            <Route
              path="/mitarbeitende/:id/bearbeiten"
              element={
                <RollenRoute recht="darfKontenVerwalten">
                  <MitarbeiterMaske />
                </RollenRoute>
              }
            />
            {/* Ein Teil der Dokumentation ist ein eigenes Gegenstueck und steht im Pfad
                (Plan #378, E6); einen unbekannten Teil fuehrt die Seite auf `/dokumentation`. */}
            <Route path="/dokumentation" element={<DokumentationPage />} />
            <Route path="/dokumentation/:teil" element={<DokumentationPage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
      </Suspense>
    </AuthProvider>
  );
}
