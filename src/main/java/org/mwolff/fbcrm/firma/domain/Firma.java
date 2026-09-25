package org.mwolff.fbcrm.firma.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Identifiable;

/**
 * Eine Firma im Stammdatenbestand.
 *
 * <p><b>Stillgelegt statt geloescht.</b> Eine Firma, mit der nicht mehr gearbeitet wird, haengt an
 * Vorgaengen, Angeboten und Rechnungen, die bleiben — deshalb kennt der Bestand kein Loeschen,
 * sondern nur den Schalter {@link #aktiv}. {@link #stillgelegt} und {@link #aktiviert} legen ihn um
 * (E3).
 *
 * <p>Unveraenderlich: Jeder der drei Uebergaenge liefert eine neue Firma, statt diese zu aendern.
 * Der Zeitpunkt kommt von aussen, weil die Domaene keine Uhr kennt (CLAUDE-java.md §6.2).
 *
 * @param id technische Id — {@code null}, solange die Firma nicht gespeichert ist
 * @param name Name der Firma; nicht eindeutig, zwei Firmen duerfen gleich heissen
 * @param anschrift Postanschrift; jede ihrer Angaben darf fehlen
 * @param steuernummer Steuernummer, oder {@code null}
 * @param umsatzsteuerId Umsatzsteuer-Identifikationsnummer, oder {@code null}
 * @param aktiv {@code false}, solange die Firma stillgelegt ist
 * @param createdAt Zeitpunkt der Anlage
 * @param updatedAt Zeitpunkt der letzten Aenderung
 */
public record Firma(
    @Nullable Long id,
    String name,
    Anschrift anschrift,
    @Nullable String steuernummer,
    @Nullable String umsatzsteuerId,
    boolean aktiv,
    Instant createdAt,
    Instant updatedAt)
    implements Identifiable {

  /**
   * Die Firma mit neuen Angaben; Kennung, Stilllegungsstand und Anlagezeitpunkt bleiben.
   *
   * @param name neuer Name
   * @param anschrift neue Anschrift
   * @param steuernummer neue Steuernummer, oder {@code null}
   * @param umsatzsteuerId neue Umsatzsteuer-Identifikationsnummer, oder {@code null}
   * @param geaendertAm Zeitpunkt der Aenderung
   */
  public Firma geaendert(
      final String name,
      final Anschrift anschrift,
      final @Nullable String steuernummer,
      final @Nullable String umsatzsteuerId,
      final Instant geaendertAm) {
    return new Firma(
        id, name, anschrift, steuernummer, umsatzsteuerId, aktiv, createdAt, geaendertAm);
  }

  /**
   * Die Firma als stillgelegt.
   *
   * @param zeitpunkt Zeitpunkt der Stilllegung
   */
  public Firma stillgelegt(final Instant zeitpunkt) {
    return new Firma(
        id, name, anschrift, steuernummer, umsatzsteuerId, false, createdAt, zeitpunkt);
  }

  /**
   * Die Firma als wieder aktiv.
   *
   * @param zeitpunkt Zeitpunkt der Wiederaktivierung
   */
  public Firma aktiviert(final Instant zeitpunkt) {
    return new Firma(id, name, anschrift, steuernummer, umsatzsteuerId, true, createdAt, zeitpunkt);
  }
}
