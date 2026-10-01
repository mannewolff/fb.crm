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
 * <p>Die Felder sind die aus A20 und vollstaendig: Je Zeile Kennung, Tag, Uhrzeit, gerechnete Dauer
 * und die Position mit Angebot und Firmenname; je Tag die Summe, dazu die des Monats. Die
 * Oberflaeche rechnet und fragt damit nichts nach.
 *
 * <p><b>Der Monat steht in der Antwort</b>, weil der Aufrufer ihn weglassen darf und dann den
 * laufenden bekommt (E4) — ohne diese Angabe muesste die Ansicht die Geschaeftszone selbst
 * nachrechnen.
 *
 * <p><b>Die Zeile traegt ihren Tag ein zweites Mal</b>, obwohl sie unter ihm steht. Das ist
 * Absicht: Der Dialog zum Aendern bekommt die Zeile allein uebergeben und braucht den Tag als
 * Vorbelegung.
 *
 * @param monat der Monat, den diese Liste zeigt
 * @param tage die Tage mit Eintraegen, aufsteigend; ein Tag ohne Eintrag fehlt
 * @param stunden die Summe des Monats
 */
public record ArbeitszeitMonatResponse(YearMonth monat, List<Tageszeile> tage, BigDecimal stunden) {

  /** Derselbe Monat in der Sprache der Schnittstelle. */
  static ArbeitszeitMonatResponse of(final Arbeitsmonat gelesen) {
    return new ArbeitszeitMonatResponse(
        gelesen.monat(), gelesen.tage().stream().map(Tageszeile::of).toList(), gelesen.stunden());
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
   */
  public record Zeitzeile(
      long id,
      LocalDate tag,
      LocalTime von,
      LocalTime bis,
      BigDecimal stunden,
      BuchungspositionResponse position) {

    static Zeitzeile of(final Zeitbuchung buchung) {
      final Zeiteintrag eintrag = buchung.eintrag();
      return new Zeitzeile(
          eintrag.requireId(),
          eintrag.tag(),
          eintrag.von(),
          eintrag.bis(),
          eintrag.stunden(),
          BuchungspositionResponse.of(buchung.position()));
    }
  }
}
