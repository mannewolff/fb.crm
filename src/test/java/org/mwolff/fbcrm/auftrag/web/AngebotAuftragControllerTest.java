package org.mwolff.fbcrm.auftrag.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.auftrag.application.AngebotNichtWaehlbar;
import org.mwolff.fbcrm.auftrag.application.AuftragAmAngebot;
import org.mwolff.fbcrm.auftrag.application.AuftragAnlegenUseCase;
import org.mwolff.fbcrm.auftrag.application.AuftragAnsicht;
import org.mwolff.fbcrm.auftrag.application.AuftragBereitsVorhanden;
import org.mwolff.fbcrm.auftrag.application.AuftragLesenUseCase;
import org.mwolff.fbcrm.auftrag.application.AuftragsuebernahmeUngueltig;
import org.mwolff.fbcrm.auftrag.application.VorgangAbgeschlossen;
import org.mwolff.fbcrm.auftrag.domain.Auftrag;
import org.mwolff.fbcrm.auftrag.domain.Auftragsposition;
import org.mwolff.fbcrm.auftrag.domain.Auftragsstatus;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen Anwendungsfall und HTTP fuer die beiden Wege am Angebot.
 *
 * <p>Der Leseweg traegt zwei Angaben, weil die Angebotsansicht beide braucht, ohne dass {@code
 * angebot} das Modul {@code auftrag} kennt (Plan E3): den Auftrag — oder {@code null} — und die
 * Auskunft {@code anlegbar}. Ein Rueckverweis in {@code AngebotResponse} waere die Rueckkante, die
 * {@code ArchitectureTest.modules_thenFreeOfCycles} abweist.
 *
 * <p>Vier Lagen enden in 409, und nur eine davon nennt Felder: Die ungueltige Uebernahme sagt,
 * <b>welche</b> Position nicht passt, die drei Zustandslagen haben kein Feld, an das eine Meldung
 * gehoerte.
 */
@ExtendWith(MockitoExtension.class)
class AngebotAuftragControllerTest {

  private static final long ANGEBOT = 11L;
  private static final long AUFTRAG = 7L;
  private static final long VORGANG = 3L;
  private static final String ANGEBOTSNUMMER = "A-2026-011";
  private static final Instant ANGELEGT = Instant.parse("2026-09-28T08:00:00Z");
  private static final String RUMPF =
      "{\"positionen\":[{\"platz\":1,\"menge\":\"2.50\",\"stundenJePersonentag\":\"7.50\"}]}";

  @Mock private AuftragLesenUseCase lesen;
  @Mock private AuftragAnlegenUseCase anlegen;

  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new AngebotAuftragController(lesen, anlegen))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private static AuftragAnsicht ansicht() {
    return new AuftragAnsicht(
        new Auftrag(
            Long.valueOf(AUFTRAG),
            VORGANG,
            ANGEBOT,
            "AU-2026-001",
            Auftragsstatus.OFFEN,
            LocalDate.of(2026, 9, 28),
            "BST-4711",
            null,
            null,
            List.of(
                new Auftragsposition(
                    "Konzeption",
                    Abrechnungsmodus.AUFWAND,
                    new BigDecimal("2.50"),
                    Einheit.PERSONENTAG,
                    new BigDecimal("1000.01"),
                    new BigDecimal("7.50"))),
            ANGELEGT,
            ANGELEGT),
        ANGEBOTSNUMMER);
  }

  @Test
  void auftrag_givenAnAcceptedAngebotWithoutAnAuftrag_thenEmptyAndAnlegbar() throws Exception {
    // Given — Plan E3: daran haengt die Taste der Angebotsansicht.
    when(lesen.zuAngebot(ANGEBOT)).thenReturn(new AuftragAmAngebot(null, true));

    // When / Then
    mockMvc
        .perform(get("/api/angebote/{angebotId}/auftrag", Long.valueOf(ANGEBOT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.auftrag").doesNotExist())
        .andExpect(jsonPath("$.anlegbar").value(true));
  }

  @Test
  void auftrag_givenAnAngebotThatIsNotAccepted_thenNotAnlegbar() throws Exception {
    // Given — Kriterium 1.
    when(lesen.zuAngebot(ANGEBOT)).thenReturn(new AuftragAmAngebot(null, false));

    // When / Then
    mockMvc
        .perform(get("/api/angebote/{angebotId}/auftrag", Long.valueOf(ANGEBOT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.anlegbar").value(false));
  }

  @Test
  void auftrag_givenAnAuftrag_thenAnswersWithItsRowAndTheAngebotNummer() throws Exception {
    // Given — Kriterium 3: am Auftrag steht, aus welchem Angebot er entstand.
    when(lesen.zuAngebot(ANGEBOT)).thenReturn(new AuftragAmAngebot(ansicht(), true));

    // When / Then
    mockMvc
        .perform(get("/api/angebote/{angebotId}/auftrag", Long.valueOf(ANGEBOT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.auftrag.id").value(Long.valueOf(AUFTRAG)))
        .andExpect(jsonPath("$.auftrag.nummer").value("AU-2026-001"))
        .andExpect(jsonPath("$.auftrag.angebotNummer").value(ANGEBOTSNUMMER))
        .andExpect(jsonPath("$.auftrag.summe").value(2500.03));
  }

  @Test
  void auftrag_givenAnUnknownAngebot_thenAnswers404() throws Exception {
    // Given
    when(lesen.zuAngebot(ANGEBOT)).thenThrow(new AngebotNichtGefunden());

    // When / Then
    mockMvc
        .perform(get("/api/angebote/{angebotId}/auftrag", Long.valueOf(ANGEBOT)))
        .andExpect(status().isNotFound());
  }

  @Test
  void anlegen_thenAnswers201WithTheNewAuftrag() throws Exception {
    // Given — Kriterium 6.
    when(anlegen.anlegen(eq(ANGEBOT), any())).thenReturn(ansicht());

    // When / Then
    mockMvc
        .perform(
            post("/api/angebote/{angebotId}/auftrag", Long.valueOf(ANGEBOT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.nummer").value("AU-2026-001"))
        .andExpect(jsonPath("$.status").value("OFFEN"))
        .andExpect(jsonPath("$.positionen[0].einzelpreis").value(1000.01))
        .andExpect(jsonPath("$.positionen[0].betrag").value(2500.03));
  }

  @Test
  void anlegen_thenPassesTheChosenPositionenOn() throws Exception {
    // Given — Plan E7: Platz, Menge und Stunden je Personentag, nichts weiter.
    when(anlegen.anlegen(eq(ANGEBOT), any())).thenReturn(ansicht());

    // When
    mockMvc
        .perform(
            post("/api/angebote/{angebotId}/auftrag", Long.valueOf(ANGEBOT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isCreated());

    // Then
    verify(anlegen)
        .anlegen(
            eq(ANGEBOT),
            org.mockito.ArgumentMatchers.argThat(
                daten -> daten.positionen().size() == 1 && daten.positionen().get(0).platz() == 1));
  }

  @Test
  void anlegen_withAnInvalidUebernahme_thenAnswers409WithTheFields() throws Exception {
    // Given — Plan E20: die Meldung nennt die Position, die nicht passt.
    when(anlegen.anlegen(eq(ANGEBOT), any()))
        .thenThrow(
            new AuftragsuebernahmeUngueltig(
                Map.of("positionen[0].menge", List.of("Mehr als vereinbart."))));

    // When / Then
    mockMvc
        .perform(
            post("/api/angebote/{angebotId}/auftrag", Long.valueOf(ANGEBOT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.fieldErrors['positionen[0].menge'][0]").value("Mehr als vereinbart."));
  }

  @Test
  void anlegen_fromAnAngebotThatIsNotAccepted_thenAnswers409WithoutFields() throws Exception {
    // Given — Kriterium 1.
    when(anlegen.anlegen(eq(ANGEBOT), any())).thenThrow(new AngebotNichtWaehlbar());

    // When / Then
    mockMvc
        .perform(
            post("/api/angebote/{angebotId}/auftrag", Long.valueOf(ANGEBOT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.fieldErrors").doesNotExist());
  }

  @Test
  void anlegen_atAClosedVorgang_thenAnswers409WithoutFields() throws Exception {
    // Given — Kriterium 11.
    when(anlegen.anlegen(eq(ANGEBOT), any())).thenThrow(new VorgangAbgeschlossen());

    // When / Then
    mockMvc
        .perform(
            post("/api/angebote/{angebotId}/auftrag", Long.valueOf(ANGEBOT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.fieldErrors").doesNotExist());
  }

  @Test
  void anlegen_whenAnAuftragAlreadyExists_thenAnswers409WithoutFields() throws Exception {
    // Given — F9.
    when(anlegen.anlegen(eq(ANGEBOT), any())).thenThrow(new AuftragBereitsVorhanden());

    // When / Then
    mockMvc
        .perform(
            post("/api/angebote/{angebotId}/auftrag", Long.valueOf(ANGEBOT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.fieldErrors").doesNotExist());
  }

  @Test
  void anlegen_fromAnUnknownAngebot_thenAnswers404() throws Exception {
    // Given
    when(anlegen.anlegen(eq(ANGEBOT), any())).thenThrow(new AngebotNichtGefunden());

    // When / Then
    mockMvc
        .perform(
            post("/api/angebote/{angebotId}/auftrag", Long.valueOf(ANGEBOT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isNotFound());
  }

  @Test
  void anlegen_withoutAnyPosition_thenAnswers400AndCallsNothing() throws Exception {
    // Given — die Form prueft die Schnittstelle, bevor der Anwendungsfall laeuft.
    // When / Then
    mockMvc
        .perform(
            post("/api/angebote/{angebotId}/auftrag", Long.valueOf(ANGEBOT))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"positionen\":[]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.positionen").exists());
    verify(anlegen, never()).anlegen(anyLong(), any());
  }
}
