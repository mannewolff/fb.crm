package org.mwolff.fbcrm.angebot.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.OngoingStubbing;
import org.mwolff.fbcrm.angebot.application.AngebotAendernUseCase;
import org.mwolff.fbcrm.angebot.application.AngebotDaten;
import org.mwolff.fbcrm.angebot.application.AngebotGeaendert;
import org.mwolff.fbcrm.angebot.application.AngebotLesenUseCase;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.application.AngebotStatusUseCase;
import org.mwolff.fbcrm.angebot.application.AngebotStorniert;
import org.mwolff.fbcrm.angebot.application.AngebotStornoUseCase;
import org.mwolff.fbcrm.angebot.application.AnsprechpartnerNichtWaehlbar;
import org.mwolff.fbcrm.angebot.application.KeinStornoVorhanden;
import org.mwolff.fbcrm.angebot.application.KennzeichenNichtAenderbar;
import org.mwolff.fbcrm.angebot.application.Kundenangaben;
import org.mwolff.fbcrm.angebot.application.KundenangabenUseCase;
import org.mwolff.fbcrm.angebot.application.PositionenNichtWaehlbar;
import org.mwolff.fbcrm.angebot.application.Positionsangabe;
import org.mwolff.fbcrm.angebot.application.Positionsangaben;
import org.mwolff.fbcrm.angebot.application.StatusGrenzeErreicht;
import org.mwolff.fbcrm.angebot.application.StornoNichtMoeglich;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;
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
 * Die Uebersetzung zwischen Anwendungsfall und HTTP fuer die Wege am einzelnen Angebot (Issue
 * #127).
 *
 * <p>Dieselbe Strecke wie in {@code FirmaControllerTest}: {@code standaloneSetup} mit dem echten
 * {@link GlobalExceptionHandler}, damit die Statuscodes der Ausnahmen mitgeprueft werden.
 *
 * <p>Gegenstand sind die gerechneten Werte aus E5 — {@code summe} und je Position der {@code
 * betrag}, obwohl keiner davon in einer Spalte steht —, die Namen des Kunden, die Pruefung der
 * Anfrage und die Statuscodes: {@code PUT} und die beiden Statuswege antworten 200 mit dem neuen
 * Stand, das Ende der Reihe ist 409, ein nicht waehlbarer Ansprechpartner 422.
 *
 * <p>Dazu die dauerhafte Kennung der Position (Plan #169, E2): Die Antwort traegt sie je Position,
 * die Anfrage darf sie mitschicken oder weglassen, und eine Kennung, die nicht zu diesem Angebot
 * gehoert, ist ebenfalls 422 — mit der Meldung am Feld {@code positionen}.
 *
 * <p><b>Und das Kennzeichen der Art</b> (Issue #227): Es reist im Rumpf, ein fehlendes Feld gilt
 * als extern, und eine bestehende Rechnung macht aus dem Wechsel ein 422 am Feld {@code intern}.
 * Weil Menge, Einheit und Preis dafuer keine Pflichtfelder der Bean-Validation mehr sind (E7), geht
 * eine Position ohne Menge jetzt bis in den Anwendungsfall durch und wird dort zu 422 statt zu 400
 * — der Test dazu haelt gerade diesen Unterschied fest.
 *
 * <p><b>Der Titel ist auf dem Aenderungsweg Pflicht</b> (Issue #317): Er fehlt nicht, ist nicht nur
 * Leerraum und hat hoechstens 200 Zeichen — sonst 400 mit {@code fieldErrors.titel}.
 *
 * <p><b>Storno und Ruecknahme</b> (Issue #334, Plan #332 E5, E16): {@code POST} und {@code DELETE}
 * auf {@code /storno} antworten 200 mit dem neuen Stand samt {@code storniert} und {@code
 * stornoGrund}; der Grund ist freiwillig und hat hoechstens 2000 Zeichen. Die drei Konflikte sind
 * 409, je mit ihrem Satz im {@code detail}.
 *
 * <p><b>Jeder Zustandswechsel legt den Stand vor</b> (#411, Kriterien 4 und 5; Plan #415, E3): Fuer
 * beide Statuswege, das Storno und die Ruecknahme gilt je dasselbe Dreigespann — ohne {@code
 * If-Match} 428, auf einem alten Stand 409 mit {@code konflikt}, auf dem aktuellen 200 mit dem
 * erhoehten Stand; die vier Wege laufen je durch dieselben drei parametrisierten Tests.
 */
@ExtendWith(MockitoExtension.class)
class AngebotControllerTest {

  private static final long ANGEBOT = 11L;
  private static final LocalDate ANGEBOTSDATUM = LocalDate.of(2026, 9, 20);
  private static final Instant ANGELEGT = Instant.parse("2026-09-20T08:00:00Z");

  /**
   * Die Kennung der Position — dauerhaft und darum Teil von Antwort und Anfrage (Plan #169, E2).
   */
  private static final Long POSITION = Long.valueOf(42L);

  private static final Angebotsposition KONZEPTION =
      new Angebotsposition(
          POSITION,
          "Konzeption",
          Abrechnungsmodus.AUFWAND,
          new BigDecimal("2.50"),
          Einheit.PERSONENTAG,
          new BigDecimal("1000.01"),
          Year.of(2026));

  /**
   * Dieselbe Position, wie die Maske sie einreicht — alle vier Angaben besetzt (Issue #227, E7).
   */
  private static final Positionsangabe KONZEPTION_ANGABE =
      new Positionsangabe(
          POSITION,
          "Konzeption",
          Abrechnungsmodus.AUFWAND,
          new BigDecimal("2.50"),
          Einheit.PERSONENTAG,
          new BigDecimal("1000.01"),
          null);

  /** Der Stand, den die Maske vorlegt (Plan #415, E2). */
  private static final long STAND = 3L;

  /** Derselbe Stand als starkes Etikett im Kopf {@code If-Match}. */
  private static final String VORGELEGT = "\"" + STAND + "\"";

  /** Das angemeldete Konto. */
  private static final long ANGEMELDET = 31L;

  /** Das Konto, das vorher gespeichert hat. */
  private static final long ANNA = 30L;

  private static final String RUMPF =
      """
      {"titel":"Wartung","angebotDatum":"2026-09-25","ansprechpartnerId":9,"beschreibung":"Neu",
       "positionen":[{"id":42,"bezeichnung":"Konzeption","abrechnungsmodus":"AUFWAND","menge":"2.50",
                      "einheit":"PERSONENTAG","einzelpreis":"1000.01"}]}
      """;

  @Mock private AngebotLesenUseCase lesen;
  @Mock private AngebotAendernUseCase aendern;
  @Mock private AngebotStatusUseCase statuswechsel;
  @Mock private AngebotStornoUseCase storno;
  @Mock private KundenangabenUseCase kunden;

  @Captor private ArgumentCaptor<AngebotDaten> daten;

  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    final AngebotController controller =
        new AngebotController(lesen, aendern, statuswechsel, storno, kunden);
    // Jede Antwort mit einem Angebot fragt die Namen des Kunden hinzu; die Fehlerwege nicht.
    lenient().when(kunden.zu(any())).thenReturn(new Kundenangaben("Adler AG", "Eva Adler"));
    // Die Sitzung wie im Betrieb: die Kennung des Kontos als Principal (Plan #415, E14).
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                Long.valueOf(ANGEMELDET), null, List.of()));
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

  private static Angebot angebot(final Angebotsstatus status) {
    return angebot(status, "Website-Relaunch");
  }

  /** Dasselbe Angebot mit frei gewaehltem Titel — {@code null} fuer ein Angebot von vor #316. */
  private static Angebot angebot(final Angebotsstatus status, final @Nullable String titel) {
    return new Angebot(
        Long.valueOf(ANGEBOT),
        3L,
        8L,
        status.intern(),
        status,
        false,
        null,
        ANGEBOTSDATUM,
        titel,
        "Neugestaltung",
        List.of(KONZEPTION),
        ANGELEGT,
        ANGELEGT,
        0L,
        null);
  }

  private void aendernAntwortet() {
    when(aendern.aendere(eq(ANGEBOT), daten.capture()))
        .thenReturn(angebot(Angebotsstatus.ANGELEGT));
  }

  @Test
  void lesen_thenAnswersWithStatusNamesAndTheCalculatedAmounts() throws Exception {
    // Given — E5: Betrag und Summe stehen in keiner Spalte.
    when(lesen.lese(ANGEBOT)).thenReturn(angebot(Angebotsstatus.BESTELLT));

    // When / Then
    mockMvc
        .perform(get("/api/angebote/{id}", Long.valueOf(ANGEBOT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(Long.valueOf(ANGEBOT)))
        .andExpect(jsonPath("$.firmaId").value(3))
        .andExpect(jsonPath("$.firmaName").value("Adler AG"))
        .andExpect(jsonPath("$.intern").value(false))
        .andExpect(jsonPath("$.ansprechpartnerId").value(8))
        .andExpect(jsonPath("$.ansprechpartnerName").value("Eva Adler"))
        .andExpect(jsonPath("$.status").value("BESTELLT"))
        .andExpect(jsonPath("$.titel").value("Website-Relaunch"))
        .andExpect(jsonPath("$.beschreibung").value("Neugestaltung"))
        .andExpect(jsonPath("$.summe").value(2500.03))
        .andExpect(jsonPath("$.positionen[0].betrag").value(2500.03))
        .andExpect(jsonPath("$.positionen[0].einheit").value("PERSONENTAG"))
        .andExpect(jsonPath("$.nummer").doesNotExist());
  }

  @Test
  void lesen_givenAnOfferWithoutTitle_thenAnswersWithNullAsTitle() throws Exception {
    // Given — #314, Kriterium 4: ein Angebot von vor dem Titel traegt keinen.
    when(lesen.lese(ANGEBOT)).thenReturn(angebot(Angebotsstatus.ANGELEGT, null));

    // When / Then
    mockMvc
        .perform(get("/api/angebote/{id}", Long.valueOf(ANGEBOT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.titel").value(nullValue()));
  }

  @Test
  void lesen_givenInternalWork_thenAnswersWithTheFlagSet() throws Exception {
    // Given — Issue #226, Kriterium 2: die Detailansicht nennt die Art des Angebots.
    when(lesen.lese(ANGEBOT)).thenReturn(angebot(Angebotsstatus.LAEUFT));

    // When / Then
    mockMvc
        .perform(get("/api/angebote/{id}", Long.valueOf(ANGEBOT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.intern").value(true))
        .andExpect(jsonPath("$.status").value("LAEUFT"));
  }

  @Test
  void lesen_thenAnswersWithTheLastingIdOfEveryPosition() throws Exception {
    // Given — Plan #169, E2: Ohne die Kennung in der Antwort hat die Maske keinen Griff, mit dem
    // sie dieselbe Position zurueckschickt, statt sie neu anzulegen.
    when(lesen.lese(ANGEBOT)).thenReturn(angebot(Angebotsstatus.ANGELEGT));

    // When / Then
    mockMvc
        .perform(get("/api/angebote/{id}", Long.valueOf(ANGEBOT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.positionen[0].id").value(POSITION));
  }

  @Test
  void aendern_withAPositionWithoutAnId_thenPassesItOnAsANewOne() throws Exception {
    // Given — die Kennung ist freiwillig; ohne sie ist die Position neu.
    aendernAntwortet();
    final String ohneKennung =
        """
        {"titel":"Wartung","angebotDatum":"2026-09-25","positionen":[{"bezeichnung":"Konzeption",
          "abrechnungsmodus":"AUFWAND","menge":"2.50","einheit":"PERSONENTAG",
          "einzelpreis":"1000.01"}]}
        """;

    // When
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ohneKennung))
        .andExpect(status().isOk());

    // Then
    assertThat(daten.getValue().positionen())
        .singleElement()
        .satisfies(position -> assertThat(position.id()).isNull());
  }

  @Test
  void aendern_withAPositionOfAnotherAngebot_thenAnswers422() throws Exception {
    // Given — Plan #169, E2: Gueltig sind nur die Kennungen der Positionen dieses Angebots.
    when(aendern.aendere(eq(ANGEBOT), daten.capture())).thenThrow(new PositionenNichtWaehlbar());

    // When / Then
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.fieldErrors.positionen[0]")
                .value("Die eingereichten Positionen passen nicht zu diesem Angebot."));
  }

  @Test
  void aendern_withoutIfMatch_thenAnswers428AndChangesNothing() throws Exception {
    // Given — E3: Ohne vorgelegten Stand wird nicht geschrieben; ein freiwilliger Kopf waere ein
    // stilles Loch im Schutz.

    // When / Then
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isPreconditionRequired())
        .andExpect(jsonPath("$.detail").value(Bedingungskopf.MELDUNG));
    verifyNoInteractions(aendern);
  }

  @Test
  void aendern_withAnOutdatedStand_thenAnswers409WithTheConflict() throws Exception {
    // Given — #411, Szenario „Zweites Speichern wird abgewiesen": Anna hat inzwischen gespeichert.
    when(aendern.aendere(eq(ANGEBOT), any()))
        .thenThrow(new AngebotGeaendert(ANNA, ANGEMELDET, Instant.parse("2026-09-28T08:42:00Z")));

    // When / Then
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.konflikt.art").value("veraltet"))
        .andExpect(jsonPath("$.konflikt.geaendertVon").value("Anna"))
        .andExpect(jsonPath("$.konflikt.geaendertAm").value("2026-09-28T08:42:00Z"))
        .andExpect(jsonPath("$.konflikt.eigenesFenster").value(false));
  }

  @Test
  void aendern_withTheCurrentStand_thenPassesStandAndAccountAndAnswersWithTheRaisedVersion()
      throws Exception {
    // Given — #411, Kriterium 8: Mit dem aktuellen Stand geht das Speichern durch, und die Antwort
    // traegt den neuen Stand, den die Maske beim naechsten Speichern vorlegt.
    when(aendern.aendere(eq(ANGEBOT), daten.capture()))
        .thenReturn(mitStand(angebot(Angebotsstatus.ANGELEGT), STAND + 1, ANGEMELDET));

    // When
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(STAND + 1))
        .andExpect(jsonPath("$.geaendertVon").value(ANGEMELDET));

    // Then — der vorgelegte Stand aus dem Kopf, das handelnde Konto aus der Sitzung.
    assertThat(daten.getValue())
        .satisfies(
            d -> assertThat(d.version()).isEqualTo(STAND),
            d -> assertThat(d.geaendertVon()).isEqualTo(ANGEMELDET));
  }

  @Test
  void lesen_thenAnswersWithTheStandOfTheAngebot() throws Exception {
    // Given — E2: Die Leseantwort gibt den Stand heraus, den die Maske wieder vorlegt.
    when(lesen.lese(ANGEBOT)).thenReturn(mitStand(angebot(Angebotsstatus.ANGELEGT), STAND, ANNA));

    // When / Then
    mockMvc
        .perform(get("/api/angebote/{id}", Long.valueOf(ANGEBOT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(STAND))
        .andExpect(jsonPath("$.geaendertVon").value(ANNA));
  }

  /** Dasselbe Angebot auf einem anderen Stand, zuletzt geaendert von {@code urheber}. */
  private static Angebot mitStand(final Angebot angebot, final long version, final long urheber) {
    return new Angebot(
        angebot.id(),
        angebot.firmaId(),
        angebot.ansprechpartnerId(),
        angebot.intern(),
        angebot.status(),
        angebot.storniert(),
        angebot.stornoGrund(),
        angebot.angebotDatum(),
        angebot.titel(),
        angebot.beschreibung(),
        angebot.positionen(),
        angebot.createdAt(),
        angebot.updatedAt(),
        version,
        Long.valueOf(urheber));
  }

  @Test
  void lesen_whenTheAngebotIsUnknown_thenAnswers404() throws Exception {
    // Given
    when(lesen.lese(ANGEBOT)).thenThrow(new AngebotNichtGefunden());

    // When / Then
    mockMvc
        .perform(get("/api/angebote/{id}", Long.valueOf(ANGEBOT)))
        .andExpect(status().isNotFound());
  }

  @Test
  void aendern_thenAnswers200WithTheNewStand() throws Exception {
    // Given
    aendernAntwortet();

    // When / Then
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(Long.valueOf(ANGEBOT)))
        .andExpect(jsonPath("$.status").value("ANGELEGT"));
  }

  @Test
  void aendern_thenPassesEveryFieldAndTheCompletePositionList() throws Exception {
    // Given — E8: das Angebot wird als Ganzes geschrieben.
    aendernAntwortet();

    // When
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isOk());

    // Then
    final AngebotDaten uebergeben = daten.getValue();
    assertThat(uebergeben.angebotDatum()).isEqualTo(LocalDate.of(2026, 9, 25));
    assertThat(uebergeben.ansprechpartnerId()).isEqualTo(9L);
    assertThat(uebergeben.titel()).isEqualTo("Wartung");
    assertThat(uebergeben.beschreibung()).isEqualTo("Neu");
    assertThat(uebergeben.intern()).isFalse();
    // Die Kennung der Position kommt mit durch: KONZEPTION_ANGABE traegt sie, und der Record
    // vergleicht sie.
    assertThat(uebergeben.positionen()).containsExactly(KONZEPTION_ANGABE);
  }

  @Test
  void aendern_withANotSelectableContact_thenAnswers422() throws Exception {
    // Given — ein neu gewaehlter Ansprechpartner ist stillgelegt oder gehoert zu einer anderen
    // Firma.
    when(aendern.aendere(eq(ANGEBOT), daten.capture()))
        .thenThrow(new AnsprechpartnerNichtWaehlbar());

    // When / Then
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.fieldErrors.ansprechpartnerId[0]")
                .value("Dieser Ansprechpartner steht für das Angebot nicht zur Wahl."));
  }

  @Test
  void aendern_withABlankBezeichnung_thenAnswersWithAFieldError() throws Exception {
    // Given — jede Position braucht eine Bezeichnung (Issue #127).
    final String leer =
        """
        {"titel":"Wartung","angebotDatum":"2026-09-25","positionen":[{"bezeichnung":"  ",
          "abrechnungsmodus":"AUFWAND","menge":"1.00","einheit":"STUNDE","einzelpreis":"10.00"}]}
        """;

    // When / Then
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(leer))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors['positionen[0].bezeichnung']").isNotEmpty());
    verifyNoInteractions(aendern);
  }

  @Test
  void aendern_withANegativeMenge_thenAnswersWithAFieldError() throws Exception {
    // Given — die Form wird an der Anfrage geprueft, nicht erst in der Datenbank.
    final String negativ =
        """
        {"titel":"Wartung","angebotDatum":"2026-09-25","positionen":[{"bezeichnung":"Konzeption",
          "abrechnungsmodus":"AUFWAND","menge":"-1.00","einheit":"STUNDE","einzelpreis":"10.00"}]}
        """;

    // When / Then
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(negativ))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors['positionen[0].menge']").isNotEmpty());
  }

  @Test
  void aendern_withoutAngebotDatum_thenAnswersWithAFieldError() throws Exception {
    // Given — das Datum ist die eine Pflichtangabe des Angebots.
    final String ohneDatum = "{\"titel\":\"Wartung\",\"positionen\":[]}";

    // When / Then
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ohneDatum))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.angebotDatum").isNotEmpty());
  }

  @Test
  void statusWeiter_thenAnswers200WithTheNewStatus() throws Exception {
    // Given
    when(statuswechsel.weiter(ANGEBOT, STAND, ANGEMELDET))
        .thenReturn(angebot(Angebotsstatus.ERLEDIGT));

    // When / Then
    mockMvc
        .perform(
            post("/api/angebote/{id}/status/weiter", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ERLEDIGT"))
        .andExpect(jsonPath("$.firmaName").value("Adler AG"))
        .andExpect(jsonPath("$.intern").value(false));
  }

  @Test
  void statusZurueck_thenAnswers200WithTheNewStatus() throws Exception {
    // Given
    when(statuswechsel.zurueck(ANGEBOT, STAND, ANGEMELDET))
        .thenReturn(angebot(Angebotsstatus.ABGEGEBEN));

    // When / Then
    mockMvc
        .perform(
            post("/api/angebote/{id}/status/zurueck", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ABGEGEBEN"));
  }

  @Test
  void statusWeiter_atTheEnd_thenAnswers409() throws Exception {
    // Given
    when(statuswechsel.weiter(ANGEBOT, STAND, ANGEMELDET)).thenThrow(new StatusGrenzeErreicht());

    // When / Then
    mockMvc
        .perform(
            post("/api/angebote/{id}/status/weiter", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isConflict());
  }

  @Test
  void statusZurueck_atTheStart_thenAnswers409() throws Exception {
    // Given
    when(statuswechsel.zurueck(ANGEBOT, STAND, ANGEMELDET)).thenThrow(new StatusGrenzeErreicht());

    // When / Then
    mockMvc
        .perform(
            post("/api/angebote/{id}/status/zurueck", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isConflict());
  }

  @Test
  void statusWeiter_whenTheAngebotIsUnknown_thenAnswers404() throws Exception {
    // Given
    when(statuswechsel.weiter(ANGEBOT, STAND, ANGEMELDET)).thenThrow(new AngebotNichtGefunden());

    // When / Then
    mockMvc
        .perform(
            post("/api/angebote/{id}/status/weiter", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isNotFound());
  }

  @Test
  void aendern_withTheInternFlag_thenPassesItOnAndAnswersWithTheInternalAngebot() throws Exception {
    // Given — Issue #227, Kriterium 8 von #207: Das Kennzeichen reist im Rumpf des PUT.
    when(aendern.aendere(eq(ANGEBOT), daten.capture())).thenReturn(angebot(Angebotsstatus.LAEUFT));
    final String intern =
        """
        {"titel":"Wartung","angebotDatum":"2026-09-25","intern":true,"positionen":[{"id":42,
          "bezeichnung":"Konzeption"}]}
        """;

    // When / Then
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(intern))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.intern").value(true))
        .andExpect(jsonPath("$.status").value("LAEUFT"));
    assertThat(daten.getValue().intern()).isTrue();
  }

  @Test
  void aendern_withoutTheInternField_thenTreatsItAsACustomerOffer() throws Exception {
    // Given — ein fehlendes Feld gilt als extern, damit der gewoehnliche Fall ohne Zutun gilt.
    aendernAntwortet();
    final String ohneArt =
        """
        {"titel":"Wartung","angebotDatum":"2026-09-25","positionen":[]}
        """;

    // When
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ohneArt))
        .andExpect(status().isOk());

    // Then
    assertThat(daten.getValue().intern()).isFalse();
  }

  @Test
  void aendern_withAnExplicitNullInternField_thenTreatsItAsACustomerOffer() throws Exception {
    // Given — ein {@code null} im Rumpf geht denselben Weg wie ein fehlendes Feld.
    aendernAntwortet();
    final String ausdruecklichNull =
        """
        {"titel":"Wartung","angebotDatum":"2026-09-25","intern":null,"positionen":[]}
        """;

    // When
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ausdruecklichNull))
        .andExpect(status().isOk());

    // Then
    assertThat(daten.getValue().intern()).isFalse();
  }

  @Test
  void aendern_whenTheArtCannotChangeBecauseARechnungExists_thenAnswers422AtTheFieldIntern()
      throws Exception {
    // Given — E20: Der Wert kommt aus einem Feld der Maske, und die Meldung gehoert an dieses Feld.
    when(aendern.aendere(eq(ANGEBOT), daten.capture())).thenThrow(new KennzeichenNichtAenderbar());

    // When / Then
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.fieldErrors.intern[0]").value(KennzeichenNichtAenderbar.MELDUNG));
  }

  @Test
  void aendern_withAPositionWithoutMenge_thenAnswers422AndNotAnymore400() throws Exception {
    // Given — E7: Menge, Einheit und Preis sind keine Pflichtfelder der Bean-Validation mehr; die
    // Pflicht entscheidet der Anwendungsfall nach der Zielart und antwortet 422 statt 400.
    when(aendern.aendere(eq(ANGEBOT), daten.capture()))
        .thenThrow(
            Positionsangaben.fehlendeAngaben(
                0,
                new Positionsangabe(
                    null,
                    "Konzeption",
                    Abrechnungsmodus.AUFWAND,
                    null,
                    Einheit.STUNDE,
                    BigDecimal.TEN,
                    null)));
    final String ohneMenge =
        """
        {"titel":"Wartung","angebotDatum":"2026-09-25","positionen":[{"bezeichnung":"Konzeption",
          "abrechnungsmodus":"AUFWAND","einheit":"STUNDE","einzelpreis":"10.00"}]}
        """;

    // When / Then
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ohneMenge))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.fieldErrors['positionen[0].menge'][0]")
                .value(Positionsangaben.ANGABE_FEHLT));
  }

  /** Ein Rumpf mit einer neuen Position, die das angegebene Leistungsjahr mitschickt. */
  private static String mitLeistungsjahr(final int jahr) {
    return """
        {"titel":"Wartung","angebotDatum":"2026-09-25","positionen":[{"bezeichnung":"Konzeption",
          "abrechnungsmodus":"AUFWAND","menge":"2.50","einheit":"PERSONENTAG",
          "einzelpreis":"1000.01","leistungsjahr":%d}]}
        """
        .formatted(Integer.valueOf(jahr));
  }

  @Test
  void lesen_thenAnswersWithTheLeistungsjahrAsText() throws Exception {
    // Given — Issue #305, E9: Jackson schriebe ein Year als Zahl; die Antwort fuehrt es als Text
    // JJJJ, wie die Startseite ihre Jahre.
    when(lesen.lese(ANGEBOT)).thenReturn(angebot(Angebotsstatus.ANGELEGT));

    // When / Then
    mockMvc
        .perform(get("/api/angebote/{id}", Long.valueOf(ANGEBOT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.positionen[0].leistungsjahr").isString())
        .andExpect(jsonPath("$.positionen[0].leistungsjahr").value("2026"));
  }

  @ParameterizedTest
  @ValueSource(ints = {2000, 2027, 2099})
  void aendern_withALeistungsjahrWithinTheBounds_thenPassesItOn(final int jahr) throws Exception {
    // Given — Issue #305, E10: 2000 bis 2099 sind gueltig, die Grenzen eingeschlossen.
    aendernAntwortet();

    // When
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mitLeistungsjahr(jahr)))
        .andExpect(status().isOk());

    // Then
    assertThat(daten.getValue().positionen())
        .singleElement()
        .satisfies(position -> assertThat(position.leistungsjahr()).isEqualTo(jahr));
  }

  @ParameterizedTest
  @ValueSource(ints = {1999, 2100})
  void aendern_withALeistungsjahrOutsideTheBounds_thenAnswers400AtTheField(final int jahr)
      throws Exception {
    // Given — E10: „20267" ist ein Tippfehler und keine Angabe; die Bean-Validation weist ab, bevor
    // der Anwendungsfall gefragt wird.

    // When / Then
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mitLeistungsjahr(jahr)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors['positionen[0].leistungsjahr']").isNotEmpty());
    verifyNoInteractions(aendern);
  }

  @Test
  void aendern_withoutTheLeistungsjahrField_thenStaysValidAndPassesNull() throws Exception {
    // Given — Issue #305, E2: Das Leistungsjahr ist nachgebbar. Ein Rumpf ohne das Feld — wie ihn
    // die Systemgrenze am internen Angebot schickt — wird weder 400 noch 422.
    aendernAntwortet();

    // When
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isOk());

    // Then
    assertThat(daten.getValue().positionen())
        .singleElement()
        .satisfies(position -> assertThat(position.leistungsjahr()).isNull());
  }

  @Test
  void aendern_withATitle_thenPassesItOnAndTheAnswerCarriesIt() throws Exception {
    // Given — #314, Kriterium 3: der Titel ist aenderbar.
    when(aendern.aendere(eq(ANGEBOT), daten.capture()))
        .thenReturn(angebot(Angebotsstatus.ANGELEGT, "Wartung"));

    // When
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.titel").value("Wartung"));

    // Then
    assertThat(daten.getValue().titel()).isEqualTo("Wartung");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"angebotDatum\":\"2026-09-25\",\"positionen\":[]}",
        "{\"titel\":null,\"angebotDatum\":\"2026-09-25\",\"positionen\":[]}",
        "{\"titel\":\"   \",\"angebotDatum\":\"2026-09-25\",\"positionen\":[]}"
      })
  void aendern_withoutATitle_thenAnswers400AtTheTitle(final String rumpf) throws Exception {
    // When / Then — #314, Kriterium 3: der Titel bleibt beim Aendern Pflicht; Leerraum zaehlt als
    // fehlend.
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.titel").isArray());
    verifyNoInteractions(aendern);
  }

  @Test
  void aendern_withATitleOf201Characters_thenAnswers400AtTheTitle() throws Exception {
    // Given — #314, Kriterium 1: hoechstens 200 Zeichen.
    final String zuLang =
        "{\"titel\":\"%s\",\"angebotDatum\":\"2026-09-25\",\"positionen\":[]}"
            .formatted("x".repeat(201));

    // When / Then
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(zuLang))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.titel").isArray());
    verifyNoInteractions(aendern);
  }

  /** Das Angebot im Status {@code status}, storniert mit {@code grund}. */
  private static Angebot storniert(final Angebotsstatus status, final @Nullable String grund) {
    return angebot(status).storniert(grund, ANGELEGT);
  }

  @Test
  void lesen_givenAnAngebotWithoutStorno_thenAnswersWithStornoFalseAndNoGrund() throws Exception {
    // Given
    when(lesen.lese(ANGEBOT)).thenReturn(angebot(Angebotsstatus.ABGEGEBEN));

    // When / Then
    mockMvc
        .perform(get("/api/angebote/{id}", Long.valueOf(ANGEBOT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.storniert").value(false))
        .andExpect(jsonPath("$.stornoGrund").value(nullValue()));
  }

  @Test
  void stornieren_withAGrund_thenPassesItOnAndAnswers200WithTheStorno() throws Exception {
    // Given — #330, Kriterien 1 und 5.
    when(storno.storniere(ANGEBOT, "vom Kunden abgelehnt", STAND, ANGEMELDET))
        .thenReturn(storniert(Angebotsstatus.ABGEGEBEN, "vom Kunden abgelehnt"));

    // When / Then
    mockMvc
        .perform(
            post("/api/angebote/{id}/storno", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"grund\":\"vom Kunden abgelehnt\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.storniert").value(true))
        .andExpect(jsonPath("$.stornoGrund").value("vom Kunden abgelehnt"))
        .andExpect(jsonPath("$.status").value("ABGEGEBEN"))
        .andExpect(jsonPath("$.firmaName").value("Adler AG"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"grund\":null}", "{}"})
  void stornieren_withoutAGrund_thenPassesNullAndAnswers200(final String rumpf) throws Exception {
    // Given — E4: der Grund ist freiwillig.
    when(storno.storniere(ANGEBOT, null, STAND, ANGEMELDET))
        .thenReturn(storniert(Angebotsstatus.BESTELLT, null));

    // When / Then
    mockMvc
        .perform(
            post("/api/angebote/{id}/storno", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.storniert").value(true))
        .andExpect(jsonPath("$.stornoGrund").value(nullValue()));
  }

  @Test
  void stornieren_withoutABody_thenPassesNullAndAnswers200() throws Exception {
    // Given — ein ganz fehlender Rumpf heisst ebenfalls „ohne Grund".
    when(storno.storniere(ANGEBOT, null, STAND, ANGEMELDET))
        .thenReturn(storniert(Angebotsstatus.BESTELLT, null));

    // When / Then
    mockMvc
        .perform(
            post("/api/angebote/{id}/storno", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.storniert").value(true))
        .andExpect(jsonPath("$.stornoGrund").value(nullValue()));
  }

  @Test
  void stornieren_withAGrundOf2000Characters_thenAnswers200() throws Exception {
    // Given — genau an der Grenze.
    final String grund = "x".repeat(2000);
    when(storno.storniere(ANGEBOT, grund, STAND, ANGEMELDET))
        .thenReturn(storniert(Angebotsstatus.BESTELLT, grund));

    // When / Then
    mockMvc
        .perform(
            post("/api/angebote/{id}/storno", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"grund\":\"" + grund + "\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void stornieren_withAGrundOf2001Characters_thenAnswers400AtTheGrund() throws Exception {
    // When / Then — E4: hoechstens 2000 Zeichen; der Anwendungsfall wird gar nicht gefragt.
    mockMvc
        .perform(
            post("/api/angebote/{id}/storno", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"grund\":\"" + "x".repeat(2001) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.grund").isNotEmpty());
    verifyNoInteractions(storno);
  }

  @Test
  void stornieren_whenTheAngebotCannotBeStornoed_thenAnswers409WithItsSentence() throws Exception {
    // Given — E16, E17.
    when(storno.storniere(ANGEBOT, null, STAND, ANGEMELDET)).thenThrow(new StornoNichtMoeglich());

    // When / Then
    mockMvc
        .perform(
            post("/api/angebote/{id}/storno", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail")
                .value("Aus diesem Zustand lässt sich das Angebot nicht stornieren."));
  }

  @Test
  void stornieren_whenTheAngebotIsUnknown_thenAnswers404() throws Exception {
    // Given
    when(storno.storniere(ANGEBOT, null, STAND, ANGEMELDET)).thenThrow(new AngebotNichtGefunden());

    // When / Then
    mockMvc
        .perform(
            post("/api/angebote/{id}/storno", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isNotFound());
  }

  @Test
  void stornoZuruecknehmen_thenAnswers200WithoutStornoAndGrund() throws Exception {
    // Given — #330, Kriterium 9.
    when(storno.nimmStornoZurueck(ANGEBOT, STAND, ANGEMELDET))
        .thenReturn(angebot(Angebotsstatus.BESTELLT));

    // When / Then
    mockMvc
        .perform(
            delete("/api/angebote/{id}/storno", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.storniert").value(false))
        .andExpect(jsonPath("$.stornoGrund").value(nullValue()))
        .andExpect(jsonPath("$.status").value("BESTELLT"));
  }

  @Test
  void stornoZuruecknehmen_whenThereIsNoStorno_thenAnswers409WithItsSentence() throws Exception {
    // Given — E16, E17.
    when(storno.nimmStornoZurueck(ANGEBOT, STAND, ANGEMELDET)).thenThrow(new KeinStornoVorhanden());

    // When / Then
    mockMvc
        .perform(
            delete("/api/angebote/{id}/storno", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Dieses Angebot ist nicht storniert."));
  }

  @Test
  void aendern_atAStornoedAngebot_thenAnswers409WithItsSentence() throws Exception {
    // Given — #330, Kriterium 10; E16.
    when(aendern.aendere(eq(ANGEBOT), any())).thenThrow(new AngebotStorniert());

    // When / Then
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Am stornierten Angebot ändert sich nichts; nimm zuerst das Storno zurück."));
  }

  /** Die Anfrage des Wegs, noch ohne {@code If-Match}. */
  private static MockHttpServletRequestBuilder anfrageZu(final String weg) {
    final Long id = Long.valueOf(ANGEBOT);
    return switch (weg) {
      case "weiter" -> post("/api/angebote/{id}/status/weiter", id);
      case "zurueck" -> post("/api/angebote/{id}/status/zurueck", id);
      case "storno" -> post("/api/angebote/{id}/storno", id);
      case "ruecknahme" -> delete("/api/angebote/{id}/storno", id);
      default -> throw new IllegalArgumentException(weg);
    };
  }

  /** Der Anwendungsfall des Wegs, mit Stand und Konto, die der Controller weitergeben muss. */
  private OngoingStubbing<Angebot> wennGerufen(final String weg) {
    return switch (weg) {
      case "weiter" -> when(statuswechsel.weiter(ANGEBOT, STAND, ANGEMELDET));
      case "zurueck" -> when(statuswechsel.zurueck(ANGEBOT, STAND, ANGEMELDET));
      case "storno" -> when(storno.storniere(ANGEBOT, null, STAND, ANGEMELDET));
      case "ruecknahme" -> when(storno.nimmStornoZurueck(ANGEBOT, STAND, ANGEMELDET));
      default -> throw new IllegalArgumentException(weg);
    };
  }

  @ParameterizedTest
  @ValueSource(strings = {"weiter", "zurueck", "storno", "ruecknahme"})
  void zustandswechsel_withoutIfMatch_thenAnswers428AndChangesNothing(final String weg)
      throws Exception {
    // Given — E3: Ohne vorgelegten Stand wird nicht geschaltet.

    // When / Then
    mockMvc
        .perform(anfrageZu(weg))
        .andExpect(status().isPreconditionRequired())
        .andExpect(jsonPath("$.detail").value(Bedingungskopf.MELDUNG));
    verifyNoInteractions(statuswechsel, storno);
  }

  @ParameterizedTest
  @ValueSource(strings = {"weiter", "zurueck", "storno", "ruecknahme"})
  void zustandswechsel_withAnOutdatedStand_thenAnswers409WithTheConflict(final String weg)
      throws Exception {
    // Given — #411, Kriterium 5: Anna hat das Angebot inzwischen geaendert.
    wennGerufen(weg)
        .thenThrow(new AngebotGeaendert(ANNA, ANGEMELDET, Instant.parse("2026-09-28T08:42:00Z")));

    // When / Then
    mockMvc
        .perform(anfrageZu(weg).header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.konflikt.art").value("veraltet"))
        .andExpect(jsonPath("$.konflikt.geaendertVon").value("Anna"))
        .andExpect(jsonPath("$.konflikt.geaendertAm").value("2026-09-28T08:42:00Z"))
        .andExpect(jsonPath("$.konflikt.eigenesFenster").value(false));
  }

  @ParameterizedTest
  @ValueSource(strings = {"weiter", "zurueck", "storno", "ruecknahme"})
  void zustandswechsel_withTheCurrentStand_thenAnswers200WithTheRaisedVersion(final String weg)
      throws Exception {
    // Given — #411, Kriterium 8: Auf dem aktuellen Stand geht der Wechsel unveraendert durch; der
    // Anwendungsfall bekommt Stand und Konto, die Antwort traegt den erhoehten Stand.
    wennGerufen(weg).thenReturn(mitStand(angebot(Angebotsstatus.BESTELLT), STAND + 1, ANGEMELDET));

    // When / Then
    mockMvc
        .perform(anfrageZu(weg).header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("BESTELLT"))
        .andExpect(jsonPath("$.version").value(STAND + 1))
        .andExpect(jsonPath("$.geaendertVon").value(ANGEMELDET));
  }
}
