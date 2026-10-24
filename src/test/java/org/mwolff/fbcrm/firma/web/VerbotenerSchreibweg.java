package org.mwolff.fbcrm.firma.web;

import org.mwolff.fbcrm.common.web.Bedingungskopf;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Der Verstoss, den {@code ArchitectureTest} verbieten soll — ein schreibender Weg eines
 * geschuetzten Datensatzes, der keinen {@link Bedingungskopf} nimmt (Plan #415, E3; Issue #456).
 *
 * <p><b>Diese Klasse ist absichtlich falsch gebaut</b> und steht nur im Testbaum: Die Regel {@code
 * geschuetzteControllers_thenEveryWritingRouteTakesABedingungskopf} laeuft einmal gegen den
 * Produktionscode, wo sie nichts findet, und einmal gegen genau diese Klasse, wo sie an {@link
 * #aendernOhneStand} anschlagen muss. Die beiden anderen Wege stehen daneben, damit der Lauf auch
 * zeigt, dass die Regel sie durchlaesst: den anlegenden und den mit Kopf.
 *
 * <p>Nicht in {@code ArchitectureTest} selbst, sondern hier: Die Regel trifft am Paket, und nur in
 * den Web-Paketen der geschuetzten Datensaetze ist der Verstoss einer. Bewusst ohne
 * {@code @RestController}: Die Komponentensuche der Integrationstests saehe die Klasse sonst und
 * oeffnete den ungeschuetzten Weg wirklich.
 */
public final class VerbotenerSchreibweg {

  /** Legt an — frei, auch ohne Kopf (Kriterium 7 der Anforderung #411). */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public void anlegen() {
    // nur fuer die Regel
  }

  /** Aendert auf dem vorgelegten Stand — so sieht ein geschuetzter Weg aus. */
  @PutMapping("/{id}")
  public void aendern(final Bedingungskopf stand) {
    // nur fuer die Regel
  }

  /** Aendert ohne Stand — genau das soll die Regel finden. */
  @PutMapping("/{id}/ohne-stand")
  public void aendernOhneStand() {
    // nur fuer die Regel
  }
}
