package org.mwolff.fbcrm.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.auth.Konten;
import org.mwolff.fbcrm.auth.application.AdresseBereitsVergeben;
import org.mwolff.fbcrm.auth.application.KontoNichtGefunden;
import org.mwolff.fbcrm.auth.application.LetzterCLevel;
import org.mwolff.fbcrm.auth.application.MitarbeitendeVerwaltung;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen der Verwaltung der Konten und HTTP.
 *
 * <p>Wie bei {@code FirmaControllerTest}: Die Abbildung wird direkt an den Methoden geprueft, die
 * Faelle, in denen die <b>Form</b> der Antwort die Aussage ist — Statuscode, Feldfehler, der eine
 * Satz zum letzten C-Level —, laufen durch MockMvc mit dem echten {@link GlobalExceptionHandler}.
 */
@ExtendWith(MockitoExtension.class)
class MitarbeitendeControllerTest {

  private static final Instant ANGELEGT = Instant.parse("2026-10-01T08:00:00Z");
  private static final String ANNA_MAIL = "anna@firma.de";
  private static final String RUMPF =
      """
      {"email":"anna@firma.de","displayName":"Anna Berg","role":"EMPLOYEE"}
      """;

  @Mock private MitarbeitendeVerwaltung verwaltung;

  private MitarbeitendeController controller;
  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    controller = new MitarbeitendeController(verwaltung);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler(konto -> Optional.empty()))
            .build();
  }

  private static Account anna(final boolean aktiv) {
    return Konten.mitId(
        7L,
        Konten.mit(null, ANNA_MAIL, Role.EMPLOYEE, aktiv, 0, ANGELEGT)
            .mitNamen("Anna Berg", ANGELEGT));
  }

  @Test
  void liste_thenMapsEveryAccount() {
    // Given
    when(verwaltung.liste()).thenReturn(List.of(anna(false)));

    // When
    final MitarbeitendeResponse antwort = controller.liste();

    // Then
    assertThat(antwort.mitarbeitende())
        .containsExactly(
            new MitarbeiterResponse(7L, "Anna Berg", ANNA_MAIL, Role.EMPLOYEE, false, null));
  }

  @Test
  void anlegen_thenAnswers201WithTheAccountAndTheGreetingFlag() throws Exception {
    // Given
    when(verwaltung.anlegen(ANNA_MAIL, "Anna Berg", Role.EMPLOYEE))
        .thenReturn(new MitarbeitendeVerwaltung.Angelegt(anna(true), true));

    // When / Then
    mockMvc
        .perform(post("/api/mitarbeitende").contentType(MediaType.APPLICATION_JSON).content(RUMPF))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(7))
        .andExpect(jsonPath("$.displayName").value("Anna Berg"))
        .andExpect(jsonPath("$.email").value(ANNA_MAIL))
        .andExpect(jsonPath("$.role").value("EMPLOYEE"))
        .andExpect(jsonPath("$.aktiv").value(true))
        .andExpect(jsonPath("$.begruessungMailVersandt").value(true));
  }

  @Test
  void liste_overHttp_thenCarriesNoGreetingFlag() throws Exception {
    // Given — das Feld gehoert allein zur Antwort auf das Anlegen.
    when(verwaltung.liste()).thenReturn(List.of(anna(true)));

    // When / Then
    mockMvc
        .perform(get("/api/mitarbeitende"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mitarbeitende[0].email").value(ANNA_MAIL))
        .andExpect(jsonPath("$.mitarbeitende[0].begruessungMailVersandt").doesNotExist());
  }

  @Test
  void anlegen_givenMissingFields_thenAnswers400AndCallsNothing() throws Exception {
    // When / Then — alle drei Angaben sind Pflicht (Kriterium 4).
    mockMvc
        .perform(
            post("/api/mitarbeitende")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"keine-adresse\",\"displayName\":\" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.email").isArray())
        .andExpect(jsonPath("$.fieldErrors.displayName").isArray())
        .andExpect(jsonPath("$.fieldErrors.role").isArray());
    verifyNoInteractions(verwaltung);
  }

  @Test
  void anlegen_givenATooLongName_thenAnswers400() throws Exception {
    // When / Then — dieselbe Grenze wie die Spalte display_name.
    mockMvc
        .perform(
            post("/api/mitarbeitende")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"anna@firma.de\",\"displayName\":\""
                        + "x".repeat(201)
                        + "\",\"role\":\"EMPLOYEE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.displayName").isArray());
    verifyNoInteractions(verwaltung);
  }

  @Test
  void anlegen_givenATakenAddress_thenAnswers409AtTheField() throws Exception {
    // Given
    when(verwaltung.anlegen(anyString(), anyString(), eq(Role.EMPLOYEE)))
        .thenThrow(new AdresseBereitsVergeben());

    // When / Then
    mockMvc
        .perform(post("/api/mitarbeitende").contentType(MediaType.APPLICATION_JSON).content(RUMPF))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(AdresseBereitsVergeben.MELDUNG))
        .andExpect(jsonPath("$.fieldErrors.email[0]").value(AdresseBereitsVergeben.MELDUNG));
  }

  @Test
  void aendern_thenAnswersWithTheChangedAccount() throws Exception {
    // Given
    when(verwaltung.aendern(7L, ANNA_MAIL, "Anna Berg", Role.EMPLOYEE)).thenReturn(anna(true));

    // When / Then
    mockMvc
        .perform(put("/api/mitarbeitende/7").contentType(MediaType.APPLICATION_JSON).content(RUMPF))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(7))
        .andExpect(jsonPath("$.begruessungMailVersandt").doesNotExist());
  }

  @Test
  void aendern_givenTheLastCLevel_thenAnswers409WithTheOneSentence() throws Exception {
    // Given
    when(verwaltung.aendern(anyLong(), anyString(), anyString(), eq(Role.EMPLOYEE)))
        .thenThrow(new LetzterCLevel());

    // When / Then — Kriterium 8: fb.crm sagt in einem Satz, warum.
    mockMvc
        .perform(put("/api/mitarbeitende/7").contentType(MediaType.APPLICATION_JSON).content(RUMPF))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(LetzterCLevel.MELDUNG));
  }

  @Test
  void stilllegen_thenAnswers204() throws Exception {
    // When / Then
    mockMvc.perform(post("/api/mitarbeitende/7/stilllegen")).andExpect(status().isNoContent());
    verify(verwaltung).stilllegen(7L);
  }

  @Test
  void stilllegen_givenAnUnknownId_thenAnswers404() throws Exception {
    // Given
    doThrow(new KontoNichtGefunden()).when(verwaltung).stilllegen(9L);

    // When / Then
    mockMvc
        .perform(post("/api/mitarbeitende/9/stilllegen"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Dieses Konto gibt es nicht."));
  }

  @Test
  void aktivieren_thenAnswers204() throws Exception {
    // When / Then
    mockMvc.perform(post("/api/mitarbeitende/7/aktivieren")).andExpect(status().isNoContent());
    verify(verwaltung).aktivieren(7L);
  }
}
