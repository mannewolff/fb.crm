package org.mwolff.fbcrm.startseite.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.mwolff.fbcrm.angebot.application.AngebotMitFirma;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.rechnung.application.Monatsabrechnung;
import org.mwolff.fbcrm.startseite.application.Abgerechnet;
import org.mwolff.fbcrm.startseite.application.Abrechnungsmonat;
import org.mwolff.fbcrm.startseite.application.Angebotsanteil;
import org.mwolff.fbcrm.startseite.application.NichtAbgerechnet;
import org.mwolff.fbcrm.startseite.application.Startseitenstand;
import org.mwolff.fbcrm.startseite.application.WaehlbareZeitraeume;
import org.mwolff.fbcrm.startseite.application.Zeitraum;

/**
 * Der Stand der Startseite, wie die Ansicht ihn zeigt (#206, Kriterien 3 bis 8; #273; Issue #214).
 *
 * <p>Die Antwort traegt fertig, was die Ansicht darstellt, und nichts darueber hinaus: je Kennzahl
 * ihre Betraege und die Zeilen darunter, dazu den geltenden Zeitraum und die waehlbaren. Die
 * Oberflaeche rechnet damit nichts und fragt nichts nach.
 *
 * <p><b>Der Zeitraum steht als Art und Wert darin</b> (Plan #274, E10): Die Ansicht schaltet an der
 * Art Beschriftungen und den Inhalt einer Karte um und muss sie nicht aus der Laenge des Werts
 * schliessen. Wert und waehlbare Jahre sind Text — genau das, was die Ansicht in die Adresse und in
 * {@code option value} setzt; ein {@code Year} schriebe Jackson als Zahl.
 *
 * <p><b>Von den Angeboten kommen vier Angaben mit, nicht das ganze Angebot.</b> Die Zeile zeigt
 * Firma und Datum, und die Kennung ist der Weg zum Angebot — Positionen, Beschreibung und
 * Zeitstempel braucht die Startseite nicht, und sie mitzusenden hiesse, den Bestand eines fremden
 * Moduls ueber diesen Weg zu veroeffentlichen.
 *
 * <p><b>Die Zahl der Angebote in Arbeit steht nicht als Feld darin</b> — die Liste ist die
 * Wahrheit, und die Ansicht zaehlt sie. Bei {@code abgerechnet} ist die Anzahl dagegen ein Feld:
 * Dort stehen die Rechnungen selbst nicht in der Antwort (Plan #208, E20).
 *
 * <p><b>Die internen Stunden stehen neben den Kennzahlen</b> und in keiner von ihnen (#207,
 * Kriterium 9): Sie sind eine Stundenzahl und kein Betrag — interne Arbeit traegt keinen Preis, und
 * ein Euro-Wert daraus ist Nicht-Ziel von #207. Darum steht das Feld oben in der Antwort und nicht
 * in {@code nichtAbgerechnet}.
 *
 * @param zeitraum der Zeitraum, fuer den „Abgerechnet" und die zweite Zeile von „Noch nicht
 *     abgerechnet" gelten; immer einer der waehlbaren
 * @param waehlbar die waehlbaren Jahre und Monate
 * @param inArbeit die Angebote im Status „bestellt" oder „erledigt", neueste zuerst
 * @param nichtAbgerechnet was aus erfasster Arbeitszeit noch abzurechnen ist
 * @param abgerechnet Netto, Brutto, Anzahl und der noch offene Anteil der im Zeitraum gestellten
 *     Rechnungen, bei einem Jahr samt seinen Monaten
 * @param interneStundenImZeitraum die im gewaehlten Zeitraum auf interne Angebote gebuchten Stunden
 */
public record StartseiteResponse(
    ZeitraumResponse zeitraum,
    WaehlbarResponse waehlbar,
    List<Angebotszeile> inArbeit,
    NichtAbgerechnetResponse nichtAbgerechnet,
    AbgerechnetResponse abgerechnet,
    BigDecimal interneStundenImZeitraum) {

  /** Derselbe Stand in der Sprache der Schnittstelle. */
  static StartseiteResponse of(final Startseitenstand stand) {
    return new StartseiteResponse(
        ZeitraumResponse.of(stand.zeitraum()),
        WaehlbarResponse.of(stand.waehlbar()),
        stand.inArbeit().stream().map(Angebotszeile::of).toList(),
        NichtAbgerechnetResponse.of(stand.nichtAbgerechnet()),
        AbgerechnetResponse.of(stand.abgerechnet()),
        stand.interneStundenImZeitraum());
  }

  /**
   * Der geltende Zeitraum (#273, Kriterien 1 und 2; Plan #274, E10).
   *
   * @param art {@code "MONAT"} oder {@code "JAHR"}
   * @param wert {@code "2026-10"} oder {@code "2026"} — der Wert des Adressparameters
   */
  public record ZeitraumResponse(String art, String wert) {

    static ZeitraumResponse of(final Zeitraum zeitraum) {
      final String art =
          switch (zeitraum) {
            case Zeitraum.Monat _ -> "MONAT";
            case Zeitraum.Jahr _ -> "JAHR";
          };
      return new ZeitraumResponse(art, zeitraum.wert());
    }
  }

  /**
   * Die waehlbaren Zeitraeume (#273, Kriterien 1 und 2).
   *
   * @param jahre die waehlbaren Jahre als Text {@code JJJJ}, neuestes zuerst (E10)
   * @param monate die waehlbaren Monate, neuester zuerst
   */
  public record WaehlbarResponse(List<String> jahre, List<YearMonth> monate) {

    static WaehlbarResponse of(final WaehlbareZeitraeume waehlbar) {
      return new WaehlbarResponse(
          waehlbar.jahre().stream().map(String::valueOf).toList(), waehlbar.monate());
    }
  }

  /**
   * Ein Angebot in Arbeit (#206, Kriterium 4).
   *
   * @param angebotId Kennung des Angebots — der Weg dorthin
   * @param firmaName Name der Firma, an die das Angebot geht
   * @param angebotDatum Datum des Angebots
   * @param status „bestellt" oder „erledigt"; die Ansicht unterscheidet die beiden
   */
  public record Angebotszeile(
      long angebotId, String firmaName, LocalDate angebotDatum, Angebotsstatus status) {

    static Angebotszeile of(final AngebotMitFirma zeile) {
      return new Angebotszeile(
          zeile.angebot().requireId(),
          zeile.firmaName(),
          zeile.angebot().angebotDatum(),
          zeile.angebot().status());
    }
  }

  /**
   * Die Kennzahl „Noch nicht abgerechnet" (#206, Kriterien 5 und 6).
   *
   * <p>Die beiden Betraege haben verschiedene Zeitraeume, und das ist Absicht — die Begruendung
   * steht bei {@link NichtAbgerechnet}: {@code netto} ist der Stand von heute ueber alle Monate,
   * {@code erfasstImZeitraum} haengt am gewaehlten Zeitraum.
   *
   * @param netto der Betrag netto, Stand von heute ueber alle Monate
   * @param erfasstImZeitraum der Wert der im gewaehlten Zeitraum erfassten Stunden, netto
   * @param angebote je beitragendem Angebot sein Anteil am {@code netto}, neueste zuerst
   */
  public record NichtAbgerechnetResponse(
      BigDecimal netto, BigDecimal erfasstImZeitraum, List<Anteilszeile> angebote) {

    static NichtAbgerechnetResponse of(final NichtAbgerechnet gerechnet) {
      return new NichtAbgerechnetResponse(
          gerechnet.betrag(),
          gerechnet.erfasstImZeitraum(),
          gerechnet.anteile().stream().map(Anteilszeile::of).toList());
    }
  }

  /**
   * Was ein einzelnes Angebot zur Kennzahl „Noch nicht abgerechnet" beitraegt (#206, Kriterium 5).
   *
   * <p>Ohne Status: Welche Angebote beitragen, entscheidet nicht der Status, sondern ob an ihren
   * Positionen noch etwas offen ist — ein abgerechnetes Angebot kann hier stehen.
   *
   * @param angebotId Kennung des Angebots — der Weg dorthin
   * @param firmaName Name der Firma, an die das Angebot geht
   * @param angebotDatum Datum des Angebots
   * @param netto sein Anteil am Betrag, netto und auf den Cent
   */
  public record Anteilszeile(
      long angebotId, String firmaName, LocalDate angebotDatum, BigDecimal netto) {

    static Anteilszeile of(final Angebotsanteil anteil) {
      final AngebotMitFirma zeile = anteil.angebot();
      return new Anteilszeile(
          zeile.angebot().requireId(),
          zeile.firmaName(),
          zeile.angebot().angebotDatum(),
          anteil.betrag());
    }
  }

  /**
   * Die Kennzahl „Abgerechnet" fuer den gewaehlten Zeitraum (#206, Kriterium 7; #273, Kriterien 4
   * und 7).
   *
   * <p>Die Monatszeilen stehen in der Kennzahl, weil sie ihre Herkunft sind (Plan #274, E11). Bei
   * Monatswahl ist die Liste leer und nicht {@code null}.
   *
   * <p><b>Das Offene steht daneben und nicht darin</b> (Issue #284): {@code netto} bleibt der
   * Umsatz des Zeitraums nach Rechnungsdatum — eine bezahlte Rechnung zaehlt dort weiter mit —, und
   * {@code offenNetto} sagt, wie viel davon noch nicht bezahlt ist. Beide Betraege sind netto; die
   * Ansicht stellt sie untereinander, und zwei Einheiten nebeneinander waeren dort nicht zu lesen.
   *
   * @param netto die Summe der Netto-Betraege der im Zeitraum gestellten Rechnungen
   * @param brutto die Summe ihrer Brutto-Betraege, jeder mit dem Satz seiner Rechnung
   * @param anzahl die Zahl dieser Rechnungen
   * @param offenNetto die Summe der Netto-Betraege derjenigen, die noch offen sind; 0,00 ohne eine
   *     solche
   * @param offenAnzahl die Zahl dieser offenen Rechnungen
   * @param monate bei Jahreswahl je Monat mit mindestens einer gestellten Rechnung eine Zeile,
   *     aeltester zuerst; bei Monatswahl leer
   */
  public record AbgerechnetResponse(
      BigDecimal netto,
      BigDecimal brutto,
      int anzahl,
      BigDecimal offenNetto,
      int offenAnzahl,
      List<Monatszeile> monate) {

    static AbgerechnetResponse of(final Abgerechnet abgerechnet) {
      final Monatsabrechnung summe = abgerechnet.summe();
      return new AbgerechnetResponse(
          summe.netto(),
          summe.brutto(),
          summe.anzahl(),
          summe.offenNetto(),
          summe.offenAnzahl(),
          abgerechnet.monate().stream().map(Monatszeile::of).toList());
    }
  }

  /**
   * Ein Monat der Liste unter „Abgerechnet" bei Jahreswahl (#273, Kriterium 7).
   *
   * @param monat der Monat des Rechnungsdatums
   * @param anzahl die Zahl der in ihm gestellten Rechnungen
   * @param netto die Summe ihrer Netto-Betraege
   * @param brutto die Summe ihrer Brutto-Betraege
   * @param offenNetto die Summe der Netto-Betraege derjenigen, die noch offen sind (Issue #284);
   *     0,00 in einem Monat, dessen Rechnungen alle bezahlt oder abgeschrieben sind
   * @param offenAnzahl die Zahl dieser offenen Rechnungen
   */
  public record Monatszeile(
      YearMonth monat,
      int anzahl,
      BigDecimal netto,
      BigDecimal brutto,
      BigDecimal offenNetto,
      int offenAnzahl) {

    static Monatszeile of(final Abrechnungsmonat zeile) {
      final Monatsabrechnung abrechnung = zeile.abrechnung();
      return new Monatszeile(
          zeile.monat(),
          abrechnung.anzahl(),
          abrechnung.netto(),
          abrechnung.brutto(),
          abrechnung.offenNetto(),
          abrechnung.offenAnzahl());
    }
  }
}
