/**
 * Die Schreibweise der Rechnungsnummer in der Oberflaeche (Plan #161, E5).
 *
 * Ein Muster besteht aus festem Text und Platzhaltern in geschweiften Klammern:
 *
 * - `{N…}` ist die laufende Nummer. Die Anzahl der `N` ist die **Mindestbreite** — kuerzere Nummern
 *   werden links mit Nullen aufgefuellt, laengere gehen vollstaendig hinaus. `{N}` heisst also
 *   „ohne Auffuellen".
 * - `{JJJJ}` oder `{JJ}` ist das Jahr, vierstellig oder zweistellig.
 *
 * Gueltig ist ein Muster mit genau einem Nummern-Platzhalter, hoechstens einem Jahres-Platzhalter,
 * hoechstens {@link MAX_LAENGE} Zeichen und festem Text aus Buchstaben, Ziffern und `-`, `_`, `/`,
 * `.`. Die enge Zeichenliste haelt die Nummer druck- und dateinamentauglich.
 *
 * Das Gegenstueck ist
 * `src/main/java/org/mwolff/fbcrm/rechnung/domain/Nummernmuster.java`. Die Regel lebt zweimal: Dort
 * entscheidet sie beim Speichern, hier speist sie die Vorschau, waehrend der Benutzer tippt (#159,
 * Kriterium 6). Beide Fassungen fahren dieselbe Beispieltabelle (`NummernmusterTest`,
 * `nummernmuster.test.ts`); das Backend bleibt die Instanz, die entscheidet.
 *
 * Ohne React und ohne Zustand: Die Regel ist eine Rechnung auf Zeichenketten, und eine Ansicht, die
 * sie in sich truege, waere fuer sich nicht pruefbar.
 */

/** Die Spaltenbreite von `rechnung_einstellungen.nummer_muster`. */
export const MAX_LAENGE = 50;

const PLATZHALTER = /\{([^{}]*)\}/gu;
const NUMMER = /^N+$/u;
const JAHR = /^(?:JJ|JJJJ)$/u;
const FESTER_TEXT = /^[A-Za-z0-9\-_/.]*$/u;

const KURZES_JAHR = 2;
const LANGES_JAHR = 4;
const JAHRHUNDERT = 100;

/**
 * Ob die Schreibweise stimmt.
 *
 * Die Pruefung antwortet und wirft nicht: Die Maske entscheidet, ob daraus eine Meldung am Feld
 * wird oder ein Hinweis anstelle der Vorschau.
 */
export function istGueltigesMuster(muster: string): boolean {
  if (muster.length > MAX_LAENGE) {
    return false;
  }
  let nummern = 0;
  let jahre = 0;
  for (const treffer of muster.matchAll(PLATZHALTER)) {
    const inhalt = treffer[1];
    if (NUMMER.test(inhalt)) {
      nummern += 1;
    } else if (JAHR.test(inhalt)) {
      jahre += 1;
    } else {
      return false;
    }
  }
  // Der feste Text wird geprueft, nachdem die Platzhalter herausgefallen sind. Dass dabei getrennte
  // Stuecke aneinanderstossen, aendert nichts: Geprueft wird Zeichen fuer Zeichen.
  const ohnePlatzhalter = muster.replace(PLATZHALTER, '');
  return nummern === 1 && jahre <= 1 && FESTER_TEXT.test(ohnePlatzhalter);
}

/**
 * Die Rechnungsnummer aus Muster, laufender Nummer und Jahr — `null` bei ungueltigem Muster.
 *
 * `null` heisst „daraus laesst sich keine Nummer bilden"; die Maske zeigt dann einen Hinweis
 * anstelle der Vorschau. Eine halb ersetzte Zeichenkette waere eine Nummer, die niemand vergibt.
 */
export function rechnungsnummer(
  muster: string,
  laufendeNummer: number,
  jahr: number,
): string | null {
  if (!istGueltigesMuster(muster)) {
    return null;
  }
  return muster.replace(PLATZHALTER, (_ganz, inhalt: string) =>
    ersatz(inhalt, laufendeNummer, jahr),
  );
}

function ersatz(inhalt: string, laufendeNummer: number, jahr: number): string {
  if (NUMMER.test(inhalt)) {
    return gefuellt(laufendeNummer, inhalt.length);
  }
  if (inhalt.length === KURZES_JAHR) {
    return gefuellt(jahr % JAHRHUNDERT, KURZES_JAHR);
  }
  return gefuellt(jahr, LANGES_JAHR);
}

/*
 * Ohne Verzweigung, und das ist Absicht: Als Bedingung waere die Mindestbreite nicht pruefbar — bei
 * genau passender Laenge liefern „auffuellen" und „nicht auffuellen" dieselbe Zeichenkette
 * (derselbe Grund wie in `Nummernmuster.java`).
 */
function gefuellt(wert: number, breite: number): string {
  return String(wert).padStart(breite, '0');
}
