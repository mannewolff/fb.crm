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
import java.time.Year;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
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
import org.mwolff.fbcrm.startseite.application.Abgerechnet;
import org.mwolff.fbcrm.startseite.application.Abrechnungsmonat;
import org.mwolff.fbcrm.startseite.application.Angebotsanteil;
import org.mwolff.fbcrm.startseite.application.NichtAbgerechnet;
import org.mwolff.fbcrm.startseite.application.StartseiteUseCase;
import org.mwolff.fbcrm.startseite.application.Startseitenstand;
import org.mwolff.fbcrm.startseite.application.WaehlbareZeitraeume;
import org.mwolff.fbcrm.startseite.application.Zeitraum;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen dem Stand der Startseite und HTTP (Issue #214; Plan #274, E1, E3, E10,
 * E11).
 *
 * <p>Dieselbe Strecke wie in {@code ArbeitszeitControllerTest}: {@code standaloneSetup} mit dem
 * echten {@link GlobalExceptionHandler}, damit ein Wert, der kein Zeitraum ist, als 400 sichtbar
 * wird und nicht als Serverfehler. Weil {@code standaloneSetup} keine Beans kennt, steht der {@link
 * ZeitraumConverter} ausdruecklich im Wandlungsdienst — sonst wuerde hier nichts gewandelt.
 *
 * <p>Gegenstand ist die Abbildung jedes Feldes des {@link Startseitenstand} auf die Antwort — die
 * drei Kennzahlen mit ihren Zeilen, der geltende Zeitraum und die waehlbaren — sowie der Zeitraum
 * als Parameter und sein Weglassen: Fehlt er, geht {@code Optional.empty()} an den Anwendungsfall,
 * und welcher Monat laeuft, entscheidet dieser an seiner Uhr (Plan #208, E8).
 *
 * <p><b>Welche Felder</b> die Antwort traegt, entscheidet dieser Layer; <b>wie</b> Monat und Datum
 * geschrieben werden, entscheidet der von Spring Boot gebaute ObjectMapper, den {@code
 * standaloneSetup} nicht kennt — hier steht deshalb nur, dass sie da sind. Die Textform belegt
 * {@link StartseiteControllerIT} am echten Weg. Art, Wert und die Jahre sind dagegen schon hier
 * Text, weil dieser Layer sie als Text baut (E10).
 */
@ExtendWith(MockitoExtension.class)
class StartseiteControllerTest {

  private static final String PFAD = "/api/startseite";

  private static final YearMonth OKTOBER = YearMonth.of(2026, 10);
  private static final Zeitraum.Monat IM_OKTOBER = new Zeitraum.Monat(OKTOBER);
  private static final Zeitraum.Jahr IM_JAHR = new Zeitraum.Jahr(Year.of(2026));
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
    final DefaultFormattingConversionService wandlung = new DefaultFormattingConversionService();
    wandlung.addConverter(new ZeitraumConverter());
    mockMvc =
        MockMvcBuilders.standaloneSetup(new StartseiteController(useCase))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setConversionService(wandlung)
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
    return stand(
        IM_OKTOBER,
        new Abgerechnet(
            new Monatsabrechnung(new BigDecimal("1000.00"), new BigDecimal("1190.00"), 2),
            List.of()));
  }

  private static Startseitenstand imJahr() {
    return stand(
        IM_JAHR,
        new Abgerechnet(
            new Monatsabrechnung(new BigDecimal("1500.00"), new BigDecimal("1785.00"), 3),
            List.of(
                new Abrechnungsmonat(
                    YearMonth.of(2026, 8),
                    new Monatsabrechnung(new BigDecimal("500.00"), new BigDecimal("595.00"), 1)),
                new Abrechnungsmonat(
                    OKTOBER,
                    new Monatsabrechnung(
                        new BigDecimal("1000.00"), new BigDecimal("1190.00"), 2)))));
  }

  private static Startseitenstand stand(final Zeitraum zeitraum, final Abgerechnet abgerechnet) {
    return new Startseitenstand(
        zeitraum,
        new WaehlbareZeitraeume(
            List.of(Year.of(2026), Year.of(2025)),
            List.of(OKTOBER, YearMonth.of(2026, 8), YearMonth.of(2025, 12))),
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
        abgerechnet,
        new BigDecimal("12.50"));
  }

  @Test
  void stand_givenAMonat_thenAnswers200WithItsArtAndWertAndTheSelectableOnes() throws Exception {
    // Given — E10: Art und Wert getrennt, die waehlbaren Jahre und Monate daneben, alles als Text.
    when(useCase.stand(Optional.of(IM_OKTOBER))).thenReturn(stand());

    // When / Then
    mockMvc
        .perform(get(PFAD).param("zeitraum", "2026-10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.zeitraum.art").value("MONAT"))
        .andExpect(jsonPath("$.zeitraum.wert").value("2026-10"))
        .andExpect(jsonPath("$.waehlbar.jahre.length()").value(2))
        .andExpect(jsonPath("$.waehlbar.jahre[0]").value("2026"))
        .andExpect(jsonPath("$.waehlbar.jahre[1]").value("2025"))
        .andExpect(jsonPath("$.waehlbar.monate.length()").value(3));
  }

  @Test
  void stand_givenAJahr_thenAnswers200WithItsArtAndWert() throws Exception {
    // Given — E1: ein Parameter fuer beide Arten.
    when(useCase.stand(Optional.of(IM_JAHR))).thenReturn(imJahr());

    // When / Then
    mockMvc
        .perform(get(PFAD).param("zeitraum", "2026"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.zeitraum.art").value("JAHR"))
        .andExpect(jsonPath("$.zeitraum.wert").value("2026"));
  }

  @Test
  void stand_thenEachOfferInProgressCarriesCompanyDateAndStatus() throws Exception {
    // Given — Kennzahl 1: die Liste ist die Wahrheit, die Ansicht zaehlt sie (E20).
    when(useCase.stand(Optional.of(IM_OKTOBER))).thenReturn(stand());

    // When / Then
    mockMvc
        .perform(get(PFAD).param("zeitraum", "2026-10"))
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
    // Given — Kriterien 5 und 6: der Stand von heute und die Zeile des Zeitraums daneben.
    when(useCase.stand(Optional.of(IM_OKTOBER))).thenReturn(stand());

    // When / Then
    mockMvc
        .perform(get(PFAD).param("zeitraum", "2026-10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nichtAbgerechnet.netto").value(1200.00))
        .andExpect(jsonPath("$.nichtAbgerechnet.erfasstImZeitraum").value(400.00))
        .andExpect(jsonPath("$.nichtAbgerechnet.angebote.length()").value(1))
        .andExpect(
            jsonPath("$.nichtAbgerechnet.angebote[0].angebotId").value(Long.valueOf(BESTELLT_ID)))
        .andExpect(jsonPath("$.nichtAbgerechnet.angebote[0].firmaName").value(ADLER))
        .andExpect(jsonPath("$.nichtAbgerechnet.angebote[0].angebotDatum").exists())
        .andExpect(jsonPath("$.nichtAbgerechnet.angebote[0].netto").value(1200.00));
  }

  @Test
  void stand_givenAMonat_thenTheThirdFigureCarriesNetGrossCountAndNoMonths() throws Exception {
    // Given — Kriterium 7 und E11: bei Monatswahl ist die Monatsliste leer und nicht null.
    when(useCase.stand(Optional.of(IM_OKTOBER))).thenReturn(stand());

    // When / Then
    mockMvc
        .perform(get(PFAD).param("zeitraum", "2026-10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.abgerechnet.netto").value(1000.00))
        .andExpect(jsonPath("$.abgerechnet.brutto").value(1190.00))
        .andExpect(jsonPath("$.abgerechnet.anzahl").value(2))
        .andExpect(jsonPath("$.abgerechnet.monate").isArray())
        .andExpect(jsonPath("$.abgerechnet.monate.length()").value(0));
  }

  @Test
  void stand_givenAJahr_thenTheThirdFigureCarriesTheSumAndItsMonths() throws Exception {
    // Given — #273, Kriterium 7: je Monat Anzahl, Netto und Brutto, aeltester zuerst.
    when(useCase.stand(Optional.of(IM_JAHR))).thenReturn(imJahr());

    // When / Then
    mockMvc
        .perform(get(PFAD).param("zeitraum", "2026"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.abgerechnet.netto").value(1500.00))
        .andExpect(jsonPath("$.abgerechnet.brutto").value(1785.00))
        .andExpect(jsonPath("$.abgerechnet.anzahl").value(3))
        .andExpect(jsonPath("$.abgerechnet.monate.length()").value(2))
        .andExpect(jsonPath("$.abgerechnet.monate[0].monat").exists())
        .andExpect(jsonPath("$.abgerechnet.monate[0].anzahl").value(1))
        .andExpect(jsonPath("$.abgerechnet.monate[0].netto").value(500.00))
        .andExpect(jsonPath("$.abgerechnet.monate[0].brutto").value(595.00))
        .andExpect(jsonPath("$.abgerechnet.monate[1].anzahl").value(2))
        .andExpect(jsonPath("$.abgerechnet.monate[1].netto").value(1000.00))
        .andExpect(jsonPath("$.abgerechnet.monate[1].brutto").value(1190.00));
  }

  @Test
  void stand_thenTheInterneStundenOfTheZeitraumStandBesideTheFigures() throws Exception {
    // Given — Kriterium 9 von #207: eine Stundenzahl, getrennt von allen Betraegen.
    when(useCase.stand(Optional.of(IM_OKTOBER))).thenReturn(stand());

    // When / Then
    mockMvc
        .perform(get(PFAD).param("zeitraum", "2026-10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.interneStundenImZeitraum").value(12.50));
  }

  @Test
  void stand_withoutTheParameter_thenAsksWithoutAZeitraum() throws Exception {
    // Given — E8: welcher Monat laeuft, entscheidet der Anwendungsfall an seiner Uhr.
    when(useCase.stand(Optional.empty())).thenReturn(stand());

    // When / Then
    mockMvc.perform(get(PFAD)).andExpect(status().isOk());
    verify(useCase).stand(Optional.empty());
  }

  @Test
  void stand_givenAValueThatIsNoZeitraum_thenAnswers400() throws Exception {
    // Given — E3: 2026-13 ist kein Zeitraum; das ist eine fehlerhafte Anfrage, kein Serverfehler.
    // When / Then
    mockMvc.perform(get(PFAD).param("zeitraum", "2026-13")).andExpect(status().isBadRequest());
    verifyNoInteractions(useCase);
  }
}
