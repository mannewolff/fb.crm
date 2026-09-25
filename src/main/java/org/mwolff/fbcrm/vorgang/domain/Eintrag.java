package org.mwolff.fbcrm.vorgang.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Identifiable;

/**
 * Ein Eintrag in der Historie eines Vorgangs — ein Kommentar oder ein Anhang.
 *
 * <p>Beide Arten liegen in einem Record und in einer Tabelle, damit die Historie als <b>eine</b>
 * nach {@link #geschehenAm} sortierte Folge lesbar bleibt (E6). Was eine Art verlangt und was sie
 * verbietet, halten die Fabriken {@link #kommentar} und {@link #anhang} an einer Stelle fest; die
 * Checks der Migration sagen dasselbe noch einmal in der Datenbank.
 *
 * <p>{@link #geschehenAm} ist der Zeitpunkt des Geschehens und nicht der der Erfassung: Ein
 * Telefonat von gestern wird heute mit dem gestrigen Zeitpunkt eingetragen. {@link #createdAt}
 * haelt daneben fest, wann der Eintrag entstand.
 *
 * @param id technische Id — {@code null}, solange der Eintrag nicht gespeichert ist
 * @param vorgangId Kennung des Vorgangs, zu dem der Eintrag gehoert
 * @param art Kommentar oder Anhang
 * @param text der Text; bei einem Kommentar Pflicht, bei einem Anhang die Beschreibung oder {@code
 *     null}
 * @param geschehenAm Zeitpunkt des Geschehens
 * @param herkunft woher der Eintrag stammt
 * @param dateiName urspruenglicher, gesaeuberter Dateiname — nur beim Anhang gesetzt (E13)
 * @param dateiGroesse Groesse in Byte — nur beim Anhang gesetzt
 * @param objektSchluessel Schluessel im Objektspeicher {@code vorgang/<vorgangId>/<uuid>} — nur
 *     beim Anhang gesetzt (E9)
 * @param createdAt Zeitpunkt der Erfassung
 * @param geaendertAm Zeitpunkt der letzten Aenderung, oder {@code null}, solange keine stattfand
 */
public record Eintrag(
    @Nullable Long id,
    long vorgangId,
    Eintragsart art,
    @Nullable String text,
    Instant geschehenAm,
    Herkunft herkunft,
    @Nullable String dateiName,
    @Nullable Long dateiGroesse,
    @Nullable String objektSchluessel,
    Instant createdAt,
    @Nullable Instant geaendertAm)
    implements Identifiable {

  /**
   * Ein neuer Kommentar: Text ist Pflicht, Dateiangaben gibt es nicht.
   *
   * @param vorgangId Kennung des Vorgangs
   * @param text der Text; darf nicht leer sein
   * @param geschehenAm Zeitpunkt des Geschehens
   * @param herkunft woher der Eintrag stammt
   * @param angelegtAm Zeitpunkt der Erfassung
   * @throws IllegalArgumentException wenn der Text leer ist
   */
  public static Eintrag kommentar(
      final long vorgangId,
      final String text,
      final Instant geschehenAm,
      final Herkunft herkunft,
      final Instant angelegtAm) {
    return new Eintrag(
        null,
        vorgangId,
        Eintragsart.KOMMENTAR,
        pflichttext(text),
        geschehenAm,
        herkunft,
        null,
        null,
        null,
        angelegtAm,
        null);
  }

  /**
   * Ein neuer Anhang: alle drei Dateiangaben sind Pflicht, der Text ist die Beschreibung.
   *
   * @param vorgangId Kennung des Vorgangs
   * @param text beschreibender Text, oder {@code null}
   * @param geschehenAm Zeitpunkt des Geschehens
   * @param herkunft woher der Eintrag stammt
   * @param dateiName gesaeuberter Dateiname; darf nicht leer sein
   * @param dateiGroesse Groesse in Byte
   * @param objektSchluessel Schluessel im Objektspeicher; darf nicht leer sein
   * @param angelegtAm Zeitpunkt der Erfassung
   * @throws IllegalArgumentException wenn Dateiname oder Objektschluessel leer sind
   */
  public static Eintrag anhang(
      final long vorgangId,
      final @Nullable String text,
      final Instant geschehenAm,
      final Herkunft herkunft,
      final String dateiName,
      final long dateiGroesse,
      final String objektSchluessel,
      final Instant angelegtAm) {
    return new Eintrag(
        null,
        vorgangId,
        Eintragsart.ANHANG,
        text,
        geschehenAm,
        herkunft,
        nichtLeer(dateiName, "Ein Anhang braucht einen Dateinamen."),
        dateiGroesse,
        nichtLeer(objektSchluessel, "Ein Anhang braucht einen Objektschluessel."),
        angelegtAm,
        null);
  }

  /**
   * Der Eintrag mit neuem Text und neuem Zeitpunkt des Geschehens; Art, Herkunft und Dateiangaben
   * bleiben unberuehrt.
   *
   * @param text neuer Text; bei einem Kommentar Pflicht
   * @param geschehenAm neuer Zeitpunkt des Geschehens
   * @param zeitpunkt Zeitpunkt der Aenderung
   * @throws IllegalArgumentException wenn der Eintrag ein Kommentar ist und der Text leer ist
   */
  public Eintrag geaendert(
      final @Nullable String text, final Instant geschehenAm, final Instant zeitpunkt) {
    if (art == Eintragsart.KOMMENTAR) {
      pflichttext(text);
    }
    return new Eintrag(
        id,
        vorgangId,
        art,
        text,
        geschehenAm,
        herkunft,
        dateiName,
        dateiGroesse,
        objektSchluessel,
        createdAt,
        zeitpunkt);
  }

  private static String pflichttext(final @Nullable String text) {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("Ein Kommentar braucht einen Text.");
    }
    return text;
  }

  private static String nichtLeer(final String wert, final String meldung) {
    if (wert.isBlank()) {
      throw new IllegalArgumentException(meldung);
    }
    return wert;
  }
}
