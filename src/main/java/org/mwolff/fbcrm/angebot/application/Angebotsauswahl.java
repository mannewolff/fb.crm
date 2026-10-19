package org.mwolff.fbcrm.angebot.application;

import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;

/**
 * Welche Angebote die Uebersicht zeigt (Issue #335, Plan #332 E6 und E7).
 *
 * <p>Zwei Merkmale, die zusammen wirken: der Status und das Storno, jedes offen ({@code null}) oder
 * festgelegt. Gebaut wird die Auswahl nur ueber ihre drei Bauweisen, und die sind die Werte des
 * einen Waehlers der Oberflaeche — eine Partition (E7): {@link #imStatus} trifft kein storniertes
 * Angebot, {@link #stornierte} jedes stornierte gleich in welchem Status, {@link #alle} beide.
 *
 * <p><b>{@code imStatus} legt das Storno auf {@code false} fest</b> (E7): Ein storniertes Angebot
 * behaelt den Status, aus dem storniert wurde. Liesse {@code imStatus} das Storno offen, stuende es
 * unter „Abgegeben" und unter „Storniert" zugleich, und der Waehler waere nicht mehr lesbar.
 *
 * <p><b>Eine vierte Bauweise gibt es nicht</b> (E6): „ein Status mit gleichgueltigem Storno" fragt
 * niemand, und ein Fall ohne Aufrufer waere nicht pruefbar.
 *
 * @param status der gesuchte Status, oder {@code null} fuer jeden
 * @param storniert das gesuchte Storno, oder {@code null} fuer stornierte und nicht stornierte
 */
public record Angebotsauswahl(@Nullable Angebotsstatus status, @Nullable Boolean storniert) {

  /** Jedes Angebot, storniert oder nicht. */
  public static Angebotsauswahl alle() {
    return new Angebotsauswahl(null, null);
  }

  /**
   * Die nicht stornierten Angebote in einem Status.
   *
   * @param status der gesuchte Status
   */
  public static Angebotsauswahl imStatus(final Angebotsstatus status) {
    return new Angebotsauswahl(status, Boolean.FALSE);
  }

  /** Jedes stornierte Angebot, gleich in welchem Status. */
  public static Angebotsauswahl stornierte() {
    return new Angebotsauswahl(null, Boolean.TRUE);
  }

  /**
   * Ob das Angebot zur Auswahl gehoert.
   *
   * @param angebot das gepruefte Angebot
   */
  public boolean passt(final Angebot angebot) {
    return (status == null || status == angebot.status())
        && (storniert == null || storniert == angebot.storniert());
  }
}
