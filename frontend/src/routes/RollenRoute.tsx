import Alert from '@mui/material/Alert';
import Link from '@mui/material/Link';
import type { ReactNode } from 'react';
import { Link as RouterLink } from 'react-router-dom';

import { useRechte } from '../auth/rechte';
import type { Recht } from '../auth/rechte';
import Karte from '../components/Karte';

/** Der eine Satz einer Ansicht, die der Rolle nicht offensteht (Kriterium 9 von #409). */
export const NICHT_OFFEN = 'Diese Ansicht steht deiner Rolle nicht offen.';

/**
 * Die Zugangsregel der Oberflaeche je Rolle (Plan #414, A11) — die zweite Stufe hinter
 * {@link ProtectedRoute}: Die Sitzung steht dann schon fest, hier geht es um ihr Recht.
 *
 * <b>Auch sie schuetzt keine Daten.</b> Das tut die Zugangsregel der API; was hier steht, ist
 * Fuehrung. Wer eine Adresse direkt eingibt, die seiner Rolle nicht offensteht, sieht statt einer
 * Ansicht, deren Anfragen der Reihe nach mit 403 scheitern, einen Satz und den Weg zur Startseite.
 * Eine Umleitung waere stummer: Der Mensch saehe die Startseite und wuesste nicht, warum.
 */
export default function RollenRoute({
  recht,
  children,
}: {
  readonly recht: Recht;
  readonly children: ReactNode;
}) {
  const rechte = useRechte();

  if (!rechte[recht]) {
    return (
      <Karte>
        <Alert severity="info">
          {NICHT_OFFEN}{' '}
          <Link component={RouterLink} to="/" underline="hover">
            Zur Startseite
          </Link>
        </Alert>
      </Karte>
    );
  }

  return <>{children}</>;
}
