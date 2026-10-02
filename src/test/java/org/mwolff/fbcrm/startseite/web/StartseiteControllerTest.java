package org.mwolff.fbcrm.startseite.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotMitFirma;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.mwolff.fbcrm.rechnung.application.Monatsabrechnung;
import org.mwolff.fbcrm.startseite.application.Angebotsanteil;
import org.mwolff.fbcrm.startseite.application.NichtAbgerechnet;
import org.mwolff.fbcrm.startseite.application.StartseiteUseCase;
import org.mwolff.fbcrm.startseite.application.Startseitenstand;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen dem Stand der Startseite und HTTP (Issue #214; Plan #208, E7).
 *
 * <p>Dieselbe Strecke wie in {@code ArbeitszeitControllerTest}: {@code standaloneSetup} mit dem
 * echten {@link GlobalExceptionHandler}, damit ein Monatswert, der kein Monat ist, als 400 sichtbar
 * wird und nicht als Serverfehler.
 *
 * <p>Gegenstand ist die Abbildung jedes Feldes des {@link Startseitenstand} auf die Antwort — die
 * drei Kennzahlen mit ihren Zeilen, der geltende Monat und die zwoelf waehlbaren — sowie der Monat
 * als Parameter und sein Weglassen: Fehlt er, geht {@code Optional.empty()} an den Anwendungsfall,
 * und welcher Monat laeuft, entscheidet dieser an seiner Uhr (Plan #208, E8).
 *
 * <p><b>Welche Felder</b> die Antwort traegt, entscheidet dieser Layer; <b>wie</b> Monat und Datum
 * geschrieben werden, entscheidet der von Spring Boot gebaute ObjectMapper, den {@code
 * standaloneSetup} nicht kennt — hier steht deshalb nur, dass sie da sind. Die Textform belegt
 * {@link StartseiteControllerIT} am echten Weg.
 */
@ExtendWith(MockitoExtension.class)
class StartseiteControllerTest {

  private static final String PFAD = "/api/startseite";

  private static final YearMonth OKTOBER = YearMonth.of(2026, 10);
  private static final LocalDate ANGEBOTSDATUM = LocalDate.of(2026, 9, 20);
  private static final Instant ANGELEGT = Instant.parse("2026-09-20T08:00:00Z");

  private static final long BESTELLT_ID = 11L;
  private static final long ERLEDIGT_ID = 12L;
  private static final String ADLER = "Adler AG";
  private static final String BUCHE = "Buche GmbH";

  @Mock private StartseiteUseCase useCase;

  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new StartseiteController(useCase))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private static AngebotMitFirma angebot(
      final long id, final Angebotsstatus status, final String firmaName) {
    return new AngebotMitFirma(
        new Angebot(
            Long.valueOf(id),
            5L,
            null,
            status.intern(),
            status,
            ANGEBOTSDATUM,
            "Neugestaltung der Website",
            List.of(
                new Angebotsposition(
                    Long.valueOf(101L),
                    "Beratung",
                    Abrechnungsmodus.AUFWAND,
                    new BigDecimal("20.00"),
                    Einheit.STUNDE,
                    new BigDecimal("100.00"))),
            ANGELEGT,
            ANGELEGT),
        firmaName);
  }

  private static Startseitenstand stand() {
    return new Startseitenstand(
        OKTOBER,
        IntStream.range(0, 12).mapToObj(OKTOBER::minusMonths).toList(),
        List.of(
            angebot(BESTELLT_ID, Angebotsstatus.BESTELLT, ADLER),
            angebot(ERLEDIGT_ID, Angebotsstatus.ERLEDIGT, BUCHE)),
        new NichtAbgerechnet(
            new BigDecimal("1200.00"),
            new BigDecimal("400.00"),
            List.of(
                new Angebotsanteil(
                    angebot(BESTELLT_ID, Angebotsstatus.BESTELLT, ADLER),
                    new BigDecimal("1200.00")))),
        new Monatsabrechnung(new BigDecimal("1000.00"), new BigDecimal("1190.00"), 2),
        new BigDecimal("12.50"));
  }

  @Test
  void stand_thenAnswers200WithTheMonthAndTheTwelveSelectableOnes() throws Exception {
    // Given — E17: die Ansicht rendert genau diese Liste, und der gezeigte Monat ist einer davon.
    when(useCase.stand(Optional.of(OKTOBER))).thenReturn(stand());

    // When / Then
    mockMvc
        .perform(get(PFAD).param("monat", "2026-10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.monat").exists())
        .andExpect(jsonPath("$.monate.length()").value(12));
  }

  @Test
  void stand_thenEachOfferInProgressCarriesCompanyDateAndStatus() throws Exception {
    // Given — Kennzahl 1: die Liste ist die Wahrheit, die Ansicht zaehlt sie (E20).
    when(useCase.stand(Optional.of(OKTOBER))).thenReturn(stand());

    // When / Then
    mockMvc
        .perform(get(PFAD).param("monat", "2026-10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.inArbeit.length()").value(2))
        .andExpect(jsonPath("$.inArbeit[0].angebotId").value(Long.valueOf(BESTELLT_ID)))
        .andExpect(jsonPath("$.inArbeit[0].firmaName").value(ADLER))
        .andExpect(jsonPath("$.inArbeit[0].angebotDatum").exists())
        .andExpect(jsonPath("$.inArbeit[0].status").value("BESTELLT"))
        .andExpect(jsonPath("$.inArbeit[1].angebotId").value(Long.valueOf(ERLEDIGT_ID)))
        .andExpect(jsonPath("$.inArbeit[1].firmaName").value(BUCHE))
        .andExpect(jsonPath("$.inArbeit[1].status").value("ERLEDIGT"));
  }

  @Test
  void stand_thenTheSecondFigureCarriesBothAmountsAndItsOffers() throws Exception {
    // Given — Kriterien 5 und 6: der Stand von heute und die Monatszeile daneben.
    when(useCase.stand(Optional.of(OKTOBER))).thenReturn(stand());

    // When / Then
    mockMvc
        .perform(get(PFAD).param("monat", "2026-10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nichtAbgerechnet.netto").value(1200.00))
        .andExpect(jsonPath("$.nichtAbgerechnet.erfasstImMonat").value(400.00))
        .andExpect(jsonPath("$.nichtAbgerechnet.angebote.length()").value(1))
        .andExpect(
            jsonPath("$.nichtAbgerechnet.angebote[0].angebotId").value(Long.valueOf(BESTELLT_ID)))
        .andExpect(jsonPath("$.nichtAbgerechnet.angebote[0].firmaName").value(ADLER))
        .andExpect(jsonPath("$.nichtAbgerechnet.angebote[0].angebotDatum").exists())
        .andExpect(jsonPath("$.nichtAbgerechnet.angebote[0].netto").value(1200.00));
  }

  @Test
  void stand_thenTheThirdFigureCarriesNetGrossAndCount() throws Exception {
    // Given — Kriterium 7: Netto fuehrt, Brutto steht daneben, die Anzahl ist ein eigenes Feld.
    when(useCase.stand(Optional.of(OKTOBER))).thenReturn(stand());

    // When / Then
    mockMvc
        .perform(get(PFAD).param("monat", "2026-10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.abgerechnet.netto").value(1000.00))
        .andExpect(jsonPath("$.abgerechnet.brutto").value(1190.00))
        .andExpect(jsonPath("$.abgerechnet.anzahl").value(2));
  }

  @Test
  void stand_thenTheInterneStundenOfTheMonthStandBesideTheFigures() throws Exception {
    // Given — Kriterium 9 von #207: eine Stundenzahl, getrennt von allen Betraegen.
    when(useCase.stand(Optional.of(OKTOBER))).thenReturn(stand());

    // When / Then
    mockMvc
        .perform(get(PFAD).param("monat", "2026-10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.interneStundenImMonat").value(12.50));
  }

  @Test
  void stand_withoutTheParameter_thenAsksWithoutAMonth() throws Exception {
    // Given — E8: welcher Monat laeuft, entscheidet der Anwendungsfall an seiner Uhr.
    when(useCase.stand(Optional.empty())).thenReturn(stand());

    // When / Then
    mockMvc.perform(get(PFAD)).andExpect(status().isOk());
    verify(useCase).stand(Optional.empty());
  }

  @Test
  void stand_givenAMonthThatIsNoMonth_thenAnswers400() throws Exception {
    // Given — 2026-13 gibt es nicht; das ist eine fehlerhafte Anfrage und kein Serverfehler.
    // When / Then
    mockMvc.perform(get(PFAD).param("monat", "2026-13")).andExpect(status().isBadRequest());
    verifyNoInteractions(useCase);
  }
}
