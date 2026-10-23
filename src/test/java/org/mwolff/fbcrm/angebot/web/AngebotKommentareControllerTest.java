package org.mwolff.fbcrm.angebot.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.application.AngebotskommentarGeaendert;
import org.mwolff.fbcrm.angebot.application.AngebotskommentarGeloescht;
import org.mwolff.fbcrm.angebot.application.AngebotskommentarNichtGefunden;
import org.mwolff.fbcrm.angebot.application.AngebotskommentarUseCase;
import org.mwolff.fbcrm.angebot.application.KommentarMitVerfasser;
import org.mwolff.fbcrm.angebot.domain.Angebotskommentar;
import org.mwolff.fbcrm.common.Urheber;
import org.mwolff.fbcrm.common.web.Bedingungskopf;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
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
 * Kennung —, die Felder der Antwort (ohne {@code updatedAt}, Kriterium 9; mit dem Namen des
 * Verfassers, Plan #414 A7), die Grenze von 2.000 Zeichen am eingereichten Text (Plan E7) und dass
 * der Verfasser eines neuen Kommentars aus der Sitzung kommt und nicht aus dem Rumpf.
 *
 * <p>Die Sitzung steht hier als {@link SecurityContextHolder} mit der Kennung des Kontos als
 * Principal — so, wie {@code SessionAuthenticationFilter} sie hinterlegt; der {@link
 * AuthenticationPrincipalArgumentResolver} liest sie wie im Betrieb.
 *
 * <p><b>Welche Felder</b> die Antwort traegt, entscheidet dieser Layer; <b>wie</b> der Zeitpunkt
 * geschrieben wird, entscheidet der von Spring Boot gebaute ObjectMapper, den {@code
 * standaloneSetup} nicht kennt — hier steht deshalb nur, dass {@code createdAt} da ist. Die
 * Textform des Zeitstempels belegt {@code AngebotKommentarIT} am echten Weg.
 *
 * <p><b>Aendern und Loeschen legen den Stand vor</b> (#411, Kriterien 4 und 6; Plan #415, E3, E8):
 * ohne {@code If-Match} 428 und kein Aufruf des Anwendungsfalls, auf einem alten Stand 409 mit
 * {@code konflikt}, an einem geloeschten Kommentar 409 mit {@code art = "gelöscht"}. Das Schreiben
 * bleibt ohne Kopf (Kriterium 7).
 */
@ExtendWith(MockitoExtension.class)
class AngebotKommentareControllerTest {

  private static final long ANGEBOT = 11L;
  private static final long KOMMENTAR = 4L;
  private static final long ANGEMELDET = 21L;
  private static final long FREMD = 99L;
  private static final String VERFASSER = "Anna Adler";
  private static final String PFAD = "/api/angebote/{angebotId}/kommentare";
  private static final String PFAD_EINZELN = PFAD + "/{kommentarId}";
  private static final Instant ANGELEGT = Instant.parse("2026-09-30T08:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-09-30T09:30:00Z");

  private static final String RUMPF = "{\"text\":\"Bitte melden.\"}";

  /** Der Stand, den die Ansicht vorlegt (Plan #415, E2). */
  private static final long STAND = 3L;

  /** Derselbe Stand als starkes Etikett im Kopf {@code If-Match}. */
  private static final String VORGELEGT = "\"" + STAND + "\"";

  /** Das Konto, das vorher geaendert oder geloescht hat. */
  private static final long ANNA = 30L;

  private static final Instant VORHIN = Instant.parse("2026-09-30T09:12:00Z");

  @Mock private AngebotskommentarUseCase useCase;

  @Captor private ArgumentCaptor<String> text;

  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                Long.valueOf(ANGEMELDET), null, List.of()));
    mockMvc =
        MockMvcBuilders.standaloneSetup(new AngebotKommentareController(useCase))
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

  private static KommentarMitVerfasser kommentar(final String inhalt) {
    return new KommentarMitVerfasser(
        new Angebotskommentar(
            Long.valueOf(KOMMENTAR),
            ANGEBOT,
            ANGEMELDET,
            inhalt,
            ANGELEGT,
            GEAENDERT,
            STAND + 1,
            Long.valueOf(ANGEMELDET),
            null,
            null),
        VERFASSER);
  }

  private static String rumpfMit(final String inhalt) {
    return "{\"text\":\"" + inhalt + "\"}";
  }

  @Test
  void liste_thenAnswersWithTheKommentareTheirCreatedAtAndTheirVerfasser() throws Exception {
    // Given — Kriterium 3: die Antwort traegt den Zeitpunkt der Anlage; #409, Kriterium 11 den
    // Namen des Verfassers.
    when(useCase.liste(ANGEBOT)).thenReturn(List.of(kommentar("Bitte melden.")));

    // When / Then
    mockMvc
        .perform(get(PFAD, Long.valueOf(ANGEBOT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.kommentare[0].id").value(Long.valueOf(KOMMENTAR)))
        .andExpect(jsonPath("$.kommentare[0].text").value("Bitte melden."))
        .andExpect(jsonPath("$.kommentare[0].createdAt").exists())
        .andExpect(jsonPath("$.kommentare[0].updatedAt").doesNotExist())
        .andExpect(jsonPath("$.kommentare[0].verfasser").value(VERFASSER))
        .andExpect(jsonPath("$.kommentare[0].version").value(STAND + 1))
        .andExpect(jsonPath("$.kommentare[0].verfasserId").doesNotExist());
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
    when(useCase.schreibe(eq(ANGEBOT), eq(ANGEMELDET), text.capture()))
        .thenReturn(kommentar("Bitte melden."));

    // When / Then
    mockMvc
        .perform(
            post(PFAD, Long.valueOf(ANGEBOT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(Long.valueOf(KOMMENTAR)))
        .andExpect(jsonPath("$.text").value("Bitte melden."))
        .andExpect(jsonPath("$.verfasser").value(VERFASSER));
    assertThat(text.getValue()).isEqualTo("Bitte melden.");
  }

  @Test
  void schreiben_withAVerfasserInTheBody_thenTakesTheVerfasserFromTheSession() throws Exception {
    // Given — A7: wer im Rumpf einen anderen nennt, schreibt trotzdem unter eigenem Namen.
    when(useCase.schreibe(eq(ANGEBOT), eq(ANGEMELDET), text.capture()))
        .thenReturn(kommentar("Bitte melden."));

    // When
    mockMvc
        .perform(
            post(PFAD, Long.valueOf(ANGEBOT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"text\":\"Bitte melden.\",\"verfasserId\":"
                        + FREMD
                        + ",\"verfasser\":\"Bert\"}"))
        .andExpect(status().isCreated());

    // Then
    verify(useCase).schreibe(ANGEBOT, ANGEMELDET, "Bitte melden.");
  }

  @Test
  void schreiben_whenTheAngebotIsUnknown_thenAnswers404() throws Exception {
    // Given
    when(useCase.schreibe(eq(ANGEBOT), eq(ANGEMELDET), text.capture()))
        .thenThrow(new AngebotNichtGefunden());

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
    when(useCase.schreibe(eq(ANGEBOT), eq(ANGEMELDET), text.capture()))
        .thenReturn(kommentar(gerade));

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
    when(useCase.aendere(eq(ANGEBOT), eq(KOMMENTAR), eq(STAND), eq(ANGEMELDET), text.capture()))
        .thenReturn(kommentar("Zweiter Stand"));

    // When / Then
    mockMvc
        .perform(
            put(PFAD_EINZELN, Long.valueOf(ANGEBOT), Long.valueOf(KOMMENTAR))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Zweiter Stand\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.text").value("Zweiter Stand"))
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.verfasser").value(VERFASSER))
        .andExpect(jsonPath("$.version").value(STAND + 1));
    assertThat(text.getValue()).isEqualTo("Zweiter Stand");
  }

  @Test
  void aendern_withAnEmptyText_thenAnswers400WithAFieldError() throws Exception {
    // When / Then
    mockMvc
        .perform(
            put(PFAD_EINZELN, Long.valueOf(ANGEBOT), Long.valueOf(KOMMENTAR))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpfMit("")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.text").isNotEmpty());
    verifyNoInteractions(useCase);
  }

  @Test
  void aendern_whenTheKommentarIsUnknown_thenAnswers404() throws Exception {
    // Given — E5: auch ein Kommentar eines anderen Angebots ist hier unbekannt.
    when(useCase.aendere(eq(ANGEBOT), eq(KOMMENTAR), eq(STAND), eq(ANGEMELDET), text.capture()))
        .thenThrow(new AngebotskommentarNichtGefunden());

    // When / Then
    mockMvc
        .perform(
            put(PFAD_EINZELN, Long.valueOf(ANGEBOT), Long.valueOf(KOMMENTAR))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isNotFound());
  }

  @Test
  void loeschen_thenAnswers204AndPassesStandAndAccount() throws Exception {
    // When / Then
    mockMvc
        .perform(
            delete(PFAD_EINZELN, Long.valueOf(ANGEBOT), Long.valueOf(KOMMENTAR))
                .header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isNoContent());
    verify(useCase).loesche(ANGEBOT, KOMMENTAR, STAND, ANGEMELDET);
  }

  @Test
  void schreiben_withoutIfMatch_thenAnswers201() throws Exception {
    // Given — Kriterium 7: ein neuer Kommentar ist nie geschuetzt und braucht keinen Kopf.
    when(useCase.schreibe(eq(ANGEBOT), eq(ANGEMELDET), text.capture()))
        .thenReturn(kommentar("Bitte melden."));

    // When / Then
    mockMvc
        .perform(
            post(PFAD, Long.valueOf(ANGEBOT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.version").value(STAND + 1));
  }

  @Test
  void aendern_withoutIfMatch_thenAnswers428AndChangesNothing() throws Exception {
    // When / Then — E3: ohne vorgelegten Stand wird nicht geschrieben.
    mockMvc
        .perform(
            put(PFAD_EINZELN, Long.valueOf(ANGEBOT), Long.valueOf(KOMMENTAR))
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isPreconditionRequired())
        .andExpect(jsonPath("$.detail").value(Bedingungskopf.MELDUNG));
    verifyNoInteractions(useCase);
  }

  @Test
  void loeschen_withoutIfMatch_thenAnswers428AndDeletesNothing() throws Exception {
    // When / Then
    mockMvc
        .perform(delete(PFAD_EINZELN, Long.valueOf(ANGEBOT), Long.valueOf(KOMMENTAR)))
        .andExpect(status().isPreconditionRequired());
    verifyNoInteractions(useCase);
  }

  @Test
  void aendern_withAnOutdatedStand_thenAnswers409WithTheConflict() throws Exception {
    // Given — Kriterium 1: Anna hat den Kommentar inzwischen geaendert.
    when(useCase.aendere(eq(ANGEBOT), eq(KOMMENTAR), eq(STAND), eq(ANGEMELDET), text.capture()))
        .thenThrow(new AngebotskommentarGeaendert(ANNA, ANGEMELDET, VORHIN));

    // When / Then
    mockMvc
        .perform(
            put(PFAD_EINZELN, Long.valueOf(ANGEBOT), Long.valueOf(KOMMENTAR))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.konflikt.art").value("veraltet"))
        .andExpect(jsonPath("$.konflikt.geaendertVon").value("Anna"))
        .andExpect(jsonPath("$.konflikt.geaendertAm").value("2026-09-30T09:12:00Z"))
        .andExpect(jsonPath("$.konflikt.eigenesFenster").value(false));
  }

  @Test
  void loeschen_withAnOutdatedStand_thenAnswers409() throws Exception {
    // Given — Kriterium 6: ein inzwischen geaenderter Kommentar wird nicht geloescht.
    doThrow(new AngebotskommentarGeaendert(ANNA, ANGEMELDET, VORHIN))
        .when(useCase)
        .loesche(ANGEBOT, KOMMENTAR, STAND, ANGEMELDET);

    // When / Then
    mockMvc
        .perform(
            delete(PFAD_EINZELN, Long.valueOf(ANGEBOT), Long.valueOf(KOMMENTAR))
                .header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.konflikt.art").value("veraltet"));
  }

  @Test
  void aendern_aDeletedKommentar_thenAnswers409AsDeletedWithUrheberAndInstant() throws Exception {
    // Given — Kriterium 6, E16: 409 statt 404, damit die Meldung Urheber und Zeitpunkt nennt.
    when(useCase.aendere(eq(ANGEBOT), eq(KOMMENTAR), eq(STAND), eq(ANGEMELDET), text.capture()))
        .thenThrow(new AngebotskommentarGeloescht(ANNA, ANGEMELDET, VORHIN));

    // When / Then
    mockMvc
        .perform(
            put(PFAD_EINZELN, Long.valueOf(ANGEBOT), Long.valueOf(KOMMENTAR))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.konflikt.art").value("gelöscht"))
        .andExpect(jsonPath("$.konflikt.geaendertVon").value("Anna"))
        .andExpect(jsonPath("$.konflikt.geaendertAm").value("2026-09-30T09:12:00Z"));
  }

  @Test
  void loeschen_aKommentarDeletedInAnotherWindow_thenAnswers409AsOwnWindow() throws Exception {
    // Given — Kriterium 6: auch die eigene Loeschung aus einem anderen Fenster wird gemeldet.
    doThrow(new AngebotskommentarGeloescht(ANGEMELDET, ANGEMELDET, VORHIN))
        .when(useCase)
        .loesche(ANGEBOT, KOMMENTAR, STAND, ANGEMELDET);

    // When / Then
    mockMvc
        .perform(
            delete(PFAD_EINZELN, Long.valueOf(ANGEBOT), Long.valueOf(KOMMENTAR))
                .header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.konflikt.art").value("gelöscht"))
        .andExpect(jsonPath("$.konflikt.eigenesFenster").value(true));
  }
}
