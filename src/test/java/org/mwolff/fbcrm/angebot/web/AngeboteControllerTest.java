package org.mwolff.fbcrm.angebot.web;

import static org.hamcrest.Matchers.nullValue;
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
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotMitFirma;
import org.mwolff.fbcrm.angebot.application.AngeboteUebersichtUseCase;
import org.mwolff.fbcrm.angebot.application.Angebotsauswahl;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen Anwendungsfall und HTTP fuer die Uebersicht aller Angebote (Issue #127,
 * Kriterium 8): mit und ohne Filter, und ein unbekannter Status ist 400 und nicht 500.
 *
 * <p><b>Der Storno-Parameter</b> (Issue #335, Plan #332 E6): Ohne Parameter fragt der Controller
 * {@code alle()}, mit {@code status} {@code imStatus(status)}, mit {@code storniert=true} allein
 * {@code stornierte()}. Jede Zeile traegt das Kennzeichen {@code storniert}.
 */
@ExtendWith(MockitoExtension.class)
class AngeboteControllerTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-20T08:00:00Z");

  @Mock private AngeboteUebersichtUseCase uebersicht;

  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new AngeboteController(uebersicht))
            .setControllerAdvice(new GlobalExceptionHandler(konto -> Optional.empty()))
            .build();
  }

  private static AngebotMitFirma zeile(final long id, final Angebotsstatus status) {
    return zeile(id, status, "Website-Relaunch");
  }

  private static AngebotMitFirma zeile(
      final long id, final Angebotsstatus status, final @Nullable String titel) {
    return new AngebotMitFirma(
        new Angebot(
            Long.valueOf(id),
            3L,
            null,
            status.intern(),
            status,
            false,
            null,
            LocalDate.of(2026, 9, 20),
            titel,
            null,
            List.of(
                new Angebotsposition(
                    null,
                    "Konzeption",
                    Abrechnungsmodus.AUFWAND,
                    new BigDecimal("2.50"),
                    Einheit.PERSONENTAG,
                    new BigDecimal("1000.01"),
                    Year.of(2026))),
            ANGELEGT,
            ANGELEGT),
        "Adler AG");
  }

  private static AngebotMitFirma storniert(final long id, final Angebotsstatus status) {
    final AngebotMitFirma zeile = zeile(id, status);
    return new AngebotMitFirma(
        zeile.angebot().storniert("vom Kunden abgelehnt", ANGELEGT), zeile.firmaName());
  }

  @Test
  void angebote_withoutAFilter_thenAnswersEveryRowInTheOrderOfTheUseCase() throws Exception {
    // Given
    when(uebersicht.angebote(Angebotsauswahl.alle()))
        .thenReturn(
            List.of(
                zeile(12L, Angebotsstatus.BESTELLT), zeile(11L, Angebotsstatus.ANGELEGT, null)));

    // When / Then
    mockMvc
        .perform(get("/api/angebote"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.angebote.length()").value(2))
        .andExpect(jsonPath("$.angebote[0].id").value(12))
        .andExpect(jsonPath("$.angebote[0].firmaId").value(3))
        .andExpect(jsonPath("$.angebote[0].firmaName").value("Adler AG"))
        .andExpect(jsonPath("$.angebote[0].titel").value("Website-Relaunch"))
        .andExpect(jsonPath("$.angebote[0].status").value("BESTELLT"))
        .andExpect(jsonPath("$.angebote[0].intern").value(false))
        .andExpect(jsonPath("$.angebote[0].summe").value(2500.03))
        .andExpect(jsonPath("$.angebote[0].storniert").value(false))
        .andExpect(jsonPath("$.angebote[1].id").value(11))
        .andExpect(jsonPath("$.angebote[1].status").value("ANGELEGT"))
        // Issue #325: das Angebot von vor #314 traegt keinen Titel, und das steht als null da.
        .andExpect(jsonPath("$.angebote[1].titel").value(nullValue()));
  }

  @Test
  void angebote_givenInternalWork_thenTheRowCarriesTheFlag() throws Exception {
    // Given — Issue #226, Kriterium 2: die Uebersicht nennt die Art jeder Zeile.
    when(uebersicht.angebote(Angebotsauswahl.alle()))
        .thenReturn(List.of(zeile(12L, Angebotsstatus.LAEUFT)));

    // When / Then
    mockMvc
        .perform(get("/api/angebote"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.angebote[0].intern").value(true))
        .andExpect(jsonPath("$.angebote[0].status").value("LAEUFT"));
  }

  @Test
  void angebote_withAStatus_thenPassesItOn() throws Exception {
    // Given
    when(uebersicht.angebote(Angebotsauswahl.imStatus(Angebotsstatus.BESTELLT)))
        .thenReturn(List.of(zeile(12L, Angebotsstatus.BESTELLT)));

    // When / Then
    mockMvc
        .perform(get("/api/angebote").param("status", "BESTELLT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.angebote.length()").value(1));
    verify(uebersicht).angebote(Angebotsauswahl.imStatus(Angebotsstatus.BESTELLT));
  }

  @Test
  void angebote_withoutAFilter_thenAsksForAll() throws Exception {
    // Given
    when(uebersicht.angebote(Angebotsauswahl.alle())).thenReturn(List.of());

    // When / Then
    mockMvc.perform(get("/api/angebote")).andExpect(status().isOk());
    verify(uebersicht).angebote(Angebotsauswahl.alle());
  }

  @Test
  void angebote_withStorniertTrue_thenAsksForTheStornierteAndTheRowCarriesTheFlag()
      throws Exception {
    // Given
    when(uebersicht.angebote(Angebotsauswahl.stornierte()))
        .thenReturn(List.of(storniert(12L, Angebotsstatus.ABGEGEBEN)));

    // When / Then
    mockMvc
        .perform(get("/api/angebote").param("storniert", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.angebote.length()").value(1))
        .andExpect(jsonPath("$.angebote[0].status").value("ABGEGEBEN"))
        .andExpect(jsonPath("$.angebote[0].storniert").value(true));
    verify(uebersicht).angebote(Angebotsauswahl.stornierte());
  }

  @Test
  void angebote_withStorniertFalseAlone_thenAsksForAll() throws Exception {
    // Given — ohne Status heisst „nicht storniert" allein keine eigene Auswahl (E6).
    when(uebersicht.angebote(Angebotsauswahl.alle())).thenReturn(List.of());

    // When / Then
    mockMvc.perform(get("/api/angebote").param("storniert", "false")).andExpect(status().isOk());
    verify(uebersicht).angebote(Angebotsauswahl.alle());
  }

  @Test
  void angebote_withAStatusAndStorniert_thenTheStatusDecides() throws Exception {
    // Given — imStatus legt das Storno selbst auf false fest (E7).
    when(uebersicht.angebote(Angebotsauswahl.imStatus(Angebotsstatus.ABGEGEBEN)))
        .thenReturn(List.of());

    // When / Then
    mockMvc
        .perform(get("/api/angebote").param("status", "ABGEGEBEN").param("storniert", "true"))
        .andExpect(status().isOk());
    verify(uebersicht).angebote(Angebotsauswahl.imStatus(Angebotsstatus.ABGEGEBEN));
  }

  @Test
  void angebote_withAnUnknownStatus_thenAnswers400() throws Exception {
    // When / Then
    mockMvc
        .perform(get("/api/angebote").param("status", "VERHANDELT"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(uebersicht);
  }

  @Test
  void angebote_whenThereAreNone_thenAnswersAnEmptyList() throws Exception {
    // Given
    when(uebersicht.angebote(Angebotsauswahl.alle())).thenReturn(List.of());

    // When / Then
    mockMvc
        .perform(get("/api/angebote"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.angebote.length()").value(0));
  }
}
