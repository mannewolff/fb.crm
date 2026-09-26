import Box from '@mui/material/Box';
import type { ReactNode } from 'react';

/**
 * Die Tafel: eine Liste in Spalten (Vorlage `.tafel-rahmen`/`.tafel` Z. 878–913, Ansicht „Liste"
 * Z. 1888–2045).
 *
 * Eine <b>echte Tabelle</b> und kein Gitter aus `div`-Elementen: Nur so nennt der Screenreader beim
 * Vorlesen einer Zelle ihre Spalte, und nur so stehen die Tastaturwege der Tabellennavigation zur
 * Verfuegung. Die Kopfzellen tragen darum `scope="col"`.
 *
 * Die <b>Gestalt der Zeilen steht hier</b>, nicht bei den Aufrufern: Zellenpolster, Haarlinien und
 * die Tönung beim Überfahren kommen ueber Nachfahren-Selektoren aus diesem `sx`. Eine Ansicht
 * uebergibt `tr`/`td` ohne eigene Farbwerte und ergaenzt allenfalls die Schriftart einer Spalte.
 * Alle Farben, Radien und Tiefen stammen aus dem Theme (CLAUDE-design.md).
 *
 * Die Kopfzeile <b>laeuft mit</b> (`position: sticky`). Sie steht unter dem Kopf der Anwendung, der
 * selbst schon klebt — daher der Abstand von oben und die kleinere Stapelstufe: Der Kopf bleibt
 * ueber der Tafel.
 *
 * Nicht enthalten sind Auswahlhaken, Massenleiste, Gruppenzeilen, Spaltenwahl und Export der
 * Vorlage (E22). Sie gehoeren zu Funktionen, die fb.crm nicht hat.
 */
export interface TafelProps {
  /** Der zugaengliche Name der Tafel — sie ist eine eigene Landmarke im Dokument. */
  readonly beschriftung: string;
  /** Die Kopfzeile: je Spalte ihre Beschriftung, in der Reihenfolge der Zellen. */
  readonly spalten: readonly string[];
  /** Die Datenzeilen als `tr` mit `td`. */
  readonly children: ReactNode;
}

/** Vorlage Z. 881: die Kopfzeile klebt unter dem Kopf der Anwendung. */
const KOPF_ABSTAND = 59;

/** Unter der Stapelstufe des Kopfes (`TopBar`: 20), ueber den Zeilen (Vorlage Z. 881). */
const KOPF_STUFE = 5;

/** Die kleinste Breite, unter der die Tafel waagerecht rollt statt zu quetschen (Vorlage Z. 879). */
const MINDESTBREITE = 720;

export default function Tafel({ beschriftung, spalten, children }: TafelProps) {
  return (
    <Box sx={{ overflowX: 'auto' }}>
      <Box
        component="table"
        aria-label={beschriftung}
        sx={(theme) => ({
          width: '100%',
          borderCollapse: 'separate',
          borderSpacing: 0,
          minWidth: MINDESTBREITE,
          '& thead th': {
            position: 'sticky',
            top: KOPF_ABSTAND,
            zIndex: KOPF_STUFE,
            textAlign: 'left',
            ...theme.typography.overline,
            color: theme.vars.palette.kupferwolke.textSchwach,
            padding: '10px 12px',
            background: theme.vars.palette.kupferwolke.flaeche,
            borderBottom: `1px solid ${theme.vars.palette.kupferwolke.linie}`,
            whiteSpace: 'nowrap',
          },
          '& tbody td': {
            padding: '9px 12px',
            borderBottom: `1px solid color-mix(in srgb, ${theme.vars.palette.kupferwolke.linie} 50%, transparent)`,
            fontSize: 12.5,
            verticalAlign: 'middle',
          },
          '& tbody tr:last-of-type td': { borderBottom: 0 },
          '& tbody tr': { transition: 'background .12s ease' },
          '& tbody tr:hover td': {
            // Hover-Grund von Zeilen ist die weiche Flaeche (CLAUDE-design.md, „Flaeche weich").
            background: theme.vars.palette.kupferwolke.flaecheWeich,
          },
        })}
      >
        <Box component="thead">
          <Box component="tr">
            {spalten.map((spalte) => (
              <Box component="th" scope="col" key={spalte}>
                {spalte}
              </Box>
            ))}
          </Box>
        </Box>
        <Box component="tbody">{children}</Box>
      </Box>
    </Box>
  );
}
