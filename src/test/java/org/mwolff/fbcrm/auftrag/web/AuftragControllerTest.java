package org.mwolff.fbcrm.auftrag.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.auftrag.application.AuftragAnsicht;
import org.mwolff.fbcrm.auftrag.application.AuftragLesenUseCase;
import org.mwolff.fbcrm.auftrag.application.AuftragLoeschenUseCase;
import org.mwolff.fbcrm.auftrag.application.AuftragNichtGefunden;
import org.mwolff.fbcrm.auftrag.application.AuftragPflegedaten;
import org.mwolff.fbcrm.auftrag.application.AuftragPflegenUseCase;
import org.mwolff.fbcrm.auftrag.domain.Auftrag;
import org.mwolff.fbcrm.auftrag.domain.Auftragsposition;
import org.mwolff.fbcrm.auftrag.domain.Auftragsstatus;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Ansicht eines einzelnen Auftrags ueber HTTP (Kriterien 3, 4, 5).
 *
 * <p>Zwei Werte kommen gerechnet und stehen in keiner Spalte (E11): die Summe und je Position ihr
 * Betrag. Die Nummer des Quell-Angebots steht <b>immer</b> in der Antwort, auch wenn eine Ansicht
 * sie gerade nicht zeigt — ein Feld, das je Weg mal da und mal nicht da ist, zwingt die Oberflaeche
 * zu zwei Formen fuer dieselbe Antwort.
 */
@ExtendWith(MockitoExtension.class)
class AuftragControllerTest {

  private static final long AUFTRAG = 7L;
  private static final long VORGANG = 3L;
  private static final long ANGEBOT = 11L;
  private static final Instant ANGELEGT = Instant.parse("2026-09-28T08:00:00Z");

  @Mock private AuftragLesenUseCase lesen;
  @Mock private AuftragPflegenUseCase pflegen;
  @Mock private AuftragLoeschenUseCase loeschen;

  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new AuftragController(lesen, pflegen, loeschen))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private static AuftragAnsicht ansicht(final Auftragsstatus status) {
    return new AuftragAnsicht(
        new Auftrag(
            Long.valueOf(AUFTRAG),
            VORGANG,
            ANGEBOT,
            "AU-2026-001",
            status,
            LocalDate.of(2026, 9, 28),
            "BST-4711",
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 12, 31),
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
        "A-2026-011");
  }

  @Test
  void lesen_thenAnswersWithTheAuftragAndItsCalculatedValues() throws Exception {
    // Given — Kriterien 3, 4, 5.
    when(lesen.lese(AUFTRAG))
        .thenReturn(
            new AuftragAnsicht(
                new Auftrag(
                    Long.valueOf(AUFTRAG),
                    VORGANG,
                    ANGEBOT,
                    "AU-2026-001",
                    Auftragsstatus.IN_ARBEIT,
                    LocalDate.of(2026, 9, 28),
                    "BST-4711",
                    LocalDate.of(2026, 10, 1),
                    LocalDate.of(2026, 12, 31),
                    List.of(
                        new Auftragsposition(
                            "Konzeption",
                            Abrechnungsmodus.AUFWAND,
                            new BigDecimal("2.50"),
                            Einheit.PERSONENTAG,
                            new BigDecimal("1000.01"),
                            new BigDecimal("7.50")),
                        new Auftragsposition(
                            "Schulungstag",
                            Abrechnungsmodus.FESTPREIS,
                            new BigDecimal("1.00"),
                            Einheit.PAUSCHAL,
                            new BigDecimal("1200.00"),
                            null)),
                    ANGELEGT,
                    ANGELEGT),
                "A-2026-011"));

    // When / Then
    mockMvc
        .perform(get("/api/auftraege/{id}", Long.valueOf(AUFTRAG)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(Long.valueOf(AUFTRAG)))
        .andExpect(jsonPath("$.vorgangId").value(Long.valueOf(VORGANG)))
        .andExpect(jsonPath("$.angebotId").value(Long.valueOf(ANGEBOT)))
        .andExpect(jsonPath("$.angebotNummer").value("A-2026-011"))
        .andExpect(jsonPath("$.status").value("IN_ARBEIT"))
        .andExpect(jsonPath("$.kundenbestellnummer").value("BST-4711"))
        .andExpect(jsonPath("$.auftragDatum").exists())
        .andExpect(jsonPath("$.leistungAb").exists())
        .andExpect(jsonPath("$.leistungBis").exists())
        .andExpect(jsonPath("$.positionen.length()").value(2))
        .andExpect(jsonPath("$.positionen[0].stundenJePersonentag").value(7.50))
        .andExpect(jsonPath("$.positionen[0].betrag").value(2500.03))
        .andExpect(jsonPath("$.positionen[1].stundenJePersonentag").doesNotExist())
        .andExpect(jsonPath("$.summe").value(3700.03));
  }

  @Test
  void lesen_givenAnUnknownAuftrag_thenAnswers404() throws Exception {
    // Given
    when(lesen.lese(AUFTRAG)).thenThrow(new AuftragNichtGefunden());

    // When / Then
    mockMvc
        .perform(get("/api/auftraege/{id}", Long.valueOf(AUFTRAG)))
        .andExpect(status().isNotFound());
  }

  @Test
  void pflegen_thenAnswersWithTheUpdatedAuftrag() throws Exception {
    // Given — Kriterium 7: ein Schreibweg fuer alle vier aenderbaren Angaben (Plan E8).
    when(pflegen.pflege(eq(AUFTRAG), any(AuftragPflegedaten.class)))
        .thenReturn(ansicht(Auftragsstatus.ABGESCHLOSSEN));

    // When / Then
    mockMvc
        .perform(
            put("/api/auftraege/{id}", Long.valueOf(AUFTRAG))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"auftragDatum":"2026-09-28","kundenbestellnummer":"BST-4711",\
                    "leistungAb":"2026-10-01","leistungBis":"2026-12-31",\
                    "status":"ABGESCHLOSSEN"}"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ABGESCHLOSSEN"))
        .andExpect(jsonPath("$.summe").value(2500.03));
  }

  @Test
  void pflegen_givenAHalfLeistungszeitraum_thenAnswers400NamingTheField() throws Exception {
    // When / Then — E21: dieselbe Regel wie beim Anlegen, gemeldet am fehlenden Tag.
    mockMvc
        .perform(
            put("/api/auftraege/{id}", Long.valueOf(AUFTRAG))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"auftragDatum":"2026-09-28","leistungAb":"2026-10-01",\
                    "status":"OFFEN"}"""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.leistungBis").exists());
  }

  @Test
  void pflegen_givenAnUnknownStatus_thenRejectsTheBodyAndNeverReachesTheUseCase() throws Exception {
    // When — ein Fremdwert ist kein Status: Die Umwandlung bricht ab, und der Anwendungsfall
    // sieht die Anfrage nie.
    final int status =
        mockMvc
            .perform(
                put("/api/auftraege/{id}", Long.valueOf(AUFTRAG))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"auftragDatum":"2026-09-28","status":"SCHWEBEND"}"""))
            .andReturn()
            .getResponse()
            .getStatus();

    // Then — geprueft wird die Abweisung, nicht ihr Statuscode: Ein unlesbarer Rumpf laeuft heute
    // in den Sammelzweig des GlobalExceptionHandler und damit in 500. Das ist eine bestehende,
    // wegunabhaengige Luecke und kein Zug dieses Pakets; sie hier auf 400 festzuschreiben hiesse,
    // sie zu zementieren.
    assertThat(status).isNotEqualTo(HttpStatus.OK.value());
    verifyNoInteractions(pflegen);
  }

  @Test
  void pflegen_givenAnUnknownAuftrag_thenAnswers404() throws Exception {
    // Given
    when(pflegen.pflege(eq(AUFTRAG), any(AuftragPflegedaten.class)))
        .thenThrow(new AuftragNichtGefunden());

    // When / Then
    mockMvc
        .perform(
            put("/api/auftraege/{id}", Long.valueOf(AUFTRAG))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"auftragDatum":"2026-09-28","status":"OFFEN"}"""))
        .andExpect(status().isNotFound());
  }

  @Test
  void loeschen_thenAnswers204AndPassesTheId() throws Exception {
    // When / Then — Kriterium 15, E19: nach dem Loeschen gibt es nichts zurueckzugeben.
    mockMvc
        .perform(delete("/api/auftraege/{id}", Long.valueOf(AUFTRAG)))
        .andExpect(status().isNoContent());
    verify(loeschen).loesche(AUFTRAG);
  }

  @Test
  void loeschen_givenAnUnknownAuftrag_thenAnswers404() throws Exception {
    // Given
    doThrow(new AuftragNichtGefunden()).when(loeschen).loesche(AUFTRAG);

    // When / Then
    mockMvc
        .perform(delete("/api/auftraege/{id}", Long.valueOf(AUFTRAG)))
        .andExpect(status().isNotFound());
  }
}
