package org.mwolff.fbcrm.firma.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Identifiable;

/**
 * Ein Ansprechpartner einer Firma.
 *
 * <p>Die Firma steht als {@link #firmaId} fest und wird von keinem der Uebergaenge angefasst: Es
 * soll keinen Weg geben, einen Ansprechpartner umzuhaengen. Sein Stilllegungsstand gehoert ihm
 * allein — das Stilllegen der Firma laesst ihn unberuehrt (E2).
 *
 * <p>Unveraenderlich: Jeder der drei Uebergaenge liefert einen neuen Ansprechpartner, statt diesen
 * zu aendern. Der Zeitpunkt kommt von aussen (CLAUDE-java.md §6.2).
 *
 * @param id technische Id — {@code null}, solange der Ansprechpartner nicht gespeichert ist
 * @param firmaId Kennung der Firma, zu der er gehoert
 * @param vorname Vorname, oder {@code null}
 * @param nachname Nachname; die einzige Pflichtangabe
 * @param rolle Rolle oder Funktion in der Firma, oder {@code null}
 * @param email E-Mail-Adresse, oder {@code null}
 * @param telefonFestnetz Festnetznummer, oder {@code null}
 * @param telefonMobil Mobilnummer, oder {@code null}
 * @param aktiv {@code false}, solange der Ansprechpartner stillgelegt ist
 * @param createdAt Zeitpunkt der Anlage
 * @param updatedAt Zeitpunkt der letzten Aenderung
 */
public record Ansprechpartner(
    @Nullable Long id,
    long firmaId,
    @Nullable String vorname,
    String nachname,
    @Nullable String rolle,
    @Nullable String email,
    @Nullable String telefonFestnetz,
    @Nullable String telefonMobil,
    boolean aktiv,
    Instant createdAt,
    Instant updatedAt)
    implements Identifiable {

  /**
   * Der Ansprechpartner mit neuen Angaben; Kennung, Firma, Stilllegungsstand und Anlagezeitpunkt
   * bleiben.
   *
   * @param vorname neuer Vorname, oder {@code null}
   * @param nachname neuer Nachname
   * @param rolle neue Rolle, oder {@code null}
   * @param email neue E-Mail-Adresse, oder {@code null}
   * @param telefonFestnetz neue Festnetznummer, oder {@code null}
   * @param telefonMobil neue Mobilnummer, oder {@code null}
   * @param geaendertAm Zeitpunkt der Aenderung
   */
  public Ansprechpartner geaendert(
      final @Nullable String vorname,
      final String nachname,
      final @Nullable String rolle,
      final @Nullable String email,
      final @Nullable String telefonFestnetz,
      final @Nullable String telefonMobil,
      final Instant geaendertAm) {
    return new Ansprechpartner(
        id,
        firmaId,
        vorname,
        nachname,
        rolle,
        email,
        telefonFestnetz,
        telefonMobil,
        aktiv,
        createdAt,
        geaendertAm);
  }

  /**
   * Der Ansprechpartner als stillgelegt.
   *
   * @param zeitpunkt Zeitpunkt der Stilllegung
   */
  public Ansprechpartner stillgelegt(final Instant zeitpunkt) {
    return new Ansprechpartner(
        id,
        firmaId,
        vorname,
        nachname,
        rolle,
        email,
        telefonFestnetz,
        telefonMobil,
        false,
        createdAt,
        zeitpunkt);
  }

  /**
   * Der Ansprechpartner als wieder aktiv.
   *
   * @param zeitpunkt Zeitpunkt der Wiederaktivierung
   */
  public Ansprechpartner aktiviert(final Instant zeitpunkt) {
    return new Ansprechpartner(
        id,
        firmaId,
        vorname,
        nachname,
        rolle,
        email,
        telefonFestnetz,
        telefonMobil,
        true,
        createdAt,
        zeitpunkt);
  }
}
