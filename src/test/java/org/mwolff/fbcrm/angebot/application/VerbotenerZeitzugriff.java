package org.mwolff.fbcrm.angebot.application;

import java.math.BigDecimal;
import java.util.Map;
import org.mwolff.fbcrm.arbeitszeit.application.Arbeitszeitauskunft;

/**
 * Der Verstoss, den {@code ArchitectureTest} verbieten soll — eine Klasse in {@code angebot}, die
 * an {@code arbeitszeit} haengt (Issue #228).
 *
 * <p><b>Diese Klasse ist absichtlich falsch gebaut</b> und steht nur im Testbaum: Die Regel {@code
 * angebotModule_thenDoesNotReachIntoArbeitszeit} laeuft einmal gegen den Produktionscode, wo sie
 * nichts findet, und einmal gegen genau diese Klasse, wo sie anschlagen muss. Ohne den zweiten Lauf
 * waere nicht gezeigt, dass die Regel ueberhaupt greift — eine Regel, die nie etwas findet, sieht
 * von aussen aus wie eine, die nichts finden kann.
 *
 * <p>Der Produktionsweg ist {@link Zeitbindung}: Das Angebot laesst sich die bebuchten Positionen
 * sagen, statt die Zeiterfassung zu fragen.
 *
 * <p>Nicht in {@code ArchitectureTest} selbst, sondern hier: Die Regel trifft am Paket, und nur in
 * {@code org.mwolff.fbcrm.angebot..} ist der Verstoss einer.
 */
public final class VerbotenerZeitzugriff {

  private final Arbeitszeitauskunft auskunft;

  /**
   * @param auskunft die Auskunft, die dieses Modul gar nicht kennen darf
   */
  public VerbotenerZeitzugriff(final Arbeitszeitauskunft auskunft) {
    this.auskunft = auskunft;
  }

  /** Greift ueber die Modulgrenze — genau das soll die Regel finden. */
  public Map<Long, BigDecimal> angefallen(final long angebotId) {
    return auskunft.angefallen(angebotId);
  }
}
