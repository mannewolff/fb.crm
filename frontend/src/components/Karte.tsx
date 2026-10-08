import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';

import { KARTE_INNENABSTAND, RADIUS_GROSS, RADIUS_RUND } from '../theme';

/**
 * Die Karte: die tragende, schwebende Flaeche der Buehne (Vorlage `.karte` Z. 53, `.kopfzeile`
 * Z. 79–80, `.karte h2 .anzahl` Z. 78).
 *
 * Sie traegt den Namen der Designquelle (E4): Die abgeloeste Designsprache kannte an dieser Stelle
 * einen anderen Baustein, und ein alter Name im Code fuehrt kuenftige Pakete dorthin zurueck.
 *
 * **Karten statt Linien** (CLAUDE-design.md, Leitgedanke): Die Karte gliedert durch Flaeche, Radius
 * und weichen Schatten. Unter dem Kartenkopf steht darum **keine** Trennlinie mehr; den Abstand
 * traegt der Kopf selbst.
 *
 * Der Kopf entsteht nur mit einem Titel. Eine Karte ohne Titel — etwa eine Meldung oder eine Liste
 * unter einer eigenen Werkzeugleiste — truege sonst eine leere Leiste.
 *
 * Der Titel ist eine Ueberschrift der zweiten Ebene: Die Seiten tragen genau eine `h1`, und die
 * Karten darunter gliedern sie. Wer mit dem Screenreader durch die Ueberschriften springt, findet
 * damit die Abschnitte einer Ansicht.
 *
 * Wo eine Ansicht aus genau dieser einen Karte besteht und keine {@link Kopfkarte} darueber steht,
 * gibt es sonst gar keine `h1` — dann hebt `titelEbene={1}` den Kartentitel auf die erste Ebene.
 * Nur die Ebene wechselt, nicht die Gestalt: Der Kartenkopf sieht in beiden Faellen gleich aus, und
 * die Vorlage kennt an dieser Stelle nur eine Groesse (`.karte h2` Z. 79).
 */
export interface KarteProps {
  readonly children: ReactNode;
  /** Ohne Titel entsteht kein Kopf. */
  readonly titel?: string;
  /** Die Zahl der Eintraege, als runde Plakette neben dem Titel. */
  readonly anzahl?: number;
  /** Beiwerk neben dem Titel, etwa ein Zusatz in Worten. */
  readonly notiz?: string;
  /** Rechts im Kopf: Filter, Waehler, die Hauptaktion einer Liste. */
  readonly werkzeug?: ReactNode;
  /** Die Ebene des Titels. Vorgabe 2; `1`, wo die Karte die ganze Ansicht ist. */
  readonly titelEbene?: 1 | 2;
}

/** Abstand zwischen Kartenkopf und Inhalt (Vorlage `.kopfzeile` Z. 79). */
const KOPF_ABSTAND = 18;

export default function Karte({
  children,
  titel,
  anzahl,
  notiz,
  werkzeug,
  titelEbene = 2,
}: KarteProps) {
  return (
    <Paper
      elevation={0}
      sx={(theme) => ({
        borderRadius: `${RADIUS_GROSS}px`,
        background: theme.vars.palette.kupferwolke.flaeche,
        boxShadow: theme.vars.palette.kupferwolke.schatten.karte,
        padding: `${String(KARTE_INNENABSTAND)}px`,
        overflow: 'hidden',
      })}
    >
      {titel === undefined ? null : (
        <Box
          data-testid="karte-kopf"
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1.25,
            flexWrap: 'wrap',
            marginBottom: `${String(KOPF_ABSTAND)}px`,
          }}
        >
          <Typography variant="h2" component={titelEbene === 1 ? 'h1' : 'h2'}>
            {titel}
          </Typography>
          {anzahl === undefined ? null : (
            <Box
              component="span"
              data-testid="karte-anzahl"
              sx={(theme) => ({
                fontSize: 12,
                fontWeight: 600,
                borderRadius: `${RADIUS_RUND}px`,
                padding: '2px 9px',
                color: theme.vars.palette.kupferwolke.textMatt,
                background: theme.vars.palette.kupferwolke.flaecheWeich,
              })}
            >
              {anzahl}
            </Box>
          )}
          {notiz === undefined ? null : (
            <Typography
              sx={(theme) => ({
                fontSize: 12.5,
                color: theme.vars.palette.kupferwolke.textSchwach,
              })}
            >
              {notiz}
            </Typography>
          )}
          {werkzeug === undefined ? null : (
            <Box
              sx={{
                marginLeft: 'auto',
                display: 'flex',
                alignItems: 'center',
                gap: 1.25,
                flexWrap: 'wrap',
              }}
            >
              {werkzeug}
            </Box>
          )}
        </Box>
      )}
      {children}
    </Paper>
  );
}
