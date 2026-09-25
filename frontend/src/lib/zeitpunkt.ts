/**
 * Zwischen dem Zeitpunkt der Schnittstelle und dem Feld der Maske.
 *
 * Das Backend fuehrt Zeitpunkte als `Instant`, also als Augenblick in UTC (`2026-09-24T09:15:00Z`).
 * Ein `<input type="datetime-local">` kennt dagegen keine Zeitzone: Es zeigt und liefert
 * `YYYY-MM-DDTHH:mm` und meint damit die Uhr dessen, der davorsitzt. Zwischen beiden liegt die
 * Umrechnung, und sie steht genau hier — nicht in jeder Maske noch einmal.
 *
 * Die Sekunden fallen dabei weg. Das ist die Zusage des Feldes, nicht ein Verlust: Wer einen
 * Kommentar auf gestern zurueckdatiert, waehlt eine Minute, keine Sekunde.
 */

function zweistellig(wert: number): string {
  return String(wert).padStart(2, '0');
}

/**
 * Der Zeitstempel der Schnittstelle als Wert fuer ein `datetime-local`-Feld — in Ortszeit.
 *
 * Ein Zeitstempel, der keiner ist, wird zur leeren Eingabe: Ein Feld mit unlesbarem Inhalt
 * lehnte der Browser stillschweigend ab, und die Maske stuende ohne Zeitpunkt da, ohne zu sagen,
 * warum.
 */
export function alsEingabe(zeitstempel: string): string {
  const zeit = new Date(zeitstempel);
  if (Number.isNaN(zeit.getTime())) {
    return '';
  }
  const tag = `${zeit.getFullYear()}-${zweistellig(zeit.getMonth() + 1)}-${zweistellig(zeit.getDate())}`;
  return `${tag}T${zweistellig(zeit.getHours())}:${zweistellig(zeit.getMinutes())}`;
}

/**
 * Die Eingabe des Feldes als Zeitstempel fuer die Schnittstelle — gelesen als Ortszeit.
 *
 * `null` heisst „kein Zeitpunkt": leeres Feld oder unlesbarer Inhalt. Die Maske macht daraus
 * eine Meldung am Feld; ein `Invalid Date` weiterzureichen ergaebe stattdessen einen Fehler aus
 * dem Backend, der den Grund nicht mehr nennt.
 */
export function alsZeitstempel(eingabe: string): string | null {
  // Ein leeres Feld faellt in denselben Zweig: `new Date('')` ist ein Invalid Date.
  const zeit = new Date(eingabe);
  if (Number.isNaN(zeit.getTime())) {
    return null;
  }
  return zeit.toISOString();
}
