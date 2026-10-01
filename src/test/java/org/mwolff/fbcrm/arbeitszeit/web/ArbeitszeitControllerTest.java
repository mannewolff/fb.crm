package org.mwolff.fbcrm.arbeitszeit.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.arbeitszeit.application.PositionNichtBuchbar;
import org.mwolff.fbcrm.arbeitszeit.application.UhrzeitNichtImRaster;
import org.mwolff.fbcrm.arbeitszeit.application.ZeiteintragNichtGefunden;
import org.mwolff.fbcrm.arbeitszeit.application.ZeiteintragUseCase;
import org.mwolff.fbcrm.arbeitszeit.domain.Zeiteintrag;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen Anwendungsfall und HTTP fuer die Schreibwege der Arbeitszeit (Issue
 * #193, Kriterien 1 und 6).
 *
 * <p>Dieselbe Strecke wie in {@code AngebotKommentareControllerTest}: {@code standaloneSetup} mit
 * dem echten {@link GlobalExceptionHandler}, damit die Statuscodes der Ausnahmen und die
 * Feldmeldungen der Bean Validation mitgeprueft werden.
 *
 * <p>Gegenstand sind die Statuscodes — 201 beim Anlegen, 200 beim Aendern, 204 beim Loeschen, 404
 * bei unbekannter Kennung —, die Felder der Antwort samt der gerechneten Dauer und die
 * Feldmeldungen der fachlichen Abweisungen (422).
 *
 * <p><b>Welche Felder</b> die Antwort traegt, entscheidet dieser Layer; <b>wie</b> Tag und Uhrzeit
 * geschrieben werden, entscheidet der von Spring Boot gebaute ObjectMapper, den {@code
 * standaloneSetup} nicht kennt — hier steht deshalb nur, dass sie da sind. Die Textform belegt
 * {@code ArbeitszeitControllerIT} am echten Weg.
 */
@ExtendWith(MockitoExtension.class)
class ArbeitszeitControllerTest {

  private static final String PFAD = "/api/arbeitszeit";
  private static final String PFAD_EINZELN = PFAD + "/{id}";

  private static final long EINTRAG = 4L;
  private static final long POSITION = 101L;
  private static final LocalDate TAG = LocalDate.of(2026, 11, 12);
  private static final Instant ANGELEGT = Instant.parse("2026-11-12T08:00:00Z");

  private static final String RUMPF =
      """
      {"angebotPositionId":101,"tag":"2026-11-12","von":"09:00","bis":"10:45"}
      """;

  @Mock private ZeiteintragUseCase useCase;

  @Captor private ArgumentCaptor<LocalTime> von;

  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ArbeitszeitController(useCase))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private static Zeiteintrag eintrag() {
    return new Zeiteintrag(
        Long.valueOf(EINTRAG),
        POSITION,
        TAG,
        LocalTime.of(9, 0),
        LocalTime.of(10, 45),
        ANGELEGT,
        ANGELEGT);
  }

  @Test
  void anlegen_thenAnswers201WithTheEntryAndItsCalculatedHours() throws Exception {
    // Given — die Dauer steht nicht in der Anfrage; sie wird gerechnet (Plan #194, A3).
    when(useCase.anlegen(POSITION, TAG, LocalTime.of(9, 0), LocalTime.of(10, 45)))
        .thenReturn(eintrag());

    // When / Then
    mockMvc
        .perform(post(PFAD).contentType(MediaType.APPLICATION_JSON).content(RUMPF))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(Long.valueOf(EINTRAG)))
        .andExpect(jsonPath("$.angebotPositionId").value(Long.valueOf(POSITION)))
        .andExpect(jsonPath("$.tag").exists())
        .andExpect(jsonPath("$.von").exists())
        .andExpect(jsonPath("$.bis").exists())
        .andExpect(jsonPath("$.stunden").value(1.75));
  }

  @Test
  void anlegen_thenPassesTheSubmittedTimesToTheUseCase() throws Exception {
    // Given
    when(useCase.anlegen(eq(POSITION), eq(TAG), von.capture(), eq(LocalTime.of(10, 45))))
        .thenReturn(eintrag());

    // When
    mockMvc
        .perform(post(PFAD).contentType(MediaType.APPLICATION_JSON).content(RUMPF))
        .andExpect(status().isCreated());

    // Then
    assertThat(von.getValue()).isEqualTo(LocalTime.of(9, 0));
  }

  @Test
  void anlegen_withoutTheTag_thenAnswers400WithAFieldError() throws Exception {
    // Given — ohne Tag gibt es keinen Eintrag; die Luecke faellt an der Schnittstelle auf.
    // When / Then
    mockMvc
        .perform(
            post(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"angebotPositionId\":101,\"von\":\"09:00\",\"bis\":\"10:45\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.tag").isNotEmpty());
    verifyNoInteractions(useCase);
  }

  @Test
  void anlegen_withoutThePosition_thenAnswers400WithAFieldError() throws Exception {
    // Given — gebucht wird immer auf eine Position.
    // When / Then
    mockMvc
        .perform(
            post(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tag\":\"2026-11-12\",\"von\":\"09:00\",\"bis\":\"10:45\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.angebotPositionId").isNotEmpty());
    verifyNoInteractions(useCase);
  }

  @Test
  void anlegen_whenTheTimeIsOutsideTheGrid_thenAnswers422WithAFieldErrorAtVon() throws Exception {
    // Given — A19: die Meldung kommt am Feld an und nicht als Serverfehler.
    when(useCase.anlegen(POSITION, TAG, LocalTime.of(9, 0), LocalTime.of(10, 45)))
        .thenThrow(UhrzeitNichtImRaster.anVon());

    // When / Then
    mockMvc
        .perform(post(PFAD).contentType(MediaType.APPLICATION_JSON).content(RUMPF))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.fieldErrors.von").isNotEmpty());
  }

  @Test
  void anlegen_whenThePositionIsNotBookable_thenAnswers422WithAFieldErrorAtThePosition()
      throws Exception {
    // Given — Antworten 2, 3 und 5.
    when(useCase.anlegen(POSITION, TAG, LocalTime.of(9, 0), LocalTime.of(10, 45)))
        .thenThrow(new PositionNichtBuchbar());

    // When / Then
    mockMvc
        .perform(post(PFAD).contentType(MediaType.APPLICATION_JSON).content(RUMPF))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.fieldErrors.angebotPositionId").isNotEmpty());
  }

  @Test
  void aendern_thenAnswers200WithTheChangedEntry() throws Exception {
    // Given — Kriterium 6.
    when(useCase.aendern(EINTRAG, POSITION, TAG, LocalTime.of(9, 0), LocalTime.of(10, 45)))
        .thenReturn(eintrag());

    // When / Then
    mockMvc
        .perform(
            put(PFAD_EINZELN, Long.valueOf(EINTRAG))
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(Long.valueOf(EINTRAG)))
        .andExpect(jsonPath("$.stunden").value(1.75));
  }

  @Test
  void aendern_whenTheEntryIsUnknown_thenAnswers404() throws Exception {
    // Given
    when(useCase.aendern(EINTRAG, POSITION, TAG, LocalTime.of(9, 0), LocalTime.of(10, 45)))
        .thenThrow(new ZeiteintragNichtGefunden());

    // When / Then
    mockMvc
        .perform(
            put(PFAD_EINZELN, Long.valueOf(EINTRAG))
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isNotFound());
  }

  @Test
  void loeschen_thenAnswers204AndDelegates() throws Exception {
    // When / Then
    mockMvc.perform(delete(PFAD_EINZELN, Long.valueOf(EINTRAG))).andExpect(status().isNoContent());
    verify(useCase).loeschen(EINTRAG);
  }
}
