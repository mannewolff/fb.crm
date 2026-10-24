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
 * @param version der Stand des Ansprechpartners, wie ihn {@code If-Match} vorlegt (Plan #415, E1);
 *     0 vor der ersten Aenderung, danach fuehrt ihn der Bestand
 * @param geaendertVon Kennung des Kontos, das ihn zuletzt geaendert hat, oder {@code null}, solange
 *     er das nicht weiss (Plan #415, E14)
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
    Instant updatedAt,
    long version,
    @Nullable Long geaendertVon)
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
        geaendertAm,
        version,
        geaendertVon);
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
        zeitpunkt,
        version,
        geaendertVon);
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
        zeitpunkt,
        version,
        geaendertVon);
  }

  /**
   * Der Ansprechpartner mit dem Konto, das ihn gerade geaendert hat (Plan #415, E14).
   *
   * <p>Wie an der Firma: Wer aendert, ist eine Angabe des Anwendungsfalls, nicht der fachlichen
   * Regel. Die Version bleibt, wie sie ist — sie zaehlt der Bestand beim Schreiben weiter.
   *
   * @param konto Kennung des handelnden Kontos
   */
  public Ansprechpartner durch(final long konto) {
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
        updatedAt,
        version,
        konto);
  }
}
