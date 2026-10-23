package org.mwolff.fbcrm.arbeitszeit.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import org.mwolff.fbcrm.arbeitszeit.application.Arbeitsmonat;
import org.mwolff.fbcrm.arbeitszeit.application.Arbeitstag;
import org.mwolff.fbcrm.arbeitszeit.application.Zeitbuchung;
import org.mwolff.fbcrm.arbeitszeit.domain.Zeiteintrag;

/**
 * Die Monatsliste, wie die Ansicht „Arbeitszeit" sie zeigt (Issue #193, Kriterium 5; Plan #194,
 * A20).
 *
 * <p>Die Felder sind die aus A20 und vollstaendig: Je Zeile Kennung, Tag, Uhrzeit, gerechnete
 * Dauer, die Position mit Angebot und Firmenname und die Person mit ihrem aktuellen Namen; je Tag
 * die Summe, dazu die des Monats. Die Oberflaeche rechnet und fragt damit nichts nach.
 *
 * <p><b>Die Person steht an jeder Zeile</b>, gleich welche Auswahl gilt (Plan #426, E14): Eine
 * Antwort, deren Felder von der Auswahl abhaengen, zwaenge den Parser zu einer Fallunterscheidung.
 * Wer eine Zeile ueberhaupt bekommt, darf ihren Namen sehen — ein Employee bekommt nur seine.
 *
 * <p><b>Die geltende Auswahl steht am Monat</b> (E10), in der Form des Parameters {@code person}:
 * die Kennung eines Kontos als Text oder {@code alle}. Wer ohne Person fragt, bekommt die
 * Vorauswahl seiner Rolle, und ohne diese Angabe wuesste die Ansicht nicht, welche das ist.
 *
 * <p><b>Der Monat steht in der Antwort</b>, weil der Aufrufer ihn weglassen darf und dann den
 * laufenden bekommt (E4) — ohne diese Angabe muesste die Ansicht die Geschaeftszone selbst
 * nachrechnen.
 *
 * <p><b>Die Monatssumme kommt aufgeteilt mit</b> (Issue #230, Kriterium 10 von #207): {@code
 * stunden} ist die Summe der zwei Teile, und die Ansicht zeigt sie darunter. Die Tage teilen nicht
 * auf.
 *
 * <p><b>Die Zeile traegt ihren Tag ein zweites Mal</b>, obwohl sie unter ihm steht. Das ist
 * Absicht: Der Dialog zum Aendern bekommt die Zeile allein uebergeben und braucht den Tag als
 * Vorbelegung.
 *
 * @param monat der Monat, den diese Liste zeigt
 * @param auswahl wessen Zeiten diese Liste zeigt: die Kennung eines Kontos als Text oder {@code
 *     alle}
 * @param tage die Tage mit Eintraegen, aufsteigend; ein Tag ohne Eintrag fehlt
 * @param stunden die Summe des Monats fuer die Eintraege der Auswahl
 * @param stundenFuerKunden der Teil davon, der auf Angebote an Kunden gebucht ist
 * @param stundenIntern der Teil davon, der auf interne Angebote gebucht ist
 */
public record ArbeitszeitMonatResponse(
    YearMonth monat,
    String auswahl,
    List<Tageszeile> tage,
    BigDecimal stunden,
    BigDecimal stundenFuerKunden,
    BigDecimal stundenIntern) {

  /** Derselbe Monat in der Sprache der Schnittstelle. */
  static ArbeitszeitMonatResponse of(final Arbeitsmonat gelesen) {
    return new ArbeitszeitMonatResponse(
        gelesen.monat(),
        gelesen.auswahl().alsParameter(),
        gelesen.tage().stream().map(Tageszeile::of).toList(),
        gelesen.stunden(),
        gelesen.stundenFuerKunden(),
        gelesen.stundenIntern());
  }

  /**
   * Ein Tag mit seinen Zeilen und seiner Summe.
   *
   * @param tag der Tag, an dem gearbeitet wurde
   * @param eintraege die Zeilen dieses Tages, nach Beginn aufsteigend
   * @param stunden die Summe des Tages
   */
  public record Tageszeile(LocalDate tag, List<Zeitzeile> eintraege, BigDecimal stunden) {

    static Tageszeile of(final Arbeitstag arbeitstag) {
      return new Tageszeile(
          arbeitstag.tag(),
          arbeitstag.buchungen().stream().map(Zeitzeile::of).toList(),
          arbeitstag.stunden());
    }
  }

  /**
   * Eine Zeile der Monatsliste.
   *
   * @param id Kennung des Zeiteintrags
   * @param tag der Tag, an dem gearbeitet wurde
   * @param von Beginn
   * @param bis Ende
   * @param stunden die gerechnete Dauer
   * @param position die Position, auf die gebucht wurde
   * @param person die Person, der der Eintrag gehoert, mit ihrem aktuellen Namen
   */
  public record Zeitzeile(
      long id,
      LocalDate tag,
      LocalTime von,
      LocalTime bis,
      BigDecimal stunden,
      BuchungspositionResponse position,
      PersonResponse person) {

    static Zeitzeile of(final Zeitbuchung buchung) {
      final Zeiteintrag eintrag = buchung.eintrag();
      return new Zeitzeile(
          eintrag.requireId(),
          eintrag.tag(),
          eintrag.von(),
          eintrag.bis(),
          eintrag.stunden(),
          BuchungspositionResponse.of(buchung.position()),
          PersonResponse.of(buchung.person()));
    }
  }
}
