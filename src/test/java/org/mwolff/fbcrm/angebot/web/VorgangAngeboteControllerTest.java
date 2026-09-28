package org.mwolff.fbcrm.angebot.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotAnlegenUseCase;
import org.mwolff.fbcrm.angebot.application.AngebotAnsicht;
import org.mwolff.fbcrm.angebot.application.AngeboteDesVorgangsUseCase;
import org.mwolff.fbcrm.angebot.application.VorgangAbgeschlossen;
import org.mwolff.fbcrm.angebot.application.VorlageNichtWaehlbar;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotsstand;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.mwolff.fbcrm.vorgang.application.VorgangNichtGefunden;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen Anwendungsfall und HTTP fuer die Wege am Vorgang.
 *
 * <p>Zwei Wege unter dem Pfad des Vorgangs: die Liste seiner Angebote (Kriterium 20) und das
 * Anlegen eines Entwurfs (Kriterien 2, 8). Geprueft wird die Form der Antwort und die Abbildung der
 * drei Ausnahmen — 409 am abgeschlossenen Vorgang (E13), 422 fuer eine Vorlage, die nicht zur Wahl
 * steht (E23), 404 fuer einen unbekannten Vorgang.
 *
 * <p>Der Rumpf des {@code POST} darf fehlen: „Angebot anlegen" ohne Vorlage ist der Normalfall, und
 * ein leerer Pflichtrumpf waere eine Formalie ohne Aussage.
 */
@ExtendWith(MockitoExtension.class)
class VorgangAngeboteControllerTest {

  private static final long VORGANG = 3L;
  private static final long ANGEBOT = 11L;
  private static final long QUELLE = 9L;
  private static final LocalDate ANGEBOTSDATUM = LocalDate.of(2026, 9, 20);
  private static final LocalDate GUELTIG_BIS = LocalDate.of(2026, 10, 20);
  private static final Instant ANGELEGT = Instant.parse("2026-09-20T08:00:00Z");

  @Mock private AngeboteDesVorgangsUseCase liste;
  @Mock private AngebotAnlegenUseCase anlegen;

  private VorgangAngeboteController controller;
  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    controller = new VorgangAngeboteController(liste, anlegen);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private static AngebotAnsicht ansicht(
      final long id, final Angebotszustand zustand, final Angebotsstand stand) {
    final boolean entwurf = zustand == Angebotszustand.ENTWURF;
    return new AngebotAnsicht(
        new Angebot(
            Long.valueOf(id),
            VORGANG,
            entwurf ? null : "A-2026-001",
            zustand,
            ANGEBOTSDATUM,
            GUELTIG_BIS,
            null,
            null,
            entwurf ? null : ANGELEGT,
            null,
            entwurf ? null : "angebot/1/beleg.pdf",
            null,
            null,
            List.of(
                new Angebotsposition(
                    "Konzeption",
                    Abrechnungsmodus.AUFWAND,
                    new BigDecimal("2.50"),
                    Einheit.PERSONENTAG,
                    new BigDecimal("1000.01"))),
            ANGELEGT,
            ANGELEGT),
        stand);
  }

  @Test
  void angebote_thenAnswersWithOneRowPerAngebot() throws Exception {
    // Given — Kriterium 20: die Liste in der Reihenfolge des Anwendungsfalls.
    when(liste.angebote(VORGANG))
        .thenReturn(
            List.of(
                ansicht(ANGEBOT, Angebotszustand.ENTWURF, Angebotsstand.ENTWURF),
                ansicht(9L, Angebotszustand.VERSENDET, Angebotsstand.ABGELAUFEN)));

    // When / Then
    mockMvc
        .perform(get("/api/vorgaenge/{vorgangId}/angebote", Long.valueOf(VORGANG)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.angebote.length()").value(2))
        .andExpect(jsonPath("$.angebote[0].id").value(Long.valueOf(ANGEBOT)))
        .andExpect(jsonPath("$.angebote[0].stand").value("ENTWURF"))
        .andExpect(jsonPath("$.angebote[0].nummer").doesNotExist())
        .andExpect(jsonPath("$.angebote[0].summe").value(2500.03))
        .andExpect(jsonPath("$.angebote[1].stand").value("ABGELAUFEN"))
        .andExpect(jsonPath("$.angebote[1].nummer").value("A-2026-001"));
  }

  @Test
  void anlegen_withoutABody_thenCreatesADraftWithoutAVorlage() throws Exception {
    // Given
    when(anlegen.anlegen(VORGANG, null))
        .thenReturn(ansicht(ANGEBOT, Angebotszustand.ENTWURF, Angebotsstand.ENTWURF));

    // When / Then
    mockMvc
        .perform(post("/api/vorgaenge/{vorgangId}/angebote", Long.valueOf(VORGANG)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(Long.valueOf(ANGEBOT)))
        .andExpect(jsonPath("$.stand").value("ENTWURF"));
    verify(anlegen).anlegen(VORGANG, null);
  }

  @Test
  void anlegen_withAVorlage_thenPassesItOn() throws Exception {
    // Given — E23.
    when(anlegen.anlegen(VORGANG, Long.valueOf(QUELLE)))
        .thenReturn(ansicht(ANGEBOT, Angebotszustand.ENTWURF, Angebotsstand.ENTWURF));

    // When / Then
    mockMvc
        .perform(
            post("/api/vorgaenge/{vorgangId}/angebote", Long.valueOf(VORGANG))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"vorlageAngebotId\":9}"))
        .andExpect(status().isCreated());
    verify(anlegen).anlegen(VORGANG, Long.valueOf(QUELLE));
  }

  @Test
  void anlegen_atAClosedVorgang_thenAnswers409() throws Exception {
    // Given — Kriterium 9, E13: am abgeschlossenen Vorgang entsteht kein Angebot.
    when(anlegen.anlegen(eq(VORGANG), isNull())).thenThrow(new VorgangAbgeschlossen());

    // When / Then
    mockMvc
        .perform(post("/api/vorgaenge/{vorgangId}/angebote", Long.valueOf(VORGANG)))
        .andExpect(status().isConflict());
  }

  @Test
  void anlegen_withAVorlageFromAnotherVorgang_thenAnswers422() throws Exception {
    // Given — E23: die Quelle steht nicht zur Wahl.
    when(anlegen.anlegen(VORGANG, Long.valueOf(QUELLE))).thenThrow(new VorlageNichtWaehlbar());

    // When / Then
    mockMvc
        .perform(
            post("/api/vorgaenge/{vorgangId}/angebote", Long.valueOf(VORGANG))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"vorlageAngebotId\":9}"))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void anlegen_atAnUnknownVorgang_thenAnswers404() throws Exception {
    // Given
    when(anlegen.anlegen(eq(VORGANG), isNull())).thenThrow(new VorgangNichtGefunden());

    // When / Then
    mockMvc
        .perform(post("/api/vorgaenge/{vorgangId}/angebote", Long.valueOf(VORGANG)))
        .andExpect(status().isNotFound());
  }
}
