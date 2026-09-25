package org.mwolff.fbcrm.vorgang.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.common.Uploadgrenze;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.mwolff.fbcrm.vorgang.application.EintragAendernUseCase;
import org.mwolff.fbcrm.vorgang.application.EintragDaten;
import org.mwolff.fbcrm.vorgang.application.EintragHinzufuegenUseCase;
import org.mwolff.fbcrm.vorgang.application.EintragNichtGefunden;
import org.mwolff.fbcrm.vorgang.application.VorgangNichtGefunden;
import org.mwolff.fbcrm.vorgang.domain.Eintrag;
import org.mwolff.fbcrm.vorgang.domain.Eintragsart;
import org.mwolff.fbcrm.vorgang.domain.Herkunft;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

/**
 * Die Uebersetzung zwischen Anfrage und Anwendungsfall fuer die Schreibwege der Historie.
 *
 * <p>Gefahren wird durchgaengig ueber MockMvc, weil hier die <b>Form</b> der Anfrage die Aussage
 * ist: Formulardaten samt Datei beim Hinzufuegen, JSON beim Aendern, und die Feldmeldungen, die
 * durch den echten {@link GlobalExceptionHandler} als {@code fieldErrors} hinausgehen.
 *
 * <p>Der Pruefer bekommt eine feste Uhr ({@link Pruefer}) — ohne sie waere „61 Sekunden voraus"
 * eine Aussage ueber den Zeitpunkt des Testlaufs.
 */
@ExtendWith(MockitoExtension.class)
class EintragControllerTest {

  private static final Instant JETZT = Instant.parse("2026-09-12T09:00:00Z");
  private static final String GESTERN = "2026-09-11T14:30:00Z";
  private static final String PFAD = "/api/vorgaenge/4/eintraege";

  @Mock private EintragHinzufuegenUseCase hinzufuegen;
  @Mock private EintragAendernUseCase aendern;
  @Captor private ArgumentCaptor<EintragDaten> daten;

  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new EintragController(hinzufuegen, aendern))
            .setValidator(
                new SpringValidatorAdapter(
                    Pruefer.mitUhr(Clock.fixed(JETZT, ZoneOffset.UTC)).getValidator()))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private static RequestBuilder kommentar(final String text) {
    return kommentar(text, GESTERN);
  }

  private static RequestBuilder kommentar(final String text, final String geschehenAm) {
    return multipart(PFAD)
        .param("art", "KOMMENTAR")
        .param("geschehenAm", geschehenAm)
        .param("text", text);
  }

  private static RequestBuilder anhang(final MockMultipartFile datei) {
    return multipart(PFAD).file(datei).param("art", "ANHANG").param("geschehenAm", GESTERN);
  }

  private static MockMultipartFile datei(final String name) {
    return new MockMultipartFile("datei", name, "application/pdf", new byte[] {1, 2, 3});
  }

  private static String aenderung(final String text, final String geschehenAm) {
    return "{\"text\":" + text + ",\"geschehenAm\":\"" + geschehenAm + "\"}";
  }

  private static Eintrag angelegt() {
    return Eintrag.kommentar(4L, "Angerufen", JETZT, Herkunft.VON_HAND, JETZT);
  }

  @Test
  void hinzufuegen_givenAComment_thenAnswersCreated() throws Exception {
    // Given — Kriterium 13.
    when(hinzufuegen.hinzufuegen(anyLong(), daten.capture())).thenReturn(angelegt());

    // When / Then
    mockMvc.perform(kommentar("Angerufen")).andExpect(status().isCreated());
  }

  @Test
  void hinzufuegen_givenAComment_thenPassesArtTextAndMomentToTheUseCase() throws Exception {
    // Given
    when(hinzufuegen.hinzufuegen(eq(4L), daten.capture())).thenReturn(angelegt());

    // When
    mockMvc.perform(kommentar("Angerufen"));

    // Then
    assertThat(daten.getValue())
        .extracting(EintragDaten::art, EintragDaten::text, EintragDaten::geschehenAm)
        .containsExactly(Eintragsart.KOMMENTAR, "Angerufen", Instant.parse(GESTERN));
  }

  @Test
  void hinzufuegen_givenAComment_thenPassesNoFile() throws Exception {
    // Given — ein Kommentar traegt keine Dateiangaben.
    when(hinzufuegen.hinzufuegen(anyLong(), daten.capture())).thenReturn(angelegt());

    // When
    mockMvc.perform(kommentar("Angerufen"));

    // Then
    assertThat(daten.getValue())
        .extracting(EintragDaten::dateiName, EintragDaten::dateiGroesse, EintragDaten::inhalt)
        .containsExactly(null, Long.valueOf(0L), null);
  }

  @Test
  void hinzufuegen_givenAnAttachment_thenPassesNameAndSizeToTheUseCase() throws Exception {
    // Given — Kriterium 15 zeigt spaeter Name und Groesse.
    when(hinzufuegen.hinzufuegen(eq(4L), daten.capture())).thenReturn(angelegt());

    // When
    mockMvc.perform(anhang(datei("Angebot.pdf")));

    // Then
    assertThat(daten.getValue())
        .extracting(EintragDaten::art, EintragDaten::dateiName, EintragDaten::dateiGroesse)
        .containsExactly(Eintragsart.ANHANG, "Angebot.pdf", Long.valueOf(3L));
  }

  @Test
  void hinzufuegen_givenAnAttachment_thenPassesAnOpenStream() throws Exception {
    // Given — der Anwendungsfall reicht ihn an den Objektspeicher weiter und schliesst ihn.
    when(hinzufuegen.hinzufuegen(anyLong(), daten.capture())).thenReturn(angelegt());

    // When
    mockMvc.perform(anhang(datei("Angebot.pdf")));

    // Then
    assertThat(daten.getValue().inhalt()).isNotNull();
  }

  @Test
  void hinzufuegen_givenACommentWithAFile_thenIgnoresTheFile() throws Exception {
    // Given — die gewaehlte Art entscheidet, nicht das mitgeschickte Feld: Wer die Art auf
    // Kommentar stellt und eine Datei im Formular stehen laesst, bekommt keinen Anhang.
    when(hinzufuegen.hinzufuegen(anyLong(), daten.capture())).thenReturn(angelegt());

    // When
    mockMvc.perform(
        multipart(PFAD)
            .file(datei("Angebot.pdf"))
            .param("art", "KOMMENTAR")
            .param("geschehenAm", GESTERN)
            .param("text", "Angerufen"));

    // Then
    assertThat(daten.getValue())
        .extracting(EintragDaten::art, EintragDaten::dateiName, EintragDaten::inhalt)
        .containsExactly(Eintragsart.KOMMENTAR, null, null);
  }

  @Test
  void hinzufuegen_givenAFileNameWithAPath_thenPassesOnlyTheName() throws Exception {
    // Given — E13: der Name kommt von aussen und waere im Schluessel eine Pfadangabe.
    when(hinzufuegen.hinzufuegen(anyLong(), daten.capture())).thenReturn(angelegt());

    // When
    mockMvc.perform(anhang(datei("../../x.pdf")));

    // Then
    assertThat(daten.getValue().dateiName()).isEqualTo("x.pdf");
  }

  @Test
  void hinzufuegen_givenACommentWithoutText_thenNamesTheFieldInTheProblemDetail() throws Exception {
    // When / Then — Kriterium 13.
    mockMvc
        .perform(kommentar("   "))
        .andExpect(jsonPath("$.fieldErrors.text[0]").value(EintragConstraint.TEXT_FEHLT));
  }

  @Test
  void hinzufuegen_givenAnAttachmentWithoutAFile_thenNamesTheFieldInTheProblemDetail()
      throws Exception {
    // When / Then — Kriterium 13.
    mockMvc
        .perform(multipart(PFAD).param("art", "ANHANG").param("geschehenAm", GESTERN))
        .andExpect(jsonPath("$.fieldErrors.datei[0]").value(EintragConstraint.DATEI_FEHLT));
  }

  @Test
  void hinzufuegen_givenAFileBeyondTheLimit_thenTheMessageNamesTheLimit() throws Exception {
    // Given — Kriterium 18.
    final MockMultipartFile gross =
        new MockMultipartFile("datei", "gross.pdf", "application/pdf", new byte[] {1}) {
          @Override
          public long getSize() {
            return Uploadgrenze.MAX_BYTE + 1L;
          }
        };

    // When / Then
    mockMvc
        .perform(anhang(gross))
        .andExpect(jsonPath("$.fieldErrors.datei[0]").value(Uploadgrenze.MELDUNG));
  }

  @Test
  void hinzufuegen_givenAMomentInTheFuture_thenNamesTheFieldInTheProblemDetail() throws Exception {
    // When / Then — Kriterium 14.
    mockMvc
        .perform(kommentar("Angerufen", JETZT.plusSeconds(61).toString()))
        .andExpect(jsonPath("$.fieldErrors.geschehenAm[0]").value(EintragConstraint.ZUKUNFT));
  }

  @Test
  void hinzufuegen_givenAnUnknownVorgang_thenAnswersNotFound() throws Exception {
    // Given
    when(hinzufuegen.hinzufuegen(anyLong(), daten.capture())).thenThrow(new VorgangNichtGefunden());

    // When / Then
    mockMvc.perform(kommentar("Angerufen")).andExpect(status().isNotFound());
  }

  @Test
  void aendern_thenAnswersWithoutContent() throws Exception {
    // When / Then — E25: die Schreibwege antworten ohne Rumpf.
    mockMvc
        .perform(
            put(PFAD + "/21")
                .contentType(MediaType.APPLICATION_JSON)
                .content(aenderung("\"Doch geschrieben\"", GESTERN)))
        .andExpect(status().isNoContent());
  }

  @Test
  void aendern_thenPassesBothIdsTextAndMomentToTheUseCase() throws Exception {
    // When — Kriterium 18.
    mockMvc.perform(
        put(PFAD + "/21")
            .contentType(MediaType.APPLICATION_JSON)
            .content(aenderung("\"Doch geschrieben\"", GESTERN)));

    // Then
    verify(aendern).aendern(4L, 21L, "Doch geschrieben", Instant.parse(GESTERN));
  }

  @Test
  void aendern_givenNoText_thenNamesTheFieldInTheProblemDetail() throws Exception {
    // When / Then — die Meldung steht am Feld.
    mockMvc
        .perform(
            put(PFAD + "/21")
                .contentType(MediaType.APPLICATION_JSON)
                .content(aenderung("\"   \"", GESTERN)))
        .andExpect(jsonPath("$.fieldErrors.text").isArray());
  }

  @Test
  void aendern_givenAMomentInTheFuture_thenNamesTheFieldInTheProblemDetail() throws Exception {
    // When / Then — Kriterium 14 gilt auch beim Aendern.
    mockMvc
        .perform(
            put(PFAD + "/21")
                .contentType(MediaType.APPLICATION_JSON)
                .content(aenderung("\"Doch geschrieben\"", JETZT.plusSeconds(61).toString())))
        .andExpect(jsonPath("$.fieldErrors.geschehenAm[0]").value(EintragConstraint.ZUKUNFT));
  }

  @Test
  void aendern_givenAnUnknownEintrag_thenAnswersNotFound() throws Exception {
    // Given
    doThrow(new EintragNichtGefunden())
        .when(aendern)
        .aendern(4L, 4711L, "Doch geschrieben", Instant.parse(GESTERN));

    // When / Then
    mockMvc
        .perform(
            put(PFAD + "/4711")
                .contentType(MediaType.APPLICATION_JSON)
                .content(aenderung("\"Doch geschrieben\"", GESTERN)))
        .andExpect(status().isNotFound());
  }
}
