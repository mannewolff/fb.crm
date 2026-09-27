package org.mwolff.fbcrm.vorgang.domain;

import java.time.Instant;
import java.time.LocalDate;
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
 * <p><b>Die Phase wird nicht gespeichert.</b> {@link #phase(boolean)} leitet sie ab (E4): Sie
 * haengt am Stand der Dokumente, und den kennt der Vorgang nicht selbst — er bekommt ihn ueber
 * {@link Belegstand} gesagt und nimmt ihn als Argument.
 *
 * <p>Unveraenderlich: Jeder der drei Uebergaenge liefert einen neuen Vorgang, statt diesen zu
 * aendern. Der Zeitpunkt kommt von aussen, weil die Domaene keine Uhr kennt (CLAUDE-java.md §6.2).
 *
 * @param id technische Id — {@code null}, solange der Vorgang nicht gespeichert ist
 * @param nummer fortlaufende Vorgangsnummer aus dem Nummernkreis, ab 1 und ohne Luecke (E3)
 * @param titel Titel des Vorgangs; die einzige Pflichtangabe neben der Firma
 * @param firmaId Kennung der Firma, zu der der Vorgang gehoert
 * @param ansprechpartnerId Kennung des Ansprechpartners, oder {@code null}
 * @param abschlusswahrscheinlichkeit Abschlusswahrscheinlichkeit in Zehnerschritten von 0 bis 100,
 *     oder {@code null} fuer „nicht eingeschaetzt" (Kriterium 21)
 * @param entscheidungErwartetAm Tag, an dem die Entscheidung des Kunden erwartet wird, oder {@code
 *     null} (Kriterium 21)
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
    @Nullable Integer abschlusswahrscheinlichkeit,
    @Nullable LocalDate entscheidungErwartetAm,
    boolean abgeschlossen,
    Instant createdAt,
    Instant updatedAt)
    implements Identifiable {

  /**
   * Die Phase des Vorgangs, abgeleitet statt gespeichert (E4).
   *
   * <p>Solange kein Angebot festgeschrieben ist, ist jeder Vorgang in der Anbahnung — auch ein
   * abgeschlossener: Der Abschluss ist ein eigener Schalter und keine Phase.
   *
   * @param angebotFestgeschrieben {@code true}, wenn an diesem Vorgang mindestens ein
   *     festgeschriebenes Angebot haengt. Der Wert kommt aus {@link Belegstand} und meint
   *     <b>nicht</b> „versendet": Ein angenommenes oder abgelehntes Angebot bleibt festgeschrieben,
   *     und die Phase faellt darum nicht zurueck (F6).
   */
  public Phase phase(final boolean angebotFestgeschrieben) {
    return angebotFestgeschrieben ? Phase.ANGEBOT : Phase.ANBAHNUNG;
  }

  /**
   * Der Vorgang mit neuen Angaben; Kennung, Nummer, Abschlussstand und Anlagezeitpunkt bleiben.
   *
   * @param titel neuer Titel
   * @param firmaId Kennung der nun zugeordneten Firma
   * @param ansprechpartnerId Kennung des nun zugeordneten Ansprechpartners, oder {@code null}
   * @param abschlusswahrscheinlichkeit neue Abschlusswahrscheinlichkeit, oder {@code null}
   * @param entscheidungErwartetAm neuer erwarteter Entscheidungszeitpunkt, oder {@code null}
   * @param geaendertAm Zeitpunkt der Aenderung
   */
  public Vorgang geaendert(
      final String titel,
      final long firmaId,
      final @Nullable Long ansprechpartnerId,
      final @Nullable Integer abschlusswahrscheinlichkeit,
      final @Nullable LocalDate entscheidungErwartetAm,
      final Instant geaendertAm) {
    return new Vorgang(
        id,
        nummer,
        titel,
        firmaId,
        ansprechpartnerId,
        abschlusswahrscheinlichkeit,
        entscheidungErwartetAm,
        abgeschlossen,
        createdAt,
        geaendertAm);
  }

  /**
   * Der Vorgang als abgeschlossen.
   *
   * @param zeitpunkt Zeitpunkt des Abschlusses
   */
  public Vorgang abgeschlossen(final Instant zeitpunkt) {
    return mitAbschluss(true, zeitpunkt);
  }

  /**
   * Der Vorgang als wieder offen.
   *
   * @param zeitpunkt Zeitpunkt der Wiedereroeffnung
   */
  public Vorgang wiederEroeffnet(final Instant zeitpunkt) {
    return mitAbschluss(false, zeitpunkt);
  }

  /*
   * Beide Uebergaenge unterscheiden sich allein im Schalter; zweimal geschrieben waere jede neue
   * Fachspalte an zwei Stellen nachzutragen, und eine vergessene faellt dabei still heraus.
   */
  private Vorgang mitAbschluss(final boolean neuerStand, final Instant zeitpunkt) {
    return new Vorgang(
        id,
        nummer,
        titel,
        firmaId,
        ansprechpartnerId,
        abschlusswahrscheinlichkeit,
        entscheidungErwartetAm,
        neuerStand,
        createdAt,
        zeitpunkt);
  }
}
