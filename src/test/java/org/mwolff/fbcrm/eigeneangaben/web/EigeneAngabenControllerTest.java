package org.mwolff.fbcrm.eigeneangaben.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Urheber;
import org.mwolff.fbcrm.common.web.Bedingungskopf;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.mwolff.fbcrm.eigeneangaben.application.BankverbindungUngueltig;
import org.mwolff.fbcrm.eigeneangaben.application.EigeneAngabenGeaendert;
import org.mwolff.fbcrm.eigeneangaben.application.EigeneAngabenLesenUseCase;
import org.mwolff.fbcrm.eigeneangaben.application.EigeneAngabenPflegenUseCase;
import org.mwolff.fbcrm.eigeneangaben.application.GespeicherteEigeneAngaben;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngabenRepository;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngabenStand;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen Anwendungsfall und HTTP fuer die beiden Wege der Selbstauskunft.
 *
 * <p>Zwei Blickwinkel in einer Klasse, wie bei {@code FirmaControllerTest}: die Abbildung direkt an
 * den Methoden, und die Faelle, bei denen die <b>Form</b> der Antwort die Aussage ist (Statuscodes,
 * Feldfehler), ueber eine schlanke MockMvc-Strecke mit dem echten {@link GlobalExceptionHandler}.
 *
 * <p>Der {@code PUT} legt den Stand vor (#411, Kriterium 4; Plan #415, E2, E3, E14): ohne {@code
 * If-Match} 428, auf veraltetem Stand 409 mit der Erweiterung {@code konflikt}, sonst 200 mit dem
 * neuen Stand samt erhoehter Version.
 */
@ExtendWith(MockitoExtension.class)
class EigeneAngabenControllerTest {

  private static final String BERUFSBEZEICHNUNG = "Freiberuflicher Softwareentwickler";
  private static final String WEBADRESSE = "https://mwolff.org";
  private static final String PFAD = "/api/eigene-angaben";

  /** Der Stand, den die Maske vorlegt (Plan #415, E2). */
  private static final long STAND = 3L;

  /** Derselbe Stand als starkes Etikett im Kopf {@code If-Match}. */
  private static final String VORGELEGT = "\"" + STAND + "\"";

  /** Das angemeldete Konto. */
  private static final long ANGEMELDET = 31L;

  /** Das Konto, das inzwischen geaendert hat. */
  private static final long ANNA = 30L;

  private static final Instant ANNAS_AENDERUNG = Instant.parse("2026-10-06T08:42:00Z");

  @Mock private EigeneAngabenLesenUseCase lesen;
  @Mock private EigeneAngabenPflegenUseCase pflegen;

  @Captor private ArgumentCaptor<EigeneAngaben> eingereichte;

  private EigeneAngabenController controller;
  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    // Die Sitzung wie im Betrieb: die Kennung des Kontos als Principal (Plan #415, E14).
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                Long.valueOf(ANGEMELDET), null, List.of()));
    controller = new EigeneAngabenController(lesen, pflegen);
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

  /** Der Anwendungsfall nimmt jede gueltige Eingabe an und meldet den erhoehten Stand. */
  private void nimmtAn() {
    when(pflegen.pflege(any(), anyLong(), anyLong()))
        .thenReturn(new GespeicherteEigeneAngaben(gepflegt(), STAND + 1));
  }

  private static EigeneAngaben gepflegt() {
    return new EigeneAngaben(
        "Manfred Wolff",
        BERUFSBEZEICHNUNG,
        new Anschrift("Am Wall 1", "28195", "Bremen", "DE"),
        "manne@example.org",
        "0421 1234",
        WEBADRESSE,
        "75/123/45678",
        "DE123456789",
        "DE89370400440532013000",
        "COBADEFFXXX",
        "Manfred Wolff",
        null);
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

  private static String rumpf(final String name) {
    return feldRumpf("name", name);
  }

  private static String feldRumpf(final String feld, final String wert) {
    return "{\"" + feld + "\":\"" + wert + "\"}";
  }

  @Test
  void lesen_thenAnswersWithEveryValue() {
    // Given
    when(lesen.lese()).thenReturn(new GespeicherteEigeneAngaben(gepflegt(), STAND));

    // When
    final EigeneAngabenResponse antwort = controller.lesen();

    // Then — die Anschrift steht flach, wie bei der Firma.
    assertThat(antwort)
        .isEqualTo(
            new EigeneAngabenResponse(
                "Manfred Wolff",
                BERUFSBEZEICHNUNG,
                "Am Wall 1",
                "28195",
                "Bremen",
                "DE",
                "manne@example.org",
                "0421 1234",
                WEBADRESSE,
                "75/123/45678",
                "DE123456789",
                "DE89370400440532013000",
                "COBADEFFXXX",
                "Manfred Wolff",
                null,
                STAND));
  }

  @Test
  void lesen_givenABisherigeBankverbindung_thenAnswersWithItForReading() throws Exception {
    // Given — Plan #461, E13: der Freitext steht da, bis IBAN und Kontoinhaber gefuellt sind.
    final EigeneAngaben bisher =
        new EigeneAngaben(
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
            "Sparkasse Bremen, Konto 1234567");
    when(lesen.lese()).thenReturn(new GespeicherteEigeneAngaben(bisher, STAND));

    // When / Then
    mockMvc
        .perform(get(PFAD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bisherigeBankverbindung").value("Sparkasse Bremen, Konto 1234567"))
        .andExpect(jsonPath("$.iban").isEmpty())
        .andExpect(jsonPath("$.bankverbindung").doesNotExist());
  }

  @Test
  @DisplayName("Szenario: Eine ungültige IBAN wird abgewiesen")
  void szenarioEineUngueltigeIbanWirdAbgewiesen() throws Exception {
    // Given — ich pflege die eigenen Angaben; der Weg laeuft durch den echten Anwendungsfall, nur
    // der Bestand ist gemockt. Er traegt die bisherige Bankverbindung auf Stand 3.
    final EigeneAngabenRepository bestand = mock(EigeneAngabenRepository.class);
    final EigeneAngaben bisher =
        new EigeneAngaben(
            "Manfred Wolff",
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
            "Sparkasse Bremen, Konto 1234567");
    when(bestand.lies()).thenReturn(bisher);
    when(bestand.stand()).thenReturn(new EigeneAngabenStand(STAND, ANNA, ANNAS_AENDERUNG));
    final MockMvc echt =
        MockMvcBuilders.standaloneSetup(
                new EigeneAngabenController(
                    new EigeneAngabenLesenUseCase(bestand),
                    new EigeneAngabenPflegenUseCase(bestand, Clock.systemUTC())))
            .setControllerAdvice(new GlobalExceptionHandler(konto -> Optional.empty()))
            .setCustomArgumentResolvers(
                new Bedingungskopf.Leser(), new AuthenticationPrincipalArgumentResolver())
            .build();

    // When — eine IBAN mit falscher Pruefziffer wird gespeichert.
    echt.perform(
            put(PFAD)
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Manfred Wolff\",\"iban\":\"DE89 3704 0044 0532 0130 01\","
                        + "\"kontoinhaber\":\"Manfred Wolff\"}"))
        // Then — am Feld steht die Meldung, dass die IBAN ungueltig ist.
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.iban[0]").value(BankverbindungUngueltig.IBAN_UNGUELTIG));

    // Und die bisher hinterlegte Bankverbindung bleibt samt Version unveraendert.
    verify(bestand, never()).speichere(any(), any(), anyLong(), anyLong());
    echt.perform(get(PFAD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bisherigeBankverbindung").value("Sparkasse Bremen, Konto 1234567"))
        .andExpect(jsonPath("$.iban").isEmpty())
        .andExpect(jsonPath("$.version").value(STAND));
  }

  @Test
  void lesen_givenTheFreshInstance_thenAnswersWithNullInEveryField() throws Exception {
    // Given — Kriterium 1: der Bereich ist von Anfang an da, nur noch ohne Inhalt.
    when(lesen.lese()).thenReturn(new GespeicherteEigeneAngaben(leer(), 0L));

    // When / Then
    mockMvc
        .perform(get("/api/eigene-angaben"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").isEmpty())
        .andExpect(jsonPath("$.strasse").isEmpty())
        .andExpect(jsonPath("$.berufsbezeichnung").isEmpty())
        .andExpect(jsonPath("$.webadresse").isEmpty())
        .andExpect(jsonPath("$.version").value(0));
  }

  @Test
  void pflegen_thenPassesTheValuesStandAndKontoOn() {
    // Given
    nimmtAn();

    // When
    final EigeneAngabenResponse antwort =
        controller.pflegen(
            new Bedingungskopf(STAND),
            ANGEMELDET,
            new EigeneAngabenRequest(
                "Manfred Wolff",
                BERUFSBEZEICHNUNG,
                "Am Wall 1",
                "28195",
                "Bremen",
                "DE",
                "manne@example.org",
                "0421 1234",
                WEBADRESSE,
                "75/123/45678",
                "DE123456789",
                "DE89 3704 0044 0532 0130 00",
                "COBADEFFXXX",
                "Manfred Wolff"));

    // Then — die IBAN geht so weiter, wie sie kam; pruefen und schreiben ist Sache der Fachschicht.
    verify(pflegen).pflege(eingereichte.capture(), eq(STAND), eq(ANGEMELDET));
    assertThat(eingereichte.getValue())
        .usingRecursiveComparison()
        .ignoringFields("iban")
        .isEqualTo(gepflegt());
    assertThat(eingereichte.getValue().iban()).isEqualTo("DE89 3704 0044 0532 0130 00");
    assertThat(antwort.version()).isEqualTo(STAND + 1);
  }

  @Test
  void pflegen_givenAnEmptyBody_thenPassesAbsentValuesOn() {
    // Given
    nimmtAn();

    // When — jede Fachangabe darf fehlen.
    controller.pflegen(
        new Bedingungskopf(STAND),
        ANGEMELDET,
        new EigeneAngabenRequest(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null));

    // Then
    verify(pflegen).pflege(eingereichte.capture(), eq(STAND), eq(ANGEMELDET));
    assertThat(eingereichte.getValue()).isEqualTo(leer());
  }

  @Test
  void pflegen_withTheCurrentStand_thenAnswers200WithTheRaisedVersion() throws Exception {
    // Given — #411, Kriterium 8: auf dem aktuellen Stand merkt man vom Schutz nichts.
    nimmtAn();

    // When / Then
    mockMvc
        .perform(
            put(PFAD)
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("Manfred Wolff")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Manfred Wolff"))
        .andExpect(jsonPath("$.version").value(STAND + 1));
  }

  @Test
  void pflegen_withoutIfMatch_thenAnswers428AndChangesNothing() throws Exception {
    // When / Then — E3: ohne vorgelegten Stand wird nicht geschrieben.
    mockMvc
        .perform(put(PFAD).contentType(MediaType.APPLICATION_JSON).content(rumpf("Manfred Wolff")))
        .andExpect(status().isPreconditionRequired())
        .andExpect(jsonPath("$.detail").value(Bedingungskopf.MELDUNG));
    verifyNoInteractions(pflegen, lesen);
  }

  @Test
  void pflegen_withAnOutdatedStand_thenAnswers409WithTheConflict() throws Exception {
    // Given — #411, Kriterium 4: Anna hat die Angaben inzwischen gespeichert.
    when(pflegen.pflege(any(), anyLong(), anyLong()))
        .thenThrow(new EigeneAngabenGeaendert(ANNA, ANGEMELDET, ANNAS_AENDERUNG));

    // When / Then
    mockMvc
        .perform(
            put(PFAD)
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("Manfred Wolff")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.konflikt.art").value("veraltet"))
        .andExpect(jsonPath("$.konflikt.geaendertVon").value("Anna"))
        .andExpect(jsonPath("$.konflikt.geaendertAm").value("2026-10-06T08:42:00Z"))
        .andExpect(jsonPath("$.konflikt.eigenesFenster").value(false));
  }

  @Test
  void pflegen_givenAnOverlongField_thenAnswersWithTheFieldError() throws Exception {
    // When / Then — die Laengengrenze ist dieselbe wie im Schema.
    mockMvc
        .perform(
            put(PFAD)
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("N".repeat(201))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.name").isArray());
  }

  @Test
  void pflegen_givenAnOverlongJobTitle_thenAnswersWithTheFieldError() throws Exception {
    // When / Then — 200 Zeichen wie die Spalte (#160, Kriterium 29).
    mockMvc
        .perform(
            put(PFAD)
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(feldRumpf("berufsbezeichnung", "B".repeat(201))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.berufsbezeichnung").isArray());
  }

  @Test
  void pflegen_givenAnOverlongWebAddress_thenAnswersWithTheFieldError() throws Exception {
    // When / Then — 200 Zeichen wie die Spalte (#160, Kriterium 29).
    mockMvc
        .perform(
            put(PFAD)
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(feldRumpf("webadresse", "W".repeat(201))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.webadresse").isArray());
  }
}
