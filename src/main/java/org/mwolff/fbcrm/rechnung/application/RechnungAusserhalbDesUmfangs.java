package org.mwolff.fbcrm.rechnung.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die Rechnung liegt ausserhalb dessen, was fb.crm als E-Rechnung stellt (#460, Kriterium 13; Plan
 * #461, E18).
 *
 * <p>Abgedeckt ist nur das Inland mit einem Steuersatz ueber 0 %. Reverse Charge, Ausland und
 * steuerfreie Leistungen bleiben eigenen Ideen vorbehalten. Ein gewoehnliches Dokument als Ersatz
 * entsteht nicht: Die Rechnung bleibt ein Entwurf, und die Meldung nennt den Grund.
 *
 * <p>422 wie {@link RechnungOhnePosition}: Die Rechnung ist im richtigen Zustand, nur ihr Inhalt
 * traegt den Schritt nicht. Die Meldung ist das {@code detail} der Antwort.
 */
@ResponseStatus(code = HttpStatus.UNPROCESSABLE_ENTITY)
public final class RechnungAusserhalbDesUmfangs extends RuntimeException {

  /** Der Satz vor dem Grund; zusammen sind sie das {@code detail} der Antwort. */
  public static final String MELDUNG = "Diese Rechnung kann fb.crm nicht als E-Rechnung stellen: ";

  /** Der Grund, wenn der Kunde nicht in Deutschland sitzt. */
  public static final String AUSLAND = "Der Kunde sitzt nicht in Deutschland.";

  /** Der Grund, wenn der Steuersatz 0 % betraegt. */
  public static final String OHNE_STEUER = "Der Steuersatz beträgt 0 %.";

  /**
   * Die Abweisung mit ihrem Grund.
   *
   * @param grund einer der Saetze {@link #AUSLAND} oder {@link #OHNE_STEUER}
   */
  RechnungAusserhalbDesUmfangs(final String grund) {
    super(MELDUNG + grund);
  }
}
