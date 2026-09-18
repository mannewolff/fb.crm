import { lazy, Suspense } from 'react';
import { Route, Routes } from 'react-router-dom';

// Route-Level Lazy Loading ist Pflicht fuer alle Top-Level-Routen (CLAUDE-react.md).
const StartPage = lazy(async () => import('./pages/StartPage'));

export default function App() {
  return (
    <Suspense fallback={null}>
      <Routes>
        <Route path="/" element={<StartPage />} />
      </Routes>
    </Suspense>
  );
}
