package org.mwolff.fbcrm.startseite.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.mwolff.fbcrm.angebot.application.AngebotMitFirma;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.rechnung.application.Monatsabrechnung;
import org.mwolff.fbcrm.startseite.application.Angebotsanteil;
import org.mwolff.fbcrm.startseite.application.NichtAbgerechnet;
import org.mwolff.fbcrm.startseite.application.Startseitenstand;
import org.mwolff.fbcrm.startseite.application.Zeitraum;

/**
 * Der Stand der Startseite, wie die Ansicht ihn zeigt (#206, Kriterien 3 bis 8; Issue #214).
 *
 * <p>Die Antwort traegt fertig, was die Ansicht darstellt, und nichts darueber hinaus: je Kennzahl
 * ihre Betraege und die Zeilen darunter, dazu den geltenden Monat und die waehlbaren. Die
 * Oberflaeche rechnet damit nichts und fragt nichts nach.
 *
 * <p><b>Der Vertrag ist noch der des Monats</b> (Plan #274): Der Stand darunter haengt schon an
 * einem {@link Zeitraum}, die Antwort schreibt aber weiter die bisherigen Felder — den Monat, die
 * waehlbaren Monate ohne die Jahre und „Abgerechnet" ohne Monatszeilen. Der Controller fragt nur
 * nach Monaten, darum ist der geltende Zeitraum hier immer ein Monat. Die neue Antwort ist ein
 * eigenes Paket.
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
 * @param monat der Monat, fuer den „Abgerechnet" und die Monatszeile gelten
 * @param monate die waehlbaren Monate, neuester zuerst; {@code monat} ist einer von ihnen
 * @param inArbeit die Angebote im Status „bestellt" oder „erledigt", neueste zuerst
 * @param nichtAbgerechnet was aus erfasster Arbeitszeit noch abzurechnen ist
 * @param abgerechnet Netto, Brutto und Anzahl der im Monat gestellten Rechnungen
 * @param interneStundenImMonat die im gewaehlten Monat auf interne Angebote gebuchten Stunden
 */
public record StartseiteResponse(
    YearMonth monat,
    List<YearMonth> monate,
    List<Angebotszeile> inArbeit,
    NichtAbgerechnetResponse nichtAbgerechnet,
    AbgerechnetResponse abgerechnet,
    BigDecimal interneStundenImMonat) {

  /** Derselbe Stand in der Sprache der Schnittstelle. */
  static StartseiteResponse of(final Startseitenstand stand) {
    return new StartseiteResponse(
        YearMonth.from(stand.zeitraum().von()),
        stand.waehlbar().monate(),
        stand.inArbeit().stream().map(Angebotszeile::of).toList(),
        NichtAbgerechnetResponse.of(stand.nichtAbgerechnet()),
        AbgerechnetResponse.of(stand.abgerechnet().summe()),
        stand.interneStundenImZeitraum());
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
   * {@code erfasstImMonat} haengt am gewaehlten Monat.
   *
   * @param netto der Betrag netto, Stand von heute ueber alle Monate
   * @param erfasstImMonat der Wert der im gewaehlten Monat erfassten Stunden, netto
   * @param angebote je beitragendem Angebot sein Anteil am {@code netto}, neueste zuerst
   */
  public record NichtAbgerechnetResponse(
      BigDecimal netto, BigDecimal erfasstImMonat, List<Anteilszeile> angebote) {

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
   * Die Kennzahl „Abgerechnet" fuer den gewaehlten Monat (#206, Kriterium 7).
   *
   * @param netto die Summe der Netto-Betraege der im Monat gestellten Rechnungen
   * @param brutto die Summe ihrer Brutto-Betraege, jeder mit dem Satz seiner Rechnung
   * @param anzahl die Zahl dieser Rechnungen
   */
  public record AbgerechnetResponse(BigDecimal netto, BigDecimal brutto, int anzahl) {

    static AbgerechnetResponse of(final Monatsabrechnung gestellt) {
      return new AbgerechnetResponse(gestellt.netto(), gestellt.brutto(), gestellt.anzahl());
    }
  }
}
