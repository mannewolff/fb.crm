package org.mwolff.fbcrm.auth;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.Role;

/**
 * Baut das Konto fuer Tests.
 *
 * <p>Zweck: eine Stelle statt vierzig, wenn der Datensatz {@link Account} sich aendert. Jeder Test,
 * der ein Konto braucht, holt es hier; nur diese Datei ruft den Konstruktor. Der Helfer kennt keine
 * Uhr — die Zeitpunkte kommen als Parameter (CLAUDE-java.md §6.2).
 */
public final class Konten {

  private static final String ANZEIGENAME = "Manne";

  private Konten() {}

  /**
   * Ein frisches, noch nicht gespeichertes Konto mit Generation 0.
   *
   * @param email Anmeldeadresse
   * @param zeitpunkt Anlage und letzte Aenderung
   */
  public static Account neu(final String email, final Instant zeitpunkt) {
    return gespeichert(null, email, 0, zeitpunkt, zeitpunkt);
  }

  /**
   * Ein Konto mit Id und Generation, angelegt und zuletzt geaendert zum selben Zeitpunkt.
   *
   * @param id technische Id
   * @param email Anmeldeadresse
   * @param generation Sitzungs-Generation
   * @param zeitpunkt Anlage und letzte Aenderung
   */
  public static Account gespeichert(
      final @Nullable Long id, final String email, final int generation, final Instant zeitpunkt) {
    return gespeichert(id, email, generation, zeitpunkt, zeitpunkt);
  }

  /**
   * Ein Konto mit getrennten Zeitpunkten fuer Anlage und letzte Aenderung.
   *
   * @param id technische Id
   * @param email Anmeldeadresse
   * @param generation Sitzungs-Generation
   * @param angelegt Zeitpunkt der Anlage
   * @param geaendert Zeitpunkt der letzten Aenderung
   */
  public static Account gespeichert(
      final @Nullable Long id,
      final String email,
      final int generation,
      final Instant angelegt,
      final Instant geaendert) {
    return baue(id, email, ANZEIGENAME, Role.ADMIN, generation, angelegt, geaendert);
  }

  /**
   * Die Vorlage mit vergebener Id — so, wie ein Repository sie beim Speichern zurueckgibt.
   *
   * @param id die vergebene Id
   * @param vorlage das ungespeicherte Konto
   */
  public static Account mitId(final Long id, final Account vorlage) {
    return baue(
        id,
        vorlage.email(),
        vorlage.displayName(),
        vorlage.role(),
        vorlage.sessionGeneration(),
        vorlage.createdAt(),
        vorlage.updatedAt());
  }

  private static Account baue(
      final @Nullable Long id,
      final String email,
      final String displayName,
      final Role role,
      final int generation,
      final Instant angelegt,
      final Instant geaendert) {
    return new Account(id, email, displayName, role, generation, angelegt, geaendert);
  }
}
