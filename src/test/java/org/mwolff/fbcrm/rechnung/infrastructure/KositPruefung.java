package org.mwolff.fbcrm.rechnung.infrastructure;

import de.kosit.validationtool.api.Check;
import de.kosit.validationtool.api.Configuration;
import de.kosit.validationtool.api.InputFactory;
import de.kosit.validationtool.api.Result;
import de.kosit.validationtool.impl.DefaultCheck;
import de.kosit.validationtool.impl.xml.ProcessorProvider;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmItem;

/**
 * Der Validator der KoSIT im Szenario EN 16931 CII, als Testhilfe (Plan #461, E20).
 *
 * <p>Die Konfiguration unter {@code src/test/resources/kosit/} traegt genau ein Szenario: das
 * XML-Schema CII D16B und die Schematron-Regeln der CEN, ohne die Regeln von XRechnung. Herkunft
 * und Pruefsummen nennt die {@code README.md} dort.
 *
 * <p>Geladen wird einmal je Testlauf: Das Uebersetzen der Schematron-Regeln dauert Sekunden, eine
 * Pruefung danach nur noch Bruchteile davon.
 *
 * <p>Ein Fehler ist jede Meldung der Stufe {@code error} im Pruefbericht. Die Stufe {@code fatal}
 * der CEN-Regeln steht dort als {@code error}, eine Warnung zaehlt nicht (fachliche Quelle #460,
 * Kriterium 2). Dazu kommt jeder Verarbeitungsfehler, und eine Ablehnung ohne Meldung — etwa weil
 * kein Szenario passt — ist ebenfalls ein Fehler: Eine Pruefung, die nichts geprueft hat, darf
 * nicht als bestanden gelten.
 */
final class KositPruefung {

  private static final String BERICHT = "http://www.xoev.de/de/validator/varl/1";

  private static final Processor SAXON = ProcessorProvider.getProcessor();

  private static final Check PRUEFUNG = geladen();

  /**
   * Jede Meldung der Stufe {@code error}, als „Code: Text". Gelesen wird der Bericht mit XPath von
   * Saxon und nicht ueber {@code getReportDocument()}: Die DOM-Huelle, die Saxon dort liefert,
   * findet mit {@code getElementsByTagNameNS} keine einzige Meldung — eine Pruefung darueber
   * meldete jede XML als fehlerfrei.
   */
  private static final XPathExecutable FEHLERMELDUNGEN = uebersetzt();

  private KositPruefung() {}

  /**
   * Die Fehler, die der Validator zu einer XML meldet.
   *
   * @param xml die XML als Bytefolge
   * @return je Fehler eine Zeile „Code: Text"; leer, wenn die XML die Pruefung besteht
   */
  static List<String> fehler(final byte[] xml) {
    final Result ergebnis = PRUEFUNG.checkInput(InputFactory.read(xml, "e-rechnung.xml"));
    final List<String> fehler = new ArrayList<>(ergebnis.getProcessingErrors());
    try {
      final XPathSelector auswahl = FEHLERMELDUNGEN.load();
      auswahl.setContextItem(ergebnis.getReport());
      for (final XdmItem meldung : auswahl.evaluate()) {
        fehler.add(meldung.getStringValue());
      }
    } catch (final SaxonApiException unlesbar) {
      throw new IllegalStateException("Der Pruefbericht ist nicht lesbar.", unlesbar);
    }
    if (fehler.isEmpty() && !ergebnis.isAcceptable()) {
      fehler.add("abgelehnt ohne Meldung: kein passendes Szenario oder keine Pruefung");
    }
    return fehler;
  }

  private static Check geladen() {
    final URI szenarien = ressource("/kosit/scenarios.xml");
    final URI ablage = ressource("/kosit/");
    final Configuration konfiguration = Configuration.load(szenarien, ablage).build(SAXON);
    return new DefaultCheck(SAXON, konfiguration);
  }

  private static XPathExecutable uebersetzt() {
    final XPathCompiler uebersetzer = SAXON.newXPathCompiler();
    uebersetzer.declareNamespace("rep", BERICHT);
    try {
      return uebersetzer.compile(
          "//rep:message[@level = 'error']/concat(@code, ': ', normalize-space(.))");
    } catch (final SaxonApiException unmoeglich) {
      throw new IllegalStateException("Der Ausdruck ist fest und gueltig.", unmoeglich);
    }
  }

  private static URI ressource(final String pfad) {
    try {
      return Objects.requireNonNull(KositPruefung.class.getResource(pfad), pfad).toURI();
    } catch (final URISyntaxException unmoeglich) {
      throw new IllegalStateException(pfad, unmoeglich);
    }
  }
}
