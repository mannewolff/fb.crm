package org.mwolff.fbcrm.angebot.web;

import static org.hamcrest.Matchers.nullValue;
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
import java.time.Year;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotAnlegenUseCase;
import org.mwolff.fbcrm.angebot.application.AngeboteDerFirmaUseCase;
import org.mwolff.fbcrm.angebot.application.AnsprechpartnerNichtWaehlbar;
import org.mwolff.fbcrm.angebot.application.FirmaStillgelegt;
import org.mwolff.fbcrm.angebot.application.Kundenangaben;
import org.mwolff.fbcrm.angebot.application.KundenangabenUseCase;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen Anwendungsfall und HTTP fuer die Wege an der Firma (Issue #126).
 *
 * <p>Zwei Wege unter dem Pfad der Firma: die Liste ihrer Angebote (Kriterium 7) und das Anlegen
 * eines Angebots (Kriterium 2). Geprueft wird die Form der Antwort und die Abbildung der drei
 * Ausnahmen — 404 fuer eine unbekannte Firma, 409 fuer eine stillgelegte, 422 fuer einen
 * Ansprechpartner, der nicht zur Wahl steht.
 *
 * <p><b>Der Rumpf des {@code POST} ist Pflicht</b> (Issue #317, E4): Er traegt den Titel, und der
 * fehlt nicht, ist nicht nur Leerraum und hat hoechstens 200 Zeichen — je mit 400 und {@code
 * fieldErrors.titel}. Ein {@code POST} ganz ohne Rumpf ist ein unlesbarer Rumpf, ohne {@code
 * fieldErrors}: Er nennt kein Feld.
 *
 * <p><b>Dazu das Kennzeichen {@code intern}</b> (Issue #226): Steht es im Rumpf, entsteht die
 * interne Arbeit; fehlt es oder steht es auf {@code null}, entsteht das Angebot an einen Kunden.
 */
@ExtendWith(MockitoExtension.class)
class FirmaAngeboteControllerTest {

  private static final long FIRMA = 3L;
  private static final long ANGEBOT = 11L;
  private static final Long PERSON = Long.valueOf(8L);
  private static final LocalDate ANGEBOTSDATUM = LocalDate.of(2026, 9, 20);
  private static final Instant ANGELEGT = Instant.parse("2026-09-20T08:00:00Z");
  private static final String TITEL = "Website-Relaunch";
  private static final String NUR_TITEL = "{\"titel\":\"Website-Relaunch\"}";
  private static final String MIT_PERSON =
      "{\"titel\":\"Website-Relaunch\",\"ansprechpartnerId\":8}";
  private static final String INTERN = "{\"titel\":\"Website-Relaunch\",\"intern\":true}";
  private static final String INTERN_NULL = "{\"titel\":\"Website-Relaunch\",\"intern\":null}";

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

  private static Angebot angebot(
      final long id, final @Nullable Long ansprechpartnerId, final Angebotsstatus status) {
    return angebot(id, ansprechpartnerId, status, "Website-Relaunch");
  }

  /** Dasselbe Angebot mit frei gewaehltem Titel — {@code null} fuer ein Angebot von vor #316. */
  private static Angebot angebot(
      final long id,
      final @Nullable Long ansprechpartnerId,
      final Angebotsstatus status,
      final @Nullable String titel) {
    return new Angebot(
        Long.valueOf(id),
        FIRMA,
        ansprechpartnerId,
        status.intern(),
        status,
        ANGEBOTSDATUM,
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
        ANGELEGT);
  }

  private static MockHttpServletRequestBuilder anlegenMit(final String rumpf) {
    return post("/api/firmen/{firmaId}/angebote", Long.valueOf(FIRMA))
        .contentType(MediaType.APPLICATION_JSON)
        .content(rumpf);
  }

  private static Angebot angelegt(final @Nullable Long ansprechpartnerId) {
    return angebot(ANGEBOT, ansprechpartnerId, Angebotsstatus.ANGELEGT);
  }

  @Test
  void angebote_thenAnswersWithOneRowPerAngebot() throws Exception {
    // Given — Kriterium 7: die Liste in der Reihenfolge des Anwendungsfalls.
    when(liste.angebote(FIRMA))
        .thenReturn(List.of(angelegt(null), angebot(9L, null, Angebotsstatus.BESTELLT)));

    // When / Then
    mockMvc
        .perform(get("/api/firmen/{firmaId}/angebote", Long.valueOf(FIRMA)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.angebote.length()").value(2))
        .andExpect(jsonPath("$.angebote[0].id").value(Long.valueOf(ANGEBOT)))
        .andExpect(jsonPath("$.angebote[0].status").value("ANGELEGT"))
        .andExpect(jsonPath("$.angebote[0].titel").value("Website-Relaunch"))
        .andExpect(jsonPath("$.angebote[0].intern").value(false))
        .andExpect(jsonPath("$.angebote[0].summe").value(2500.03))
        .andExpect(jsonPath("$.angebote[1].id").value(9))
        .andExpect(jsonPath("$.angebote[1].status").value("BESTELLT"));
    verifyNoInteractions(kunden);
  }

  @Test
  void angebote_givenAnOfferWithoutTitle_thenTheRowCarriesNullAsTitle() throws Exception {
    // Given — #314, Kriterium 4: ein Angebot von vor dem Titel traegt keinen.
    when(liste.angebote(FIRMA))
        .thenReturn(List.of(angebot(ANGEBOT, null, Angebotsstatus.ANGELEGT, null)));

    // When / Then
    mockMvc
        .perform(get("/api/firmen/{firmaId}/angebote", Long.valueOf(FIRMA)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.angebote[0].titel").value(nullValue()));
  }

  @Test
  void anlegen_withOnlyATitle_thenCreatesAnAngebotWithoutAContact() throws Exception {
    // Given
    when(anlegen.anlegen(FIRMA, TITEL, null, false)).thenReturn(angelegt(null));
    when(kunden.zu(any())).thenReturn(new Kundenangaben("Adler AG", null));

    // When / Then
    mockMvc
        .perform(anlegenMit(NUR_TITEL))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(Long.valueOf(ANGEBOT)))
        .andExpect(jsonPath("$.status").value("ANGELEGT"))
        .andExpect(jsonPath("$.firmaId").value(Long.valueOf(FIRMA)))
        .andExpect(jsonPath("$.firmaName").value("Adler AG"))
        .andExpect(jsonPath("$.titel").value("Website-Relaunch"))
        .andExpect(jsonPath("$.ansprechpartnerId").doesNotExist())
        .andExpect(jsonPath("$.ansprechpartnerName").doesNotExist())
        .andExpect(jsonPath("$.intern").value(false));
    verify(anlegen).anlegen(FIRMA, TITEL, null, false);
  }

  @Test
  void anlegen_withAContact_thenPassesItOnAndNamesIt() throws Exception {
    // Given
    when(anlegen.anlegen(FIRMA, TITEL, PERSON, false)).thenReturn(angelegt(PERSON));
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
    verify(anlegen).anlegen(FIRMA, TITEL, PERSON, false);
  }

  @Test
  void anlegen_withTheInternalFlag_thenPassesItOnAndAnswersWithTheInternalStatus()
      throws Exception {
    // Given — Issue #226, Kriterien 1 und 2: die interne Arbeit beginnt in LAEUFT.
    when(anlegen.anlegen(FIRMA, TITEL, null, true))
        .thenReturn(angebot(ANGEBOT, null, Angebotsstatus.LAEUFT));
    when(kunden.zu(any())).thenReturn(new Kundenangaben("Adler AG", null));

    // When / Then
    mockMvc
        .perform(
            post("/api/firmen/{firmaId}/angebote", Long.valueOf(FIRMA))
                .contentType(MediaType.APPLICATION_JSON)
                .content(INTERN))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.intern").value(true))
        .andExpect(jsonPath("$.status").value("LAEUFT"));
    verify(anlegen).anlegen(FIRMA, TITEL, null, true);
  }

  @Test
  void anlegen_withAnExplicitNullFlag_thenCreatesACustomerOffer() throws Exception {
    // Given — ein {@code null} geht denselben Weg wie ein fehlendes Feld.
    when(anlegen.anlegen(FIRMA, TITEL, null, false)).thenReturn(angelegt(null));
    when(kunden.zu(any())).thenReturn(new Kundenangaben("Adler AG", null));

    // When / Then
    mockMvc
        .perform(
            post("/api/firmen/{firmaId}/angebote", Long.valueOf(FIRMA))
                .contentType(MediaType.APPLICATION_JSON)
                .content(INTERN_NULL))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.intern").value(false))
        .andExpect(jsonPath("$.status").value("ANGELEGT"));
    verify(anlegen).anlegen(FIRMA, TITEL, null, false);
  }

  @Test
  void anlegen_atAnUnknownFirma_thenAnswers404() throws Exception {
    // Given
    when(anlegen.anlegen(eq(FIRMA), eq(TITEL), isNull(), eq(false)))
        .thenThrow(new FirmaNichtGefunden());

    // When / Then
    mockMvc.perform(anlegenMit(NUR_TITEL)).andExpect(status().isNotFound());
  }

  @Test
  void anlegen_atAStillgelegteFirma_thenAnswers409() throws Exception {
    // Given
    when(anlegen.anlegen(eq(FIRMA), eq(TITEL), isNull(), eq(false)))
        .thenThrow(new FirmaStillgelegt());

    // When / Then
    mockMvc.perform(anlegenMit(NUR_TITEL)).andExpect(status().isConflict());
  }

  @Test
  void anlegen_withAContactThatIsNotAvailable_thenAnswers422() throws Exception {
    // Given
    when(anlegen.anlegen(FIRMA, TITEL, PERSON, false))
        .thenThrow(new AnsprechpartnerNichtWaehlbar());

    // When / Then
    mockMvc
        .perform(
            post("/api/firmen/{firmaId}/angebote", Long.valueOf(FIRMA))
                .contentType(MediaType.APPLICATION_JSON)
                .content(MIT_PERSON))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.fieldErrors.ansprechpartnerId[0]")
                .value("Dieser Ansprechpartner steht für das Angebot nicht zur Wahl."));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"ansprechpartnerId\":8}", "{\"titel\":null}", "{\"titel\":\"   \"}"})
  void anlegen_withoutATitle_thenAnswers400AtTheTitle(final String rumpf) throws Exception {
    // When / Then — #314, Kriterium 2: ohne Titel entsteht kein Angebot; Leerraum zaehlt als
    // fehlend.
    mockMvc
        .perform(anlegenMit(rumpf))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.titel").isArray());
    verifyNoInteractions(anlegen);
  }

  @Test
  void anlegen_withATitleOf201Characters_thenAnswers400AtTheTitle() throws Exception {
    // When / Then — #314, Kriterium 1: hoechstens 200 Zeichen.
    mockMvc
        .perform(anlegenMit(mitTitel("x".repeat(201))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.titel").isArray());
    verifyNoInteractions(anlegen);
  }

  @Test
  void anlegen_withATitleOf200Characters_thenCreatesTheAngebot() throws Exception {
    // Given — die Grenze selbst geht durch.
    final String titel = "x".repeat(200);
    when(anlegen.anlegen(FIRMA, titel, null, false))
        .thenReturn(angebot(ANGEBOT, null, Angebotsstatus.ANGELEGT, titel));
    when(kunden.zu(any())).thenReturn(new Kundenangaben("Adler AG", null));

    // When / Then
    mockMvc
        .perform(anlegenMit(mitTitel(titel)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.titel").value(titel));
  }

  @Test
  void anlegen_withoutAnyBody_thenAnswers400WithoutFieldErrors() throws Exception {
    // When / Then — E4: Der Rumpf ist Pflicht. Ein fehlender nennt kein Feld und ist darum ein
    // unlesbarer Rumpf, nicht ein fehlender Titel.
    mockMvc
        .perform(post("/api/firmen/{firmaId}/angebote", Long.valueOf(FIRMA)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(GlobalExceptionHandler.UNLESBARER_RUMPF))
        .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    verifyNoInteractions(anlegen);
  }

  private static String mitTitel(final String titel) {
    return "{\"titel\":\"" + titel + "\"}";
  }
}
