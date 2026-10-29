package org.mwolff.fbcrm.rechnung.infrastructure;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Macht aus dem geschriebenen Beleg eine E-Rechnung: PDF/A-3b mit eingebetteter {@code
 * factur-x.xml} (Plan #461, E3, E4).
 *
 * <p>Drei Teile kommen zu den Seiten hinzu, die {@link PdfBoxDrucker} geschrieben hat, und keiner
 * davon ist zu sehen:
 *
 * <ol>
 *   <li>der <b>OutputIntent sRGB</b>: Die Farben des Belegs sind DeviceRGB, und PDF/A verlangt
 *       dafuer ein Farbprofil. Es ist das sRGB-Profil des JDK, ohne fremde Datei im Repository;
 *   <li>die <b>Metadaten</b> in XMP aus {@link ERechnungsmetadaten}, mit dem Titel „Rechnung
 *       &lt;Nummer&gt;", den auch das Info-Woerterbuch traegt;
 *   <li>die <b>XML</b> als eingebettete Datei {@code factur-x.xml}, eingetragen unter {@code
 *       /Names/EmbeddedFiles} und im Katalog unter {@code /AF}, mit {@code
 *       AFRelationship=Alternative}: Sie ist dieselbe Rechnung in maschinenlesbarer Form.
 * </ol>
 *
 * <p>Die Kennung im Trailer schreibt PDFBox beim Speichern selbst.
 */
final class ERechnungshuelle {

  /** Der Name, unter dem ZUGFeRD die XML im Dokument erwartet. */
  static final String DATEINAME = "factur-x.xml";

  /** Die Kennung des Farbraums im OutputIntent. */
  static final String SRGB = "sRGB IEC61966-2.1";

  private static final String MIME_XML = "text/xml";

  /**
   * Ein Datum, wie PDF es schreibt, in UTC: „D:20261029093000Z". Geschrieben wird es von hier aus
   * und nicht ueber {@code PDEmbeddedFile.setModDate}: Dessen {@code Calendar} ist im Projekt
   * gesperrt (CLAUDE-java.md §6.2), {@code java.time} kennt dieselbe Angabe.
   */
  private static final DateTimeFormatter PDF_DATUM =
      DateTimeFormatter.ofPattern("'D:'yyyyMMddHHmmss'Z'").withZone(ZoneOffset.UTC);

  private static final String RSM = "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100";

  private static final String RAM =
      "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100";

  /** Das Merkmal des Parsers, das jede DOCTYPE-Deklaration abweist (Schutz vor XXE). */
  private static final String OHNE_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl";

  private ERechnungshuelle() {}

  /**
   * Legt OutputIntent, Metadaten und XML in das Dokument.
   *
   * @param dokument der geschriebene Beleg, noch nicht gespeichert
   * @param cii die XML der E-Rechnung in UTF-8
   * @param jetzt der Augenblick des Schreibens, als Aenderungsdatum der eingebetteten Datei
   * @throws IOException wenn PDFBox ein Teil nicht schreiben kann
   */
  static void umhuelle(final PDDocument dokument, final byte[] cii, final Instant jetzt)
      throws IOException {
    final PDDocumentCatalog katalog = dokument.getDocumentCatalog();
    katalog.addOutputIntent(farbraum(dokument));
    final String titel = "Rechnung " + nummer(cii);
    dokument.getDocumentInformation().setTitle(titel);
    final PDMetadata metadaten = new PDMetadata(dokument);
    metadaten.importXMPMetadata(ERechnungsmetadaten.xmp(titel));
    katalog.setMetadata(metadaten);
    eingebettet(dokument, katalog, cii, jetzt);
  }

  private static PDOutputIntent farbraum(final PDDocument dokument) throws IOException {
    final byte[] profil = ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData();
    final PDOutputIntent absicht = new PDOutputIntent(dokument, new ByteArrayInputStream(profil));
    absicht.setInfo(SRGB);
    absicht.setOutputCondition(SRGB);
    absicht.setOutputConditionIdentifier(SRGB);
    absicht.setRegistryName("http://www.color.org");
    return absicht;
  }

  private static void eingebettet(
      final PDDocument dokument,
      final PDDocumentCatalog katalog,
      final byte[] cii,
      final Instant jetzt)
      throws IOException {
    final PDEmbeddedFile datei = new PDEmbeddedFile(dokument, new ByteArrayInputStream(cii));
    datei.setSubtype(MIME_XML);
    final COSDictionary angaben = new COSDictionary();
    angaben.setString(COSName.MOD_DATE, PDF_DATUM.format(jetzt));
    datei.getCOSObject().setItem(COSName.PARAMS, angaben);
    datei.setSize(cii.length);
    final PDComplexFileSpecification beschreibung = new PDComplexFileSpecification();
    beschreibung.setFile(DATEINAME);
    beschreibung.setFileUnicode(DATEINAME);
    beschreibung.setFileDescription("Rechnungsdaten nach EN 16931 (ZUGFeRD, Factur-X)");
    beschreibung.setEmbeddedFile(datei);
    beschreibung.setEmbeddedFileUnicode(datei);
    beschreibung.getCOSObject().setName(COSName.getPDFName("AFRelationship"), "Alternative");

    final PDEmbeddedFilesNameTreeNode baum = new PDEmbeddedFilesNameTreeNode();
    baum.setNames(Map.of(DATEINAME, beschreibung));
    final PDDocumentNameDictionary namen = new PDDocumentNameDictionary(katalog);
    namen.setEmbeddedFiles(baum);
    katalog.setNames(namen);

    final COSArray anlagen = new COSArray();
    anlagen.add(beschreibung);
    katalog.getCOSObject().setItem(COSName.AF, anlagen);
  }

  /** Die Rechnungsnummer (BT-1) aus der XML; aus ihr entsteht der Titel des Dokuments. */
  private static String nummer(final byte[] cii) throws IOException {
    try {
      final DocumentBuilderFactory fabrik = DocumentBuilderFactory.newInstance();
      fabrik.setNamespaceAware(true);
      // Die XML kommt vom eigenen Schreiber; gelesen wird sie trotzdem ohne DOCTYPE, damit keine
      // externe Entitaet nachgeladen werden kann.
      fabrik.setFeature(OHNE_DOCTYPE, true);
      final NodeList kopf =
          fabrik
              .newDocumentBuilder()
              .parse(new ByteArrayInputStream(cii))
              .getElementsByTagNameNS(RSM, "ExchangedDocument");
      final NodeList kennung = ((Element) kopf.item(0)).getElementsByTagNameNS(RAM, "ID");
      return kennung.item(0).getTextContent();
    } catch (final ParserConfigurationException | SAXException unlesbar) {
      throw new IOException("Die E-Rechnung ist keine lesbare XML.", unlesbar);
    }
  }
}
