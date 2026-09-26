import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';

import type { ToenungName } from '../theme';
import { RADIUS_KLEIN, RADIUS_SYMBOL } from '../theme';

/**
 * Die Zeitleiste: eine Folge von Ereignissen (Vorlage `.zeit` Z. 94–99, HTML Z. 189–196).
 *
 * Ein **eigener Baustein**, weil die Vorlage die Zeitleiste als Baustein zeigt und nicht als
 * Gestaltung einer einzelnen Ansicht (Plan E11): Die Historie eines Vorgangs befuellt ihn spaeter
 * nur noch, statt ihn nachzubauen.
 *
 * Eine **echte Liste** (`ul`/`li`): Nur so sagt der Screenreader an, wie viele Ereignisse es sind
 * und bei welchem man gerade ist. Das Symbolfeld bleibt aus dem Vorgelesenen heraus — die Art des
 * Ereignisses steht im Titel als Wort, die Toenung stuetzt sie nur
 * (CLAUDE-design.md, „Zustandsformen").
 *
 * Gegliedert wird ohne Linien: Jeder Eintrag ist eine Flaeche mit Radius klein, die im Hover auf
 * „Flaeche weich" geht.
 */
export interface ZeitleisteEintrag {
  /** Der stabile Schluessel des Ereignisses — keine Position in der Liste. */
  readonly id: string;
  /** Das Symbol im Feld links. Dekorativ; die Zeitleiste haengt es aus dem Baum aus. */
  readonly symbol: ReactNode;
  /** Die Toenung nach Art des Ereignisses (CLAUDE-design.md, „Toenungen"). */
  readonly toenung: ToenungName;
  /** Der Titel — er nennt die Art des Ereignisses in Worten. */
  readonly titel: ReactNode;
  /**
   * Der zugaengliche Name des Eintrags.
   *
   * `listitem` bildet seinen Namen nicht aus dem Inhalt: Ohne Benennung haette der Eintrag gar
   * keinen, und wer durch die Liste springt, hoerte nur „Listenelement". Sie ist wahlfrei, weil
   * eine Zeitleiste, deren Titel schon alles sagt, nichts zu wiederholen hat.
   */
  readonly benennung?: string;
  /** Die Unterzeile: Zeitpunkt, Autor, Betrag. */
  readonly unterzeile?: ReactNode;
  /** Zusatz im Eintrag selbst, etwa die Maske zum Aendern an Ort und Stelle. */
  readonly inhalt?: ReactNode;
}

export interface ZeitleisteProps {
  readonly eintraege: readonly ZeitleisteEintrag[];
  /**
   * Der Name der Liste — etwa „Historie".
   *
   * Ohne ihn bleibt die Liste unbenannt: Steht sie unter einem Kartenkopf, der sie schon nennt,
   * waere ein zweiter Name dieselbe Angabe ein zweites Mal.
   */
  readonly beschriftung?: string;
}

/** Kantenlaenge des Symbolfelds (CLAUDE-design.md, „Bausteine": 36 px). */
const SYMBOLFELD = 36;

export default function Zeitleiste({ eintraege, beschriftung }: ZeitleisteProps) {
  return (
    <Box
      component="ul"
      aria-label={beschriftung}
      sx={{
        listStyle: 'none',
        margin: 0,
        padding: 0,
        display: 'flex',
        flexDirection: 'column',
        gap: '4px',
      }}
    >
      {eintraege.map((eintrag) => (
        <Box
          component="li"
          key={eintrag.id}
          aria-label={eintrag.benennung}
          sx={(theme) => ({
            display: 'flex',
            alignItems: 'flex-start',
            gap: '14px',
            padding: '12px',
            borderRadius: `${RADIUS_KLEIN}px`,
            transition: 'background .12s ease',
            '&:hover': { background: theme.vars.palette.kupferwolke.flaecheWeich },
          })}
        >
          <Box
            data-testid="zeitleiste-symbol"
            aria-hidden
            sx={(theme) => ({
              width: SYMBOLFELD,
              height: SYMBOLFELD,
              flex: 'none',
              display: 'grid',
              placeItems: 'center',
              borderRadius: `${RADIUS_SYMBOL}px`,
              color: theme.vars.palette.kupferwolke.toenung[eintrag.toenung].schrift,
              background: theme.vars.palette.kupferwolke.toenung[eintrag.toenung].flaeche,
            })}
          >
            {eintrag.symbol}
          </Box>
          <Box sx={{ minWidth: 0, flex: 1 }}>
            <Typography component="span" sx={{ display: 'block', fontSize: 14, fontWeight: 600 }}>
              {eintrag.titel}
            </Typography>
            {eintrag.unterzeile === undefined ? null : (
              <Typography
                component="span"
                data-testid="zeitleiste-unterzeile"
                sx={(theme) => ({
                  display: 'block',
                  fontSize: 12.5,
                  color: theme.vars.palette.kupferwolke.textSchwach,
                })}
              >
                {eintrag.unterzeile}
              </Typography>
            )}
            {eintrag.inhalt === undefined ? null : (
              <Box sx={{ marginTop: '10px' }}>{eintrag.inhalt}</Box>
            )}
          </Box>
        </Box>
      ))}
    </Box>
  );
}
