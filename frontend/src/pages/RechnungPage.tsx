import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';

import { rechnungLesen } from '../api/rechnungen';
import type { Rechnung } from '../api/rechnungen';
import Karte from '../components/Karte';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import RechnungszustandChip from '../components/RechnungszustandChip';
import { nichtGefunden } from '../lib/apifehler';
import { kennungAus } from '../lib/kennung';

/**
 * Die Ansicht einer Rechnung — erste Fassung (Issue #184).
 *
 * Sie traegt in diesem Stand nur den Kopf: Ueberschrift, Firma und den Zustand als Chip. Die
 * Entwurfsmaske mit „jetzt abrechnen" und die Ansicht der gestellten Rechnung entstehen in den
 * Folgepaketen (Issues #185, #186); bis dahin sagt ein Satz, dass sie folgt.
 *
 * <b>Warum diese Seite jetzt schon da ist.</b> „Neue Rechnung" in {@link RechnungenPage} legt einen
 * Entwurf an und fuehrt auf ihn — ohne diese Route fuehrte die Wahl ins Leere, und das Paket waere
 * fuer sich nicht abnehmbar.
 *
 * Laden, „gibt es nicht" (404) und Ausfall wie in {@link AngebotPage}: Eine Kennung, die keine ist,
 * geht gar nicht erst ans Netz (`lib/kennung.ts`).
 */

const NICHT_GEFUNDEN = 'Diese Rechnung gibt es nicht.';
const AUSFALL = 'Die Rechnung ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const LAEDT = 'Die Rechnung wird geladen …';
const FOLGT = 'Die vollständige Ansicht dieser Rechnung folgt.';

/** Ueber jeder Rechnungsansicht steht die Liste der Rechnungen (E6). */
const ZU_RECHNUNGEN: PfadVerweis = { titel: 'Rechnungen', ziel: '/rechnungen' };

/** Was die Ansicht gerade weiss. */
type Stand =
  | { readonly art: 'laedt' }
  | { readonly art: 'daten'; readonly rechnung: Rechnung }
  | { readonly art: 'unbekannt' }
  | { readonly art: 'ausfall' };

/** Die Ueberschrift: der Entwurf hat keine Nummer, er heisst nach seinem Zustand (Kriterium 15). */
function ueberschriftZu(rechnung: Rechnung): string {
  return rechnung.nummer === null ? 'Rechnung (Entwurf)' : `Rechnung ${rechnung.nummer}`;
}

export default function RechnungPage() {
  const { rechnungId } = useParams();
  const kennung = kennungAus(rechnungId);
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });
  useKopfPfad(
    [ZU_RECHNUNGEN],
    stand.art === 'daten' ? ueberschriftZu(stand.rechnung) : 'Rechnung',
  );

  useEffect(() => {
    if (kennung === null) {
      // Eine Kennung, die keine ist, geht gar nicht erst ans Netz (kennung.ts).
      setzeStand({ art: 'unbekannt' });
      return;
    }
    void rechnungLesen(kennung)
      .then((rechnung) => {
        setzeStand({ art: 'daten', rechnung });
      })
      .catch((ursache: unknown) => {
        setzeStand(nichtGefunden(ursache) ? { art: 'unbekannt' } : { art: 'ausfall' });
      });
  }, [kennung]);

  let inhalt: ReactNode;
  if (stand.art === 'daten') {
    const { rechnung } = stand;
    inhalt = (
      <Karte titel={ueberschriftZu(rechnung)} titelEbene={1}>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
            <Link
              component={RouterLink}
              to={`/firmen/${String(rechnung.firmaId)}`}
              underline="hover"
              sx={{ fontSize: 13.5, fontWeight: 500 }}
            >
              {rechnung.firmaName}
            </Link>
            <RechnungszustandChip zustand={rechnung.zustand} />
          </Box>
          <Typography
            role="status"
            sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textMatt })}
          >
            {FOLGT}
          </Typography>
        </Box>
      </Karte>
    );
  } else if (stand.art === 'laedt') {
    inhalt = (
      <Karte>
        <Typography
          sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textSchwach })}
        >
          {LAEDT}
        </Typography>
      </Karte>
    );
  } else {
    inhalt = (
      <Karte>
        <Alert severity="error">{stand.art === 'unbekannt' ? NICHT_GEFUNDEN : AUSFALL}</Alert>
      </Karte>
    );
  }

  return <Box sx={{ display: 'flex', flexDirection: 'column', gap: '22px' }}>{inhalt}</Box>;
}
