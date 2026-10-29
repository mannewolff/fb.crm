package org.mwolff.fbcrm.rechnung.infrastructure;

import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.xml.transform.TransformerException;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.PDFAExtensionSchema;
import org.apache.xmpbox.schema.PDFAIdentificationSchema;
import org.apache.xmpbox.schema.XMPSchema;
import org.apache.xmpbox.type.ArrayProperty;
import org.apache.xmpbox.type.BadFieldValueException;
import org.apache.xmpbox.type.Cardinality;
import org.apache.xmpbox.type.DefinedStructuredType;
import org.apache.xmpbox.type.TypeMapping;
import org.apache.xmpbox.xml.XmpSerializer;
import org.mwolff.fbcrm.common.ExcludeFromJacocoGeneratedReport;

/**
 * Die XMP-Metadaten der E-Rechnung (Plan #461, E4): {@code pdfaid} mit Teil 3 und Stufe B, der
 * Titel in Dublin Core und das Erweiterungsschema {@code fx} von Factur-X samt der Beschreibung,
 * die PDF/A fuer jedes Schema ausserhalb seiner eigenen Liste verlangt.
 *
 * <p>Eine eigene Klasse neben {@link ERechnungshuelle}, weil hier allein xmpbox spricht: Die Huelle
 * legt die Bytes nur noch in den Katalog.
 */
final class ERechnungsmetadaten {

  /** Der Namensraum des Erweiterungsschemas von Factur-X und ZUGFeRD 2. */
  static final String FACTUR_X = "urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#";

  /** Das Kuerzel des Erweiterungsschemas. */
  static final String FX = "fx";

  private static final String SCHEMA_NS = "http://www.aiim.org/pdfa/ns/schema#";

  private static final String SCHEMA_PREFIX = "pdfaSchema";

  private static final String PROPERTY_NS = "http://www.aiim.org/pdfa/ns/property#";

  private static final String PROPERTY_PREFIX = "pdfaProperty";

  /** Die vier Angaben des Schemas {@code fx}, in der Reihenfolge der Spezifikation. */
  private static final List<Angabe> FX_ANGABEN =
      List.of(
          new Angabe(
              "DocumentFileName",
              ERechnungshuelle.DATEINAME,
              "The name of the embedded XML document"),
          new Angabe(
              "DocumentType",
              "INVOICE",
              "The type of the hybrid document in capital letters, e.g. INVOICE"),
          new Angabe(
              "Version",
              "1.0",
              "The actual version of the standard applying to the embedded XML document"),
          new Angabe(
              "ConformanceLevel",
              "EN 16931",
              "The conformance level of the embedded XML document"));

  private ERechnungsmetadaten() {}

  /*
   * Ausgenommen ist allein dieser Rahmen, nicht die Arbeit darin: Die Stufe „B" ist fest und
   * gueltig, und der Serialisierer schreibt in einen ByteArrayOutputStream — beide Ausnahmen sind
   * nicht erreichbar, und ein Test dafuer muesste die Bibliothek verbiegen. Methodengenaue Ausnahme
   * nach CLAUDE-java.md §5.4, wie bei PdfBoxDrucker.drucke; geschrieben() bleibt in Abdeckung und
   * Mutationstest, geprueft von PdfBoxDruckerTest.
   */
  /**
   * Die Metadaten als XMP-Paket.
   *
   * @param titel der Titel des Dokuments, „Rechnung &lt;Nummer&gt;"
   * @return das serialisierte XMP-Paket
   */
  @ExcludeFromJacocoGeneratedReport
  static byte[] xmp(final String titel) {
    try {
      return geschrieben(titel);
    } catch (final BadFieldValueException | TransformerException nichtErreichbar) {
      throw new IllegalStateException(
          "Die Metadaten liessen sich nicht schreiben.", nichtErreichbar);
    }
  }

  private static byte[] geschrieben(final String titel)
      throws BadFieldValueException, TransformerException {
    final XMPMetadata xmp = XMPMetadata.createXMPMetadata();
    xmp.createAndAddDublinCoreSchema().setTitle(titel);
    final PDFAIdentificationSchema kennung = xmp.createAndAddPDFAIdentificationSchema();
    kennung.setPart(3);
    kennung.setConformance("B");
    final XMPSchema fx = new XMPSchema(xmp, FACTUR_X, FX);
    for (final Angabe angabe : FX_ANGABEN) {
      fx.setTextPropertyValue(angabe.name(), angabe.wert());
    }
    xmp.addSchema(fx);
    erweiterung(xmp);
    final ByteArrayOutputStream ausgabe = new ByteArrayOutputStream();
    new XmpSerializer().serialize(xmp, ausgabe, true);
    return ausgabe.toByteArray();
  }

  /** Die Beschreibung des Schemas {@code fx}, die PDF/A fuer jedes fremde Schema verlangt. */
  private static void erweiterung(final XMPMetadata xmp) {
    final TypeMapping typen = xmp.getTypeMapping();
    // Die Praefixe pdfaSchema und pdfaProperty erklaert der Serialisierer selbst an rdf:RDF, weil
    // jede Eigenschaft ihren Namensraum mitbringt; ein addNamespace am Schema waere wirkungslos.
    final PDFAExtensionSchema erweiterung = xmp.createAndAddPDFAExtensionSchemaWithDefaultNS();
    final ArrayProperty schemata =
        erweiterung.createArrayProperty(PDFAExtensionSchema.SCHEMAS, Cardinality.Bag);
    final DefinedStructuredType schema =
        new DefinedStructuredType(xmp, SCHEMA_NS, SCHEMA_PREFIX, null);
    schema.addProperty(
        typen.createText(SCHEMA_NS, SCHEMA_PREFIX, "schema", "Factur-X PDFA Extension Schema"));
    schema.addProperty(typen.createURI(SCHEMA_NS, SCHEMA_PREFIX, "namespaceURI", FACTUR_X));
    schema.addProperty(typen.createText(SCHEMA_NS, SCHEMA_PREFIX, "prefix", FX));
    final ArrayProperty eigenschaften =
        typen.createArrayProperty(SCHEMA_NS, SCHEMA_PREFIX, "property", Cardinality.Seq);
    for (final Angabe angabe : FX_ANGABEN) {
      final DefinedStructuredType eigenschaft =
          new DefinedStructuredType(xmp, PROPERTY_NS, PROPERTY_PREFIX, null);
      eigenschaft.addProperty(
          typen.createText(PROPERTY_NS, PROPERTY_PREFIX, "name", angabe.name()));
      eigenschaft.addProperty(typen.createText(PROPERTY_NS, PROPERTY_PREFIX, "valueType", "Text"));
      eigenschaft.addProperty(
          typen.createChoice(PROPERTY_NS, PROPERTY_PREFIX, "category", "external"));
      eigenschaft.addProperty(
          typen.createText(PROPERTY_NS, PROPERTY_PREFIX, "description", angabe.beschreibung()));
      eigenschaften.getContainer().addProperty(eigenschaft);
    }
    schema.addProperty(eigenschaften);
    schemata.getContainer().addProperty(schema);
    erweiterung.addProperty(schemata);
  }

  /** Eine Angabe des Schemas {@code fx}: ihr Name, ihr Wert und ihre Beschreibung. */
  private record Angabe(String name, String wert, String beschreibung) {}
}
