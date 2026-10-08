/**
 * Die Schreibweise, in der eine CSS-Variable des Themes **in der Regel** landet.
 *
 * `theme.vars.palette.…` liefert `var(--fb-…, #FBE4E4)` — mit Rueckfallwert. Emotion schreibt in die
 * erzeugte Regel nur `var(--fb-…)`, und genau das liest `getComputedStyle` in jsdom. Ohne dieses
 * Abschneiden verglichen Tests zwei Schreibweisen desselben Wertes und schlugen fehl, obwohl der
 * Baustein den richtigen Token traegt.
 *
 * Abgeschnitten wird ab dem ersten Komma hinter der letzten inneren Klammer bis vor die
 * schliessende Klammer am Ende — ohne regulaeren Ausdruck, weil der bisherige bei langen Werten
 * super-linear zurueckverfolgte (Sonar `typescript:S8786`). Ein Wert ohne Rueckfall bleibt, wie er
 * ist.
 */
export function ohneRueckfall(tokenWert: string): string {
  if (!tokenWert.endsWith(')')) {
    return tokenWert;
  }
  const rumpf = tokenWert.slice(0, -1);
  const komma = rumpf.indexOf(',', rumpf.lastIndexOf(')') + 1);
  return komma === -1 ? tokenWert : `${rumpf.slice(0, komma)})`;
}
