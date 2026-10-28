package org.mwolff.fbcrm.firma.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.text.Collator;
import java.util.List;
import java.util.Locale;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.common.Laenderliste;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Laenderliste fuer die Oberflaeche (Plan #461, E11, E21).
 *
 * <p>Wer sie lesen darf, steht in der Zugangsregel und wird gegen den laufenden Server in {@code
 * LaenderControllerIT} geprueft; hier geht es um Form und Reihenfolge der Antwort.
 */
class LaenderControllerTest {

  private final LaenderController controller = new LaenderController();

  @Test
  void alle_thenEveryCountryOfTheListWithCodeAndGermanName() {
    // When
    final List<LandResponse> antwort = controller.alle();

    // Then
    assertThat(antwort)
        .hasSize(Laenderliste.alle().size())
        .contains(new LandResponse("DE", "Deutschland"), new LandResponse("AT", "Österreich"));
  }

  @Test
  void alle_thenSortedByTheGermanName() {
    // When
    final List<LandResponse> antwort = controller.alle();

    // Then
    assertThat(antwort)
        .extracting(LandResponse::name)
        .isSortedAccordingTo(Collator.getInstance(Locale.GERMAN));
  }

  @Test
  void alle_overHttp_thenAnswersWithCodeAndNameOnly() throws Exception {
    // Given
    final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    // When / Then — der englische Name dient nur der Zuordnung und geht nicht hinaus.
    mockMvc
        .perform(get("/api/laender"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.kuerzel == 'DE')].name").value(Matchers.contains("Deutschland")))
        .andExpect(jsonPath("$[0].englisch").doesNotExist());
  }
}
