package org.mwolff.fbcrm.auftrag.web;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Was {@link LeistungszeitraumConstraint} an einer Anfrage liest: die beiden Tage.
 *
 * <p>Die Regel gilt beim Anlegen wie beim Pflegen, und sie gilt Wort fuer Wort dieselbe (E21). Ohne
 * diese gemeinsame Sicht braeuchte jede Anfrage ihren eigenen Validator — zwei Abschriften
 * derselben Regel, die beim ersten Nachziehen auseinanderlaufen.
 *
 * <p>Paketprivat: Die Schnittstelle ist ein Mittel dieser Constraint und keine Aussage nach aussen.
 */
interface Leistungszeitraum {

  /** Erster Tag des Leistungszeitraums, oder {@code null}. */
  @Nullable LocalDate leistungAb();

  /** Letzter Tag des Leistungszeitraums, oder {@code null}. */
  @Nullable LocalDate leistungBis();
}
