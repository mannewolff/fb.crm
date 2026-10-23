package org.mwolff.fbcrm.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.auth.Konten;
import org.mwolff.fbcrm.auth.application.AdresseBereitsVergeben;
import org.mwolff.fbcrm.auth.application.KontoGeaendert;
import org.mwolff.fbcrm.auth.application.KontoNichtGefunden;
import org.mwolff.fbcrm.auth.application.LetzterCLevel;
import org.mwolff.fbcrm.auth.application.MitarbeitendeVerwaltung;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.common.Urheber;
import org.mwolff.fbcrm.common.web.Bedingungskopf;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen der Verwaltung der Konten und HTTP.
 *
 * <p>Wie bei {@code FirmaControllerTest}: Die Abbildung wird direkt an den Methoden geprueft, die
 * Faelle, in denen die <b>Form</b> der Antwort die Aussage ist — Statuscode, Feldfehler, der eine
 * Satz zum letzten C-Level —, laufen durch MockMvc mit dem echten {@link GlobalExceptionHandler}.
 *
 * <p><b>Aendern, Stilllegen und Wiederaktivieren legen den Stand vor</b> (#411, Kriterium 4; Plan
 * #415, E3, E14, E19): Fuer jeden der drei Wege gilt dasselbe Dreigespann — ohne {@code If-Match}
 * 428, auf einem alten Stand 409 mit {@code konflikt}, auf dem aktuellen 200 mit der erhoehten
 * Version. Das Anlegen geht ohne Kopf durch (Kriterium 7).
 */
@ExtendWith(MockitoExtension.class)
class MitarbeitendeControllerTest {

  private static final Instant ANGELEGT = Instant.parse("2026-10-01T08:00:00Z");
  private static final String ANNA_MAIL = "anna@firma.de";
  private static final String RUMPF =
      """
      {"email":"anna@firma.de","displayName":"Anna Berg","role":"EMPLOYEE"}
      """;

  /** Der Stand, den die Oberflaeche vorlegt (Plan #415, E2). */
  private static final long STAND = 3L;

  /** Derselbe Stand als starkes Etikett im Kopf {@code If-Match}. */
  private static final String VORGELEGT = "\"" + STAND + "\"";

  /** Das angemeldete C-Level-Konto. */
  private static final long ANGEMELDET = 31L;

  /** Das Konto, das inzwischen verwaltet hat. */
  private static final long BERTA = 30L;

  private static final Instant BERTAS_AENDERUNG = Instant.parse("2026-10-06T08:42:00Z");

  @Mock private MitarbeitendeVerwaltung verwaltung;

  private MitarbeitendeController controller;
  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    // Die Sitzung wie im Betrieb: die Kennung des Kontos als Principal (Plan #415, E14).
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                Long.valueOf(ANGEMELDET), null, List.of()));
    controller = new MitarbeitendeController(verwaltung);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new GlobalExceptionHandler(
                    konto ->
                        konto == BERTA
                            ? Optional.of(new Urheber(BERTA, "Berta"))
                            : Optional.empty()))
            .setCustomArgumentResolvers(
                new Bedingungskopf.Leser(), new AuthenticationPrincipalArgumentResolver())
            .build();
  }

  @AfterEach
  void raeumeDieSitzungAb() {
    SecurityContextHolder.clearContext();
  }

  private static Account anna(final boolean aktiv) {
    return anna(aktiv, 0L);
  }

  private static Account anna(final boolean aktiv, final long version) {
    return Konten.imStand(
        Konten.mitId(
            7L,
            Konten.benannt(
                Konten.mit(null, ANNA_MAIL, Role.EMPLOYEE, aktiv, 0, ANGELEGT), "Anna Berg")),
        version,
        null);
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
            new MitarbeiterResponse(7L, "Anna Berg", ANNA_MAIL, Role.EMPLOYEE, false, 0L, null));
  }

  @Test
  void anlegen_thenAnswers201WithTheAccountAndTheGreetingFlag() throws Exception {
    // Given — ohne If-Match: Anlegen bleibt frei (#411, Kriterium 7).
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
        .andExpect(jsonPath("$.version").value(0))
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
    when(verwaltung.aendern(7L, ANNA_MAIL, "Anna Berg", Role.EMPLOYEE, STAND, ANGEMELDET))
        .thenReturn(anna(true));

    // When / Then
    mockMvc
        .perform(anfrageZu("aendern").header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(7))
        .andExpect(jsonPath("$.begruessungMailVersandt").doesNotExist());
  }

  @Test
  void aendern_givenTheLastCLevel_thenAnswers409WithTheOneSentence() throws Exception {
    // Given
    when(verwaltung.aendern(
            anyLong(), anyString(), anyString(), eq(Role.EMPLOYEE), anyLong(), anyLong()))
        .thenThrow(new LetzterCLevel());

    // When / Then — Kriterium 8: fb.crm sagt in einem Satz, warum.
    mockMvc
        .perform(anfrageZu("aendern").header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(LetzterCLevel.MELDUNG))
        .andExpect(jsonPath("$.konflikt").doesNotExist());
  }

  @Test
  void stilllegen_givenAnUnknownId_thenAnswers404() throws Exception {
    // Given
    when(verwaltung.stilllegen(9L, STAND, ANGEMELDET)).thenThrow(new KontoNichtGefunden());

    // When / Then
    mockMvc
        .perform(post("/api/mitarbeitende/9/stilllegen").header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Dieses Konto gibt es nicht."));
  }

  // --- Stand des Kontos (#411, Kriterium 4; Plan #415, E3, E19) ---------------------------

  /** Die Anfrage eines schreibenden Wegs, noch ohne Kopf {@code If-Match}. */
  private static MockHttpServletRequestBuilder anfrageZu(final String weg) {
    return switch (weg) {
      case "aendern" ->
          put("/api/mitarbeitende/7").contentType(MediaType.APPLICATION_JSON).content(RUMPF);
      case "stilllegen" -> post("/api/mitarbeitende/7/stilllegen");
      case "aktivieren" -> post("/api/mitarbeitende/7/aktivieren");
      default -> throw new IllegalArgumentException(weg);
    };
  }

  /** Die Verwaltung beantwortet den Weg mit dem Ergebnis — mit Stand und Konto aus der Anfrage. */
  private void antwortet(final String weg, final Account ergebnis) {
    switch (weg) {
      case "aendern" ->
          when(verwaltung.aendern(7L, ANNA_MAIL, "Anna Berg", Role.EMPLOYEE, STAND, ANGEMELDET))
              .thenReturn(ergebnis);
      case "stilllegen" -> when(verwaltung.stilllegen(7L, STAND, ANGEMELDET)).thenReturn(ergebnis);
      case "aktivieren" -> when(verwaltung.aktivieren(7L, STAND, ANGEMELDET)).thenReturn(ergebnis);
      default -> throw new IllegalArgumentException(weg);
    }
  }

  /** Die Verwaltung weist den Weg mit dem Konflikt ab. */
  private void wirftKonflikt(final String weg) {
    final KontoGeaendert konflikt = new KontoGeaendert(BERTA, ANGEMELDET, BERTAS_AENDERUNG);
    switch (weg) {
      case "aendern" ->
          when(verwaltung.aendern(7L, ANNA_MAIL, "Anna Berg", Role.EMPLOYEE, STAND, ANGEMELDET))
              .thenThrow(konflikt);
      case "stilllegen" -> when(verwaltung.stilllegen(7L, STAND, ANGEMELDET)).thenThrow(konflikt);
      case "aktivieren" -> when(verwaltung.aktivieren(7L, STAND, ANGEMELDET)).thenThrow(konflikt);
      default -> throw new IllegalArgumentException(weg);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"aendern", "stilllegen", "aktivieren"})
  void schreibenderWeg_withoutIfMatch_thenAnswers428AndChangesNothing(final String weg)
      throws Exception {
    // When / Then — E3: Ohne vorgelegten Stand wird nicht geschrieben.
    mockMvc
        .perform(anfrageZu(weg))
        .andExpect(status().isPreconditionRequired())
        .andExpect(jsonPath("$.detail").value(Bedingungskopf.MELDUNG));
    verifyNoInteractions(verwaltung);
  }

  @ParameterizedTest
  @ValueSource(strings = {"aendern", "stilllegen", "aktivieren"})
  void schreibenderWeg_withAnOutdatedStand_thenAnswers409WithTheConflict(final String weg)
      throws Exception {
    // Given — #411, Kriterium 4: Berta hat das Konto inzwischen verwaltet.
    wirftKonflikt(weg);

    // When / Then
    mockMvc
        .perform(anfrageZu(weg).header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.konflikt.art").value("veraltet"))
        .andExpect(jsonPath("$.konflikt.geaendertVon").value("Berta"))
        .andExpect(jsonPath("$.konflikt.geaendertAm").value("2026-10-06T08:42:00Z"))
        .andExpect(jsonPath("$.konflikt.eigenesFenster").value(false));
  }

  @ParameterizedTest
  @ValueSource(strings = {"aendern", "stilllegen", "aktivieren"})
  void schreibenderWeg_withTheCurrentStand_thenAnswers200WithTheRaisedVersion(final String weg)
      throws Exception {
    // Given — #411, Kriterium 8: Auf dem aktuellen Stand geht der Schritt unveraendert durch; die
    // Antwort traegt die erhoehte Version, die die Oberflaeche beim naechsten Schritt vorlegt.
    antwortet(weg, anna(!"stilllegen".equals(weg), STAND + 1));

    // When / Then
    mockMvc
        .perform(anfrageZu(weg).header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(7))
        .andExpect(jsonPath("$.version").value(STAND + 1))
        .andExpect(jsonPath("$.aktiv").value(!"stilllegen".equals(weg)));
  }
}
