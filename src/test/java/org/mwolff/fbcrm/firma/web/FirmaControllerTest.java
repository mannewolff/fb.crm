package org.mwolff.fbcrm.firma.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Urheber;
import org.mwolff.fbcrm.common.web.Bedingungskopf;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.mwolff.fbcrm.firma.application.FirmaAendernUseCase;
import org.mwolff.fbcrm.firma.application.FirmaAnlegenUseCase;
import org.mwolff.fbcrm.firma.application.FirmaDaten;
import org.mwolff.fbcrm.firma.application.FirmaGeaendert;
import org.mwolff.fbcrm.firma.application.FirmaLesenUseCase;
import org.mwolff.fbcrm.firma.application.FirmaMitAnsprechpartnern;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.application.FirmaStilllegenUseCase;
import org.mwolff.fbcrm.firma.application.FirmaZeile;
import org.mwolff.fbcrm.firma.application.FirmenUebersicht;
import org.mwolff.fbcrm.firma.application.FirmenUebersichtUseCase;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen Anwendungsfall und HTTP fuer die Firma.
 *
 * <p>Zwei Blickwinkel in einer Klasse: Die Abbildung von Anfrage und Antwort wird direkt an den
 * Methoden geprueft — schnell und ohne Rahmen. Die beiden Faelle, bei denen die <b>Form</b> der
 * Antwort die Aussage ist (Feldfehler und unbekannte Kennung), laufen durch eine schlanke
 * MockMvc-Strecke mit dem echten {@link GlobalExceptionHandler}: Nur dort zeigt sich, dass ein
 * Validierungsfehler wirklich als {@code fieldErrors} beim Feld {@code name} ankommt.
 *
 * <p><b>Aendern, Stilllegen und Wiederaktivieren legen den Stand vor</b> (#411, Kriterium 4; Plan
 * #415, E3, E14, E18): Fuer jeden der drei Wege gilt dasselbe Dreigespann — ohne {@code If-Match}
 * 428, auf einem alten Stand 409 mit {@code konflikt}, auf dem aktuellen 200 mit dem erhoehten
 * Stand. Das Anlegen geht ohne Kopf durch (Kriterium 7).
 */
@ExtendWith(MockitoExtension.class)
class FirmaControllerTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");

  /** Der Stand, den die Oberflaeche vorlegt (Plan #415, E2). */
  private static final long STAND = 3L;

  /** Derselbe Stand als starkes Etikett im Kopf {@code If-Match}. */
  private static final String VORGELEGT = "\"" + STAND + "\"";

  /** Das angemeldete Konto. */
  private static final long ANGEMELDET = 31L;

  /** Das Konto, das inzwischen geaendert hat. */
  private static final long ANNA = 30L;

  private static final Instant ANNAS_AENDERUNG = Instant.parse("2026-10-06T08:42:00Z");

  @Mock private FirmenUebersichtUseCase uebersicht;
  @Mock private FirmaLesenUseCase lesen;
  @Mock private FirmaAnlegenUseCase anlegen;
  @Mock private FirmaAendernUseCase aendern;
  @Mock private FirmaStilllegenUseCase stilllegen;

  private FirmaController controller;
  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    // Die Sitzung wie im Betrieb: die Kennung des Kontos als Principal (Plan #415, E14).
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                Long.valueOf(ANGEMELDET), null, List.of()));
    controller = new FirmaController(uebersicht, lesen, anlegen, aendern, stilllegen);
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

  private static Firma adlerAg(final boolean aktiv) {
    return adlerAg(aktiv, 0L);
  }

  private static Firma adlerAg(final boolean aktiv, final long version) {
    return new Firma(
        7L,
        "Adler AG",
        new Anschrift("Am Wall 1", "28195", "Bremen", "DE"),
        "75/123/45678",
        "DE123456789",
        aktiv,
        ANGELEGT,
        ANGELEGT,
        version,
        null);
  }

  private static Ansprechpartner maxMueller(final boolean aktiv) {
    return new Ansprechpartner(
        3L,
        7L,
        "Max",
        "Mueller",
        "Einkauf",
        "max@firma.de",
        "0421 1234",
        "0170 1234",
        aktiv,
        ANGELEGT,
        ANGELEGT,
        0L,
        null);
  }

  private static FirmaRequest anfrage(final String name) {
    return new FirmaRequest(
        name, "Am Wall 1", "28195", "Bremen", "DE", "75/123/45678", "DE123456789");
  }

  private static FirmaDaten erwarteteDaten(final String name) {
    return new FirmaDaten(
        name, new Anschrift("Am Wall 1", "28195", "Bremen", "DE"), "75/123/45678", "DE123456789");
  }

  @Test
  void uebersicht_thenAnswersWithRowsAndTheTotal() {
    // Given
    when(uebersicht.uebersicht("adler", true))
        .thenReturn(
            new FirmenUebersicht(List.of(new FirmaZeile(7L, "Adler AG", "Bremen", 2L, false)), 9L));

    // When
    final FirmenUebersichtResponse antwort = controller.uebersicht("adler", true);

    // Then
    assertThat(antwort)
        .isEqualTo(
            new FirmenUebersichtResponse(
                List.of(new FirmaZeileResponse(7L, "Adler AG", "Bremen", 2L, false)), 9L));
  }

  @Test
  void uebersicht_givenNoParameters_thenSearchesForEverythingWithoutRetiredFirmen()
      throws Exception {
    // Given — die Standardwerte stehen an der Schnittstelle, nicht im Anwendungsfall.
    when(uebersicht.uebersicht("", false)).thenReturn(new FirmenUebersicht(List.of(), 0L));

    // When
    mockMvc.perform(get("/api/firmen")).andExpect(status().isOk());

    // Then
    verify(uebersicht).uebersicht("", false);
  }

  @Test
  void lesen_thenAnswersWithTheFirmaAndItsContacts() {
    // Given
    when(lesen.lese(7L))
        .thenReturn(new FirmaMitAnsprechpartnern(adlerAg(true), List.of(maxMueller(false))));

    // When
    final FirmaResponse antwort = controller.lesen(7L);

    // Then
    assertThat(antwort)
        .isEqualTo(
            new FirmaResponse(
                7L,
                "Adler AG",
                "Am Wall 1",
                "28195",
                "Bremen",
                "DE",
                "Deutschland",
                "75/123/45678",
                "DE123456789",
                true,
                List.of(
                    new AnsprechpartnerResponse(
                        3L,
                        "Max",
                        "Mueller",
                        "Einkauf",
                        "max@firma.de",
                        "0421 1234",
                        "0170 1234",
                        false,
                        0L)),
                0L));
  }

  @Test
  void anlegen_thenPassesTheTrimmedRequestToTheUseCase() {
    // Given
    when(anlegen.anlegen(erwarteteDaten("Adler AG"))).thenReturn(adlerAg(true));

    // When
    controller.anlegen(anfrage("Adler AG"));

    // Then
    verify(anlegen).anlegen(erwarteteDaten("Adler AG"));
  }

  @Test
  void anlegen_thenAnswersWithTheNewFirmaAndNoContactsYet() {
    // Given
    when(anlegen.anlegen(erwarteteDaten("Adler AG"))).thenReturn(adlerAg(true));

    // When
    final FirmaResponse antwort = controller.anlegen(anfrage("Adler AG"));

    // Then — Kriterium 7: die Kennung fuehrt die Oberflaeche zur Detailansicht.
    assertThat(antwort)
        .extracting(FirmaResponse::id, FirmaResponse::ansprechpartner)
        .containsExactly(7L, List.of());
  }

  @Test
  void anlegen_thenAnswersWithStatusCreated() throws Exception {
    // Given
    when(anlegen.anlegen(erwarteteDaten("Adler AG"))).thenReturn(adlerAg(true));

    // When / Then
    mockMvc
        .perform(
            post("/api/firmen").contentType(MediaType.APPLICATION_JSON).content(rumpf("Adler AG")))
        .andExpect(status().isCreated());
  }

  @Test
  void aendern_thenPassesIdRequestStandAndAccountToTheUseCase() {
    // Given
    when(lesen.lese(7L)).thenReturn(new FirmaMitAnsprechpartnern(adlerAg(true), List.of()));

    // When
    controller.aendern(7L, new Bedingungskopf(STAND), ANGEMELDET, anfrage("Adler GmbH"));

    // Then
    verify(aendern).aendern(7L, erwarteteDaten("Adler GmbH"), STAND, ANGEMELDET);
  }

  @Test
  void anlegen_withoutIfMatch_thenAnswersCreated() throws Exception {
    // Given — #411, Kriterium 7: das Anlegen bleibt frei, auch mit dem Leser des Kopfs im Spiel.
    when(anlegen.anlegen(erwarteteDaten("Adler AG"))).thenReturn(adlerAg(true));

    // When / Then
    mockMvc
        .perform(
            post("/api/firmen").contentType(MediaType.APPLICATION_JSON).content(rumpf("Adler AG")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.version").value(0));
  }

  /** Die Anfrage eines schreibenden Wegs, noch ohne Kopf {@code If-Match}. */
  private static MockHttpServletRequestBuilder anfrageZu(final String weg) {
    return switch (weg) {
      case "aendern" ->
          put("/api/firmen/7").contentType(MediaType.APPLICATION_JSON).content(rumpf("Adler AG"));
      case "stilllegen" -> post("/api/firmen/7/stilllegen");
      case "aktivieren" -> post("/api/firmen/7/aktivieren");
      default -> throw new IllegalArgumentException(weg);
    };
  }

  /** Der Anwendungsfall des Wegs wirft den Konflikt — mit Stand und Konto aus der Anfrage. */
  private void wirftKonflikt(final String weg) {
    final FirmaGeaendert konflikt = new FirmaGeaendert(ANNA, ANGEMELDET, ANNAS_AENDERUNG);
    switch (weg) {
      case "aendern" ->
          doThrow(konflikt).when(aendern).aendern(eq(7L), any(), eq(STAND), eq(ANGEMELDET));
      case "stilllegen" -> doThrow(konflikt).when(stilllegen).stilllegen(7L, STAND, ANGEMELDET);
      case "aktivieren" -> doThrow(konflikt).when(stilllegen).aktivieren(7L, STAND, ANGEMELDET);
      default -> throw new IllegalArgumentException(weg);
    }
  }

  /** Prueft, dass der Anwendungsfall des Wegs Stand und Konto bekommen hat. */
  private void bekamStandUndKonto(final String weg) {
    switch (weg) {
      case "aendern" -> verify(aendern).aendern(eq(7L), any(), eq(STAND), eq(ANGEMELDET));
      case "stilllegen" -> verify(stilllegen).stilllegen(7L, STAND, ANGEMELDET);
      case "aktivieren" -> verify(stilllegen).aktivieren(7L, STAND, ANGEMELDET);
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
    verifyNoInteractions(aendern, stilllegen, lesen);
  }

  @ParameterizedTest
  @ValueSource(strings = {"aendern", "stilllegen", "aktivieren"})
  void schreibenderWeg_withAnOutdatedStand_thenAnswers409WithTheConflict(final String weg)
      throws Exception {
    // Given — #411, Kriterium 4: Anna hat die Firma inzwischen geaendert.
    wirftKonflikt(weg);

    // When / Then
    mockMvc
        .perform(anfrageZu(weg).header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.konflikt.art").value("veraltet"))
        .andExpect(jsonPath("$.konflikt.geaendertVon").value("Anna"))
        .andExpect(jsonPath("$.konflikt.geaendertAm").value("2026-10-06T08:42:00Z"))
        .andExpect(jsonPath("$.konflikt.eigenesFenster").value(false));
    verifyNoInteractions(lesen);
  }

  @ParameterizedTest
  @ValueSource(strings = {"aendern", "stilllegen", "aktivieren"})
  void schreibenderWeg_withTheCurrentStand_thenAnswers200WithTheRaisedVersion(final String weg)
      throws Exception {
    // Given — #411, Kriterium 8: Auf dem aktuellen Stand geht der Schritt unveraendert durch; die
    // Antwort traegt den erhoehten Stand, den die Oberflaeche beim naechsten Schritt vorlegt.
    when(lesen.lese(7L))
        .thenReturn(new FirmaMitAnsprechpartnern(adlerAg(true, STAND + 1), List.of()));

    // When
    mockMvc
        .perform(anfrageZu(weg).header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(STAND + 1));

    // Then
    bekamStandUndKonto(weg);
  }

  @Test
  void anlegen_givenABlankName_thenAnswersBadRequest() throws Exception {
    // When / Then
    mockMvc
        .perform(post("/api/firmen").contentType(MediaType.APPLICATION_JSON).content(rumpf("   ")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anlegen_givenABlankName_thenNamesTheFieldInTheProblemDetail() throws Exception {
    // When / Then — das Format stammt aus GlobalExceptionHandler; die Maske liest es aus.
    mockMvc
        .perform(post("/api/firmen").contentType(MediaType.APPLICATION_JSON).content(rumpf("   ")))
        .andExpect(jsonPath("$.fieldErrors.name").isArray());
  }

  @Test
  void lesen_givenAnUnknownId_thenAnswersNotFound() throws Exception {
    // Given
    when(lesen.lese(4711L)).thenThrow(new FirmaNichtGefunden());

    // When / Then
    mockMvc.perform(get("/api/firmen/4711")).andExpect(status().isNotFound());
  }

  @Test
  void anlegen_givenAnUnknownCountryCode_thenNamesTheFieldLand() throws Exception {
    // When / Then — Plan #461, E11: nur ein Kuerzel der Laenderliste.
    mockMvc
        .perform(
            post("/api/firmen")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("Adler AG", "\"XX\"")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.land").isArray());
    verifyNoInteractions(anlegen);
  }

  @Test
  void anlegen_givenNoCountry_thenNamesTheFieldLand() throws Exception {
    // When / Then — das Land ist Pflicht.
    mockMvc
        .perform(
            post("/api/firmen")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("Adler AG", "null")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.land").isArray());
    verifyNoInteractions(anlegen);
  }

  @Test
  void anlegen_givenAustria_thenAnswersWithTheCodeAndTheGermanName() throws Exception {
    // Given
    final FirmaDaten inOesterreich =
        new FirmaDaten(
            "Adler AG",
            new Anschrift("Am Wall 1", "28195", "Bremen", "AT"),
            "75/123/45678",
            "DE123456789");
    when(anlegen.anlegen(inOesterreich))
        .thenReturn(
            new Firma(
                7L,
                "Adler AG",
                inOesterreich.anschrift(),
                "75/123/45678",
                "DE123456789",
                true,
                ANGELEGT,
                ANGELEGT,
                0L,
                null));

    // When / Then
    mockMvc
        .perform(
            post("/api/firmen")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("Adler AG", "\"AT\"")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.land").value("AT"))
        .andExpect(jsonPath("$.landName").value("Österreich"));
  }

  private static String rumpf(final String name) {
    return rumpf(name, "\"DE\"");
  }

  private static String rumpf(final String name, final String land) {
    return "{\"name\":\""
        + name
        + "\",\"strasse\":\"Am Wall 1\",\"plz\":\"28195\","
        + "\"ort\":\"Bremen\",\"land\":"
        + land
        + ",\"steuernummer\":\"75/123/45678\","
        + "\"umsatzsteuerId\":\"DE123456789\"}";
  }
}
