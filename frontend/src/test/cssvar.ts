/**
 * Die Schreibweise, in der eine CSS-Variable des Themes **in der Regel** landet.
 *
 * `theme.vars.palette.…` liefert `var(--fb-…, #FBE4E4)` — mit Rueckfallwert. Emotion schreibt in die
 * erzeugte Regel nur `var(--fb-…)`, und genau das liest `getComputedStyle` in jsdom. Ohne dieses
 * Abschneiden verglichen Tests zwei Schreibweisen desselben Wertes und schlugen fehl, obwohl der
 * Baustein den richtigen Token traegt.
 */
export function ohneRueckfall(tokenWert: string): string {
  return tokenWert.replace(/,[^)]*\)$/, ')');
}
