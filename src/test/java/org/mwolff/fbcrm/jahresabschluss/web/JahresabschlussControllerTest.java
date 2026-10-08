package org.mwolff.fbcrm.jahresabschluss.web;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.mwolff.fbcrm.jahresabschluss.application.Angebotsbilanz;
import org.mwolff.fbcrm.jahresabschluss.application.Einnahmen;
import org.mwolff.fbcrm.jahresabschluss.application.JahrOhneDaten;
import org.mwolff.fbcrm.jahresabschluss.application.Jahresabschluss;
import org.mwolff.fbcrm.jahresabschluss.application.JahresabschlussUseCase;
import org.mwolff.fbcrm.jahresabschluss.application.Jahresarbeitszeit;
import org.mwolff.fbcrm.jahresabschluss.application.Jahreszeile;
import org.mwolff.fbcrm.jahresabschluss.application.Kundenzeile;
import org.mwolff.fbcrm.jahresabschluss.application.Rechnungsstand;
import org.mwolff.fbcrm.jahresabschluss.application.Steuerzeile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen dem Jahresabschluss und HTTP (Issue #294; Plan #288, E2, E3, E23).
 *
 * <p>Dieselbe Strecke wie in {@code StartseiteControllerTest}: {@code standaloneSetup} mit dem
 * echten {@link GlobalExceptionHandler}, damit ein Jahr ohne Daten als 404 und ein Pfad, der kein
 * Jahr ist, als 400 sichtbar wird. Einen eigenen Wandler fuer {@link Year} gibt dieser Test dem
 * Wandlungsdienst <b>nicht</b> mit — er belegt damit, dass Spring {@code String} → {@code Year} von
 * selbst wandelt (E23) und ein {@code JahrConverter} entfaellt.
 *
 * <p>Gegenstand ist die Abbildung jedes Feldes von {@link Jahreszeile} und {@link Jahresabschluss}
 * auf die Antwort, das Jahr als vierstelliger Text und {@code null} dort, wo eine Kennzahl nicht
 * berechenbar ist (#287, Kriterium 11) — nicht 0.
 */
@ExtendWith(MockitoExtension.class)
class JahresabschlussControllerTest {

  private static final String PFAD = "/api/jahresabschluesse";

  private static final Year JAHR_2025 = Year.of(2025);
  private static final Year JAHR_2026 = Year.of(2026);

  @Mock private JahresabschlussUseCase useCase;

  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new JahresabschlussController(useCase))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  /** Ein Abschluss mit allen Teilen und mit Werten, die berechenbar sind. */
  private static Jahresabschluss abschluss() {
    return new Jahresabschluss(
        JAHR_2025,
        false,
        new Einnahmen(
            new BigDecimal("1500.00"), new BigDecimal("1765.00"), new BigDecimal("265.00")),
        new Rechnungsstand(3, 1, new BigDecimal("200.00"), 1, new BigDecimal("100.00")),
        List.of(
            new Steuerzeile(
                new BigDecimal("19.00"), new BigDecimal("1000.00"), new BigDecimal("190.00")),
            new Steuerzeile(null, new BigDecimal("500.00"), new BigDecimal("75.00"))),
        new Angebotsbilanz(
            4, 3, 1, new BigDecimal("75.0"), new BigDecimal("4000.00"), new BigDecimal("3000.00")),
        List.of(
            new Kundenzeile("Adler AG", new BigDecimal("1000.00"), new BigDecimal("66.7")),
            new Kundenzeile("Buche GmbH", new BigDecimal("500.00"), new BigDecimal("33.3"))),
        new Jahresarbeitszeit(
            new BigDecimal("15.00"), new BigDecimal("4.50"), new BigDecimal("100.00")));
  }

  /** Ein Abschluss, in dem keine Quote und kein Erloes je Stunde berechenbar ist. */
  private static Jahresabschluss ohneNenner() {
    return new Jahresabschluss(
        JAHR_2026,
        true,
        new Einnahmen(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
        new Rechnungsstand(0, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO),
        List.of(),
        new Angebotsbilanz(0, 0, 0, null, BigDecimal.ZERO, BigDecimal.ZERO),
        List.of(new Kundenzeile("Adler AG", BigDecimal.ZERO, null)),
        new Jahresarbeitszeit(BigDecimal.ZERO, new BigDecimal("2.00"), null));
  }

  @Test
  void jahre_thenAnswers200WithOneLinePerYearInTheGivenOrder() throws Exception {
    // Given — E2: je Jahr die drei Hauptzahlen; die Reihenfolge entscheidet der Anwendungsfall.
    when(useCase.jahre())
        .thenReturn(
            List.of(
                new Jahreszeile(
                    JAHR_2026, true, new BigDecimal("800.00"), 2, new BigDecimal("50.0")),
                new Jahreszeile(JAHR_2025, false, new BigDecimal("1500.00"), 3, null)));

    // When / Then — das Jahr als Text, die nicht berechenbare Quote als null und nicht als 0.
    mockMvc
        .perform(get(PFAD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].jahr").value("2026"))
        .andExpect(jsonPath("$[0].laeuftNoch").value(true))
        .andExpect(jsonPath("$[0].netto").value(800.00))
        .andExpect(jsonPath("$[0].anzahl").value(2))
        .andExpect(jsonPath("$[0].annahmequote").value(50.0))
        .andExpect(jsonPath("$[1].jahr").value("2025"))
        .andExpect(jsonPath("$[1].laeuftNoch").value(false))
        .andExpect(jsonPath("$[1].netto").value(1500.00))
        .andExpect(jsonPath("$[1].anzahl").value(3))
        .andExpect(jsonPath("$[1].annahmequote").value(nullValue()))
        .andExpect(jsonPath("$[1].annahmequote").hasJsonPath());
  }

  @Test
  void jahre_givenAnEmptyStock_thenAnswers200WithAnEmptyList() throws Exception {
    // Given — #287, Kriterium 2: ohne Jahr eine leere Liste, kein Fehler.
    when(useCase.jahre()).thenReturn(List.of());

    // When / Then
    mockMvc
        .perform(get(PFAD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void abschluss_thenAnswers200WithYearAndRunningFlag() throws Exception {
    // Given
    when(useCase.abschluss(JAHR_2025)).thenReturn(abschluss());

    // When / Then
    mockMvc
        .perform(get(PFAD + "/2025"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jahr").value("2025"))
        .andExpect(jsonPath("$.laeuftNoch").value(false));
  }

  @Test
  void abschluss_thenCarriesEinnahmenAndRechnungsstand() throws Exception {
    // Given — #287, Kriterien 4 und 5.
    when(useCase.abschluss(JAHR_2025)).thenReturn(abschluss());

    // When / Then
    mockMvc
        .perform(get(PFAD + "/2025"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.einnahmen.netto").value(1500.00))
        .andExpect(jsonPath("$.einnahmen.brutto").value(1765.00))
        .andExpect(jsonPath("$.einnahmen.umsatzsteuer").value(265.00))
        .andExpect(jsonPath("$.rechnungsstand.anzahl").value(3))
        .andExpect(jsonPath("$.rechnungsstand.offenAnzahl").value(1))
        .andExpect(jsonPath("$.rechnungsstand.offenNetto").value(200.00))
        .andExpect(jsonPath("$.rechnungsstand.abgeschriebenAnzahl").value(1))
        .andExpect(jsonPath("$.rechnungsstand.abgeschriebenNetto").value(100.00));
  }

  @Test
  void abschluss_thenCarriesTheTaxLinesWithNullAsRateOfTheNachtragLine() throws Exception {
    // Given — #287, Kriterium 6: die Zeile „Steuersatz nicht erfasst" traegt keinen Satz.
    when(useCase.abschluss(JAHR_2025)).thenReturn(abschluss());

    // When / Then
    mockMvc
        .perform(get(PFAD + "/2025"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.steuerzeilen.length()").value(2))
        .andExpect(jsonPath("$.steuerzeilen[0].satz").value(19.00))
        .andExpect(jsonPath("$.steuerzeilen[0].netto").value(1000.00))
        .andExpect(jsonPath("$.steuerzeilen[0].umsatzsteuer").value(190.00))
        .andExpect(jsonPath("$.steuerzeilen[1].satz").hasJsonPath())
        .andExpect(jsonPath("$.steuerzeilen[1].satz").value(nullValue()))
        .andExpect(jsonPath("$.steuerzeilen[1].netto").value(500.00))
        .andExpect(jsonPath("$.steuerzeilen[1].umsatzsteuer").value(75.00));
  }

  @Test
  void abschluss_thenCarriesAngebotsbilanzKundenAndArbeitszeit() throws Exception {
    // Given — #287, Kriterien 7 bis 10.
    when(useCase.abschluss(JAHR_2025)).thenReturn(abschluss());

    // When / Then
    mockMvc
        .perform(get(PFAD + "/2025"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.angebotsbilanz.abgegeben").value(4))
        .andExpect(jsonPath("$.angebotsbilanz.angenommen").value(3))
        .andExpect(jsonPath("$.angebotsbilanz.offen").value(1))
        .andExpect(jsonPath("$.angebotsbilanz.annahmequote").value(75.0))
        .andExpect(jsonPath("$.angebotsbilanz.volumenAbgegeben").value(4000.00))
        .andExpect(jsonPath("$.angebotsbilanz.volumenAngenommen").value(3000.00))
        .andExpect(jsonPath("$.kunden.length()").value(2))
        .andExpect(jsonPath("$.kunden[0].firmaName").value("Adler AG"))
        .andExpect(jsonPath("$.kunden[0].netto").value(1000.00))
        .andExpect(jsonPath("$.kunden[0].anteil").value(66.7))
        .andExpect(jsonPath("$.kunden[1].firmaName").value("Buche GmbH"))
        .andExpect(jsonPath("$.kunden[1].netto").value(500.00))
        .andExpect(jsonPath("$.kunden[1].anteil").value(33.3))
        .andExpect(jsonPath("$.arbeitszeit.kundenStunden").value(15.00))
        .andExpect(jsonPath("$.arbeitszeit.interneStunden").value(4.50))
        .andExpect(jsonPath("$.arbeitszeit.erloesJeStunde").value(100.00));
  }

  @Test
  void abschluss_givenNoDenominators_thenTheFiguresAreNullAndNotZero() throws Exception {
    // Given — #287, Kriterium 11: Quote, Anteil und Erloes je Stunde ohne Nenner.
    when(useCase.abschluss(JAHR_2026)).thenReturn(ohneNenner());

    // When / Then
    mockMvc
        .perform(get(PFAD + "/2026"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.laeuftNoch").value(true))
        .andExpect(jsonPath("$.steuerzeilen.length()").value(0))
        .andExpect(jsonPath("$.angebotsbilanz.annahmequote").hasJsonPath())
        .andExpect(jsonPath("$.angebotsbilanz.annahmequote").value(nullValue()))
        .andExpect(jsonPath("$.kunden[0].anteil").hasJsonPath())
        .andExpect(jsonPath("$.kunden[0].anteil").value(nullValue()))
        .andExpect(jsonPath("$.arbeitszeit.erloesJeStunde").hasJsonPath())
        .andExpect(jsonPath("$.arbeitszeit.erloesJeStunde").value(nullValue()))
        .andExpect(jsonPath("$.arbeitszeit.interneStunden").value(2.00));
  }

  @Test
  void abschluss_givenAYearWithoutData_thenAnswers404() throws Exception {
    // Given — E3: ein Jahr ohne gestellte Rechnung und ohne abgegebenes Angebot hat keinen.
    when(useCase.abschluss(Year.of(1999))).thenThrow(new JahrOhneDaten());

    // When / Then
    mockMvc.perform(get(PFAD + "/1999")).andExpect(status().isNotFound());
  }

  @Test
  void abschluss_givenAPathThatIsNoYear_thenAnswers400AndNot404() throws Exception {
    // Given — E23: „zweitausend" ist kein Jahr; das ist eine fehlerhafte Anfrage, kein Jahr ohne
    // Daten.
    // When / Then
    mockMvc.perform(get(PFAD + "/zweitausend")).andExpect(status().isBadRequest());
    verifyNoInteractions(useCase);
  }
}
