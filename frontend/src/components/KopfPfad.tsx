import Box from '@mui/material/Box';
import Breadcrumbs from '@mui/material/Breadcrumbs';
import Link from '@mui/material/Link';
import { IconChevronRight } from '@tabler/icons-react';
import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { Link as RouterLink } from 'react-router-dom';

/**
 * Der Pfad im Kopf: verlinkte Stufen, die letzte als Text (E6, Vorlage `.kopf`/`.pfad` Z. 46–47).
 *
 * Der Kopf steht im Rahmen und nicht in der Ansicht — dieses Modul ist die Bruecke:
 * {@link AppShell} spannt den Kontext auf, {@link TopBar} zeichnet den Pfad, und eine Seite meldet
 * ihren mit {@link useKopfPfad}.
 *
 * **Daten statt Portal.** Ein Pfad ist kein Baustein, sondern eine
 * kurze Liste aus Beschriftung und Ziel. Sie liegt darum im Zustand des Kontexts. Damit das nicht
 * zur Endlosschleife wird — eine Ansicht schreibt ihre Stufen bei jedem Rendern neu, und ein
 * Effekt an der Identitaet des Feldes liefe immer wieder —, haengt der Effekt an der
 * **Zeichenkette** der Stufen: Gemeldet wird genau dann, wenn sich Beschriftung oder Ziel einer
 * Stufe wirklich aendert.
 *
 * Der Kopf zeigt sonst nichts: keine Suche, keine Glocke. Beides waere ein Bedienelement ohne
 * fachlichen Anlass (Plan A3).
 */

/** Eine Stufe oberhalb der aktuellen Ansicht — immer ein Weg. */
export interface PfadVerweis {
  readonly titel: string;
  readonly ziel: string;
}

/** Der ganze Pfad: die Wege darueber und die Ansicht, auf der man steht. */
interface Pfad {
  readonly verweise: readonly PfadVerweis[];
  readonly aktuell: string;
}

interface KopfPfadWert {
  /** Der gemeldete Pfad, oder `null`, solange keine Ansicht einen gemeldet hat. */
  readonly pfad: Pfad | null;
  readonly meldePfad: (pfad: Pfad | null) => void;
}

const Kontext = createContext<KopfPfadWert | null>(null);

function useKontext(): KopfPfadWert {
  const wert = useContext(Kontext);
  if (wert === null) {
    throw new Error('Der Pfad des Kopfes steht nur innerhalb von AppShell zur Verfügung.');
  }
  return wert;
}

/** Spannt den Kontext auf; gehoert um Kopf und Inhalt herum. */
export function KopfPfadProvider({ children }: { readonly children: ReactNode }) {
  const [pfad, meldePfad] = useState<Pfad | null>(null);
  const wert = useMemo(() => ({ pfad, meldePfad }), [pfad]);
  return <Kontext.Provider value={wert}>{children}</Kontext.Provider>;
}

/**
 * Meldet den Pfad der Ansicht an den Kopf — eine Zeile je Seite.
 *
 * `verweise` sind die Stufen darueber, `aktuell` die Ansicht selbst. Der Schnitt ist bewusst:
 * Die aktuelle Stufe ist nie ein Weg, und ein Feld, das beides koennte, haette einen Zweig, den
 * keine Ansicht geht.
 */
export function useKopfPfad(verweise: readonly PfadVerweis[], aktuell: string): void {
  const { meldePfad } = useKontext();
  // Der Abdruck ist zugleich die Nachricht: Was verglichen wird, wird auch gemeldet — so kann
  // beides nicht auseinanderlaufen (siehe Klassenkommentar).
  const abdruck = JSON.stringify({ verweise, aktuell });

  useEffect(() => {
    meldePfad(JSON.parse(abdruck) as Pfad);
    return () => {
      // Die Seite geht, ihr Pfad geht mit: Sonst stuende er noch ueber der naechsten Ansicht.
      meldePfad(null);
    };
  }, [abdruck, meldePfad]);
}

export default function KopfPfad() {
  const { pfad } = useKontext();
  if (pfad === null) {
    return null;
  }
  return (
    <Breadcrumbs
      aria-label="Pfad"
      separator={<IconChevronRight size={15} stroke={1.8} aria-hidden />}
      sx={(theme) => ({
        fontSize: 13.5,
        fontWeight: 500,
        color: theme.vars.palette.kupferwolke.textSchwach,
        minWidth: 0,
        '& .MuiBreadcrumbs-separator': { marginInline: '4px' },
        '& .MuiBreadcrumbs-ol': { flexWrap: 'nowrap' },
      })}
    >
      {pfad.verweise.map((verweis) => (
        <Link
          key={verweis.ziel}
          component={RouterLink}
          to={verweis.ziel}
          underline="hover"
          sx={(theme) => ({
            color: theme.vars.palette.kupferwolke.textSchwach,
            fontWeight: 500,
          })}
        >
          {verweis.titel}
        </Link>
      ))}
      <Box
        component="span"
        // Die letzte Stufe ist die Seite, auf der man steht — als Text, und fuer Hilfsmittel als
        // die aktuelle Stelle im Pfad ausgezeichnet (Vorlage `.pfad b` Z. 47).
        aria-current="page"
        sx={(theme) => ({
          color: theme.vars.palette.kupferwolke.text,
          fontWeight: 700,
          whiteSpace: 'nowrap',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
        })}
      >
        {pfad.aktuell}
      </Box>
    </Breadcrumbs>
  );
}
