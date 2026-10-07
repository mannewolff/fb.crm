package org.mwolff.fbcrm.jahresabschluss.web;

import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.jahresabschluss.application.Angebotsbilanz;
import org.mwolff.fbcrm.jahresabschluss.application.Einnahmen;
import org.mwolff.fbcrm.jahresabschluss.application.Jahresabschluss;
import org.mwolff.fbcrm.jahresabschluss.application.Jahresarbeitszeit;
import org.mwolff.fbcrm.jahresabschluss.application.Kundenzeile;
import org.mwolff.fbcrm.jahresabschluss.application.Rechnungsstand;
import org.mwolff.fbcrm.jahresabschluss.application.Steuerzeile;

/**
 * Der Abschluss eines Jahres, wie die Ansicht ihn zeigt (#287, Kriterien 4 bis 12; Plan #288, E2).
 *
 * <p>Die Antwort traegt fertig, was die Ansicht darstellt: Die Oberflaeche rechnet damit nichts.
 * Eine Kennzahl, deren Nenner null ist, steht als {@code null} und nicht als 0 (Kriterium 11) — die
 * Ansicht zeigt dort den Strich mit seinem Grund. Ebenso der Satz der Zeile „Steuersatz nicht
 * erfasst" (Kriterium 6).
 *
 * <p><b>Das Jahr steht als Text darin</b>, wie in {@link JahresuebersichtResponse}.
 *
 * @param jahr das Kalenderjahr als {@code JJJJ}
 * @param laeuftNoch ob das Jahr das laufende ist (#287, Kriterium 1)
 * @param einnahmen netto, brutto und die Umsatzsteuer dazwischen (Kriterium 4)
 * @param rechnungsstand die gestellten Rechnungen und davon die offenen und abgeschriebenen
 *     (Kriterium 5)
 * @param steuerzeilen netto und Umsatzsteuer je Steuersatz; leer, wo die Aufteilung nur eine Zeile
 *     ergaebe (Kriterium 6)
 * @param angebotsbilanz Stueckzahlen, Annahmequote und Volumen der Angebote (Kriterien 7, 8, 12)
 * @param kunden die Kunden des Jahres mit Betrag und Anteil, absteigend (Kriterium 9)
 * @param arbeitszeit die Stunden des Jahres und der Erloes je Stunde (Kriterium 10)
 */
public record JahresabschlussResponse(
    String jahr,
    boolean laeuftNoch,
    EinnahmenResponse einnahmen,
    RechnungsstandResponse rechnungsstand,
    List<Steuerzeilenantwort> steuerzeilen,
    AngebotsbilanzResponse angebotsbilanz,
    List<Kundenzeilenantwort> kunden,
    ArbeitszeitResponse arbeitszeit) {

  /** Derselbe Abschluss in der Sprache der Schnittstelle. */
  static JahresabschlussResponse of(final Jahresabschluss abschluss) {
    return new JahresabschlussResponse(
        String.valueOf(abschluss.jahr()),
        abschluss.laeuftNoch(),
        EinnahmenResponse.of(abschluss.einnahmen()),
        RechnungsstandResponse.of(abschluss.rechnungsstand()),
        abschluss.steuerzeilen().stream().map(Steuerzeilenantwort::of).toList(),
        AngebotsbilanzResponse.of(abschluss.angebotsbilanz()),
        abschluss.kunden().stream().map(Kundenzeilenantwort::of).toList(),
        ArbeitszeitResponse.of(abschluss.arbeitszeit()));
  }

  /**
   * Die Einnahmen des Jahres nach Rechnungsdatum (#287, Kriterium 4).
   *
   * @param netto die Summe netto
   * @param brutto die Summe brutto
   * @param umsatzsteuer brutto minus netto
   */
  public record EinnahmenResponse(BigDecimal netto, BigDecimal brutto, BigDecimal umsatzsteuer) {

    static EinnahmenResponse of(final Einnahmen einnahmen) {
      return new EinnahmenResponse(einnahmen.netto(), einnahmen.brutto(), einnahmen.umsatzsteuer());
    }
  }

  /**
   * Die gestellten Rechnungen des Jahres (#287, Kriterium 5).
   *
   * @param anzahl die Zahl der gestellten Rechnungen
   * @param offenAnzahl die Zahl der heute noch offenen davon
   * @param offenNetto deren Summe netto
   * @param abgeschriebenAnzahl die Zahl der abgeschriebenen davon
   * @param abgeschriebenNetto deren Summe netto
   */
  public record RechnungsstandResponse(
      int anzahl,
      int offenAnzahl,
      BigDecimal offenNetto,
      int abgeschriebenAnzahl,
      BigDecimal abgeschriebenNetto) {

    static RechnungsstandResponse of(final Rechnungsstand stand) {
      return new RechnungsstandResponse(
          stand.anzahl(),
          stand.offenAnzahl(),
          stand.offenNetto(),
          stand.abgeschriebenAnzahl(),
          stand.abgeschriebenNetto());
    }
  }

  /**
   * Eine Zeile der Umsatzsteuer je Steuersatz (#287, Kriterium 6).
   *
   * @param satz der Steuersatz in Prozent; {@code null} in der Zeile „Steuersatz nicht erfasst"
   * @param netto die Summe netto der Rechnungen mit diesem Satz
   * @param umsatzsteuer deren Umsatzsteuer
   */
  public record Steuerzeilenantwort(
      @Nullable BigDecimal satz, BigDecimal netto, BigDecimal umsatzsteuer) {

    static Steuerzeilenantwort of(final Steuerzeile zeile) {
      return new Steuerzeilenantwort(zeile.satz(), zeile.netto(), zeile.umsatzsteuer());
    }
  }

  /**
   * Die Angebote des Jahres (#287, Kriterien 7, 8 und 12).
   *
   * @param abgegeben die Zahl der abgegebenen Angebote
   * @param angenommen die Zahl der heute bestellten, erledigten oder abgerechneten davon
   * @param offen die Zahl der heute noch abgegebenen davon
   * @param annahmequote angenommen durch abgegeben in Prozent; {@code null} ohne abgegebenes
   *     Angebot
   * @param volumenAbgegeben die Summe netto der abgegebenen Angebote
   * @param volumenAngenommen die Summe netto der angenommenen Angebote
   */
  public record AngebotsbilanzResponse(
      int abgegeben,
      int angenommen,
      int offen,
      @Nullable BigDecimal annahmequote,
      BigDecimal volumenAbgegeben,
      BigDecimal volumenAngenommen) {

    static AngebotsbilanzResponse of(final Angebotsbilanz bilanz) {
      return new AngebotsbilanzResponse(
          bilanz.abgegeben(),
          bilanz.angenommen(),
          bilanz.offen(),
          bilanz.annahmequote(),
          bilanz.volumenAbgegeben(),
          bilanz.volumenAngenommen());
    }
  }

  /**
   * Ein Kunde des Jahres (#287, Kriterium 9).
   *
   * @param firmaName der Name der Firma
   * @param netto ihr Rechnungsbetrag netto im Jahr
   * @param anteil ihr Anteil am Jahresumsatz in Prozent; {@code null} ohne Umsatz
   */
  public record Kundenzeilenantwort(
      String firmaName, BigDecimal netto, @Nullable BigDecimal anteil) {

    static Kundenzeilenantwort of(final Kundenzeile zeile) {
      return new Kundenzeilenantwort(zeile.firmaName(), zeile.netto(), zeile.anteil());
    }
  }

  /**
   * Die Arbeitszeit des Jahres (#287, Kriterium 10).
   *
   * @param kundenStunden die auf Kundenangebote erfassten Stunden
   * @param interneStunden die auf interne Projekte erfassten Stunden
   * @param erloesJeStunde Einnahmen netto durch Kundenstunden; {@code null} ohne Kundenstunden
   */
  public record ArbeitszeitResponse(
      BigDecimal kundenStunden, BigDecimal interneStunden, @Nullable BigDecimal erloesJeStunde) {

    static ArbeitszeitResponse of(final Jahresarbeitszeit arbeitszeit) {
      return new ArbeitszeitResponse(
          arbeitszeit.kundenStunden(), arbeitszeit.interneStunden(), arbeitszeit.erloesJeStunde());
    }
  }
}
