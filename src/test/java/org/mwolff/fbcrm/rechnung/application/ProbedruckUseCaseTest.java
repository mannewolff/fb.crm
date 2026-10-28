package org.mwolff.fbcrm.rechnung.application;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngabenRepository;
import org.mwolff.fbcrm.rechnung.domain.Briefbogen;
import org.mwolff.fbcrm.rechnung.domain.BriefbogenRepository;
import org.mwolff.fbcrm.rechnung.domain.Briefpapierblatt;
import org.mwolff.fbcrm.rechnung.domain.Briefpapierspeicher;
import org.mwolff.fbcrm.rechnung.domain.Randmasse;
import org.mwolff.fbcrm.rechnung.infrastructure.EchterDrucker;

/**
 * Der Probedruck auf dem hinterlegten Briefpapier (fachliche Quelle #361, Kriterium 9; Plan #363,
 * E13 und E14).
 *
 * <p>Satz und Drucker sind echt, Bestand, Speicher und eigene Angaben gemockt: Was zaehlt, steht im
 * fertigen PDF — welches Blatt unter welcher Seite liegt, dass jede Seite das schraege „Muster"
 * traegt und welche eigenen Angaben eingedruckt sind. Dass keine Rechnung entsteht und keine Nummer
 * gezogen wird, zeigt {@code BriefpapierIT} gegen den echten Bestand; hier kennt der Anwendungsfall
 * beides gar nicht erst.
 */
@ExtendWith(MockitoExtension.class)
class ProbedruckUseCaseTest {

  private static final String SCHLUESSEL = "briefpapier/kopf.pdf";

  /** Die Raender aus Kriterium 15 in Zehntel-Millimetern. */
  private static final Randmasse VORGABE = new Randmasse(250, 200, 985, 200, 200, 200);

  private static final Briefpapierblatt BLATT =
      new Briefpapierblatt(SCHLUESSEL, "kopf.pdf", 4_321L, 2);

  /** Hellgrau 210/255 als Fuellfarbe im Inhaltsstrom. */
  private static final String HELLGRAU = "0.82353 0.82353 0.82353 rg";

  @Mock private BriefbogenRepository bestand;
  @Mock private Briefpapierspeicher speicher;
  @Mock private EigeneAngabenRepository eigeneAngaben;

  private final Mitschrift drucker = new Mitschrift();

  private ProbedruckUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new ProbedruckUseCase(bestand, speicher, eigeneAngaben, drucker);
  }

  private static EigeneAngaben vollstaendig() {
    return new EigeneAngaben(
        "Max Mustermann",
        "AGILE COACH",
        new Anschrift("Muster Str. 34a", "28197", "Bremen", null),
        "info@mmuster.org",
        "0173 0000 000",
        "mmuster.org",
        "12/345/67890",
        "DE 01010101010",
        null,
        null,
        null,
        "MLP Bank Heidelberg");
  }

  private static EigeneAngaben leer() {
    return new EigeneAngaben(
        null,
        null,
        new Anschrift(null, null, null, null),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private void hinterlegt(final EigeneAngaben angaben) throws IOException {
    when(bestand.lies()).thenReturn(new Briefbogen(BLATT, true, true, VORGABE));
    when(speicher.lies(SCHLUESSEL))
        .thenReturn(briefpapier("Briefkopf Blatt eins", "Briefkopf Blatt zwei"));
    when(eigeneAngaben.lies()).thenReturn(angaben);
  }

  /** Ein Briefpapier, dessen Blaetter je ihre Aufschrift als Text tragen. */
  private static byte[] briefpapier(final String... aufschriften) throws IOException {
    try (PDDocument dokument = new PDDocument()) {
      final PDType1Font schrift = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      for (final String aufschrift : aufschriften) {
        final PDPage blatt = new PDPage(PDRectangle.A4);
        dokument.addPage(blatt);
        try (PDPageContentStream inhalt = new PDPageContentStream(dokument, blatt)) {
          inhalt.beginText();
          inhalt.setFont(schrift, 9);
          inhalt.newLineAtOffset(400, 800);
          inhalt.showText(aufschrift);
          inhalt.endText();
        }
      }
      final ByteArrayOutputStream ausgabe = new ByteArrayOutputStream();
      dokument.save(ausgabe);
      return ausgabe.toByteArray();
    }
  }

  private static int seiten(final byte[] pdf) throws IOException {
    try (PDDocument dokument = Loader.loadPDF(pdf)) {
      return dokument.getNumberOfPages();
    }
  }

  private static String ausgelesen(final byte[] pdf, final int seite) throws IOException {
    try (PDDocument dokument = Loader.loadPDF(pdf)) {
      final PDFTextStripper leser = new PDFTextStripper();
      leser.setStartPage(seite);
      leser.setEndPage(seite);
      return leser.getText(dokument);
    }
  }

  /** Der Inhaltsstrom der Seite, entpackt — dort stehen Drehung und Farbe des Musters. */
  private static String inhaltsstrom(final byte[] pdf, final int seite) throws IOException {
    try (PDDocument dokument = Loader.loadPDF(pdf);
        InputStream strom = dokument.getPage(seite - 1).getContents()) {
      return new String(strom.readAllBytes(), ISO_8859_1);
    }
  }

  private List<String> texte() {
    return drucker.elemente.stream()
        .filter(Druckelement.Text.class::isInstance)
        .map(Druckelement.Text.class::cast)
        .map(Druckelement.Text::text)
        .toList();
  }

  @Test
  void probedruck_givenZweiseitigesBriefpapier_thenProbedruckVorDerErstenRechnung()
      throws IOException {
    // Given — Szenario „Probedruck vor der ersten Rechnung".
    hinterlegt(vollstaendig());

    // When
    final byte[] pdf = useCase.drucke();

    // Then — eine Musterrechnung mit erfundenem Empfaenger und mindestens einer Folgeseite.
    final int seiten = seiten(pdf);
    assertThat(seiten).isGreaterThanOrEqualTo(2);
    assertThat(ausgelesen(pdf, 1))
        .contains("Briefkopf Blatt eins")
        .doesNotContain("Briefkopf Blatt zwei")
        .contains("Beispiel GmbH");
    for (int seite = 2; seite <= seiten; seite++) {
      assertThat(ausgelesen(pdf, seite))
          .contains("Briefkopf Blatt zwei")
          .doesNotContain("Briefkopf Blatt eins");
    }
    // Then — jede Seite traegt das schraege „Muster" in Hellgrau.
    for (int seite = 1; seite <= seiten; seite++) {
      assertThat(inhaltsstrom(pdf, seite))
          .contains(HELLGRAU)
          .contains("(Muster) Tj")
          .containsPattern("0\\.\\d+ 0\\.\\d+ -0\\.\\d+ 0\\.\\d+ \\d+ \\d+ Tm");
    }
    assertThat(
            drucker.elemente.stream()
                .filter(Druckelement.Schraegtext.class::isInstance)
                .map(Druckelement.Schraegtext.class::cast)
                .map(Druckelement.Schraegtext::seite))
        .containsExactlyElementsOf(IntStream.rangeClosed(1, seiten).boxed().toList());
  }

  @Test
  void probedruck_givenOhneBriefpapier_thenNichtGefunden() {
    // Given — Kriterium 9, zweiter Satz.
    when(bestand.lies()).thenReturn(new Briefbogen(null, true, true, VORGABE));

    // When / Then
    assertThatThrownBy(useCase::drucke).isInstanceOf(BriefpapierNichtGefunden.class);
    verifyNoInteractions(speicher, eigeneAngaben);
    assertThat(drucker.elemente).isEmpty();
  }

  @Test
  void probedruck_givenLeereEigeneAngaben_thenNurVorhandeneZeilen() throws IOException {
    // Given — Kriterium 9, vierter Satz: kein Name, keine Steuernummer, keine USt-IdNr., keine
    // Bankverbindung.
    hinterlegt(leer());

    // When
    final byte[] pdf = useCase.drucke();

    // Then — der Probedruck entsteht; Absenderzeile, Steuer- und Bankzeile und Name fehlen.
    assertThat(seiten(pdf)).isGreaterThanOrEqualTo(2);
    assertThat(texte())
        .contains(Belegschreibweisen.GRUSS)
        .noneMatch(text -> text.contains("USt-IdNr."))
        .noneMatch(text -> text.contains("Steuernummer"))
        .noneMatch(text -> text.contains("Bankverbindung"))
        .noneMatch(text -> text.contains(" · "));
    assertThat(texte().getLast()).isEqualTo(Belegschreibweisen.GRUSS);
  }

  @Test
  void probedruck_thenEchteEigeneAngabenInAbsenderUndSteuerBankzeile() throws IOException {
    // Given
    hinterlegt(vollstaendig());

    // When
    final byte[] pdf = useCase.drucke();

    // Then — die echten eigenen Angaben; die USt-IdNr. steht vor der Steuernummer.
    final String zeile = "USt-IdNr. DE 01010101010 · Bankverbindung: MLP Bank Heidelberg";
    assertThat(ausgelesen(pdf, 1))
        .contains("Max Mustermann · Muster Str. 34a · 28197 Bremen")
        .contains(zeile);
    for (int seite = 2; seite <= seiten(pdf); seite++) {
      assertThat(ausgelesen(pdf, seite)).contains(zeile);
    }
    assertThat(drucker.briefpapier).isNotNull();
  }

  /** Der echte Drucker, der mitschreibt, was er bekam. */
  private static final class Mitschrift implements Belegdrucker {

    private final Belegdrucker echt = EchterDrucker.pdfBox();
    private final List<Druckelement> elemente = new ArrayList<>();
    private byte @Nullable [] briefpapier;

    @Override
    public byte[] drucke(final List<Druckelement> elemente, final byte @Nullable [] briefpapier) {
      this.elemente.addAll(elemente);
      this.briefpapier = briefpapier;
      return echt.drucke(elemente, briefpapier);
    }
  }
}
