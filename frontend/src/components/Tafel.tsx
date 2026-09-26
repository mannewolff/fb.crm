import Box from '@mui/material/Box';
import type { ReactNode } from 'react';

import { RADIUS_KLEIN } from '../theme';

/**
 * Die Tafel: eine Liste mit vielen Eintraegen in Spalten (CLAUDE-design.md, „Bausteine": viele
 * Eintraege sind Zeilen, kein Kartenraster; Vorlage `.kopfzeile` Z. 79–80 fuer den Kartenkopf
 * darueber).
 *
 * Eine <b>echte Tabelle</b> und kein Gitter aus `div`-Elementen: Nur so nennt der Screenreader beim
 * Vorlesen einer Zelle ihre Spalte, und nur so stehen die Tastaturwege der Tabellennavigation zur
 * Verfuegung. Die Kopfzellen tragen darum `scope="col"`.
 *
 * Die <b>Gestalt der Zeilen steht hier</b>, nicht bei den Aufrufern: Zellenpolster, Radius und die
 * weiche Flaeche beim Ueberfahren kommen ueber Nachfahren-Selektoren aus diesem `sx`. Eine Ansicht
 * uebergibt `tr`/`td` ohne eigene Farbwerte. Alle Farben, Radien und Tiefen stammen aus dem Theme.
 *
 * <b>Keine Zeilenlinien</b> (E10): Gegliedert wird durch Abstand und den Hover auf „Flaeche weich"
 * mit Radius klein — die Zeile wird zur Flaeche, nicht zum Streifen zwischen zwei Strichen. Die
 * Kopfzeile steht in Satzschreibung ohne Versalien (CLAUDE-design.md, Typografie).
 *
 * <b>Zahlen stellt der Aufrufer untereinander</b>: Nummern und Betraege tragen die Klasse aus
 * `ZAHLEN_KLASSE` an ihrer `td`. Die Tafel bekommt fertige Zeilen und weiss nicht, welche
 * Spalte eine Zahl fuehrt; eine Liste von Spaltennummern im Aufruf waere eine zweite, stumme
 * Beschreibung derselben Zeilen.
 *
 * Nicht enthalten sind Auswahlhaken, Massenleiste, Gruppenzeilen, Spaltenwahl und Export. Sie
 * gehoeren zu Funktionen, die fb.crm nicht hat.
 */
export interface TafelProps {
  /** Der zugaengliche Name der Tafel — sie ist eine eigene Landmarke im Dokument. */
  readonly beschriftung: string;
  /** Die Kopfzeile: je Spalte ihre Beschriftung, in der Reihenfolge der Zellen. */
  readonly spalten: readonly string[];
  /** Die Datenzeilen als `tr` mit `td`. */
  readonly children: ReactNode;
}

/** Die kleinste Breite, unter der die Tafel waagerecht rollt statt zu quetschen. */
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
            textAlign: 'left',
            fontSize: 12.5,
            fontWeight: 600,
            // Satzschreibung: keine Versalien mit Laufweite (CLAUDE-design.md, Typografie).
            textTransform: 'none',
            letterSpacing: 'normal',
            color: theme.vars.palette.kupferwolke.textSchwach,
            padding: '8px 14px',
            whiteSpace: 'nowrap',
          },
          '& tbody td': {
            padding: '12px 14px',
            fontSize: 13.5,
            verticalAlign: 'middle',
          },
          // Die Zeile wird im Hover zur weichen Flaeche mit Radius klein. Den Radius tragen die
          // aeussersten Zellen: Eine `tr` nimmt in einer Tabelle keinen eigenen Radius an.
          '& tbody td:first-of-type': {
            borderTopLeftRadius: `${RADIUS_KLEIN}px`,
            borderBottomLeftRadius: `${RADIUS_KLEIN}px`,
          },
          '& tbody td:last-of-type': {
            borderTopRightRadius: `${RADIUS_KLEIN}px`,
            borderBottomRightRadius: `${RADIUS_KLEIN}px`,
          },
          '& tbody tr td': { transition: 'background .12s ease' },
          '& tbody tr:hover td': {
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
