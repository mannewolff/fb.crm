package org.mwolff.fbcrm.rechnung.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.mwolff.fbcrm.rechnung.application.BriefbogenLesenUseCase;
import org.mwolff.fbcrm.rechnung.application.BriefbogenPflegenUseCase;
import org.mwolff.fbcrm.rechnung.application.Briefbogenstand;
import org.mwolff.fbcrm.rechnung.domain.Briefbogen;
import org.mwolff.fbcrm.rechnung.domain.BriefbogenRepository;
import org.mwolff.fbcrm.rechnung.domain.Randmasse;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen Anwendungsfall und HTTP fuer Stand und Pflege des Briefbogens
 * (fachliche Quelle #361, Kriterien 13, 15 und 16; Plan #363, E1 und E9).
 *
 * <p>Wie {@code RechnungseinstellungenControllerTest} ueber eine schlanke MockMvc-Strecke mit dem
 * echten {@link GlobalExceptionHandler}: Hier ist die <b>Form</b> der Antwort die Aussage. Fuer den
 * unmoeglichen Rand laeuft der echte {@link BriefbogenPflegenUseCase} gegen einen gemockten Bestand
 * — die Abweisung entsteht in der Fachschicht, und erst so zeigt sich, dass sie als 422 am Feld
 * ankommt und den Grenzwert nennt.
 */
@ExtendWith(MockitoExtension.class)
class BriefbogenControllerTest {

  private static final String PFAD = "/api/briefpapier";

  /** Die Raender aus Kriterium 15 in Zehntel-Millimetern. */
  private static final Randmasse VORGABE = new Randmasse(250, 200, 985, 200, 200, 200);

  private static final Randmasse EINGESTELLT = new Randmasse(300, 150, 1_000, 250, 180, 220);

  @Mock private BriefbogenLesenUseCase lesen;
  @Mock private BriefbogenPflegenUseCase pflegen;
  @Mock private BriefbogenRepository bestand;

  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    mockMvc = strecke(new BriefbogenController(lesen, pflegen));
  }

  private static MockMvc strecke(final BriefbogenController controller) {
    return MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new GlobalExceptionHandler())
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
        .thenReturn(new Briefbogenstand(true, "kopf.pdf", 98_765L, 2, true, false, EINGESTELLT));

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
        .thenReturn(new Briefbogenstand(false, null, null, null, true, true, VORGABE));

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
    when(pflegen.pflege(true, false, EINGESTELLT))
        .thenReturn(new Briefbogenstand(false, null, null, null, true, false, EINGESTELLT));

    // When / Then
    mockMvc
        .perform(put(PFAD).contentType(MediaType.APPLICATION_JSON).content(rumpfMit("", "")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.absenderzeile").value(true))
        .andExpect(jsonPath("$.steuerBankZeile").value(false))
        .andExpect(jsonPath("$.links").value(300))
        .andExpect(jsonPath("$.folgeUnten").value(220));
    verify(pflegen).pflege(true, false, EINGESTELLT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"links", "rechts", "inhaltOben", "unten", "folgeOben", "folgeUnten"})
  void pflegen_givenANegativeMargin_thenAnswersWith400AndTheFieldError(final String feld)
      throws Exception {
    // When / Then — Kriterium 13, erster Satz: an der Schnittstelle, nicht erst am
    // Kompaktkonstruktor von Randmasse — der antwortete mit 500.
    mockMvc
        .perform(put(PFAD).contentType(MediaType.APPLICATION_JSON).content(rumpfMit(feld, "-1")))
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
        .perform(put(PFAD).contentType(MediaType.APPLICATION_JSON).content(rumpfMit(feld, "null")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors." + feld).isArray());
    verifyNoInteractions(pflegen);
  }

  @Test
  void pflegen_givenAnImpossibleMargin_thenAnswersWith422AtItsFieldNamingTheLargestValue()
      throws Exception {
    // Given — die echte Pflege gegen einen Bestand mit den Vorgaben; allein der untere Rand
    // weicht ab und traegt hoechstens 70,0 mm (RandgrenzenTest).
    when(bestand.lies()).thenReturn(new Briefbogen(null, true, true, VORGABE));
    final MockMvc echt =
        strecke(
            new BriefbogenController(
                lesen,
                new BriefbogenPflegenUseCase(
                    bestand, Clock.fixed(Instant.parse("2026-10-19T09:30:00Z"), ZoneOffset.UTC))));

    // When / Then
    echt.perform(
            put(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("true", "true", "250", "200", "985", "701", "200", "200")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.fieldErrors.unten[0]")
                .value(
                    "Bei den übrigen eingestellten Rändern darf dieser Rand höchstens 70,0 mm"
                        + " betragen."));
    verify(bestand, never()).speichere(any(), any());
  }
}
