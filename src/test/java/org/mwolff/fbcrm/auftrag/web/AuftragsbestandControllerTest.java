package org.mwolff.fbcrm.auftrag.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.auftrag.application.Auftragsbestand;
import org.mwolff.fbcrm.auftrag.application.AuftragsbestandUseCase;
import org.mwolff.fbcrm.auftrag.application.AuftragsbestandZeile;
import org.mwolff.fbcrm.auftrag.domain.Auftragsstatus;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen Anwendungsfall und HTTP fuer den Auftragsbestand (Kriterien 12, 13).
 *
 * <p>Zwei Blickwinkel in einer Klasse, wie in {@code PipelineControllerTest}: die Abbildung direkt
 * an der Methode, und die Faelle, bei denen die <b>Form</b> der Antwort die Aussage ist, ueber eine
 * schlanke MockMvc-Strecke. Der Gegenstand dort ist das Geld: Es geht als Dezimaltext mit zwei
 * Nachkommastellen hinaus, und der leere Bestand traegt darum zweimal {@code 0.00} — eine {@code 0}
 * ohne Cent waere eine andere Zahl auf dem Bildschirm.
 *
 * <p>Die Summen kommen als Vorgabe des Anwendungsfalls und werden hier nicht neu gebildet: Der
 * Controller entscheidet nichts (CLAUDE-java.md §6.3). Dass der Weg ohne Sitzung mit 401 antwortet,
 * weist {@code AuftragsbestandIT} gegen den echten Kontext nach — die schlanke Strecke hier traegt
 * die Sicherheitskette nicht.
 */
@ExtendWith(MockitoExtension.class)
class AuftragsbestandControllerTest {

  private static final BigDecimal NULL_EURO = new BigDecimal("0.00");

  @Mock private AuftragsbestandUseCase useCase;

  private AuftragsbestandController controller;
  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    controller = new AuftragsbestandController(useCase);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private static AuftragsbestandZeile zeile() {
    return new AuftragsbestandZeile(
        3L,
        13L,
        "Website-Relaunch",
        "Adler AG",
        7L,
        "AU-2026-001",
        Auftragsstatus.IN_ARBEIT,
        new BigDecimal("1000.00"),
        NULL_EURO,
        new BigDecimal("1000.00"));
  }

  private static Auftragsbestand bestand() {
    return new Auftragsbestand(
        List.of(zeile()), new BigDecimal("1000.00"), new BigDecimal("1000.00"));
  }

  @Test
  void auftragsbestand_thenAnswersWithBothTotalsAndEveryFieldOfTheRow() {
    // Given — Kriterien 12, 13.
    when(useCase.bestand()).thenReturn(bestand());

    // When
    final AuftragsbestandResponse antwort = controller.auftragsbestand();

    // Then
    assertThat(antwort)
        .isEqualTo(
            new AuftragsbestandResponse(
                List.of(
                    new AuftragsbestandZeileResponse(
                        3L,
                        13L,
                        "Website-Relaunch",
                        "Adler AG",
                        7L,
                        "AU-2026-001",
                        Auftragsstatus.IN_ARBEIT,
                        new BigDecimal("1000.00"),
                        NULL_EURO,
                        new BigDecimal("1000.00"))),
                new BigDecimal("1000.00"),
                new BigDecimal("1000.00")));
  }

  @Test
  void auftragsbestand_thenCarriesEveryFieldUnderTheAgreedName() throws Exception {
    // Given — die Feldnamen sind im Plan festgelegt, damit Parser und Record zusammen entstehen.
    when(useCase.bestand()).thenReturn(bestand());

    // When / Then
    mockMvc
        .perform(get("/api/auftragsbestand"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.beauftragt").value(1000.00))
        .andExpect(jsonPath("$.nochOffen").value(1000.00))
        .andExpect(jsonPath("$.zeilen[0].vorgangId").value(3))
        .andExpect(jsonPath("$.zeilen[0].vorgangNummer").value(13))
        .andExpect(jsonPath("$.zeilen[0].vorgangTitel").value("Website-Relaunch"))
        .andExpect(jsonPath("$.zeilen[0].firma").value("Adler AG"))
        .andExpect(jsonPath("$.zeilen[0].auftragId").value(7))
        .andExpect(jsonPath("$.zeilen[0].nummer").value("AU-2026-001"))
        .andExpect(jsonPath("$.zeilen[0].status").value("IN_ARBEIT"))
        .andExpect(jsonPath("$.zeilen[0].auftragssumme").value(1000.00))
        .andExpect(jsonPath("$.zeilen[0].abgerechnet").value(0.00))
        .andExpect(jsonPath("$.zeilen[0].offenerRest").value(1000.00));
  }

  @Test
  void auftragsbestand_givenAnEmptyBestand_thenTwoZeroTotalsAndNoRow() throws Exception {
    // Given — daran erkennt die Oberflaeche „kein Auftragsbestand".
    when(useCase.bestand()).thenReturn(new Auftragsbestand(List.of(), NULL_EURO, NULL_EURO));

    // When / Then
    mockMvc
        .perform(get("/api/auftragsbestand"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.zeilen").isEmpty())
        .andExpect(content().string(containsString("\"beauftragt\":0.00")))
        .andExpect(content().string(containsString("\"nochOffen\":0.00")));
  }
}
