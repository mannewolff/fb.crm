package org.mwolff.fbcrm.firma.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Identifiable;

/**
 * Eine Firma im Stammdatenbestand.
 *
 * <p><b>Stillgelegt statt geloescht.</b> Eine Firma, mit der nicht mehr gearbeitet wird, haengt an
 * Angeboten und Rechnungen, die bleiben — deshalb kennt der Bestand kein Loeschen, sondern nur den
 * Schalter {@link #aktiv}. {@link #stillgelegt} und {@link #aktiviert} legen ihn um (E3).
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
 * @param version der Stand der Firma, wie ihn {@code If-Match} vorlegt (Plan #415, E1); 0 vor der
 *     ersten Aenderung, danach fuehrt ihn der Bestand
 * @param geaendertVon Kennung des Kontos, das die Firma zuletzt geaendert hat, oder {@code null},
 *     solange sie das nicht weiss (Plan #415, E14)
 */
public record Firma(
    @Nullable Long id,
    String name,
    Anschrift anschrift,
    @Nullable String steuernummer,
    @Nullable String umsatzsteuerId,
    boolean aktiv,
    Instant createdAt,
    Instant updatedAt,
    long version,
    @Nullable Long geaendertVon)
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
        id,
        name,
        anschrift,
        steuernummer,
        umsatzsteuerId,
        aktiv,
        createdAt,
        geaendertAm,
        version,
        geaendertVon);
  }

  /**
   * Die Firma als stillgelegt.
   *
   * @param zeitpunkt Zeitpunkt der Stilllegung
   */
  public Firma stillgelegt(final Instant zeitpunkt) {
    return new Firma(
        id,
        name,
        anschrift,
        steuernummer,
        umsatzsteuerId,
        false,
        createdAt,
        zeitpunkt,
        version,
        geaendertVon);
  }

  /**
   * Die Firma als wieder aktiv.
   *
   * @param zeitpunkt Zeitpunkt der Wiederaktivierung
   */
  public Firma aktiviert(final Instant zeitpunkt) {
    return new Firma(
        id,
        name,
        anschrift,
        steuernummer,
        umsatzsteuerId,
        true,
        createdAt,
        zeitpunkt,
        version,
        geaendertVon);
  }

  /**
   * Die Firma mit dem Konto, das sie gerade geaendert hat (Plan #415, E14).
   *
   * <p>Ein eigener Schritt neben den Uebergaengen: Wer aendert, ist eine Angabe des
   * Anwendungsfalls, nicht der fachlichen Regel. Die Version bleibt, wie sie ist — sie zaehlt der
   * Bestand beim Schreiben weiter.
   *
   * @param konto Kennung des handelnden Kontos
   */
  public Firma durch(final long konto) {
    return new Firma(
        id,
        name,
        anschrift,
        steuernummer,
        umsatzsteuerId,
        aktiv,
        createdAt,
        updatedAt,
        version,
        konto);
  }
}
