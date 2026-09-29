package org.mwolff.fbcrm.angebot.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotAnlegenUseCase;
import org.mwolff.fbcrm.angebot.application.AngebotAnsicht;
import org.mwolff.fbcrm.angebot.application.AngeboteDerFirmaUseCase;
import org.mwolff.fbcrm.angebot.application.AnsprechpartnerNichtWaehlbar;
import org.mwolff.fbcrm.angebot.application.FirmaStillgelegt;
import org.mwolff.fbcrm.angebot.application.Kundenangaben;
import org.mwolff.fbcrm.angebot.application.KundenangabenUseCase;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotsstand;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen Anwendungsfall und HTTP fuer die Wege an der Firma (Issue #126).
 *
 * <p>Zwei Wege unter dem Pfad der Firma: die Liste ihrer Angebote (Kriterium 20) und das Anlegen
 * eines Entwurfs (Kriterium 2). Geprueft wird die Form der Antwort und die Abbildung der drei
 * Ausnahmen — 404 fuer eine unbekannte Firma, 409 fuer eine stillgelegte, 422 fuer einen
 * Ansprechpartner, der nicht zur Wahl steht.
 *
 * <p>Der Rumpf des {@code POST} darf fehlen: Ein Angebot ohne Ansprechpartner ist erlaubt, und ein
 * leerer Pflichtrumpf waere eine Formalie ohne Aussage.
 */
@ExtendWith(MockitoExtension.class)
class FirmaAngeboteControllerTest {

  private static final long FIRMA = 3L;
  private static final long ANGEBOT = 11L;
  private static final Long PERSON = Long.valueOf(8L);
  private static final LocalDate ANGEBOTSDATUM = LocalDate.of(2026, 9, 20);
  private static final LocalDate GUELTIG_BIS = LocalDate.of(2026, 10, 20);
  private static final Instant ANGELEGT = Instant.parse("2026-09-20T08:00:00Z");
  private static final String MIT_PERSON = "{\"ansprechpartnerId\":8}";

  @Mock private AngeboteDerFirmaUseCase liste;
  @Mock private AngebotAnlegenUseCase anlegen;
  @Mock private KundenangabenUseCase kunden;

  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new FirmaAngeboteController(liste, anlegen, kunden))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private static AngebotAnsicht ansicht(
      final long id,
      final @Nullable Long ansprechpartnerId,
      final Angebotszustand zustand,
      final Angebotsstand stand) {
    final boolean entwurf = zustand == Angebotszustand.ENTWURF;
    return new AngebotAnsicht(
        new Angebot(
            Long.valueOf(id),
            FIRMA,
            ansprechpartnerId,
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

  private static AngebotAnsicht entwurf(final @Nullable Long ansprechpartnerId) {
    return ansicht(ANGEBOT, ansprechpartnerId, Angebotszustand.ENTWURF, Angebotsstand.ENTWURF);
  }

  @Test
  void angebote_thenAnswersWithOneRowPerAngebot() throws Exception {
    // Given — Kriterium 20: die Liste in der Reihenfolge des Anwendungsfalls.
    when(liste.angebote(FIRMA))
        .thenReturn(
            List.of(
                entwurf(null),
                ansicht(9L, null, Angebotszustand.VERSENDET, Angebotsstand.ABGELAUFEN)));

    // When / Then
    mockMvc
        .perform(get("/api/firmen/{firmaId}/angebote", Long.valueOf(FIRMA)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.angebote.length()").value(2))
        .andExpect(jsonPath("$.angebote[0].id").value(Long.valueOf(ANGEBOT)))
        .andExpect(jsonPath("$.angebote[0].stand").value("ENTWURF"))
        .andExpect(jsonPath("$.angebote[0].nummer").doesNotExist())
        .andExpect(jsonPath("$.angebote[0].summe").value(2500.03))
        .andExpect(jsonPath("$.angebote[1].stand").value("ABGELAUFEN"))
        .andExpect(jsonPath("$.angebote[1].nummer").value("A-2026-001"));
    verifyNoInteractions(kunden);
  }

  @Test
  void anlegen_withoutABody_thenCreatesADraftWithoutAContact() throws Exception {
    // Given
    when(anlegen.anlegen(FIRMA, null)).thenReturn(entwurf(null));
    when(kunden.zu(any())).thenReturn(new Kundenangaben("Adler AG", null));

    // When / Then
    mockMvc
        .perform(post("/api/firmen/{firmaId}/angebote", Long.valueOf(FIRMA)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(Long.valueOf(ANGEBOT)))
        .andExpect(jsonPath("$.stand").value("ENTWURF"))
        .andExpect(jsonPath("$.firmaId").value(Long.valueOf(FIRMA)))
        .andExpect(jsonPath("$.firmaName").value("Adler AG"))
        .andExpect(jsonPath("$.ansprechpartnerId").doesNotExist())
        .andExpect(jsonPath("$.ansprechpartnerName").doesNotExist());
    verify(anlegen).anlegen(FIRMA, null);
  }

  @Test
  void anlegen_withAContact_thenPassesItOnAndNamesIt() throws Exception {
    // Given
    when(anlegen.anlegen(FIRMA, PERSON)).thenReturn(entwurf(PERSON));
    when(kunden.zu(any())).thenReturn(new Kundenangaben("Adler AG", "Eva Adler"));

    // When / Then
    mockMvc
        .perform(
            post("/api/firmen/{firmaId}/angebote", Long.valueOf(FIRMA))
                .contentType(MediaType.APPLICATION_JSON)
                .content(MIT_PERSON))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.ansprechpartnerId").value(PERSON))
        .andExpect(jsonPath("$.ansprechpartnerName").value("Eva Adler"));
    verify(anlegen).anlegen(FIRMA, PERSON);
  }

  @Test
  void anlegen_atAnUnknownFirma_thenAnswers404() throws Exception {
    // Given
    when(anlegen.anlegen(eq(FIRMA), isNull())).thenThrow(new FirmaNichtGefunden());

    // When / Then
    mockMvc
        .perform(post("/api/firmen/{firmaId}/angebote", Long.valueOf(FIRMA)))
        .andExpect(status().isNotFound());
  }

  @Test
  void anlegen_atAStillgelegteFirma_thenAnswers409() throws Exception {
    // Given
    when(anlegen.anlegen(eq(FIRMA), isNull())).thenThrow(new FirmaStillgelegt());

    // When / Then
    mockMvc
        .perform(post("/api/firmen/{firmaId}/angebote", Long.valueOf(FIRMA)))
        .andExpect(status().isConflict());
  }

  @Test
  void anlegen_withAContactThatIsNotAvailable_thenAnswers422() throws Exception {
    // Given
    when(anlegen.anlegen(FIRMA, PERSON)).thenThrow(new AnsprechpartnerNichtWaehlbar());

    // When / Then
    mockMvc
        .perform(
            post("/api/firmen/{firmaId}/angebote", Long.valueOf(FIRMA))
                .contentType(MediaType.APPLICATION_JSON)
                .content(MIT_PERSON))
        .andExpect(status().isUnprocessableEntity());
  }
}
