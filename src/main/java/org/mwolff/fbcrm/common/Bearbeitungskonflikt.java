package org.mwolff.fbcrm.common;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Ein Schritt beruht auf einem Stand, den es so nicht mehr gibt: Jemand hat den Eintrag inzwischen
 * geaendert oder geloescht (Plan #415, E4, E16).
 *
 * <p>Abgebildet wird der Vertrag in {@code common.web.GlobalExceptionHandler}: Status 409 und die
 * Erweiterung {@code konflikt} mit Art, Zeitpunkt, Name des Urhebers und der Angabe, ob die fremde
 * Aenderung die eigene aus einem anderen Fenster war. Der Anwendungsfall legt nur die
 * Konto-Kennungen und den Zeitpunkt hinein; den Namen loest die Abbildung einmal ueber {@link
 * Urheberauskunft} auf (E6), und sie entscheidet auch ueber das eigene Fenster (E15).
 *
 * <p><b>Warum der Vertrag hier steht und nicht im Fachmodul.</b> Aus demselben Grund wie {@link
 * Feldfehler}: Kennte die Abbildung die Ausnahme des Fachmoduls, zeigte {@code common} auf das
 * Fachmodul und das Fachmodul auf {@code common} — ein Paketzyklus ({@code ArchitectureTest},
 * {@code modules_thenFreeOfCycles}).
 *
 * <p>Framework-frei und ohne Statuscode-Annotation: Jeder Bearbeitungskonflikt traegt 409, darum
 * setzt ihn die Abbildung selbst.
 */
public abstract class Bearbeitungskonflikt extends RuntimeException {

  /** Was mit dem Eintrag seit dem Oeffnen geschehen ist. */
  public enum Art {
    /** Ein anderer Stand wurde inzwischen gespeichert. */
    VERALTET("veraltet"),
    /** Der Eintrag wurde inzwischen geloescht. */
    GELOESCHT("gelöscht");

    private final String wort;

    Art(final String wort) {
      this.wort = wort;
    }

    /** Das Wort, an dem die Oberflaeche die Lage erkennt. */
    public String wert() {
      return wort;
    }
  }

  private final Art lage;
  private final @Nullable Long urheber;
  private final long handelnder;
  private final @Nullable Instant geschehenAm;

  /**
   * Ein Konflikt mit allem, was die Meldung nennen soll.
   *
   * @param art was mit dem Eintrag geschehen ist
   * @param urheberKonto wer zuletzt geaendert oder geloescht hat; {@code null}, wenn die Zeile das
   *     nicht weiss
   * @param handelndesKonto wer gerade abgewiesen wird
   * @param zeitpunkt wann zuletzt geaendert oder geloescht wurde; {@code null}, wenn die Zeile das
   *     nicht weiss
   */
  protected Bearbeitungskonflikt(
      final Art art,
      final @Nullable Long urheberKonto,
      final long handelndesKonto,
      final @Nullable Instant zeitpunkt) {
    super();
    this.lage = art;
    this.urheber = urheberKonto;
    this.handelnder = handelndesKonto;
    this.geschehenAm = zeitpunkt;
  }

  /**
   * Weist einen Schritt ab, dessen vorgelegter Stand nicht der aktuelle ist (E12).
   *
   * <p>Der eine Vergleich, den jeder Anwendungsfall in einer Zeile ruft. Abgewiesen wird jede
   * Abweichung, nicht nur ein aelterer Stand: Ein vorgelegter Stand, den es nie gab, ist ebenso
   * wenig der, den der Handelnde gesehen hat.
   *
   * @param vorgelegt die Version aus dem Kopf {@code If-Match}
   * @param aktuell die Version der Zeile
   * @param konflikt baut die Ausnahme des Fachmoduls, nur wenn sie gebraucht wird
   */
  public static void pruefe(
      final long vorgelegt,
      final long aktuell,
      final Supplier<? extends Bearbeitungskonflikt> konflikt) {
    if (vorgelegt != aktuell) {
      throw konflikt.get();
    }
  }

  /** Was mit dem Eintrag geschehen ist. */
  public Art art() {
    return lage;
  }

  /** Wer zuletzt geaendert oder geloescht hat, sofern die Zeile es weiss. */
  public OptionalLong urheberKonto() {
    return urheber == null ? OptionalLong.empty() : OptionalLong.of(urheber);
  }

  /** Wer gerade abgewiesen wird. */
  public long handelndesKonto() {
    return handelnder;
  }

  /** Wann zuletzt geaendert oder geloescht wurde, sofern die Zeile es weiss. */
  public Optional<Instant> zeitpunkt() {
    return Optional.ofNullable(geschehenAm);
  }
}
