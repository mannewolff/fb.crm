package org.mwolff.fbcrm.vorgang.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Identifiable;

/**
 * Ein Vorgang — die Klammer einer Geschaeftschance von der Anfrage bis zum Zahlungseingang.
 *
 * <p><b>Abgeschlossen statt geloescht.</b> Ein Vorgang, aus dem nichts wird, bleibt mit seiner
 * Historie stehen und wird nur abgeschlossen; {@link #abgeschlossen(Instant)} und {@link
 * #wiederEroeffnet} legen den Schalter um (E5) — dasselbe Muster wie {@code aktiv} an Firma und
 * Ansprechpartner.
 *
 * <p><b>Die Phase wird nicht gespeichert.</b> {@link #phase()} leitet sie ab: Solange keine
 * Dokumente am Vorgang haengen, ist sie {@link Phase#ANBAHNUNG} (E4).
 *
 * <p>Unveraenderlich: Jeder der drei Uebergaenge liefert einen neuen Vorgang, statt diesen zu
 * aendern. Der Zeitpunkt kommt von aussen, weil die Domaene keine Uhr kennt (CLAUDE-java.md §6.2).
 *
 * @param id technische Id — {@code null}, solange der Vorgang nicht gespeichert ist
 * @param nummer fortlaufende Vorgangsnummer aus dem Nummernkreis, ab 1 und ohne Luecke (E3)
 * @param titel Titel des Vorgangs; die einzige Pflichtangabe neben der Firma
 * @param firmaId Kennung der Firma, zu der der Vorgang gehoert
 * @param ansprechpartnerId Kennung des Ansprechpartners, oder {@code null}
 * @param abgeschlossen {@code true}, solange der Vorgang abgeschlossen ist
 * @param createdAt Zeitpunkt der Anlage
 * @param updatedAt Zeitpunkt der letzten Aenderung
 */
public record Vorgang(
    @Nullable Long id,
    long nummer,
    String titel,
    long firmaId,
    @Nullable Long ansprechpartnerId,
    boolean abgeschlossen,
    Instant createdAt,
    Instant updatedAt)
    implements Identifiable {

  /**
   * Die Phase des Vorgangs, abgeleitet statt gespeichert (E4).
   *
   * <p>Solange es keine Dokumente gibt, ist jeder Vorgang in der Anbahnung — auch ein
   * abgeschlossener: Der Abschluss ist ein eigener Schalter und keine Phase.
   */
  public Phase phase() {
    return Phase.ANBAHNUNG;
  }

  /**
   * Der Vorgang mit neuen Angaben; Kennung, Nummer, Abschlussstand und Anlagezeitpunkt bleiben.
   *
   * @param titel neuer Titel
   * @param firmaId Kennung der nun zugeordneten Firma
   * @param ansprechpartnerId Kennung des nun zugeordneten Ansprechpartners, oder {@code null}
   * @param geaendertAm Zeitpunkt der Aenderung
   */
  public Vorgang geaendert(
      final String titel,
      final long firmaId,
      final @Nullable Long ansprechpartnerId,
      final Instant geaendertAm) {
    return new Vorgang(
        id, nummer, titel, firmaId, ansprechpartnerId, abgeschlossen, createdAt, geaendertAm);
  }

  /**
   * Der Vorgang als abgeschlossen.
   *
   * @param zeitpunkt Zeitpunkt des Abschlusses
   */
  public Vorgang abgeschlossen(final Instant zeitpunkt) {
    return new Vorgang(id, nummer, titel, firmaId, ansprechpartnerId, true, createdAt, zeitpunkt);
  }

  /**
   * Der Vorgang als wieder offen.
   *
   * @param zeitpunkt Zeitpunkt der Wiedereroeffnung
   */
  public Vorgang wiederEroeffnet(final Instant zeitpunkt) {
    return new Vorgang(id, nummer, titel, firmaId, ansprechpartnerId, false, createdAt, zeitpunkt);
  }
}
