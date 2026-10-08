import Box from '@mui/material/Box';
import { IconAlertTriangle } from '@tabler/icons-react';

import { dezimal } from '../lib/geld';

/**
 * Der Hinweis an einer Zeile, die ueber das Angebot hinausgeht (#160, Kriterien 8 und 26).
 *
 * <b>Wort und Symbol, nicht Farbe allein</b> (CLAUDE-design.md, „Zustandsformen"): Die Toenung
 * stuetzt die Aussage, sie traegt sie nicht.
 *
 * Er steht hier und nicht in einer Ansicht, weil ihn zwei zeigen: die Entwurfsmaske der Rechnung
 * an der Menge, die gerade getippt wird ({@link RechnungPage}), und die Angebotsansicht an der
 * Position, deren Rechnungen zusammen zu viel ergeben ({@link AngebotPage}). Zweimal derselbe Satz
 * in zwei Dateien hiesse, ihn auch zweimal zu aendern.
 *
 * Die Kennung im `data-testid` ist die der <b>Angebotsposition</b> — an ihr haengt der Stand in
 * beiden Ansichten.
 *
 * <b>Zwei Arten, zwei Saetze</b> (Issue #193, Kriterium 8): Die abgerechnete Menge geht „ueber das
 * Angebot" hinaus, die angefallene Zeit „ueberschreitet das Kontingent". Beide koennen an derselben
 * Zeile stehen — darum traegt jede Art ihre eigene Kennung, sonst gaebe es `data-testid` zweimal.
 */

/** Die Symbolgroesse im Hinweis an einer Zeile. */
const SYMBOL_HINWEIS = 14;

/** Woran der Hinweis haengt: an der abgerechneten Menge oder an der angefallenen Zeit. */
export type Hinweisart = 'abrechnung' | 'angefallen';

/**
 * Der Satz je Art.
 *
 * Die Einheit steht nur im Satz zur Zeit, und sie steht dort fest: Angefallen ist nur, was auf eine
 * Position nach Aufwand in <b>Stunden</b> gebucht wurde — eine andere Einheit kommt hier nicht an
 * (`arbeitszeit.application.Buchbarkeit`).
 */
const SATZ: Readonly<Record<Hinweisart, (menge: string) => string>> = {
  abrechnung: (menge) => `${menge} über dem Angebot`,
  angefallen: (menge) => `Kontingent um ${menge} Std. überschritten`,
};

/** Die Kennung je Art — zwei Hinweise an einer Zeile bleiben damit unterscheidbar. */
const KENNUNG: Readonly<Record<Hinweisart, string>> = {
  abrechnung: 'zeile-hinweis',
  angefallen: 'zeile-angefallen-hinweis',
};

export interface UeberschreitungshinweisProps {
  /** Was mit dieser Menge zusammen zu viel waere, in Hundertsteln — stets groesser als 0. */
  readonly mengeInHundertsteln: number;
  /** Die Kennung der Angebotsposition, an der der Hinweis steht. */
  readonly angebotPositionId: number;
  /** Woran der Hinweis haengt; ohne Angabe die abgerechnete Menge. */
  readonly art?: Hinweisart;
}

export default function Ueberschreitungshinweis({
  mengeInHundertsteln,
  angebotPositionId,
  art = 'abrechnung',
}: UeberschreitungshinweisProps) {
  return (
    <Box
      component="span"
      data-testid={`${KENNUNG[art]}-${String(angebotPositionId)}`}
      sx={(theme) => ({
        display: 'inline-flex',
        alignItems: 'center',
        gap: '4px',
        color: theme.vars.palette.kupferwolke.toenung.bernstein.schrift,
      })}
    >
      <IconAlertTriangle size={SYMBOL_HINWEIS} stroke={1.8} aria-hidden />
      {SATZ[art](dezimal(mengeInHundertsteln, ','))}
    </Box>
  );
}
