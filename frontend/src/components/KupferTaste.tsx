import Button from '@mui/material/Button';
import type { Theme } from '@mui/material/styles';
import type { ReactNode } from 'react';
import { Link as RouterLink } from 'react-router-dom';

import { RADIUS_RUND, TASTE_INNENABSTAND } from '../theme';
import TastenSymbol from './TastenSymbol';

/**
 * Die Haupttaste einer Ansicht: eine Pille im Kupferverlauf mit farbigem Schatten
 * (Vorlage `.taste` Z. 63–64, `.taste.primaer` Z. 65).
 *
 * **Genau eine je Ansicht** (CLAUDE-design.md, „Tasten") — sie steht in der Kopfkarte einer
 * Detailansicht, rechts im Kartenkopf einer Liste oder am Ende eines Formulars.
 *
 * Zwei Gestalten, eine Optik: Mit `to` ist die Taste ein **echter Link** und kein Knopf mit
 * `onClick`. Ein Weg gehoert in ein `a` mit `href` — sonst faellt er aus dem Tabulatorweg der
 * Links, laesst sich nicht in einem neuen Reiter oeffnen und wird vom Screenreader als Schalter
 * angesagt, obwohl er die Seite wechselt.
 */
export interface KupferTasteProps {
  readonly children: ReactNode;
  /** Das Ziel eines Weges. Ohne `to` ist die Taste der Absender ihres Formulars. */
  readonly to?: string;
  /**
   * Was die Taste tut, wo sie kein Weg und kein Absender ist — „Abschliessen", „Freigeben".
   *
   * Mit `onClick` traegt sie `type="button"` statt `submit`: Eine Hauptaktion steht nicht immer in
   * einem Formular, und eine `submit`-Taste ausserhalb eines Formulars tut beim Klick nichts.
   */
  readonly onClick?: () => void;
  /** Gesperrt, solange die Aktion laeuft — nur fuer die schaltenden Gestalten. */
  readonly disabled?: boolean;
  /** Das stuetzende Symbol links der Aufschrift. */
  readonly symbol?: ReactNode;
}

/**
 * Die Gestalt der Kupfertaste, geteilt von beiden Varianten.
 *
 * Sie ist ausgewiesen, aus demselben Grund wie {@link weichSx}: Eine dritte Stelle braucht dieselbe
 * Pille, die diese Komponente nicht bauen kann — das „Herunterladen" der gestellten Rechnung ist
 * ein `<a download>` auf einen Weg der Schnittstelle, kein Router-Link und kein Schalter
 * (Issue #186). Eine Abschrift der Gestalt dort liefe beim naechsten Nachziehen der Vorlage
 * auseinander.
 */
export function kupferSx(theme: Theme) {
  return {
    borderRadius: `${RADIUS_RUND}px`,
    padding: TASTE_INNENABSTAND,
    gap: '8px',
    color: theme.vars.palette.kupferwolke.kupferSchrift,
    background: `linear-gradient(135deg, ${theme.vars.palette.kupferwolke.kupferTaste}, ${theme.vars.palette.kupferwolke.kupferTief})`,
    border: 0,
    boxShadow: theme.vars.palette.kupferwolke.schatten.kupfer,
    '&:hover': {
      background: `linear-gradient(135deg, ${theme.vars.palette.kupferwolke.kupferTaste}, ${theme.vars.palette.kupferwolke.kupferTief})`,
      boxShadow: theme.vars.palette.kupferwolke.schatten.kupfer,
      // Tasten heben sich im Hover um 1 px (CLAUDE-design.md, „Tasten").
      transform: 'translateY(-1px)',
    },
    '&:active': { transform: 'none' },
  };
}

export default function KupferTaste({
  children,
  to,
  onClick,
  disabled = false,
  symbol,
}: KupferTasteProps) {
  const inhalt = (
    <>
      {symbol === undefined ? null : <TastenSymbol>{symbol}</TastenSymbol>}
      {children}
    </>
  );
  if (to !== undefined) {
    return (
      <Button component={RouterLink} to={to} sx={kupferSx}>
        {inhalt}
      </Button>
    );
  }
  return (
    <Button
      type={onClick === undefined ? 'submit' : 'button'}
      onClick={onClick}
      disabled={disabled}
      sx={kupferSx}
    >
      {inhalt}
    </Button>
  );
}
