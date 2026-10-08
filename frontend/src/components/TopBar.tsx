import Box from '@mui/material/Box';
import type { ReactNode } from 'react';

import KopfPfad from './KopfPfad';

/**
 * Der Kopf: eine Zeile ohne eigene Flaeche ueber der Buehne (Vorlage `.kopf` Z. 46–52).
 *
 * Er zeigt nur noch den Pfad — links der {@link KopfPfad} und davor die Schaltflaeche der Schiene,
 * sofern der Rahmen eine mitbringt: unterhalb von 900 px liegt die Schiene dahinter
 * (CLAUDE-design.md, „Mindestbreite").
 *
 * Der Platz fuer die Hauptaktion einer Ansicht ist entfallen (E5): Jede Ansicht traegt ihre
 * Hauptaktion inzwischen selbst, in ihrer eigenen Kopfkarte.
 *
 * Das Nutzer-Mal stand hier bis zur Kupferwolke; es ist die Nutzerkarte im Fuss der Schiene
 * geworden (E7). Suche, Glocke, Reiterleiste und Kennzahlen der Vorlage entstehen nicht — kein
 * Bedienelement ohne fachlichen Anlass (Plan A3).
 */
export default function TopBar({ schalter }: { readonly schalter?: ReactNode }) {
  return (
    <Box
      component="header"
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 2,
        padding: '6px 4px',
        // Der Kopf haelt seine Zeile, auch wenn eine Ansicht keinen Pfad meldet — sonst sprangen
        // die Karten darunter je Ansicht um seine Hoehe.
        minHeight: 34,
      }}
    >
      <Box
        data-testid="kopf-links"
        sx={{ flex: 1, minWidth: 0, display: 'flex', alignItems: 'center', gap: 2 }}
      >
        {schalter}
        <KopfPfad />
      </Box>
    </Box>
  );
}
