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
 *
 * <p>Ohne Angabe ist ein Konto C-Level und aktiv, wie das erste Konto der Einrichtung; {@link #mit}
 * nimmt Rolle und Zustand als Parameter (Plan #414, E12).
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
    return baue(id, email, ANZEIGENAME, Role.C_LEVEL, true, generation, angelegt, geaendert);
  }

  /**
   * Ein Konto mit Rolle und Zustand, angelegt und zuletzt geaendert zum selben Zeitpunkt.
   *
   * @param id technische Id
   * @param email Anmeldeadresse
   * @param role Rolle des Kontos
   * @param aktiv ob das Konto aktiv ist; {@code false} heisst stillgelegt
   * @param generation Sitzungs-Generation
   * @param zeitpunkt Anlage und letzte Aenderung
   */
  public static Account mit(
      final @Nullable Long id,
      final String email,
      final Role role,
      final boolean aktiv,
      final int generation,
      final Instant zeitpunkt) {
    return baue(id, email, ANZEIGENAME, role, aktiv, generation, zeitpunkt, zeitpunkt);
  }

  /**
   * Die Vorlage mit vergebener Id — so, wie ein Repository sie beim Speichern zurueckgibt.
   *
   * @param id die vergebene Id
   * @param vorlage das ungespeicherte Konto
   */
  public static Account mitId(final Long id, final Account vorlage) {
    return imStand(
        id,
        vorlage.email(),
        vorlage.displayName(),
        vorlage,
        vorlage.version(),
        vorlage.geaendertVon());
  }

  /**
   * Die Vorlage unter einem anderen Anzeigenamen, ohne Version und Urheber zu beruehren.
   *
   * <p>Fuer Tests, die nur einen Namen brauchen: {@link Account#mitNamen} ist ein Schritt der
   * Kontenverwaltung, zaehlt die Version und setzt einen Urheber — der in einem Integrationstest
   * als Fremdschluessel auf ein Konto zeigte, das es dort nicht gibt.
   *
   * @param vorlage das Konto
   * @param name der Anzeigename
   */
  public static Account benannt(final Account vorlage, final String name) {
    return imStand(
        vorlage.id(), vorlage.email(), name, vorlage, vorlage.version(), vorlage.geaendertVon());
  }

  /**
   * Die Vorlage mit einem Stand der Kontenverwaltung (Plan #415, E19).
   *
   * @param vorlage das Konto
   * @param version die Version, die das Konto tragen soll
   * @param geaendertVon wer es zuletzt verwaltet hat, oder {@code null}
   */
  public static Account imStand(
      final Account vorlage, final long version, final @Nullable Long geaendertVon) {
    return imStand(
        vorlage.id(), vorlage.email(), vorlage.displayName(), vorlage, version, geaendertVon);
  }

  private static Account imStand(
      final @Nullable Long id,
      final String email,
      final String displayName,
      final Account vorlage,
      final long version,
      final @Nullable Long geaendertVon) {
    return new Account(
        id,
        email,
        displayName,
        vorlage.role(),
        vorlage.aktiv(),
        vorlage.sessionGeneration(),
        vorlage.createdAt(),
        vorlage.updatedAt(),
        version,
        geaendertVon);
  }

  private static Account baue(
      final @Nullable Long id,
      final String email,
      final String displayName,
      final Role role,
      final boolean aktiv,
      final int generation,
      final Instant angelegt,
      final Instant geaendert) {
    return new Account(
        id, email, displayName, role, aktiv, generation, angelegt, geaendert, 0L, null);
  }
}
