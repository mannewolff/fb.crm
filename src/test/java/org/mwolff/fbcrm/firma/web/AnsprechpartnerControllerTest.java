package org.mwolff.fbcrm.firma.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
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
import org.mwolff.fbcrm.common.Urheber;
import org.mwolff.fbcrm.common.web.Bedingungskopf;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.mwolff.fbcrm.firma.application.AnsprechpartnerAendernUseCase;
import org.mwolff.fbcrm.firma.application.AnsprechpartnerAnlegenUseCase;
import org.mwolff.fbcrm.firma.application.AnsprechpartnerDaten;
import org.mwolff.fbcrm.firma.application.AnsprechpartnerGeaendert;
import org.mwolff.fbcrm.firma.application.AnsprechpartnerNichtGefunden;
import org.mwolff.fbcrm.firma.application.AnsprechpartnerStilllegenUseCase;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen Anwendungsfall und HTTP fuer den Ansprechpartner.
 *
 * <p>Zwei Blickwinkel in einer Klasse wie bei {@link FirmaControllerTest}: die Abbildung direkt an
 * den Methoden, die Faelle mit aussagekraeftiger Antwort<b>form</b> ueber eine schlanke
 * MockMvc-Strecke mit dem echten {@link GlobalExceptionHandler}.
 *
 * <p><b>Aendern, Stilllegen und Wiederaktivieren legen den Stand vor</b> (#411, Kriterium 4; Plan
 * #415, E3, E14, E18): je Weg ohne {@code If-Match} 428, auf einem alten Stand 409 mit {@code
 * konflikt}, auf dem aktuellen 200 mit dem erhoehten Stand. Das Anlegen geht ohne Kopf durch
 * (Kriterium 7).
 */
@ExtendWith(MockitoExtension.class)
class AnsprechpartnerControllerTest {

  private static final long FIRMA = 7L;
  private static final long ID = 3L;
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final String PFAD = "/api/firmen/7/ansprechpartner";

  /** Der Stand, den die Oberflaeche vorlegt (Plan #415, E2). */
  private static final long STAND = 3L;

  /** Derselbe Stand als starkes Etikett im Kopf {@code If-Match}. */
  private static final String VORGELEGT = "\"" + STAND + "\"";

  /** Das angemeldete Konto. */
  private static final long ANGEMELDET = 31L;

  /** Das Konto, das inzwischen geaendert hat. */
  private static final long ANNA = 30L;

  private static final Instant ANNAS_AENDERUNG = Instant.parse("2026-10-06T08:42:00Z");

  @Mock private AnsprechpartnerAnlegenUseCase anlegen;
  @Mock private AnsprechpartnerAendernUseCase aendern;
  @Mock private AnsprechpartnerStilllegenUseCase stilllegen;

  private AnsprechpartnerController controller;
  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    // Die Sitzung wie im Betrieb: die Kennung des Kontos als Principal (Plan #415, E14).
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                Long.valueOf(ANGEMELDET), null, List.of()));
    controller = new AnsprechpartnerController(anlegen, aendern, stilllegen);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new GlobalExceptionHandler(
                    konto ->
                        konto == ANNA ? Optional.of(new Urheber(ANNA, "Anna")) : Optional.empty()))
            .setCustomArgumentResolvers(
                new Bedingungskopf.Leser(), new AuthenticationPrincipalArgumentResolver())
            .build();
  }

  @AfterEach
  void raeumeDieSitzungAb() {
    SecurityContextHolder.clearContext();
  }

  private static Ansprechpartner maxMueller() {
    return maxMueller(0L);
  }

  private static Ansprechpartner maxMueller(final long version) {
    return new Ansprechpartner(
        ID,
        FIRMA,
        "Max",
        "Mueller",
        "Einkauf",
        "max@firma.de",
        "0421 1234",
        "0170 1234",
        true,
        ANGELEGT,
        ANGELEGT,
        version,
        null);
  }

  private static AnsprechpartnerRequest anfrage(final String nachname) {
    return new AnsprechpartnerRequest(
        "Max", nachname, "Einkauf", "max@firma.de", "0421 1234", "0170 1234");
  }

  private static AnsprechpartnerDaten erwarteteDaten(final String nachname) {
    return new AnsprechpartnerDaten(
        "Max", nachname, "Einkauf", "max@firma.de", "0421 1234", "0170 1234");
  }

  private static String rumpf(final String nachname, final String email) {
    return "{\"vorname\":\"Max\",\"nachname\":\""
        + nachname
        + "\",\"rolle\":\"Einkauf\",\"email\":\""
        + email
        + "\",\"telefonFestnetz\":\"0421 1234\",\"telefonMobil\":\"0170 1234\"}";
  }

  @Test
  void anlegen_thenPassesThePathFirmaAndTheRequestToTheUseCase() {
    // Given
    when(anlegen.anlegen(FIRMA, erwarteteDaten("Mueller"))).thenReturn(maxMueller());

    // When
    controller.anlegen(FIRMA, anfrage("Mueller"));

    // Then — E7: die Firma kommt aus dem Pfad.
    verify(anlegen).anlegen(FIRMA, erwarteteDaten("Mueller"));
  }

  @Test
  void anlegen_thenAnswersWithTheNewContact() {
    // Given
    when(anlegen.anlegen(FIRMA, erwarteteDaten("Mueller"))).thenReturn(maxMueller());

    // When
    final AnsprechpartnerResponse antwort = controller.anlegen(FIRMA, anfrage("Mueller"));

    // Then
    assertThat(antwort)
        .isEqualTo(
            new AnsprechpartnerResponse(
                ID,
                "Max",
                "Mueller",
                "Einkauf",
                "max@firma.de",
                "0421 1234",
                "0170 1234",
                true,
                0L));
  }

  @Test
  void anlegen_thenAnswersWithStatusCreated() throws Exception {
    // Given
    when(anlegen.anlegen(FIRMA, erwarteteDaten("Mueller"))).thenReturn(maxMueller());

    // When / Then
    mockMvc
        .perform(
            post(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("Mueller", "max@firma.de")))
        .andExpect(status().isCreated());
  }

  @Test
  void anlegen_withoutIfMatch_thenAnswersCreated() throws Exception {
    // Given — #411, Kriterium 7: ein neuer Ansprechpartner entsteht ohne Rueckfrage.
    when(anlegen.anlegen(FIRMA, erwarteteDaten("Mueller"))).thenReturn(maxMueller());

    // When / Then
    mockMvc
        .perform(
            post(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("Mueller", "max@firma.de")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.version").value(0));
  }

  @Test
  void aendern_thenPassesFirmaIdIdRequestStandAndAccountToTheUseCase() {
    // Given
    when(aendern.aendern(FIRMA, ID, erwarteteDaten("Maier"), STAND, ANGEMELDET))
        .thenReturn(maxMueller());

    // When
    controller.aendern(FIRMA, ID, new Bedingungskopf(STAND), ANGEMELDET, anfrage("Maier"));

    // Then
    verify(aendern).aendern(FIRMA, ID, erwarteteDaten("Maier"), STAND, ANGEMELDET);
  }

  /** Die Anfrage eines schreibenden Wegs, noch ohne Kopf {@code If-Match}. */
  private static MockHttpServletRequestBuilder anfrageZu(final String weg) {
    return switch (weg) {
      case "aendern" ->
          put(PFAD + "/3")
              .contentType(MediaType.APPLICATION_JSON)
              .content(rumpf("Maier", "max@firma.de"));
      case "stilllegen" -> post(PFAD + "/3/stilllegen");
      case "aktivieren" -> post(PFAD + "/3/aktivieren");
      default -> throw new IllegalArgumentException(weg);
    };
  }

  /** Der Anwendungsfall des Wegs wirft den Konflikt — mit Stand und Konto aus der Anfrage. */
  private void wirftKonflikt(final String weg) {
    final AnsprechpartnerGeaendert konflikt =
        new AnsprechpartnerGeaendert(ANNA, ANGEMELDET, ANNAS_AENDERUNG);
    switch (weg) {
      case "aendern" ->
          when(aendern.aendern(eq(FIRMA), eq(ID), any(), eq(STAND), eq(ANGEMELDET)))
              .thenThrow(konflikt);
      case "stilllegen" ->
          when(stilllegen.stilllegen(FIRMA, ID, STAND, ANGEMELDET)).thenThrow(konflikt);
      case "aktivieren" ->
          when(stilllegen.aktivieren(FIRMA, ID, STAND, ANGEMELDET)).thenThrow(konflikt);
      default -> throw new IllegalArgumentException(weg);
    }
  }

  /** Der Anwendungsfall des Wegs liefert den Ansprechpartner mit erhoehtem Stand. */
  private void liefertErhoehtenStand(final String weg) {
    final Ansprechpartner geschrieben = maxMueller(STAND + 1);
    switch (weg) {
      case "aendern" ->
          when(aendern.aendern(eq(FIRMA), eq(ID), any(), eq(STAND), eq(ANGEMELDET)))
              .thenReturn(geschrieben);
      case "stilllegen" ->
          when(stilllegen.stilllegen(FIRMA, ID, STAND, ANGEMELDET)).thenReturn(geschrieben);
      case "aktivieren" ->
          when(stilllegen.aktivieren(FIRMA, ID, STAND, ANGEMELDET)).thenReturn(geschrieben);
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
    verifyNoInteractions(aendern, stilllegen);
  }

  @ParameterizedTest
  @ValueSource(strings = {"aendern", "stilllegen", "aktivieren"})
  void schreibenderWeg_withAnOutdatedStand_thenAnswers409WithTheConflict(final String weg)
      throws Exception {
    // Given — #411, Kriterium 4: Anna hat den Ansprechpartner inzwischen geaendert.
    wirftKonflikt(weg);

    // When / Then
    mockMvc
        .perform(anfrageZu(weg).header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.konflikt.art").value("veraltet"))
        .andExpect(jsonPath("$.konflikt.geaendertVon").value("Anna"))
        .andExpect(jsonPath("$.konflikt.geaendertAm").value("2026-10-06T08:42:00Z"))
        .andExpect(jsonPath("$.konflikt.eigenesFenster").value(false));
  }

  @ParameterizedTest
  @ValueSource(strings = {"aendern", "stilllegen", "aktivieren"})
  void schreibenderWeg_withTheCurrentStand_thenAnswers200WithTheRaisedVersion(final String weg)
      throws Exception {
    // Given — #411, Kriterium 8: Auf dem aktuellen Stand geht der Schritt unveraendert durch.
    liefertErhoehtenStand(weg);

    // When / Then
    mockMvc
        .perform(anfrageZu(weg).header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(ID))
        .andExpect(jsonPath("$.version").value(STAND + 1));
  }

  @Test
  void anlegen_givenABlankLastName_thenAnswersBadRequest() throws Exception {
    // When / Then
    mockMvc
        .perform(
            post(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("   ", "max@firma.de")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anlegen_givenABlankLastName_thenNamesTheFieldInTheProblemDetail() throws Exception {
    // When / Then — das Format stammt aus GlobalExceptionHandler; die Maske liest es aus.
    mockMvc
        .perform(
            post(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("   ", "max@firma.de")))
        .andExpect(jsonPath("$.fieldErrors.nachname").isArray());
  }

  @Test
  void anlegen_givenAMalformedEmail_thenNamesTheEmailFieldInTheProblemDetail() throws Exception {
    // When / Then — E8: „max@firma" ist keine Adresse, die dieses Projekt annimmt.
    mockMvc
        .perform(
            post(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("Mueller", "max@firma")))
        .andExpect(jsonPath("$.fieldErrors.email").isArray());
  }

  @Test
  void aendern_givenAnUnknownId_thenAnswersNotFound() throws Exception {
    // Given
    doThrow(new AnsprechpartnerNichtGefunden())
        .when(aendern)
        .aendern(FIRMA, ID, erwarteteDaten("Maier"), STAND, ANGEMELDET);

    // When / Then
    mockMvc
        .perform(anfrageZu("aendern").header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isNotFound());
  }
}
