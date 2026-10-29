package org.mwolff.fbcrm.rechnung.infrastructure;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.common.Iban;
import org.mwolff.fbcrm.rechnung.application.ERechnungsdaten;
import org.mwolff.fbcrm.rechnung.application.ERechnungsdaten.Kaeufer;
import org.mwolff.fbcrm.rechnung.application.ERechnungsdaten.Position;
import org.mwolff.fbcrm.rechnung.application.ERechnungsdaten.Verkaeufer;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Der Schreiber der E-Rechnung gegen die Norm: aus den Daten einer gestellten Rechnung wird CII
 * D16B nach EN 16931 (Plan #461, E1, E6 bis E9).
 *
 * <p>Zweierlei wird geprueft. Der Validator der KoSIT ({@link KositPruefung}) sagt, ob die XML die
 * Regeln der Norm besteht — fuer drei Rechnungen, die zusammen jede Abzweigung des Schreibers
 * nehmen (fachliche Quelle #460, Kriterium 2). Per XPath wird abgelesen, dass jede Angabe an ihrer
 * Stelle steht und dieselbe ist, die auf dem Dokument steht (Kriterium 3): Die Norm verlangt nur
 * einen Teil der Felder, und eine fehlende freiwillige Angabe bemerkte der Validator nicht.
 */
class CiiSchreiberTest {

  private static final LocalDate DATUM = LocalDate.of(2026, 10, 1);

  private static final Anschrift EIGENE_ANSCHRIFT =
      new Anschrift("Am Deich 2", "28199", "Bremen", "Deutschland");

  private static final Kaeufer KUNDE =
      new Kaeufer(
          "Müller & Söhne GmbH",
          new Anschrift("Hauptstraße 1", "20095", "Hamburg", "Deutschland"),
          "DE");

  private static final Iban IBAN = Iban.aus("DE89 3704 0044 0532 0130 00");

  /** Mit USt-IdNr. und Steuernummer, mit BIC, drei Einheiten und einem Leistungszeitraum. */
  private static final ERechnungsdaten MIT_UST_ID =
      new ERechnungsdaten(
          "R26-0042",
          DATUM,
          "September 2026",
          new Verkaeufer(
              "Manfred Wolff",
              EIGENE_ANSCHRIFT,
              "75/123/45678",
              "DE123456789",
              IBAN,
              "COBADEFFXXX",
              "Manfred Wolff Beratung"),
          KUNDE,
          List.of(
              new Position(
                  "Beratung",
                  new BigDecimal("12.00"),
                  Einheit.STUNDE,
                  new BigDecimal("100.00"),
                  new BigDecimal("1200.00")),
              new Position(
                  "Workshop",
                  new BigDecimal("2"),
                  Einheit.PERSONENTAG,
                  new BigDecimal("800.00"),
                  new BigDecimal("1600.00")),
              new Position(
                  "Projektpauschale",
                  BigDecimal.ONE,
                  Einheit.PAUSCHAL,
                  new BigDecimal("500.00"),
                  new BigDecimal("500.00"))),
          new BigDecimal("3300.00"),
          new BigDecimal("19.00"),
          new BigDecimal("627.00"),
          new BigDecimal("3927.00"),
          DATUM.plusDays(14),
          "Bitte überweisen Sie den Betrag innerhalb von 14 Tagen");

  /** Nur mit Steuernummer, ohne BIC, ohne Leistungszeitraum, sofort faellig. */
  private static final ERechnungsdaten NUR_STEUERNUMMER =
      new ERechnungsdaten(
          "R26-0043",
          DATUM,
          null,
          new Verkaeufer(
              "Manfred Wolff", EIGENE_ANSCHRIFT, "75/123/45678", null, IBAN, null, "Manfred Wolff"),
          KUNDE,
          List.of(
              new Position(
                  "Beratung",
                  new BigDecimal("10"),
                  Einheit.STUNDE,
                  new BigDecimal("95.00"),
                  new BigDecimal("950.00"))),
          new BigDecimal("950.00"),
          new BigDecimal("19.00"),
          new BigDecimal("180.50"),
          new BigDecimal("1130.50"),
          DATUM,
          "Bitte überweisen Sie den Betrag sofort");

  /** Sieben Prozent und eine Menge mit Nachkommastellen; nur die USt-IdNr. */
  private static final ERechnungsdaten SIEBEN_PROZENT =
      new ERechnungsdaten(
          "R26-0044",
          DATUM,
          "Oktober 2026",
          new Verkaeufer(
              "Manfred Wolff",
              EIGENE_ANSCHRIFT,
              null,
              "DE123456789",
              IBAN,
              "COBADEFFXXX",
              "Manfred Wolff"),
          KUNDE,
          List.of(
              new Position(
                  "Lektorat",
                  new BigDecimal("7.50"),
                  Einheit.STUNDE,
                  new BigDecimal("80.00"),
                  new BigDecimal("600.00"))),
          new BigDecimal("600.00"),
          new BigDecimal("7.00"),
          new BigDecimal("42.00"),
          new BigDecimal("642.00"),
          DATUM.plusDays(30),
          "Bitte überweisen Sie den Betrag innerhalb von 30 Tagen");

  private static final String KOPF = "/rsm:CrossIndustryInvoice/rsm:ExchangedDocument/";

  private static final String HANDEL = "/rsm:CrossIndustryInvoice/rsm:SupplyChainTradeTransaction/";

  private static final String VERKAEUFER =
      HANDEL + "ram:ApplicableHeaderTradeAgreement/ram:SellerTradeParty/";

  private static final String KAEUFER =
      HANDEL + "ram:ApplicableHeaderTradeAgreement/ram:BuyerTradeParty/";

  private static final String ABRECHNUNG = HANDEL + "ram:ApplicableHeaderTradeSettlement/";

  private static final String ZAHLUNGSART =
      ABRECHNUNG + "ram:SpecifiedTradeSettlementPaymentMeans/";

  private static final String SUMMEN =
      ABRECHNUNG + "ram:SpecifiedTradeSettlementHeaderMonetarySummation/";

  private static final Map<String, String> NAMENSRAEUME =
      Map.of(
          "rsm", "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100",
          "ram",
              "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100",
          "udt", "urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100");

  private final CiiSchreiber schreiber = new CiiSchreiber();

  @Test
  void cii_withUstIdBicThreeEinheitenAndLeistungszeitraum_thenTheValidatorReportsNoFehler() {
    // When
    final byte[] xml = schreiber.cii(MIT_UST_ID);

    // Then
    assertThat(KositPruefung.fehler(xml)).isEmpty();
  }

  @Test
  void cii_withOnlySteuernummerWithoutBicAndLeistungszeitraum_thenTheValidatorReportsNoFehler() {
    // When
    final byte[] xml = schreiber.cii(NUR_STEUERNUMMER);

    // Then
    assertThat(KositPruefung.fehler(xml)).isEmpty();
  }

  @Test
  void cii_withSiebenProzentAndAFractionalMenge_thenTheValidatorReportsNoFehler() {
    // When
    final byte[] xml = schreiber.cii(SIEBEN_PROZENT);

    // Then
    assertThat(KositPruefung.fehler(xml)).isEmpty();
  }

  @Test
  void kositPruefung_whenTheVerkaeuferHasNoKennung_thenItReportsBrCo26() {
    // Given — ein Verkaeufer ohne USt-IdNr. und ohne Steuernummer: Die XML traegt keine Kennung.
    final ERechnungsdaten ohneKennung =
        mitVerkaeufer(
            new Verkaeufer(
                "Manfred Wolff", EIGENE_ANSCHRIFT, null, null, IBAN, null, "Manfred Wolff"));

    // When
    final byte[] xml = schreiber.cii(ohneKennung);

    // Then — die Pruefung wirkt: Sie meldet genau die Regel, die fehlt.
    assertThat(anzahl(xml, VERKAEUFER + "ram:ID")).isZero();
    assertThat(anzahl(xml, VERKAEUFER + "ram:SpecifiedTaxRegistration")).isZero();
    assertThat(KositPruefung.fehler(xml)).anyMatch(fehler -> fehler.startsWith("BR-CO-26"));
  }

  @Test
  void cii_thenItIsUtf8WithItsDeclaration() {
    // When
    final byte[] xml = schreiber.cii(MIT_UST_ID);

    // Then — Umlaut und Kaufmanns-Und kommen unverfaelscht an.
    assertThat(new String(xml, UTF_8)).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    assertThat(wert(xml, KAEUFER + "ram:Name")).isEqualTo("Müller & Söhne GmbH");
  }

  @Test
  void cii_thenTheKopfNamesProfileTypeNummerAndDatum() {
    // When
    final byte[] xml = schreiber.cii(MIT_UST_ID);

    // Then
    assertThat(
            wert(
                xml,
                "/rsm:CrossIndustryInvoice/rsm:ExchangedDocumentContext"
                    + "/ram:GuidelineSpecifiedDocumentContextParameter/ram:ID"))
        .isEqualTo("urn:cen.eu:en16931:2017");
    assertThat(wert(xml, KOPF + "ram:ID")).isEqualTo("R26-0042");
    assertThat(wert(xml, KOPF + "ram:TypeCode")).isEqualTo("380");
    assertThat(wert(xml, KOPF + "ram:IssueDateTime/udt:DateTimeString")).isEqualTo("20261001");
    assertThat(wert(xml, KOPF + "ram:IssueDateTime/udt:DateTimeString/@format")).isEqualTo("102");
  }

  @Test
  void cii_withLeistungszeitraum_thenItStandsAsHinweis() {
    // When
    final byte[] xml = schreiber.cii(MIT_UST_ID);

    // Then (E7)
    assertThat(wert(xml, KOPF + "ram:IncludedNote/ram:Content"))
        .isEqualTo("Leistungszeitraum: September 2026");
  }

  @Test
  void cii_withoutLeistungszeitraum_thenThereIsNoHinweis() {
    // When
    final byte[] xml = schreiber.cii(NUR_STEUERNUMMER);

    // Then
    assertThat(anzahl(xml, KOPF + "ram:IncludedNote")).isZero();
  }

  @Test
  void cii_thenEveryEinheitBecomesItsUnitCode() {
    // When
    final byte[] xml = schreiber.cii(MIT_UST_ID);

    // Then — Stunde, Personentag und Pauschale nach UN/ECE Rec. 20 (E6).
    assertThat(positionswert(xml, 1, "ram:SpecifiedLineTradeDelivery/ram:BilledQuantity/@unitCode"))
        .isEqualTo("HUR");
    assertThat(positionswert(xml, 2, "ram:SpecifiedLineTradeDelivery/ram:BilledQuantity/@unitCode"))
        .isEqualTo("DAY");
    assertThat(positionswert(xml, 3, "ram:SpecifiedLineTradeDelivery/ram:BilledQuantity/@unitCode"))
        .isEqualTo("LS");
  }

  @Test
  void cii_thenEveryPositionCarriesItsAngabenInItsOrder() {
    // When
    final byte[] xml = schreiber.cii(MIT_UST_ID);

    // Then
    assertThat(anzahl(xml, HANDEL + "ram:IncludedSupplyChainTradeLineItem")).isEqualTo(3);
    assertThat(position(xml, 2))
        .containsExactly("2", "Workshop", "800.00", "2", "VAT", "S", "19.00", "1600.00");
    assertThat(position(xml, 3))
        .containsExactly("3", "Projektpauschale", "500.00", "1", "VAT", "S", "19.00", "500.00");
  }

  @Test
  void cii_withAFractionalMenge_thenTheMengeStandsAsItIs() {
    // When
    final byte[] xml = schreiber.cii(SIEBEN_PROZENT);

    // Then
    assertThat(position(xml, 1))
        .containsExactly("1", "Lektorat", "80.00", "7.50", "VAT", "S", "7.00", "600.00");
  }

  @Test
  void cii_thenThePaymentIsASepaUeberweisungWithIbanKontoinhaberAndBic() {
    // When
    final byte[] xml = schreiber.cii(MIT_UST_ID);

    // Then (E8) — die IBAN in der elektronischen Form, ohne Leerzeichen.
    assertThat(wert(xml, ABRECHNUNG + "ram:InvoiceCurrencyCode")).isEqualTo("EUR");
    assertThat(wert(xml, ZAHLUNGSART + "ram:TypeCode")).isEqualTo("58");
    assertThat(wert(xml, ZAHLUNGSART + "ram:PayeePartyCreditorFinancialAccount/ram:IBANID"))
        .isEqualTo("DE89370400440532013000");
    assertThat(wert(xml, ZAHLUNGSART + "ram:PayeePartyCreditorFinancialAccount/ram:AccountName"))
        .isEqualTo("Manfred Wolff Beratung");
    assertThat(wert(xml, ZAHLUNGSART + "ram:PayeeSpecifiedCreditorFinancialInstitution/ram:BICID"))
        .isEqualTo("COBADEFFXXX");
  }

  @Test
  void cii_withoutBic_thenThereIsNoInstitution() {
    // When
    final byte[] xml = schreiber.cii(NUR_STEUERNUMMER);

    // Then
    assertThat(anzahl(xml, ZAHLUNGSART + "ram:PayeeSpecifiedCreditorFinancialInstitution"))
        .isZero();
    assertThat(wert(xml, ZAHLUNGSART + "ram:PayeePartyCreditorFinancialAccount/ram:AccountName"))
        .isEqualTo("Manfred Wolff");
  }

  @Test
  void cii_thenTheFaelligkeitIsTheRechnungsdatumPlusTheZahlungsziel() {
    // When — Rechnungsdatum 01.10.2026, 14 Tage Zahlungsziel.
    final byte[] xml = schreiber.cii(MIT_UST_ID);

    // Then (E8)
    final String bedingung = ABRECHNUNG + "ram:SpecifiedTradePaymentTerms/";
    assertThat(wert(xml, bedingung + "ram:DueDateDateTime/udt:DateTimeString"))
        .isEqualTo("20261015");
    assertThat(wert(xml, bedingung + "ram:DueDateDateTime/udt:DateTimeString/@format"))
        .isEqualTo("102");
    assertThat(wert(xml, bedingung + "ram:Description"))
        .isEqualTo("Bitte überweisen Sie den Betrag innerhalb von 14 Tagen");
  }

  @Test
  void cii_withUstId_thenBothKennungenStandWithTheirSchemeAndThereIsNoVerkaeuferId() {
    // When
    final byte[] xml = schreiber.cii(MIT_UST_ID);

    // Then (E9)
    assertThat(wert(xml, VERKAEUFER + "ram:SpecifiedTaxRegistration/ram:ID[@schemeID='VA']"))
        .isEqualTo("DE123456789");
    assertThat(wert(xml, VERKAEUFER + "ram:SpecifiedTaxRegistration/ram:ID[@schemeID='FC']"))
        .isEqualTo("75/123/45678");
    assertThat(anzahl(xml, VERKAEUFER + "ram:ID")).isZero();
  }

  @Test
  void cii_withOnlySteuernummer_thenItStandsAlsoAsVerkaeuferId() {
    // When
    final byte[] xml = schreiber.cii(NUR_STEUERNUMMER);

    // Then (E9) — BR-CO-26 verlangt BT-29, BT-30 oder BT-31; BT-32 allein genuegt nicht.
    assertThat(wert(xml, VERKAEUFER + "ram:ID")).isEqualTo("75/123/45678");
    assertThat(wert(xml, VERKAEUFER + "ram:SpecifiedTaxRegistration/ram:ID[@schemeID='FC']"))
        .isEqualTo("75/123/45678");
    assertThat(anzahl(xml, VERKAEUFER + "ram:SpecifiedTaxRegistration/ram:ID[@schemeID='VA']"))
        .isZero();
  }

  @Test
  void cii_withOnlyUstId_thenThereIsNoSteuernummer() {
    // When
    final byte[] xml = schreiber.cii(SIEBEN_PROZENT);

    // Then
    assertThat(anzahl(xml, VERKAEUFER + "ram:SpecifiedTaxRegistration")).isEqualTo(1);
    assertThat(wert(xml, VERKAEUFER + "ram:SpecifiedTaxRegistration/ram:ID[@schemeID='VA']"))
        .isEqualTo("DE123456789");
    assertThat(anzahl(xml, VERKAEUFER + "ram:ID")).isZero();
  }

  @Test
  void cii_thenTheVerkaeuferStandsWithNameAndAnschriftInDeutschland() {
    // When
    final byte[] xml = schreiber.cii(MIT_UST_ID);

    // Then — das Land des Verkaeufers ist fest DE (E9).
    assertThat(wert(xml, VERKAEUFER + "ram:Name")).isEqualTo("Manfred Wolff");
    assertThat(anschrift(xml, VERKAEUFER)).containsExactly("28199", "Am Deich 2", "Bremen", "DE");
  }

  @Test
  void cii_thenTheKaeuferLandComesFromTheKuerzelOfTheCopy() {
    // Given — gedruckt steht „Österreich", in die XML gehoert das Kuerzel.
    final ERechnungsdaten nachWien =
        mitKaeufer(
            new Kaeufer("Adler GmbH", new Anschrift("Ring 1", "1010", "Wien", "Österreich"), "AT"));

    // When
    final byte[] xml = schreiber.cii(nachWien);

    // Then
    assertThat(wert(xml, KAEUFER + "ram:Name")).isEqualTo("Adler GmbH");
    assertThat(anschrift(xml, KAEUFER)).containsExactly("1010", "Ring 1", "Wien", "AT");
    assertThat(wert(xml, VERKAEUFER + "ram:PostalTradeAddress/ram:CountryID")).isEqualTo("DE");
  }

  @Test
  void cii_whenAnschriftenAreEmpty_thenOnlyTheLandStands() {
    // Given
    final Anschrift leer = new Anschrift(null, null, null, null);
    final ERechnungsdaten ohneAnschrift =
        mitKaeufer(
            new Kaeufer("Adler AG", leer, "DE"),
            new Verkaeufer(
                "Manfred Wolff", leer, null, "DE123456789", IBAN, null, "Manfred Wolff"));

    // When
    final byte[] xml = schreiber.cii(ohneAnschrift);

    // Then
    assertThat(anzahl(xml, VERKAEUFER + "ram:PostalTradeAddress/*")).isEqualTo(1);
    assertThat(anzahl(xml, KAEUFER + "ram:PostalTradeAddress/*")).isEqualTo(1);
    assertThat(wert(xml, KAEUFER + "ram:PostalTradeAddress/ram:CountryID")).isEqualTo("DE");
  }

  @Test
  void cii_thenSteuerAndSummenAreTheOnesOfTheRechnung() {
    // When
    final byte[] xml = schreiber.cii(NUR_STEUERNUMMER);

    // Then — mit zwei Nachkommastellen, wie die Rechnung sie gerechnet hat.
    final String steuer = ABRECHNUNG + "ram:ApplicableTradeTax/";
    assertThat(wert(xml, steuer + "ram:CalculatedAmount")).isEqualTo("180.50");
    assertThat(wert(xml, steuer + "ram:TypeCode")).isEqualTo("VAT");
    assertThat(wert(xml, steuer + "ram:BasisAmount")).isEqualTo("950.00");
    assertThat(wert(xml, steuer + "ram:CategoryCode")).isEqualTo("S");
    assertThat(wert(xml, steuer + "ram:RateApplicablePercent")).isEqualTo("19.00");
    assertThat(wert(xml, SUMMEN + "ram:LineTotalAmount")).isEqualTo("950.00");
    assertThat(wert(xml, SUMMEN + "ram:TaxBasisTotalAmount")).isEqualTo("950.00");
    assertThat(wert(xml, SUMMEN + "ram:TaxTotalAmount")).isEqualTo("180.50");
    assertThat(wert(xml, SUMMEN + "ram:TaxTotalAmount/@currencyID")).isEqualTo("EUR");
    assertThat(wert(xml, SUMMEN + "ram:GrandTotalAmount")).isEqualTo("1130.50");
    assertThat(wert(xml, SUMMEN + "ram:DuePayableAmount")).isEqualTo("1130.50");
  }

  @Test
  void cii_whenABetragHasNoNachkommastellen_thenItStandsWithTwo() {
    // Given — dieselben Zahlen ohne Nachkommastellen.
    final ERechnungsdaten ganz =
        new ERechnungsdaten(
            "R26-0045",
            DATUM,
            null,
            NUR_STEUERNUMMER.verkaeufer(),
            KUNDE,
            List.of(
                new Position(
                    "Beratung",
                    new BigDecimal("10"),
                    Einheit.STUNDE,
                    new BigDecimal("100"),
                    new BigDecimal("1000"))),
            new BigDecimal("1000"),
            new BigDecimal("19"),
            new BigDecimal("190"),
            new BigDecimal("1190"),
            DATUM,
            "Bitte überweisen Sie den Betrag sofort");

    // When
    final byte[] xml = schreiber.cii(ganz);

    // Then
    assertThat(position(xml, 1))
        .containsExactly("1", "Beratung", "100", "10", "VAT", "S", "19", "1000.00");
    assertThat(wert(xml, ABRECHNUNG + "ram:ApplicableTradeTax/ram:CalculatedAmount"))
        .isEqualTo("190.00");
    assertThat(wert(xml, SUMMEN + "ram:GrandTotalAmount")).isEqualTo("1190.00");
    assertThat(KositPruefung.fehler(xml)).isEmpty();
  }

  private static ERechnungsdaten mitVerkaeufer(final Verkaeufer verkaeufer) {
    return mitKaeufer(KUNDE, verkaeufer);
  }

  private static ERechnungsdaten mitKaeufer(final Kaeufer kaeufer) {
    return mitKaeufer(kaeufer, NUR_STEUERNUMMER.verkaeufer());
  }

  private static ERechnungsdaten mitKaeufer(final Kaeufer kaeufer, final Verkaeufer verkaeufer) {
    final ERechnungsdaten vorlage = NUR_STEUERNUMMER;
    return new ERechnungsdaten(
        vorlage.nummer(),
        vorlage.rechnungDatum(),
        vorlage.leistungszeitraum(),
        verkaeufer,
        kaeufer,
        vorlage.positionen(),
        vorlage.netto(),
        vorlage.steuersatz(),
        vorlage.steuer(),
        vorlage.brutto(),
        vorlage.faelligkeit(),
        vorlage.zahlungsbedingung());
  }

  /** Zeilennummer, Bezeichnung, Preis, Menge, Steuerart, Kategorie, Satz und Betrag. */
  private static List<String> position(final byte[] xml, final int nummer) {
    return List.of(
        positionswert(xml, nummer, "ram:AssociatedDocumentLineDocument/ram:LineID"),
        positionswert(xml, nummer, "ram:SpecifiedTradeProduct/ram:Name"),
        positionswert(
            xml,
            nummer,
            "ram:SpecifiedLineTradeAgreement/ram:NetPriceProductTradePrice/ram:ChargeAmount"),
        positionswert(xml, nummer, "ram:SpecifiedLineTradeDelivery/ram:BilledQuantity"),
        positionswert(
            xml, nummer, "ram:SpecifiedLineTradeSettlement/ram:ApplicableTradeTax/ram:TypeCode"),
        positionswert(
            xml,
            nummer,
            "ram:SpecifiedLineTradeSettlement/ram:ApplicableTradeTax/ram:CategoryCode"),
        positionswert(
            xml,
            nummer,
            "ram:SpecifiedLineTradeSettlement/ram:ApplicableTradeTax/ram:RateApplicablePercent"),
        positionswert(
            xml,
            nummer,
            "ram:SpecifiedLineTradeSettlement"
                + "/ram:SpecifiedTradeSettlementLineMonetarySummation/ram:LineTotalAmount"));
  }

  private static String positionswert(final byte[] xml, final int nummer, final String pfad) {
    return wert(xml, HANDEL + "ram:IncludedSupplyChainTradeLineItem[" + nummer + "]/" + pfad);
  }

  /** Postleitzahl, Strasse, Ort und Land, in der Reihenfolge des Schemas. */
  private static List<String> anschrift(final byte[] xml, final String partei) {
    final String adresse = partei + "ram:PostalTradeAddress/";
    return List.of(
        wert(xml, adresse + "*[1][self::ram:PostcodeCode]"),
        wert(xml, adresse + "*[2][self::ram:LineOne]"),
        wert(xml, adresse + "*[3][self::ram:CityName]"),
        wert(xml, adresse + "*[4][self::ram:CountryID]"));
  }

  private static String wert(final byte[] xml, final String pfad) {
    return auswerten(xml, "string(" + pfad + ")");
  }

  private static int anzahl(final byte[] xml, final String pfad) {
    return Integer.parseInt(auswerten(xml, "count(" + pfad + ")"));
  }

  private static String auswerten(final byte[] xml, final String ausdruck) {
    try {
      final DocumentBuilderFactory fabrik = DocumentBuilderFactory.newInstance();
      fabrik.setNamespaceAware(true);
      final Document dokument = fabrik.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
      final XPath xpath = XPathFactory.newInstance().newXPath();
      xpath.setNamespaceContext(new Namensraeume());
      return xpath.evaluate(ausdruck, dokument);
    } catch (final ParserConfigurationException
        | SAXException
        | IOException
        | XPathExpressionException fehler) {
      throw new AssertionError("Die XML laesst sich nicht auswerten: " + ausdruck, fehler);
    }
  }

  /** Die drei Praefixe der CII. */
  private static final class Namensraeume implements NamespaceContext {

    @Override
    public String getNamespaceURI(final String praefix) {
      return NAMENSRAEUME.getOrDefault(praefix, XMLConstants.NULL_NS_URI);
    }

    @Override
    public String getPrefix(final String namensraum) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<String> getPrefixes(final String namensraum) {
      throw new UnsupportedOperationException();
    }
  }
}
