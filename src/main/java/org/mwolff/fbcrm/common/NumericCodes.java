package org.mwolff.fbcrm.common;

import java.security.SecureRandom;

/**
 * Der Einmalcode der Anmeldung: sechs Dezimalstellen, die ein Mensch aus der Mail abtippt.
 *
 * <p>Sechs Stellen legt die fachliche Quelle fest (Issue #392, Kriterium 2). Gezogen wird einmal
 * gleichverteilt aus {@link SecureRandom} ueber 000000–999999 — {@link SecureRandom#nextInt(int)}
 * mit der Schranke 10^6 kennt keinen Restklassen-Bias — und mit fuehrenden Nullen ausgegeben.
 *
 * <p>Die Zufallsquelle steckt im Konstruktor, damit Tests sie ersetzen koennen.
 */
public final class NumericCodes {

  private static final int CODE_RANGE = 1_000_000;

  private final SecureRandom random;

  /** Nutzt die Zufallsquelle der Plattform. */
  public NumericCodes() {
    this(new SecureRandom());
  }

  public NumericCodes(final SecureRandom random) {
    this.random = random;
  }

  /** Ein frischer, sechsstelliger Code mit fuehrenden Nullen. */
  public String newCode() {
    return String.format("%06d", random.nextInt(CODE_RANGE));
  }
}
