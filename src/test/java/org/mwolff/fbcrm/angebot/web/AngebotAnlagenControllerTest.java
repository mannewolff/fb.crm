package org.mwolff.fbcrm.angebot.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.application.AngebotsanlageNichtGefunden;
import org.mwolff.fbcrm.angebot.application.AngebotsanlageUseCase;
import org.mwolff.fbcrm.angebot.application.AnlageOhneInhalt;
import org.mwolff.fbcrm.angebot.application.AnlageOhneNamen;
import org.mwolff.fbcrm.angebot.application.AnlageZuGross;
import org.mwolff.fbcrm.angebot.application.Anlageninhalt;
import org.mwolff.fbcrm.angebot.domain.Angebotsanlage;
import org.mwolff.fbcrm.angebot.domain.Vorschauart;
import org.mwolff.fbcrm.common.Uploadgrenze;
import org.mwolff.fbcrm.common.web.Anlagekopf;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen Anwendungsfall und HTTP fuer die Anlagen am Angebot (Issue #148).
 *
 * <p>Dieselbe Strecke wie in {@code AngebotKommentareControllerTest}: {@code standaloneSetup} mit
 * dem echten {@link GlobalExceptionHandler}, damit die Statuscodes der Ausnahmen und die
 * Feldmeldungen am Feld {@code datei} mitgeprueft werden.
 *
 * <p>Der Schwerpunkt liegt auf dem Inhaltsweg, denn dort sitzt die Sicherheitsgrenze (Plan #150,
 * E5): Die Kopfzeilen sind <b>immer</b> dieselben, und der {@code Content-Type} folgt allein der
 * gespeicherten {@link Vorschauart}. Dass die beim Hochladen gemeldete Art nirgends einfliesst,
 * steht hier zweimal: Der Anwendungsfall bekommt sie nicht zu sehen, und eine Anlage ohne
 * Vorschauart geht als {@code application/octet-stream} hinaus — auch wenn sie als {@code
 * text/html} hereinkam.
 */
@ExtendWith(MockitoExtension.class)
class AngebotAnlagenControllerTest {

  private static final long ANGEBOT = 11L;
  private static final long ANLAGE = 4L;
  private static final String PFAD = "/api/angebote/{angebotId}/anlagen";
  private static final String PFAD_EINZELN = PFAD + "/{anlageId}";
  private static final String PFAD_INHALT = PFAD_EINZELN + "/inhalt";
  private static final Instant ANGELEGT = Instant.parse("2026-09-30T08:00:00Z");
  private static final String NAME = "Bericht.pdf";
  private static final byte[] BYTES = "%PDF-1.7 Inhalt".getBytes(StandardCharsets.UTF_8);

  @Mock private AngebotsanlageUseCase useCase;

  @Captor private ArgumentCaptor<String> dateiName;

  @Captor private ArgumentCaptor<InputStream> strom;

  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new AngebotAnlagenController(useCase))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private static Angebotsanlage anlage(final @Nullable Vorschauart art) {
    return new Angebotsanlage(
        Long.valueOf(ANLAGE), ANGEBOT, NAME, BYTES.length, art, "angebot/11/anlage/3f2c", ANGELEGT);
  }

  private static MockMultipartFile teil(final String name, final String gemeldeteArt) {
    return new MockMultipartFile("datei", name, gemeldeteArt, BYTES);
  }

  private static Anlageninhalt inhalt(final @Nullable Vorschauart art) {
    return new Anlageninhalt(NAME, BYTES.length, art, new ByteArrayInputStream(BYTES));
  }

  @Test
  void liste_thenAnswersWithTheAnlagenAndTheirFields() throws Exception {
    // Given — Kriterium 4: Name, Groesse und Zeitpunkt stehen in der Antwort.
    when(useCase.liste(ANGEBOT)).thenReturn(List.of(anlage(Vorschauart.PDF)));

    // When / Then
    mockMvc
        .perform(get(PFAD, Long.valueOf(ANGEBOT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.anlagen[0].id").value(Long.valueOf(ANLAGE)))
        .andExpect(jsonPath("$.anlagen[0].dateiName").value(NAME))
        .andExpect(jsonPath("$.anlagen[0].groesse").value(Integer.valueOf(BYTES.length)))
        .andExpect(jsonPath("$.anlagen[0].vorschauArt").value("PDF"))
        .andExpect(jsonPath("$.anlagen[0].createdAt").exists());
  }

  @Test
  void liste_thenTheAnswerCarriesNeitherTheObjectKeyNorAnAuthor() throws Exception {
    // Given — E5: der Objektschluessel ist die interne Adresse; einen Verfasser gibt es nicht.
    when(useCase.liste(ANGEBOT)).thenReturn(List.of(anlage(null)));

    // When / Then
    mockMvc
        .perform(get(PFAD, Long.valueOf(ANGEBOT)))
        .andExpect(jsonPath("$.anlagen[0].objektSchluessel").doesNotExist())
        .andExpect(jsonPath("$.anlagen[0].verfasser").doesNotExist())
        .andExpect(jsonPath("$.anlagen[0].updatedAt").doesNotExist());
  }

  @Test
  void liste_withoutAnyAnlage_thenAnswersAnEmptyList() throws Exception {
    // Given — Kriterium 11: eine leere Liste heisst „noch keine Anlage".
    when(useCase.liste(ANGEBOT)).thenReturn(List.of());

    // When / Then
    mockMvc
        .perform(get(PFAD, Long.valueOf(ANGEBOT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.anlagen").isEmpty());
  }

  @Test
  void liste_whenTheAngebotIsUnknown_thenAnswers404() throws Exception {
    // Given
    when(useCase.liste(ANGEBOT)).thenThrow(new AngebotNichtGefunden());

    // When / Then
    mockMvc.perform(get(PFAD, Long.valueOf(ANGEBOT))).andExpect(status().isNotFound());
  }

  @Test
  void hochladen_thenAnswers201WithTheNewAnlage() throws Exception {
    // Given — Kriterium 2: die Oberflaeche reiht die Zeile ohne zweiten Aufruf ein.
    when(useCase.ladeHoch(
            eq(ANGEBOT), dateiName.capture(), strom.capture(), eq((long) BYTES.length)))
        .thenReturn(anlage(Vorschauart.PDF));

    // When / Then
    mockMvc
        .perform(multipart(PFAD, Long.valueOf(ANGEBOT)).file(teil(NAME, "application/pdf")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(Long.valueOf(ANLAGE)))
        .andExpect(jsonPath("$.dateiName").value(NAME))
        .andExpect(jsonPath("$.groesse").value(Integer.valueOf(BYTES.length)))
        .andExpect(jsonPath("$.vorschauArt").value("PDF"))
        .andExpect(jsonPath("$.createdAt").exists());
  }

  @Test
  void hochladen_thenPassesTheRawNameAndTheStreamOfThePartNamedDatei() throws Exception {
    // Given — der Controller entscheidet nichts: Der Name geht ungesaeubert weiter, die Bytes
    // gehen als Strom weiter.
    when(useCase.ladeHoch(
            eq(ANGEBOT), dateiName.capture(), strom.capture(), eq((long) BYTES.length)))
        .thenReturn(anlage(Vorschauart.PDF));

    // When
    mockMvc
        .perform(
            multipart(PFAD, Long.valueOf(ANGEBOT))
                .file(teil("C:\\Ablage\\Bericht.pdf", "application/pdf")))
        .andExpect(status().isCreated());

    // Then
    assertThat(dateiName.getValue()).isEqualTo("C:\\Ablage\\Bericht.pdf");
    assertThat(strom.getValue().readAllBytes()).isEqualTo(BYTES);
  }

  @Test
  void hochladen_givenAnHtmlFileNamedPdf_thenTheAnswerFollowsTheStoredKindAlone() throws Exception {
    // Given — Plan E4: die gemeldete Art wird nirgends verwendet, auch nicht in der Antwort.
    when(useCase.ladeHoch(
            eq(ANGEBOT), dateiName.capture(), strom.capture(), eq((long) BYTES.length)))
        .thenReturn(anlage(null));

    // When / Then
    mockMvc
        .perform(multipart(PFAD, Long.valueOf(ANGEBOT)).file(teil("bericht.pdf", "text/html")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.vorschauArt").value((Object) null));
  }

  @Test
  void hochladen_whenTheAngebotIsUnknown_thenAnswers404() throws Exception {
    // Given
    when(useCase.ladeHoch(eq(ANGEBOT), any(), any(), eq((long) BYTES.length)))
        .thenThrow(new AngebotNichtGefunden());

    // When / Then
    mockMvc
        .perform(multipart(PFAD, Long.valueOf(ANGEBOT)).file(teil(NAME, "application/pdf")))
        .andExpect(status().isNotFound());
  }

  @Test
  void hochladen_whenTheFileIsEmpty_thenAnswers400WithAFieldErrorOnDatei() throws Exception {
    // Given — Kriterium 5.
    when(useCase.ladeHoch(eq(ANGEBOT), any(), any(), eq((long) BYTES.length)))
        .thenThrow(new AnlageOhneInhalt());

    // When / Then
    mockMvc
        .perform(multipart(PFAD, Long.valueOf(ANGEBOT)).file(teil(NAME, "application/pdf")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.datei[0]").value(AnlageOhneInhalt.MELDUNG));
  }

  @Test
  void hochladen_whenTheFileIsTooLarge_thenAnswers400WithTheLimitOnDatei() throws Exception {
    // Given — Kriterium 6: dieselbe Meldung wie der Riegel des Containers.
    when(useCase.ladeHoch(eq(ANGEBOT), any(), any(), eq((long) BYTES.length)))
        .thenThrow(new AnlageZuGross());

    // When / Then
    mockMvc
        .perform(multipart(PFAD, Long.valueOf(ANGEBOT)).file(teil(NAME, "application/pdf")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.datei[0]").value(Uploadgrenze.MELDUNG));
  }

  @Test
  void hochladen_whenNothingIsLeftOfTheName_thenAnswers400WithAFieldErrorOnDatei()
      throws Exception {
    // Given — Plan E8.
    when(useCase.ladeHoch(eq(ANGEBOT), any(), any(), eq((long) BYTES.length)))
        .thenThrow(new AnlageOhneNamen());

    // When / Then
    mockMvc
        .perform(multipart(PFAD, Long.valueOf(ANGEBOT)).file(teil("  ", "application/pdf")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.datei[0]").value(AnlageOhneNamen.MELDUNG));
  }

  @Test
  void inhalt_thenAnswersTheBytesOfTheAnlage() throws Exception {
    // Given — Kriterium 9.
    when(useCase.liesInhalt(ANGEBOT, ANLAGE)).thenReturn(inhalt(Vorschauart.PDF));

    // When / Then
    mockMvc
        .perform(get(PFAD_INHALT, Long.valueOf(ANGEBOT), Long.valueOf(ANLAGE)))
        .andExpect(status().isOk())
        .andExpect(content().bytes(BYTES))
        .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, BYTES.length));
  }

  @Test
  void inhalt_thenAlwaysOffersTheFileForSavingUnderItsName() throws Exception {
    // Given — E5: immer attachment, mit dem Namen nach RFC 6266/5987.
    when(useCase.liesInhalt(ANGEBOT, ANLAGE)).thenReturn(inhalt(Vorschauart.PDF));

    // When / Then
    mockMvc
        .perform(get(PFAD_INHALT, Long.valueOf(ANGEBOT), Long.valueOf(ANLAGE)))
        .andExpect(
            header().string(HttpHeaders.CONTENT_DISPOSITION, Anlagekopf.contentDisposition(NAME)));
  }

  @Test
  void inhalt_thenCarriesTheSandboxPolicy() throws Exception {
    // Given — E5: die letzte Schranke, falls ein Empfaenger den Inhalt doch rendert.
    when(useCase.liesInhalt(ANGEBOT, ANLAGE)).thenReturn(inhalt(Vorschauart.PDF));

    // When / Then
    mockMvc
        .perform(get(PFAD_INHALT, Long.valueOf(ANGEBOT), Long.valueOf(ANLAGE)))
        .andExpect(header().string(Anlagekopf.INHALTSREGEL, Anlagekopf.SANDKASTEN));
  }

  @Test
  void inhalt_givenAStoredKind_thenTheContentTypeIsExactlyItsMimeType() throws Exception {
    // Given — E5: der Typ folgt allein der gespeicherten Vorschauart.
    when(useCase.liesInhalt(ANGEBOT, ANLAGE)).thenReturn(inhalt(Vorschauart.PNG));

    // When / Then
    mockMvc
        .perform(get(PFAD_INHALT, Long.valueOf(ANGEBOT), Long.valueOf(ANLAGE)))
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"));
  }

  @Test
  void inhalt_withoutAStoredKind_thenTheContentTypeIsOctetStream() throws Exception {
    // Given — E5: ohne Vorschauart geht die Anlage als undeuteter Bytestrom hinaus.
    when(useCase.liesInhalt(ANGEBOT, ANLAGE)).thenReturn(inhalt(null));

    // When / Then
    mockMvc
        .perform(get(PFAD_INHALT, Long.valueOf(ANGEBOT), Long.valueOf(ANLAGE)))
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/octet-stream"));
  }

  @Test
  void inhalt_whenTheAnlageIsUnknown_thenAnswers404() throws Exception {
    // Given — E10: dieselbe Antwort fuer unbekannt, fremdes Angebot und fehlendes Objekt.
    when(useCase.liesInhalt(ANGEBOT, ANLAGE)).thenThrow(new AngebotsanlageNichtGefunden());

    // When / Then
    mockMvc
        .perform(get(PFAD_INHALT, Long.valueOf(ANGEBOT), Long.valueOf(ANLAGE)))
        .andExpect(status().isNotFound());
  }

  @Test
  void inhalt_whenTheAngebotIsUnknown_thenAnswers404() throws Exception {
    // Given
    when(useCase.liesInhalt(ANGEBOT, ANLAGE)).thenThrow(new AngebotNichtGefunden());

    // When / Then
    mockMvc
        .perform(get(PFAD_INHALT, Long.valueOf(ANGEBOT), Long.valueOf(ANLAGE)))
        .andExpect(status().isNotFound());
  }

  @Test
  void loeschen_thenAnswers204AndDelegates() throws Exception {
    // When / Then — Kriterium 10.
    mockMvc
        .perform(delete(PFAD_EINZELN, Long.valueOf(ANGEBOT), Long.valueOf(ANLAGE)))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
    verify(useCase).loesche(ANGEBOT, ANLAGE);
  }

  @Test
  void anlagenwege_whenNoPartNamedDateiIsSent_thenAnswers400WithoutCallingTheUseCase()
      throws Exception {
    // Given — der Teil heisst „datei"; ein anderer Name ist eine fehlerhafte Anfrage.

    // When / Then
    mockMvc
        .perform(
            multipart(PFAD, Long.valueOf(ANGEBOT))
                .file(new MockMultipartFile("anhang", NAME, "application/pdf", BYTES)))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(useCase);
  }
}
