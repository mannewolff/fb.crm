import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';

import type { ToenungName } from '../theme';
import { KARTE_INNENABSTAND, RADIUS_GROSS } from '../theme';
import Mal from './Mal';

/**
 * Die Kopfkarte: der Kopf einer Detailansicht (Vorlage `.held` Z. 55–57, HTML Z. 133–151).
 *
 * **Zeigen, wie es steht** (CLAUDE-design.md, Leitgedanke): Zuerst Kuerzel, Name, die wichtigsten
 * Stammdaten in einer Zeile und die Zustands-Chips — dann die Aktionen. Die Reihenfolge im DOM ist
 * die Reihenfolge, in der vorgelesen wird, und sie entspricht der gelesenen.
 *
 * Der Titel ist die **eine `h1` der Ansicht**. Wer mit dem Screenreader auf eine Seite kommt,
 * findet damit an erster Stelle, um welches Objekt es geht.
 *
 * Das **Mal** gehoert der Kopfkarte, nicht dem Aufrufer: Groesse (84 px) und Form (das abgerundete
 * Quadrat) sind an dieser Stelle festgelegt, und ein Aufrufer, der sie uebergaebe, koennte sie
 * falsch waehlen. Es steht neben dem Namen und ist darum dekorativ.
 *
 * Der **Pfirsich-Kreis rechts oben** ist Schmuck. Er entsteht als `::after` und damit als reine
 * Zeichnung ohne Knoten im Dokument — ein Hilfsmittel findet ihn nicht einmal, um ihn zu
 * uebergehen.
 */
export interface KopfkarteProps {
  /** Der Name, aus dem das Kuerzel des Mals entsteht. */
  readonly malName: string;
  /** Die Toenung des Mals. Vorgabe Pfirsich — die Kupfer-Familie (CLAUDE-design.md, „Toenungen"). */
  readonly malToenung?: ToenungName;
  /** Der Titel der Ansicht. Als Knoten, damit eine Nummer ihre Tabellenziffern tragen kann. */
  readonly titel: ReactNode;
  /** Eine Zeile mit den wichtigsten Stammdaten, Teile durch „·" getrennt. */
  readonly zeile?: ReactNode;
  /** Die Zustands-Chips unter der Zeile. */
  readonly chips?: ReactNode;
  /** Rechts: weiche Taste, Kupfertaste, ⋯-Menue. */
  readonly aktionen?: ReactNode;
}

/** Kantenlaenge des Mals in der Kopfkarte (CLAUDE-design.md, „Bausteine"). */
const MAL = 84;

/** Spalt wie in der Vorlage (`.held` Z. 55); den Innenabstand teilt die Kopfkarte mit der Karte. */
const SPALT = 24;

export default function Kopfkarte({
  malName,
  malToenung = 'pfirsich',
  titel,
  zeile,
  chips,
  aktionen,
}: KopfkarteProps) {
  return (
    <Paper
      elevation={0}
      data-testid="kopfkarte"
      sx={(theme) => ({
        position: 'relative',
        overflow: 'hidden',
        display: 'flex',
        alignItems: 'center',
        flexWrap: 'wrap',
        gap: `${String(SPALT)}px`,
        padding: `${String(KARTE_INNENABSTAND)}px`,
        borderRadius: `${RADIUS_GROSS}px`,
        background: theme.vars.palette.kupferwolke.flaeche,
        boxShadow: theme.vars.palette.kupferwolke.schatten.karte,
        '&::after': {
          content: '""',
          position: 'absolute',
          right: -80,
          top: -120,
          width: 340,
          height: 340,
          borderRadius: '50%',
          background: `radial-gradient(circle, ${theme.vars.palette.kupferwolke.toenung.pfirsich.flaeche}, transparent 70%)`,
          pointerEvents: 'none',
        },
      })}
    >
      <Mal name={malName} groesse={MAL} toenung={malToenung} form="quadrat" />
      <Box sx={{ minWidth: 0, flex: '1 1 240px', position: 'relative', zIndex: 1 }}>
        <Typography variant="h1" data-testid="kopfkarte-titel" sx={{ margin: '0 0 6px' }}>
          {titel}
        </Typography>
        {zeile === undefined ? null : (
          <Typography
            data-testid="kopfkarte-zeile"
            sx={(theme) => ({
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              flexWrap: 'wrap',
              color: theme.vars.palette.kupferwolke.textMatt,
            })}
          >
            {zeile}
          </Typography>
        )}
        {chips === undefined ? null : (
          <Box
            data-testid="kopfkarte-chips"
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              flexWrap: 'wrap',
              marginTop: '12px',
            }}
          >
            {chips}
          </Box>
        )}
      </Box>
      {aktionen === undefined ? null : (
        <Box
          data-testid="kopfkarte-aktionen"
          sx={(theme) => ({
            marginLeft: 'auto',
            display: 'flex',
            alignItems: 'center',
            gap: '10px',
            flexWrap: 'wrap',
            // Der Schmuck-Kreis liegt hinter den Aktionen; ohne diese Ebene faenge er ihre Klicks.
            position: 'relative',
            zIndex: 1,
            // Unter 900 px bricht der Aktionsbereich unter den Titel, statt ihn zu quetschen.
            [theme.breakpoints.down('md')]: { marginLeft: 0, width: '100%' },
          })}
        >
          {aktionen}
        </Box>
      )}
    </Paper>
  );
}
