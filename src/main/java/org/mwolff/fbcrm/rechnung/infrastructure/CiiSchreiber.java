package org.mwolff.fbcrm.rechnung.infrastructure;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.common.ExcludeFromJacocoGeneratedReport;
import org.mwolff.fbcrm.rechnung.application.ERechnungsdaten;
import org.mwolff.fbcrm.rechnung.application.ERechnungsdaten.Kaeufer;
import org.mwolff.fbcrm.rechnung.application.ERechnungsdaten.Position;
import org.mwolff.fbcrm.rechnung.application.ERechnungsdaten.Verkaeufer;
import org.mwolff.fbcrm.rechnung.application.ERechnungsschreiber;
import org.springframework.stereotype.Component;

/**
 * Setzt den Port {@link ERechnungsschreiber} um: CII D16B nach EN 16931, geschrieben mit {@code
 * javax.xml.stream} aus dem JDK (Plan #461, E1).
 *
 * <p>Wie der Drucker absichtlich duenn: Was in der E-Rechnung steht, ist in {@link ERechnungsdaten}
 * entschieden. Hier wird es an die Stelle geschrieben, die die Norm vorsieht, in der Reihenfolge
 * des Schemas. Geschrieben wird nur, was fb.crm fuellt — Kopf, Positionen, Verkaeufer und Kaeufer,
 * Zahlung, Steuer und Summen —, und eine fehlende freiwillige Angabe hinterlaesst kein leeres
 * Element.
 *
 * <p>Die Festlegungen des Plans: die Einheiten als Codes nach UN/ECE Rec. 20 (E6), der
 * Leistungszeitraum als Hinweis (E7), die SEPA-Ueberweisung mit Faelligkeit und gedrucktem
 * Zahlungssatz (E8), die Steuerkennungen mit ihrem Schema und das Land des Verkaeufers fest {@code
 * DE} (E9). Fehlt die USt-IdNr., steht die Steuernummer zusaetzlich als Kennung des Verkaeufers
 * (BT-29), weil die Regel BR-CO-26 eine Kennung verlangt und BT-32 dafuer nicht genuegt.
 *
 * <p>Die Betraege stehen mit zwei Nachkommastellen und sind die, die die Rechnung gerechnet hat;
 * Menge, Einzelpreis und Steuersatz stehen, wie sie sind.
 */
/*
 * PMD.TooManyMethods: Jede Methode schreibt einen Abschnitt der Norm — Kopf, Position, Partei,
 * Anschrift, Zahlung, Steuer, Summen. Sie auf mehrere Klassen zu verteilen, risse die eine
 * Reihenfolge des Schemas auseinander, die nur am Stueck zu lesen ist.
 */
@SuppressWarnings("PMD.TooManyMethods")
@Component
class CiiSchreiber implements ERechnungsschreiber {

  private static final String RSM = "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100";

  private static final String RAM =
      "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100";

  private static final String UDT = "urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100";

  /** Das Profil EN 16931 ohne Erweiterung (BT-24). */
  private static final String PROFIL = "urn:cen.eu:en16931:2017";

  /** Handelsrechnung (BT-3). */
  private static final String RECHNUNG = "380";

  /** SEPA-Ueberweisung (BT-81, E8). */
  private static final String SEPA_UEBERWEISUNG = "58";

  /** Normal besteuert (BT-151, BT-118). */
  private static final String NORMALSATZ = "S";

  private static final String UMSATZSTEUER = "VAT";

  private static final String EURO = "EUR";

  /** Das Land des Verkaeufers (E9). */
  private static final String DEUTSCHLAND = "DE";

  /** Datumsformat 102 der UN/CEFACT: „20261001". */
  private static final String FORMAT_102 = "102";

  private static final DateTimeFormatter DATUM = DateTimeFormatter.BASIC_ISO_DATE;

  private static final int CENT = 2;

  /*
   * Ausgenommen ist allein dieser Rahmen, nicht die Arbeit darin: Der XMLStreamException-Zweig ist
   * mit einem StringWriter und Namen aus Konstanten nicht erreichbar, und ein Test dafuer
   * muesste die Bibliothek verbiegen. Methodengenaue Ausnahme nach CLAUDE-java.md §5.4, wie bei
   * PdfBoxDrucker.drucke; geschrieben() bleibt in Abdeckung und Mutationstest, geprueft von
   * CiiSchreiberTest.
   */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public byte[] cii(final ERechnungsdaten daten) {
    try {
      return geschrieben(daten);
    } catch (final XMLStreamException nichtErreichbar) {
      throw new IllegalStateException(
          "Die E-Rechnung liess sich nicht schreiben.", nichtErreichbar);
    }
  }

  private static byte[] geschrieben(final ERechnungsdaten daten) throws XMLStreamException {
    // Der Schreiber des JDK und kein anderer, den der Klassenpfad anbietet (E1): Er schreibt in
    // den Text durch, ohne zu puffern — ein flush() am Ende aenderte nichts, und dass die XML
    // vollstaendig ist, lesen die Tests am Ergebnis ab. In UTF-8 wird erst am Ende kodiert.
    final StringWriter text = new StringWriter();
    final XMLStreamWriter xml = XMLOutputFactory.newDefaultFactory().createXMLStreamWriter(text);
    xml.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0");
    xml.writeStartElement("rsm", "CrossIndustryInvoice", RSM);
    xml.writeNamespace("rsm", RSM);
    xml.writeNamespace("ram", RAM);
    xml.writeNamespace("udt", UDT);
    kontext(xml);
    kopf(xml, daten);
    xml.writeStartElement("rsm", "SupplyChainTradeTransaction", RSM);
    for (int i = 0; i < daten.positionen().size(); i++) {
      position(xml, i + 1, daten.positionen().get(i), daten.steuersatz());
    }
    vereinbarung(xml, daten.verkaeufer(), daten.kaeufer());
    xml.writeEmptyElement("ram", "ApplicableHeaderTradeDelivery", RAM);
    abrechnung(xml, daten);
    xml.writeEndElement();
    xml.writeEndElement();
    return text.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static void kontext(final XMLStreamWriter xml) throws XMLStreamException {
    xml.writeStartElement("rsm", "ExchangedDocumentContext", RSM);
    beginne(xml, "GuidelineSpecifiedDocumentContextParameter");
    element(xml, "ID", PROFIL);
    xml.writeEndElement();
    xml.writeEndElement();
  }

  private static void kopf(final XMLStreamWriter xml, final ERechnungsdaten daten)
      throws XMLStreamException {
    xml.writeStartElement("rsm", "ExchangedDocument", RSM);
    element(xml, "ID", daten.nummer());
    element(xml, "TypeCode", RECHNUNG);
    zeitpunkt(xml, "IssueDateTime", daten.rechnungDatum());
    final String leistungszeitraum = daten.leistungszeitraum();
    if (leistungszeitraum != null) {
      beginne(xml, "IncludedNote");
      element(xml, "Content", "Leistungszeitraum: " + leistungszeitraum);
      xml.writeEndElement();
    }
    xml.writeEndElement();
  }

  private static void position(
      final XMLStreamWriter xml, final int zeile, final Position position, final BigDecimal satz)
      throws XMLStreamException {
    beginne(xml, "IncludedSupplyChainTradeLineItem");
    beginne(xml, "AssociatedDocumentLineDocument");
    element(xml, "LineID", Integer.toString(zeile));
    xml.writeEndElement();
    beginne(xml, "SpecifiedTradeProduct");
    element(xml, "Name", position.bezeichnung());
    xml.writeEndElement();
    beginne(xml, "SpecifiedLineTradeAgreement");
    beginne(xml, "NetPriceProductTradePrice");
    element(xml, "ChargeAmount", position.einzelpreis().toPlainString());
    xml.writeEndElement();
    xml.writeEndElement();
    beginne(xml, "SpecifiedLineTradeDelivery");
    beginne(xml, "BilledQuantity");
    xml.writeAttribute("unitCode", einheitencode(position.einheit()));
    xml.writeCharacters(position.menge().toPlainString());
    xml.writeEndElement();
    xml.writeEndElement();
    beginne(xml, "SpecifiedLineTradeSettlement");
    beginne(xml, "ApplicableTradeTax");
    element(xml, "TypeCode", UMSATZSTEUER);
    element(xml, "CategoryCode", NORMALSATZ);
    element(xml, "RateApplicablePercent", satz.toPlainString());
    xml.writeEndElement();
    beginne(xml, "SpecifiedTradeSettlementLineMonetarySummation");
    element(xml, "LineTotalAmount", betrag(position.betrag()));
    xml.writeEndElement();
    xml.writeEndElement();
    xml.writeEndElement();
  }

  /** Die Einheit als Code nach UN/ECE Rec. 20 (E6). */
  private static String einheitencode(final Einheit einheit) {
    return switch (einheit) {
      case STUNDE -> "HUR";
      case PERSONENTAG -> "DAY";
      case PAUSCHAL -> "LS";
    };
  }

  private static void vereinbarung(
      final XMLStreamWriter xml, final Verkaeufer verkaeufer, final Kaeufer kaeufer)
      throws XMLStreamException {
    beginne(xml, "ApplicableHeaderTradeAgreement");
    beginne(xml, "SellerTradeParty");
    if (verkaeufer.umsatzsteuerId() == null) {
      wennVorhanden(xml, "ID", verkaeufer.steuernummer());
    }
    element(xml, "Name", verkaeufer.name());
    anschrift(xml, verkaeufer.anschrift(), DEUTSCHLAND);
    steuerkennung(xml, "VA", verkaeufer.umsatzsteuerId());
    steuerkennung(xml, "FC", verkaeufer.steuernummer());
    xml.writeEndElement();
    beginne(xml, "BuyerTradeParty");
    element(xml, "Name", kaeufer.name());
    anschrift(xml, kaeufer.anschrift(), kaeufer.landKuerzel());
    xml.writeEndElement();
    xml.writeEndElement();
  }

  private static void anschrift(
      final XMLStreamWriter xml, final Anschrift anschrift, final String land)
      throws XMLStreamException {
    beginne(xml, "PostalTradeAddress");
    wennVorhanden(xml, "PostcodeCode", anschrift.plz());
    wennVorhanden(xml, "LineOne", anschrift.strasse());
    wennVorhanden(xml, "CityName", anschrift.ort());
    element(xml, "CountryID", land);
    xml.writeEndElement();
  }

  private static void steuerkennung(
      final XMLStreamWriter xml, final String schema, final @Nullable String kennung)
      throws XMLStreamException {
    if (kennung != null) {
      beginne(xml, "SpecifiedTaxRegistration");
      beginne(xml, "ID");
      xml.writeAttribute("schemeID", schema);
      xml.writeCharacters(kennung);
      xml.writeEndElement();
      xml.writeEndElement();
    }
  }

  private static void abrechnung(final XMLStreamWriter xml, final ERechnungsdaten daten)
      throws XMLStreamException {
    beginne(xml, "ApplicableHeaderTradeSettlement");
    element(xml, "InvoiceCurrencyCode", EURO);
    zahlungsart(xml, daten.verkaeufer());
    beginne(xml, "ApplicableTradeTax");
    element(xml, "CalculatedAmount", betrag(daten.steuer()));
    element(xml, "TypeCode", UMSATZSTEUER);
    element(xml, "BasisAmount", betrag(daten.netto()));
    element(xml, "CategoryCode", NORMALSATZ);
    element(xml, "RateApplicablePercent", daten.steuersatz().toPlainString());
    xml.writeEndElement();
    beginne(xml, "SpecifiedTradePaymentTerms");
    element(xml, "Description", daten.zahlungsbedingung());
    zeitpunkt(xml, "DueDateDateTime", daten.faelligkeit());
    xml.writeEndElement();
    summen(xml, daten);
    xml.writeEndElement();
  }

  private static void zahlungsart(final XMLStreamWriter xml, final Verkaeufer verkaeufer)
      throws XMLStreamException {
    beginne(xml, "SpecifiedTradeSettlementPaymentMeans");
    element(xml, "TypeCode", SEPA_UEBERWEISUNG);
    beginne(xml, "PayeePartyCreditorFinancialAccount");
    element(xml, "IBANID", verkaeufer.iban().elektronisch());
    element(xml, "AccountName", verkaeufer.kontoinhaber());
    xml.writeEndElement();
    final String bic = verkaeufer.bic();
    if (bic != null) {
      beginne(xml, "PayeeSpecifiedCreditorFinancialInstitution");
      element(xml, "BICID", bic);
      xml.writeEndElement();
    }
    xml.writeEndElement();
  }

  private static void summen(final XMLStreamWriter xml, final ERechnungsdaten daten)
      throws XMLStreamException {
    beginne(xml, "SpecifiedTradeSettlementHeaderMonetarySummation");
    element(xml, "LineTotalAmount", betrag(daten.netto()));
    element(xml, "TaxBasisTotalAmount", betrag(daten.netto()));
    beginne(xml, "TaxTotalAmount");
    xml.writeAttribute("currencyID", EURO);
    xml.writeCharacters(betrag(daten.steuer()));
    xml.writeEndElement();
    element(xml, "GrandTotalAmount", betrag(daten.brutto()));
    element(xml, "DuePayableAmount", betrag(daten.brutto()));
    xml.writeEndElement();
  }

  private static void zeitpunkt(final XMLStreamWriter xml, final String name, final LocalDate tag)
      throws XMLStreamException {
    beginne(xml, name);
    xml.writeStartElement("udt", "DateTimeString", UDT);
    xml.writeAttribute("format", FORMAT_102);
    xml.writeCharacters(DATUM.format(tag));
    xml.writeEndElement();
    xml.writeEndElement();
  }

  /**
   * Ein Betrag mit zwei Nachkommastellen; gerundet wird hier nichts, das hat die Rechnung getan.
   */
  private static String betrag(final BigDecimal wert) {
    return wert.setScale(CENT, RoundingMode.UNNECESSARY).toPlainString();
  }

  private static void wennVorhanden(
      final XMLStreamWriter xml, final String name, final @Nullable String wert)
      throws XMLStreamException {
    if (wert != null) {
      element(xml, name, wert);
    }
  }

  private static void element(final XMLStreamWriter xml, final String name, final String wert)
      throws XMLStreamException {
    beginne(xml, name);
    xml.writeCharacters(wert);
    xml.writeEndElement();
  }

  private static void beginne(final XMLStreamWriter xml, final String name)
      throws XMLStreamException {
    xml.writeStartElement("ram", name, RAM);
  }
}
