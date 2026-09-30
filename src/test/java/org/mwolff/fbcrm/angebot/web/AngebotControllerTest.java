package org.mwolff.fbcrm.angebot.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotAendernUseCase;
import org.mwolff.fbcrm.angebot.application.AngebotDaten;
import org.mwolff.fbcrm.angebot.application.AngebotLesenUseCase;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.application.AngebotStatusUseCase;
import org.mwolff.fbcrm.angebot.application.AnsprechpartnerNichtWaehlbar;
import org.mwolff.fbcrm.angebot.application.Kundenangaben;
import org.mwolff.fbcrm.angebot.application.KundenangabenUseCase;
import org.mwolff.fbcrm.angebot.application.PositionenNichtWaehlbar;
import org.mwolff.fbcrm.angebot.application.StatusGrenzeErreicht;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
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
          new BigDecimal("1000.01"));

  private static final String RUMPF =
      """
      {"angebotDatum":"2026-09-25","ansprechpartnerId":9,"beschreibung":"Neu",
       "positionen":[{"id":42,"bezeichnung":"Konzeption","abrechnungsmodus":"AUFWAND","menge":"2.50",
                      "einheit":"PERSONENTAG","einzelpreis":"1000.01"}]}
      """;

  @Mock private AngebotLesenUseCase lesen;
  @Mock private AngebotAendernUseCase aendern;
  @Mock private AngebotStatusUseCase statuswechsel;
  @Mock private KundenangabenUseCase kunden;

  @Captor private ArgumentCaptor<AngebotDaten> daten;

  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    final AngebotController controller =
        new AngebotController(lesen, aendern, statuswechsel, kunden);
    // Jede Antwort mit einem Angebot fragt die Namen des Kunden hinzu; die Fehlerwege nicht.
    lenient().when(kunden.zu(any())).thenReturn(new Kundenangaben("Adler AG", "Eva Adler"));
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private static Angebot angebot(final Angebotsstatus status) {
    return new Angebot(
        Long.valueOf(ANGEBOT),
        3L,
        8L,
        status,
        ANGEBOTSDATUM,
        "Neugestaltung",
        List.of(KONZEPTION),
        ANGELEGT,
        ANGELEGT);
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
        .andExpect(jsonPath("$.ansprechpartnerId").value(8))
        .andExpect(jsonPath("$.ansprechpartnerName").value("Eva Adler"))
        .andExpect(jsonPath("$.status").value("BESTELLT"))
        .andExpect(jsonPath("$.beschreibung").value("Neugestaltung"))
        .andExpect(jsonPath("$.summe").value(2500.03))
        .andExpect(jsonPath("$.positionen[0].betrag").value(2500.03))
        .andExpect(jsonPath("$.positionen[0].einheit").value("PERSONENTAG"))
        .andExpect(jsonPath("$.nummer").doesNotExist());
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
        {"angebotDatum":"2026-09-25","positionen":[{"bezeichnung":"Konzeption",
          "abrechnungsmodus":"AUFWAND","menge":"2.50","einheit":"PERSONENTAG",
          "einzelpreis":"1000.01"}]}
        """;

    // When
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
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
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.fieldErrors.positionen[0]")
                .value("Die eingereichten Positionen passen nicht zu diesem Angebot."));
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
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isOk());

    // Then
    final AngebotDaten uebergeben = daten.getValue();
    assertThat(uebergeben.angebotDatum()).isEqualTo(LocalDate.of(2026, 9, 25));
    assertThat(uebergeben.ansprechpartnerId()).isEqualTo(9L);
    assertThat(uebergeben.beschreibung()).isEqualTo("Neu");
    // Die Kennung der Position kommt mit durch: KONZEPTION traegt sie, und der Record vergleicht
    // sie.
    assertThat(uebergeben.positionen()).containsExactly(KONZEPTION);
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
        {"angebotDatum":"2026-09-25","positionen":[{"bezeichnung":"  ",
          "abrechnungsmodus":"AUFWAND","menge":"1.00","einheit":"STUNDE","einzelpreis":"10.00"}]}
        """;

    // When / Then
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
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
        {"angebotDatum":"2026-09-25","positionen":[{"bezeichnung":"Konzeption",
          "abrechnungsmodus":"AUFWAND","menge":"-1.00","einheit":"STUNDE","einzelpreis":"10.00"}]}
        """;

    // When / Then
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(negativ))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors['positionen[0].menge']").isNotEmpty());
  }

  @Test
  void aendern_withoutAngebotDatum_thenAnswersWithAFieldError() throws Exception {
    // Given — das Datum ist die eine Pflichtangabe des Angebots.
    final String ohneDatum = "{\"positionen\":[]}";

    // When / Then
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(ohneDatum))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.angebotDatum").isNotEmpty());
  }

  @Test
  void statusWeiter_thenAnswers200WithTheNewStatus() throws Exception {
    // Given
    when(statuswechsel.weiter(ANGEBOT)).thenReturn(angebot(Angebotsstatus.ERLEDIGT));

    // When / Then
    mockMvc
        .perform(post("/api/angebote/{id}/status/weiter", Long.valueOf(ANGEBOT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ERLEDIGT"))
        .andExpect(jsonPath("$.firmaName").value("Adler AG"));
  }

  @Test
  void statusZurueck_thenAnswers200WithTheNewStatus() throws Exception {
    // Given
    when(statuswechsel.zurueck(ANGEBOT)).thenReturn(angebot(Angebotsstatus.ABGEGEBEN));

    // When / Then
    mockMvc
        .perform(post("/api/angebote/{id}/status/zurueck", Long.valueOf(ANGEBOT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ABGEGEBEN"));
  }

  @Test
  void statusWeiter_atTheEnd_thenAnswers409() throws Exception {
    // Given
    when(statuswechsel.weiter(ANGEBOT)).thenThrow(new StatusGrenzeErreicht());

    // When / Then
    mockMvc
        .perform(post("/api/angebote/{id}/status/weiter", Long.valueOf(ANGEBOT)))
        .andExpect(status().isConflict());
  }

  @Test
  void statusZurueck_atTheStart_thenAnswers409() throws Exception {
    // Given
    when(statuswechsel.zurueck(ANGEBOT)).thenThrow(new StatusGrenzeErreicht());

    // When / Then
    mockMvc
        .perform(post("/api/angebote/{id}/status/zurueck", Long.valueOf(ANGEBOT)))
        .andExpect(status().isConflict());
  }

  @Test
  void statusWeiter_whenTheAngebotIsUnknown_thenAnswers404() throws Exception {
    // Given
    when(statuswechsel.weiter(ANGEBOT)).thenThrow(new AngebotNichtGefunden());

    // When / Then
    mockMvc
        .perform(post("/api/angebote/{id}/status/weiter", Long.valueOf(ANGEBOT)))
        .andExpect(status().isNotFound());
  }
}
