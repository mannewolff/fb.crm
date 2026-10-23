package org.mwolff.fbcrm.rechnung.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import org.mwolff.fbcrm.rechnung.application.BriefbogenGeaendert;
import org.mwolff.fbcrm.rechnung.application.BriefbogenLesenUseCase;
import org.mwolff.fbcrm.rechnung.application.BriefbogenPflegenUseCase;
import org.mwolff.fbcrm.rechnung.application.Briefbogenstand;
import org.mwolff.fbcrm.rechnung.application.BriefpapierNichtAnnehmbar;
import org.mwolff.fbcrm.rechnung.application.BriefpapierNichtGefunden;
import org.mwolff.fbcrm.rechnung.application.BriefpapierUseCase;
import org.mwolff.fbcrm.rechnung.application.ProbedruckUseCase;
import org.mwolff.fbcrm.rechnung.domain.Briefbogen;
import org.mwolff.fbcrm.rechnung.domain.BriefbogenRepository;
import org.mwolff.fbcrm.rechnung.domain.Randmasse;
import org.mwolff.fbcrm.rechnung.domain.Zeilenstand;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen Anwendungsfall und HTTP fuer Stand und Pflege des Briefbogens und fuer
 * das Hochladen und Entfernen des Briefpapiers und den Probedruck (fachliche Quelle #361, Kriterien
 * 1, 2, 9, 13, 15 und 16; Plan #363, E1, E5, E9 und E14).
 *
 * <p>Wie {@code RechnungseinstellungenControllerTest} ueber eine schlanke MockMvc-Strecke mit dem
 * echten {@link GlobalExceptionHandler}: Hier ist die <b>Form</b> der Antwort die Aussage. Fuer den
 * unmoeglichen Rand laeuft der echte {@link BriefbogenPflegenUseCase} gegen einen gemockten Bestand
 * — die Abweisung entsteht in der Fachschicht, und erst so zeigt sich, dass sie als 422 am Feld
 * ankommt und den Grenzwert nennt.
 *
 * <p>Die drei schreibenden Wege legen den Stand vor (#411, Kriterium 4; Plan #415, E2, E3, E11,
 * E14): je Weg ohne {@code If-Match} 428, auf veraltetem Stand 409, sonst 200 mit erhoehter
 * Version.
 */
@ExtendWith(MockitoExtension.class)
class BriefbogenControllerTest {

  private static final String PFAD = "/api/briefpapier";

  /** Die Raender aus Kriterium 15 in Zehntel-Millimetern. */
  private static final Randmasse VORGABE = new Randmasse(250, 200, 985, 200, 200, 200);

  private static final Randmasse EINGESTELLT = new Randmasse(300, 150, 1_000, 250, 180, 220);

  private static final byte[] PDF = "%PDF-1.7\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

  /** Der Stand, den die Maske vorlegt (Plan #415, E2). */
  private static final long STAND = 3L;

  /** Derselbe Stand als starkes Etikett im Kopf {@code If-Match}. */
  private static final String VORGELEGT = "\"" + STAND + "\"";

  /** Das angemeldete Konto. */
  private static final long ANGEMELDET = 31L;

  /** Das Konto, das inzwischen geaendert hat. */
  private static final long ANNA = 30L;

  private static final Instant ANNAS_AENDERUNG = Instant.parse("2026-10-19T08:42:00Z");

  @Mock private BriefbogenLesenUseCase lesen;
  @Mock private BriefbogenPflegenUseCase pflegen;
  @Mock private BriefbogenRepository bestand;
  @Mock private BriefpapierUseCase briefpapier;
  @Mock private ProbedruckUseCase probedruck;

  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    // Die Sitzung wie im Betrieb: die Kennung des Kontos als Principal (Plan #415, E14).
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                Long.valueOf(ANGEMELDET), null, List.of()));
    mockMvc = strecke(new BriefbogenController(lesen, pflegen, briefpapier, probedruck));
  }

  @AfterEach
  void raeumeDieSitzungAb() {
    SecurityContextHolder.clearContext();
  }

  private static MockMvc strecke(final BriefbogenController controller) {
    return MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(
            new GlobalExceptionHandler(
                konto -> konto == ANNA ? Optional.of(new Urheber(ANNA, "Anna")) : Optional.empty()))
        .setCustomArgumentResolvers(
            new Bedingungskopf.Leser(), new AuthenticationPrincipalArgumentResolver())
        .build();
  }

  /**
   * Der Rumpf des {@code PUT}; die Werte kommen als JSON-Schnipsel, damit auch {@code null} geht.
   */
  private static String rumpf(
      final String absenderzeile,
      final String steuerBankZeile,
      final String links,
      final String rechts,
      final String inhaltOben,
      final String unten,
      final String folgeOben,
      final String folgeUnten) {
    return "{\"absenderzeile\":%s,\"steuerBankZeile\":%s,\"links\":%s,\"rechts\":%s,"
            .formatted(absenderzeile, steuerBankZeile, links, rechts)
        + "\"inhaltOben\":%s,\"unten\":%s,\"folgeOben\":%s,\"folgeUnten\":%s}"
            .formatted(inhaltOben, unten, folgeOben, folgeUnten);
  }

  /** Ein gueltiger Rumpf, in dem genau ein Feld durch den genannten Wert ersetzt ist. */
  private static String rumpfMit(final String feld, final String wert) {
    return rumpf(
        "absenderzeile".equals(feld) ? wert : "true",
        "steuerBankZeile".equals(feld) ? wert : "false",
        "links".equals(feld) ? wert : "300",
        "rechts".equals(feld) ? wert : "150",
        "inhaltOben".equals(feld) ? wert : "1000",
        "unten".equals(feld) ? wert : "250",
        "folgeOben".equals(feld) ? wert : "180",
        "folgeUnten".equals(feld) ? wert : "220");
  }

  @Test
  void lesen_thenAnswersWithTheStandWithoutAKey() throws Exception {
    // Given
    when(lesen.lese())
        .thenReturn(
            new Briefbogenstand(true, "kopf.pdf", 98_765L, 2, true, false, EINGESTELLT, STAND + 1));

    // When / Then
    final String inhalt =
        mockMvc
            .perform(get(PFAD))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.briefpapier").value(true))
            .andExpect(jsonPath("$.dateiname").value("kopf.pdf"))
            .andExpect(jsonPath("$.groesse").value(98_765))
            .andExpect(jsonPath("$.seiten").value(2))
            .andExpect(jsonPath("$.absenderzeile").value(true))
            .andExpect(jsonPath("$.steuerBankZeile").value(false))
            .andExpect(jsonPath("$.links").value(300))
            .andExpect(jsonPath("$.rechts").value(150))
            .andExpect(jsonPath("$.inhaltOben").value(1_000))
            .andExpect(jsonPath("$.unten").value(250))
            .andExpect(jsonPath("$.folgeOben").value(180))
            .andExpect(jsonPath("$.folgeUnten").value(220))
            .andExpect(jsonPath("$.version").value(STAND + 1))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Then
    assertThat(inhalt).doesNotContainIgnoringCase("schluessel");
  }

  @Test
  void lesen_givenNoBriefpapier_thenAnswersWithEmptyFileDetails() throws Exception {
    // Given
    when(lesen.lese())
        .thenReturn(new Briefbogenstand(false, null, null, null, true, true, VORGABE, STAND + 1));

    // When / Then
    mockMvc
        .perform(get(PFAD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.briefpapier").value(false))
        .andExpect(jsonPath("$.dateiname").isEmpty())
        .andExpect(jsonPath("$.groesse").isEmpty())
        .andExpect(jsonPath("$.seiten").isEmpty())
        .andExpect(jsonPath("$.inhaltOben").value(985));
  }

  @Test
  void pflegen_thenPassesTheValuesOnAndAnswersWithTheNewStand() throws Exception {
    // Given
    when(pflegen.pflege(true, false, EINGESTELLT, STAND, ANGEMELDET))
        .thenReturn(
            new Briefbogenstand(false, null, null, null, true, false, EINGESTELLT, STAND + 1));

    // When / Then
    mockMvc
        .perform(
            put(PFAD)
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpfMit("", "")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.absenderzeile").value(true))
        .andExpect(jsonPath("$.steuerBankZeile").value(false))
        .andExpect(jsonPath("$.links").value(300))
        .andExpect(jsonPath("$.folgeUnten").value(220));
    verify(pflegen).pflege(true, false, EINGESTELLT, STAND, ANGEMELDET);
  }

  @ParameterizedTest
  @ValueSource(strings = {"links", "rechts", "inhaltOben", "unten", "folgeOben", "folgeUnten"})
  void pflegen_givenANegativeMargin_thenAnswersWith400AndTheFieldError(final String feld)
      throws Exception {
    // When / Then — Kriterium 13, erster Satz: an der Schnittstelle, nicht erst am
    // Kompaktkonstruktor von Randmasse — der antwortete mit 500.
    mockMvc
        .perform(
            put(PFAD)
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpfMit(feld, "-1")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors." + feld).isArray());
    verifyNoInteractions(pflegen);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "absenderzeile",
        "steuerBankZeile",
        "links",
        "rechts",
        "inhaltOben",
        "unten",
        "folgeOben",
        "folgeUnten"
      })
  void pflegen_givenAMissingValue_thenAnswersWith400AndTheFieldError(final String feld)
      throws Exception {
    // When / Then — die Maske schickt den ganzen Satz; ein fehlender Wert ist keine Vorgabe.
    mockMvc
        .perform(
            put(PFAD)
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpfMit(feld, "null")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors." + feld).isArray());
    verifyNoInteractions(pflegen);
  }

  @Test
  void pflegen_givenAnImpossibleMargin_thenAnswersWith422AtItsFieldNamingTheLargestValue()
      throws Exception {
    // Given — die echte Pflege gegen einen Bestand mit den Vorgaben; allein der untere Rand
    // weicht ab und traegt hoechstens 70,0 mm (RandgrenzenTest).
    when(bestand.stand())
        .thenReturn(new Zeilenstand(STAND, ANNA, Instant.parse("2026-10-19T08:00:00Z")));
    when(bestand.lies()).thenReturn(new Briefbogen(null, true, true, VORGABE));
    final MockMvc echt =
        strecke(
            new BriefbogenController(
                lesen,
                new BriefbogenPflegenUseCase(
                    bestand, Clock.fixed(Instant.parse("2026-10-19T09:30:00Z"), ZoneOffset.UTC)),
                briefpapier,
                probedruck));

    // When / Then
    echt.perform(
            put(PFAD)
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("true", "true", "250", "200", "985", "701", "200", "200")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.fieldErrors.unten[0]")
                .value(
                    "Bei den übrigen eingestellten Rändern darf dieser Rand höchstens 70,0 mm"
                        + " betragen."));
    verify(bestand, never()).speichere(any(), any(), anyLong(), anyLong());
  }

  // --- /datei ------------------------------------------------------------------------------

  @Test
  void hochladen_thenReichtNameUndBytesDesTeilsDateiWeiterUndAntwortetMitDemStand()
      throws Exception {
    // Given
    when(briefpapier.ladeHoch("kopf.pdf", PDF, STAND, ANGEMELDET))
        .thenReturn(
            new Briefbogenstand(true, "kopf.pdf", 4_321L, 1, false, true, EINGESTELLT, STAND + 1));

    // When / Then
    final String inhalt =
        mockMvc
            .perform(
                multipart(PFAD + "/datei")
                    .file(new MockMultipartFile("datei", "kopf.pdf", "text/html", PDF))
                    .header(HttpHeaders.IF_MATCH, VORGELEGT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.briefpapier").value(true))
            .andExpect(jsonPath("$.dateiname").value("kopf.pdf"))
            .andExpect(jsonPath("$.groesse").value(4_321))
            .andExpect(jsonPath("$.seiten").value(1))
            .andExpect(jsonPath("$.absenderzeile").value(false))
            .andExpect(jsonPath("$.links").value(300))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Then
    assertThat(inhalt).doesNotContainIgnoringCase("schluessel");
  }

  @Test
  void hochladen_givenOhneDateinamen_thenReichtEinenLeerenWeiter() throws Exception {
    // Given — die Saeuberung und der Ersatzname gehoeren dem Anwendungsfall.
    when(briefpapier.ladeHoch("", PDF, STAND, ANGEMELDET))
        .thenReturn(
            new Briefbogenstand(
                true, "briefpapier.pdf", 4_321L, 1, true, true, VORGABE, STAND + 1));

    // When / Then
    mockMvc
        .perform(
            multipart(PFAD + "/datei")
                .file(new MockMultipartFile("datei", null, "application/pdf", PDF))
                .header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isOk());
    verify(briefpapier).ladeHoch("", PDF, STAND, ANGEMELDET);
  }

  @Test
  void hochladen_givenOhneTeilDatei_thenAntwortetMit400() throws Exception {
    // When / Then
    mockMvc
        .perform(
            multipart(PFAD + "/datei")
                .file(new MockMultipartFile("anderes", "kopf.pdf", "application/pdf", PDF))
                .header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(briefpapier);
  }

  @Test
  void hochladen_givenEinAbgewiesenesPdf_thenAntwortetMit422AmFeldDatei() throws Exception {
    // Given — Szenario „Ungeeignetes PDF".
    when(briefpapier.ladeHoch(any(), any(), eq(STAND), eq(ANGEMELDET)))
        .thenThrow(BriefpapierNichtAnnehmbar.falschesFormat());

    // When / Then
    mockMvc
        .perform(
            multipart(PFAD + "/datei")
                .file(new MockMultipartFile("datei", "quer.pdf", "application/pdf", PDF))
                .header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.fieldErrors.datei[0]").value(BriefpapierNichtAnnehmbar.MELDUNG_FORMAT));
  }

  @Test
  void entfernen_thenAntwortetMitDemStandOhneBriefpapier() throws Exception {
    // Given
    when(briefpapier.entferne(STAND, ANGEMELDET))
        .thenReturn(
            new Briefbogenstand(false, null, null, null, false, true, EINGESTELLT, STAND + 1));

    // When / Then — Schalter und Raender bleiben (Kriterium 16).
    mockMvc
        .perform(delete(PFAD + "/datei").header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.briefpapier").value(false))
        .andExpect(jsonPath("$.dateiname").isEmpty())
        .andExpect(jsonPath("$.absenderzeile").value(false))
        .andExpect(jsonPath("$.steuerBankZeile").value(true))
        .andExpect(jsonPath("$.folgeUnten").value(220));
  }

  @Test
  void entfernen_givenOhneHinterlegtesBriefpapier_thenAntwortetMit404() throws Exception {
    // Given
    when(briefpapier.entferne(STAND, ANGEMELDET)).thenThrow(new BriefpapierNichtGefunden());

    // When / Then
    mockMvc
        .perform(delete(PFAD + "/datei").header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isNotFound());
  }

  // --- Stand vorlegen (Plan #415) -----------------------------------------------------------

  /**
   * Die Anfrage des genannten Wegs, ohne Kopf — jeder Test setzt ihn selbst oder laesst ihn weg.
   */
  private static MockHttpServletRequestBuilder anfrageZu(final String weg) {
    return switch (weg) {
      case "pflegen" -> put(PFAD).contentType(MediaType.APPLICATION_JSON).content(rumpfMit("", ""));
      case "hochladen" ->
          multipart(PFAD + "/datei")
              .file(new MockMultipartFile("datei", "kopf.pdf", "application/pdf", PDF));
      case "entfernen" -> delete(PFAD + "/datei");
      default -> throw new IllegalArgumentException(weg);
    };
  }

  /** Der Anwendungsfall des Wegs antwortet mit dem genannten Stand. */
  private void antwortet(final String weg, final Briefbogenstand neu) {
    switch (weg) {
      case "pflegen" ->
          when(pflegen.pflege(true, false, EINGESTELLT, STAND, ANGEMELDET)).thenReturn(neu);
      case "hochladen" ->
          when(briefpapier.ladeHoch("kopf.pdf", PDF, STAND, ANGEMELDET)).thenReturn(neu);
      case "entfernen" -> when(briefpapier.entferne(STAND, ANGEMELDET)).thenReturn(neu);
      default -> throw new IllegalArgumentException(weg);
    }
  }

  /** Der Anwendungsfall des Wegs wirft den Konflikt — mit Stand und Konto aus der Anfrage. */
  private void wirftKonflikt(final String weg) {
    final BriefbogenGeaendert konflikt = new BriefbogenGeaendert(ANNA, ANGEMELDET, ANNAS_AENDERUNG);
    switch (weg) {
      case "pflegen" ->
          when(pflegen.pflege(true, false, EINGESTELLT, STAND, ANGEMELDET)).thenThrow(konflikt);
      case "hochladen" ->
          when(briefpapier.ladeHoch("kopf.pdf", PDF, STAND, ANGEMELDET)).thenThrow(konflikt);
      case "entfernen" -> when(briefpapier.entferne(STAND, ANGEMELDET)).thenThrow(konflikt);
      default -> throw new IllegalArgumentException(weg);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"pflegen", "hochladen", "entfernen"})
  void schreibenderWeg_withoutIfMatch_thenAnswers428AndChangesNothing(final String weg)
      throws Exception {
    // When / Then — E3: ohne vorgelegten Stand wird nicht geschrieben.
    mockMvc
        .perform(anfrageZu(weg))
        .andExpect(status().isPreconditionRequired())
        .andExpect(jsonPath("$.detail").value(Bedingungskopf.MELDUNG));
    verifyNoInteractions(pflegen, briefpapier, lesen);
  }

  @ParameterizedTest
  @ValueSource(strings = {"pflegen", "hochladen", "entfernen"})
  void schreibenderWeg_withAnOutdatedStand_thenAnswers409WithTheConflict(final String weg)
      throws Exception {
    // Given — #411, Kriterium 4: Anna hat den Briefbogen inzwischen geaendert.
    wirftKonflikt(weg);

    // When / Then
    mockMvc
        .perform(anfrageZu(weg).header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.konflikt.art").value("veraltet"))
        .andExpect(jsonPath("$.konflikt.geaendertVon").value("Anna"))
        .andExpect(jsonPath("$.konflikt.geaendertAm").value("2026-10-19T08:42:00Z"))
        .andExpect(jsonPath("$.konflikt.eigenesFenster").value(false));
  }

  @ParameterizedTest
  @ValueSource(strings = {"pflegen", "hochladen", "entfernen"})
  void schreibenderWeg_withTheCurrentStand_thenAnswers200WithTheRaisedVersion(final String weg)
      throws Exception {
    // Given — #411, Kriterium 8: Auf dem aktuellen Stand geht der Schritt unveraendert durch; die
    // Antwort traegt den erhoehten Stand, den die Oberflaeche beim naechsten Schritt vorlegt.
    antwortet(
        weg, new Briefbogenstand(false, null, null, null, true, false, EINGESTELLT, STAND + 1));

    // When / Then
    mockMvc
        .perform(anfrageZu(weg).header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(STAND + 1));
  }

  // --- /probedruck -------------------------------------------------------------------------

  @Test
  void probedruck_thenAntwortetMitDemPdfAlsAnhang() throws Exception {
    // Given
    when(probedruck.drucke()).thenReturn(PDF.clone());

    // When / Then — dieselben strengen Kopfzeilen wie beim Dokument einer Rechnung.
    mockMvc
        .perform(get(PFAD + "/probedruck"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(content().bytes(PDF.clone()))
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"Probedruck.pdf\"; filename*=UTF-8''Probedruck.pdf"))
        .andExpect(header().string("Content-Security-Policy", "sandbox"));
  }

  @Test
  void probedruck_givenOhneHinterlegtesBriefpapier_thenAntwortetMit404() throws Exception {
    // Given — Kriterium 9, zweiter Satz.
    when(probedruck.drucke()).thenThrow(new BriefpapierNichtGefunden());

    // When / Then
    mockMvc.perform(get(PFAD + "/probedruck")).andExpect(status().isNotFound());
  }
}
