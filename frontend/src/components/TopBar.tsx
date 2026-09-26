import Box from '@mui/material/Box';
import type { ReactNode } from 'react';

import { useKopfAktionPlatz } from './KopfAktion';
import KopfPfad from './KopfPfad';

/**
 * Der Kopf: eine Zeile ohne eigene Flaeche ueber der Buehne (Vorlage `.kopf` Z. 46).
 *
 * Links der {@link KopfPfad} — und davor die Schaltflaeche der Schiene, sofern der Rahmen eine
 * mitbringt: unterhalb von 900 px liegt die Schiene dahinter (CLAUDE-design.md, „Mindestbreite").
 * Rechts bleibt der Platz fuer die Hauptaktion der Ansicht, bis alle Ansichten sie selbst tragen
 * (E5); welche Aktion dort steht, bringt die Ansicht mit ({@link KopfAktion}).
 *
 * Das Nutzer-Mal stand hier bis zur Kupferwolke; es ist die Nutzerkarte im Fuss der Schiene
 * geworden (E7). Suche, Glocke, Reiterleiste und Kennzahlen der Vorlage entstehen nicht — kein
 * Bedienelement ohne fachlichen Anlass (Plan A3).
 */
export default function TopBar({ schalter }: { readonly schalter?: ReactNode }) {
  const meldePlatz = useKopfAktionPlatz();

  return (
    <Box
      component="header"
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 2,
        padding: '6px 4px',
        // Der Kopf haelt seine Zeile, auch wenn eine Ansicht weder Pfad noch Aktion meldet —
        // sonst sprangen die Karten darunter je Ansicht um seine Hoehe.
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
      <Box
        data-testid="kopf-aktion"
        ref={meldePlatz}
        sx={{ display: 'flex', alignItems: 'center', gap: 1 }}
      />
    </Box>
  );
}
