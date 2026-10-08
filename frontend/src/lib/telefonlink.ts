/**
 * Die Ziele der beiden Links, die an einem Ansprechpartner haengen: `tel:` und `mailto:` (E16).
 *
 * **Das Schema steht fest im Code, nie im Wert.** Ein gespeicherter Wert kommt aus einer Maske und
 * darf nicht bestimmen, wohin ein Link fuehrt — `javascript:alert(1)` in einem `href` waere sonst
 * ein Weg, fremden Code auf der eigenen Seite laufen zu lassen. Deshalb wird der Wert hier nicht
 * bereinigt, sondern beurteilt: Was nicht wie eine Nummer beziehungsweise eine Adresse aussieht,
 * bekommt gar keinen Link.
 */

/**
 * Zeichen, die in einer aufgeschriebenen Rufnummer vorkommen duerfen.
 *
 * Bewusst eine Erlaubnisliste und keine Verbotsliste: Ein Buchstabe oder ein Doppelpunkt im Wert
 * ist keine Nummer, und eine Verbotsliste muesste jede Schreibweise eines Angriffs vorhersehen.
 */
const ERLAUBT = /^[\d+\-/().\s]*$/;

/** Alles ausser Ziffern. */
const KEINE_ZIFFER = /\D/g;

/**
 * Das Ziel eines `tel:`-Links aus einer gespeicherten Nummer — nur Ziffern und ein fuehrendes `+`.
 *
 * `null`, wenn der Wert fremde Zeichen traegt oder keine einzige Ziffer enthaelt: Ein Link ohne
 * waehlbare Nummer fuehrt nirgendwohin und waere nur ein Ziel fuer den Tabulator.
 */
export function telefonZiel(wert: string): string | null {
  const sauber = wert.trim();
  if (!ERLAUBT.test(sauber)) {
    return null;
  }
  const ziffern = sauber.replace(KEINE_ZIFFER, '');
  if (ziffern === '') {
    return null;
  }
  // Das `+` gilt allein als Laenderzeichen am Anfang; mitten im Wert ist es Zierde und faellt weg.
  return `tel:${sauber.startsWith('+') ? '+' : ''}${ziffern}`;
}

/**
 * Das Ziel eines `mailto:`-Links aus einer gespeicherten Adresse.
 *
 * Der Wert wird vollstaendig kodiert: Ein angehaengtes `?bcc=…` waere sonst ein Kopffeld der Mail,
 * die der Nutzer im Glauben abschickt, sie ginge nur an den Ansprechpartner.
 */
export function emailZiel(wert: string): string | null {
  const sauber = wert.trim();
  return sauber === '' ? null : `mailto:${encodeURIComponent(sauber)}`;
}
