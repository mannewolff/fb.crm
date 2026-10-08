package org.mwolff.fbcrm.angebot.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.application.AngebotskommentarNichtGefunden;
import org.mwolff.fbcrm.angebot.application.AngebotskommentarUseCase;
import org.mwolff.fbcrm.angebot.domain.Angebotskommentar;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen Anwendungsfall und HTTP fuer die Kommentare am Angebot (Issue #140).
 *
 * <p>Dieselbe Strecke wie in {@code AngebotControllerTest}: {@code standaloneSetup} mit dem echten
 * {@link GlobalExceptionHandler}, damit die Statuscodes der Ausnahmen und die Feldfehler der Bean
 * Validation mitgeprueft werden.
 *
 * <p>Gegenstand sind die Statuscodes — 201 beim Schreiben, 204 beim Loeschen, 404 bei unbekannter
 * Kennung —, die Felder der Antwort (ohne {@code updatedAt} und ohne Verfasser, Kriterium 9) und
 * die Grenze von 2.000 Zeichen am eingereichten Text (Plan E7).
 *
 * <p><b>Welche Felder</b> die Antwort traegt, entscheidet dieser Layer; <b>wie</b> der Zeitpunkt
 * geschrieben wird, entscheidet der von Spring Boot gebaute ObjectMapper, den {@code
 * standaloneSetup} nicht kennt — hier steht deshalb nur, dass {@code createdAt} da ist. Die
 * Textform des Zeitstempels belegt {@code AngebotKommentarIT} am echten Weg.
 */
@ExtendWith(MockitoExtension.class)
class AngebotKommentareControllerTest {

  private static final long ANGEBOT = 11L;
  private static final long KOMMENTAR = 4L;
  private static final String PFAD = "/api/angebote/{angebotId}/kommentare";
  private static final String PFAD_EINZELN = PFAD + "/{kommentarId}";
  private static final Instant ANGELEGT = Instant.parse("2026-09-30T08:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-09-30T09:30:00Z");

  private static final String RUMPF = "{\"text\":\"Bitte melden.\"}";

  @Mock private AngebotskommentarUseCase useCase;

  @Captor private ArgumentCaptor<String> text;

  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new AngebotKommentareController(useCase))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private static Angebotskommentar kommentar(final String inhalt) {
    return new Angebotskommentar(Long.valueOf(KOMMENTAR), ANGEBOT, inhalt, ANGELEGT, GEAENDERT);
  }

  private static String rumpfMit(final String inhalt) {
    return "{\"text\":\"" + inhalt + "\"}";
  }

  @Test
  void liste_thenAnswersWithTheKommentareAndTheirCreatedAt() throws Exception {
    // Given — Kriterium 3: die Antwort traegt den Zeitpunkt der Anlage.
    when(useCase.liste(ANGEBOT)).thenReturn(List.of(kommentar("Bitte melden.")));

    // When / Then
    mockMvc
        .perform(get(PFAD, Long.valueOf(ANGEBOT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.kommentare[0].id").value(Long.valueOf(KOMMENTAR)))
        .andExpect(jsonPath("$.kommentare[0].text").value("Bitte melden."))
        .andExpect(jsonPath("$.kommentare[0].createdAt").exists())
        .andExpect(jsonPath("$.kommentare[0].updatedAt").doesNotExist())
        .andExpect(jsonPath("$.kommentare[0].verfasser").doesNotExist());
  }

  @Test
  void liste_whenTheAngebotIsUnknown_thenAnswers404() throws Exception {
    // Given
    when(useCase.liste(ANGEBOT)).thenThrow(new AngebotNichtGefunden());

    // When / Then
    mockMvc.perform(get(PFAD, Long.valueOf(ANGEBOT))).andExpect(status().isNotFound());
  }

  @Test
  void schreiben_thenAnswers201WithTheKommentarAndPassesTheText() throws Exception {
    // Given
    when(useCase.schreibe(eq(ANGEBOT), text.capture())).thenReturn(kommentar("Bitte melden."));

    // When / Then
    mockMvc
        .perform(
            post(PFAD, Long.valueOf(ANGEBOT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(Long.valueOf(KOMMENTAR)))
        .andExpect(jsonPath("$.text").value("Bitte melden."));
    assertThat(text.getValue()).isEqualTo("Bitte melden.");
  }

  @Test
  void schreiben_whenTheAngebotIsUnknown_thenAnswers404() throws Exception {
    // Given
    when(useCase.schreibe(eq(ANGEBOT), text.capture())).thenThrow(new AngebotNichtGefunden());

    // When / Then
    mockMvc
        .perform(
            post(PFAD, Long.valueOf(ANGEBOT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isNotFound());
  }

  @Test
  void schreiben_withAnEmptyText_thenAnswers400WithAFieldError() throws Exception {
    // When / Then
    mockMvc
        .perform(
            post(PFAD, Long.valueOf(ANGEBOT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpfMit("")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.text").isNotEmpty());
    verifyNoInteractions(useCase);
  }

  @Test
  void schreiben_withATextOfOnlyWhitespace_thenAnswers400WithAFieldError() throws Exception {
    // Given — Kriterium 7: ein Kommentar aus Leerzeilen wird nicht gespeichert.
    // When / Then
    mockMvc
        .perform(
            post(PFAD, Long.valueOf(ANGEBOT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpfMit("  \\n \\t ")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.text").isNotEmpty());
    verifyNoInteractions(useCase);
  }

  @Test
  void schreiben_with2001Characters_thenAnswers400WithAFieldError() throws Exception {
    // Given — Plan E7: die Grenze gilt an der Schnittstelle fuer den eingereichten Text.
    // When / Then
    mockMvc
        .perform(
            post(PFAD, Long.valueOf(ANGEBOT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpfMit("x".repeat(2001))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.text").isNotEmpty());
    verifyNoInteractions(useCase);
  }

  @Test
  void schreiben_with2000Characters_thenAnswers201() throws Exception {
    // Given — die Grenze selbst geht noch durch.
    final String gerade = "x".repeat(2000);
    when(useCase.schreibe(eq(ANGEBOT), text.capture())).thenReturn(kommentar(gerade));

    // When / Then
    mockMvc
        .perform(
            post(PFAD, Long.valueOf(ANGEBOT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpfMit(gerade)))
        .andExpect(status().isCreated());
  }

  @Test
  void aendern_thenAnswers200WithTheChangedKommentar() throws Exception {
    // Given — Kriterium 9: die Antwort traegt weiter den Zeitpunkt der Anlage.
    when(useCase.aendere(eq(ANGEBOT), eq(KOMMENTAR), text.capture()))
        .thenReturn(kommentar("Zweiter Stand"));

    // When / Then
    mockMvc
        .perform(
            put(PFAD_EINZELN, Long.valueOf(ANGEBOT), Long.valueOf(KOMMENTAR))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Zweiter Stand\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.text").value("Zweiter Stand"))
        .andExpect(jsonPath("$.createdAt").exists());
    assertThat(text.getValue()).isEqualTo("Zweiter Stand");
  }

  @Test
  void aendern_withAnEmptyText_thenAnswers400WithAFieldError() throws Exception {
    // When / Then
    mockMvc
        .perform(
            put(PFAD_EINZELN, Long.valueOf(ANGEBOT), Long.valueOf(KOMMENTAR))
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpfMit("")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.text").isNotEmpty());
    verifyNoInteractions(useCase);
  }

  @Test
  void aendern_whenTheKommentarIsUnknown_thenAnswers404() throws Exception {
    // Given — E5: auch ein Kommentar eines anderen Angebots ist hier unbekannt.
    when(useCase.aendere(eq(ANGEBOT), eq(KOMMENTAR), text.capture()))
        .thenThrow(new AngebotskommentarNichtGefunden());

    // When / Then
    mockMvc
        .perform(
            put(PFAD_EINZELN, Long.valueOf(ANGEBOT), Long.valueOf(KOMMENTAR))
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isNotFound());
  }

  @Test
  void loeschen_thenAnswers204AndDelegates() throws Exception {
    // When / Then
    mockMvc
        .perform(delete(PFAD_EINZELN, Long.valueOf(ANGEBOT), Long.valueOf(KOMMENTAR)))
        .andExpect(status().isNoContent());
    verify(useCase).loesche(ANGEBOT, KOMMENTAR);
  }
}
