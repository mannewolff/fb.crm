import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';

import { useAuth } from '../auth/AuthContext';

/**
 * Die Zugangsregel der Oberflaeche (E20, E21).
 *
 * <b>Sie schuetzt keine Daten.</b> Das tut die Zugangsregel der API (`SecurityConfig`); eine
 * SPA-Route traegt nichts, was sich verbergen liesse. Was hier steht, ist Fuehrung: Wer
 * keine Sitzung hat, soll nicht vor einer leeren Ansicht landen, sondern auf der
 * Anmeldeseite.
 *
 * Beide Wege gehen mit `replace` — sonst legte jeder abgewiesene Aufruf einen Eintrag in den
 * Verlauf, und der Zurueck-Knopf liefe in eine Schleife (E21).
 */
export default function ProtectedRoute({ children }: { readonly children: ReactNode }) {
  const { sitzung } = useAuth();

  if (sitzung.status === 'unbekannt') {
    return (
      <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center' }}>
        <Typography role="status" sx={{ color: 'text.secondary' }}>
          Sitzung wird geprüft …
        </Typography>
      </Box>
    );
  }

  if (sitzung.status === 'abgemeldet') {
    return <Navigate to="/anmelden" replace />;
  }

  return <>{children}</>;
}
