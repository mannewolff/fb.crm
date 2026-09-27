package org.mwolff.fbcrm.vorgang.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.vorgang.application.EintragAnsicht;
import org.mwolff.fbcrm.vorgang.application.FirmaNichtWaehlbar;
import org.mwolff.fbcrm.vorgang.application.VorgaengeUebersicht;
import org.mwolff.fbcrm.vorgang.application.VorgaengeUebersichtUseCase;
import org.mwolff.fbcrm.vorgang.application.VorgangAbschliessenUseCase;
import org.mwolff.fbcrm.vorgang.application.VorgangAendernUseCase;
import org.mwolff.fbcrm.vorgang.application.VorgangAnlegenUseCase;
import org.mwolff.fbcrm.vorgang.application.VorgangDaten;
import org.mwolff.fbcrm.vorgang.application.VorgangLesenUseCase;
import org.mwolff.fbcrm.vorgang.application.VorgangMitHistorie;
import org.mwolff.fbcrm.vorgang.application.VorgangNichtGefunden;
import org.mwolff.fbcrm.vorgang.application.VorgangZeile;
import org.mwolff.fbcrm.vorgang.domain.Eintragsart;
import org.mwolff.fbcrm.vorgang.domain.Herkunft;
import org.mwolff.fbcrm.vorgang.domain.Phase;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen Anwendungsfall und HTTP fuer die Lese- und Schreibwege des Vorgangs.
 *
 * <p>Zwei Blickwinkel in einer Klasse, wie bei {@code FirmaControllerTest}: Die Abbildung wird
 * direkt an den Methoden geprueft, und die Faelle, bei denen die <b>Form</b> der Antwort die
 * Aussage ist (Standardwerte der Parameter, Feldfehler, unbekannte Kennung, Statuscodes der
 * Schreibwege), laufen durch eine schlanke MockMvc-Strecke mit dem echten {@link
 * GlobalExceptionHandler}.
 *
 * <p>Die Nummer geht als Zahl hinaus; das {@code #} setzt die Oberflaeche.
 */
@ExtendWith(MockitoExtension.class)
class VorgangControllerTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant GESCHEHEN = Instant.parse("2026-09-12T09:00:00Z");
  private static final LocalDate ERWARTETE_ENTSCHEIDUNG = LocalDate.of(2026, 10, 15);

  @Mock private VorgaengeUebersichtUseCase uebersicht;
  @Mock private VorgangLesenUseCase lesen;
  @Mock private VorgangAnlegenUseCase anlegen;
  @Mock private VorgangAendernUseCase aendern;
  @Mock private VorgangAbschliessenUseCase abschliessen;

  private VorgangController controller;
  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    controller = new VorgangController(uebersicht, lesen, anlegen, aendern, abschliessen);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private static String rumpf(final String titel, final String firmaId) {
    return "{\"titel\":"
        + titel
        + ",\"firmaId\":"
        + firmaId
        + ",\"ansprechpartnerId\":3,\"abschlusswahrscheinlichkeit\":30,"
        + "\"entscheidungErwartetAm\":\"2026-10-15\"}";
  }

  private static VorgangRequest anfrage(final String titel) {
    return new VorgangRequest(
        titel, Long.valueOf(7L), Long.valueOf(3L), Integer.valueOf(30), ERWARTETE_ENTSCHEIDUNG);
  }

  private static VorgangDaten erwarteteDaten(final String titel) {
    return new VorgangDaten(
        titel, 7L, Long.valueOf(3L), Integer.valueOf(30), ERWARTETE_ENTSCHEIDUNG);
  }

  private static Firma adlerAg(final boolean aktiv) {
    return new Firma(
        7L,
        "Adler AG",
        new Anschrift(null, null, null, null),
        null,
        null,
        aktiv,
        ANGELEGT,
        ANGELEGT);
  }

  private static Ansprechpartner maxMueller(final String vorname, final boolean aktiv) {
    return new Ansprechpartner(
        3L, 7L, vorname, "Mueller", null, null, null, null, aktiv, ANGELEGT, ANGELEGT);
  }

  private static Vorgang vorgang() {
    return new Vorgang(
        4L,
        12L,
        "Website-Relaunch",
        7L,
        null,
        Integer.valueOf(30),
        ERWARTETE_ENTSCHEIDUNG,
        false,
        ANGELEGT,
        ANGELEGT);
  }

  private static VorgangMitHistorie gelesen(
      final Ansprechpartner partner, final List<EintragAnsicht> historie) {
    return new VorgangMitHistorie(vorgang(), Phase.ANBAHNUNG, adlerAg(true), partner, historie);
  }

  @Test
  void uebersicht_thenAnswersWithRowsAndTheTotal() {
    // Given
    when(uebersicht.uebersicht("adler", true))
        .thenReturn(
            new VorgaengeUebersicht(
                List.of(
                    new VorgangZeile(
                        4L,
                        12L,
                        "Website-Relaunch",
                        "Adler AG",
                        Phase.ANBAHNUNG,
                        false,
                        GESCHEHEN)),
                9L));

    // When
    final VorgaengeUebersichtResponse antwort = controller.uebersicht("adler", true);

    // Then
    assertThat(antwort)
        .isEqualTo(
            new VorgaengeUebersichtResponse(
                List.of(
                    new VorgangZeileResponse(
                        4L,
                        12L,
                        "Website-Relaunch",
                        "Adler AG",
                        Phase.ANBAHNUNG,
                        false,
                        GESCHEHEN)),
                9L));
  }

  @Test
  void uebersicht_givenNoParameters_thenSearchesForEverythingWithoutClosedVorgaenge()
      throws Exception {
    // Given — die Standardwerte stehen an der Schnittstelle, nicht im Anwendungsfall.
    when(uebersicht.uebersicht("", false)).thenReturn(new VorgaengeUebersicht(List.of(), 0L));

    // When
    mockMvc.perform(get("/api/vorgaenge")).andExpect(status().isOk());

    // Then
    verify(uebersicht).uebersicht("", false);
  }

  @Test
  void lesen_thenAnswersWithVorgangFirmaAndHistory() {
    // Given — Kriterien 9, 11, 15: alles in einer Antwort (E25).
    when(lesen.lese(4L))
        .thenReturn(
            gelesen(
                maxMueller("Max", true),
                List.of(
                    new EintragAnsicht(
                        21L,
                        Eintragsart.KOMMENTAR,
                        "Angerufen",
                        GESCHEHEN,
                        Herkunft.VON_HAND,
                        null,
                        null,
                        null))));

    // When
    final VorgangResponse antwort = controller.lesen(4L);

    // Then
    assertThat(antwort)
        .isEqualTo(
            new VorgangResponse(
                4L,
                12L,
                "Website-Relaunch",
                Phase.ANBAHNUNG,
                false,
                Integer.valueOf(30),
                ERWARTETE_ENTSCHEIDUNG,
                new ZuordnungResponse(7L, "Adler AG", true),
                new ZuordnungResponse(3L, "Max Mueller", true),
                List.of(
                    new EintragResponse(
                        21L,
                        Eintragsart.KOMMENTAR,
                        "Angerufen",
                        GESCHEHEN,
                        Herkunft.VON_HAND,
                        null,
                        null,
                        null))));
  }

  @Test
  void lesen_givenARetiredFirmaAndAnsprechpartner_thenBothCarryTheirState() {
    // Given — Kriterium 23: daran haengt die Kennzeichnung in der Ansicht.
    when(lesen.lese(4L))
        .thenReturn(
            new VorgangMitHistorie(
                vorgang(), Phase.ANBAHNUNG, adlerAg(false), maxMueller("Max", false), List.of()));

    // When
    final VorgangResponse antwort = controller.lesen(4L);

    // Then
    assertThat(antwort)
        .extracting(a -> a.firma().aktiv(), a -> a.ansprechpartner().aktiv())
        .containsExactly(false, false);
  }

  @Test
  void lesen_givenACommittedOffer_thenAnswersWithThePhaseAngebot() {
    // Given — Kriterium 22: die Phase kommt aus dem Anwendungsfall, nicht aus dem Vorgang allein.
    when(lesen.lese(4L))
        .thenReturn(
            new VorgangMitHistorie(vorgang(), Phase.ANGEBOT, adlerAg(true), null, List.of()));

    // When
    final VorgangResponse antwort = controller.lesen(4L);

    // Then
    assertThat(antwort.phase()).isEqualTo(Phase.ANGEBOT);
  }

  @Test
  void lesen_givenAVorgangWithoutPipelineFields_thenBothStayAbsent() {
    // Given — Kriterium 21: beide Angaben sind optional.
    when(lesen.lese(4L))
        .thenReturn(
            new VorgangMitHistorie(
                new Vorgang(
                    4L, 12L, "Website-Relaunch", 7L, null, null, null, false, ANGELEGT, ANGELEGT),
                Phase.ANBAHNUNG,
                adlerAg(true),
                null,
                List.of()));

    // When
    final VorgangResponse antwort = controller.lesen(4L);

    // Then
    assertThat(antwort)
        .extracting(
            VorgangResponse::abschlusswahrscheinlichkeit, VorgangResponse::entscheidungErwartetAm)
        .containsOnlyNulls();
  }

  @Test
  void lesen_givenAnAnsprechpartnerWithoutAFirstName_thenUsesTheLastNameAlone() {
    // Given
    when(lesen.lese(4L)).thenReturn(gelesen(maxMueller(null, true), List.of()));

    // When
    final VorgangResponse antwort = controller.lesen(4L);

    // Then
    assertThat(antwort.ansprechpartner()).isEqualTo(new ZuordnungResponse(3L, "Mueller", true));
  }

  @Test
  void lesen_givenAVorgangWithoutAnAnsprechpartner_thenLeavesHimAbsent() {
    // Given
    when(lesen.lese(4L)).thenReturn(gelesen(null, List.of()));

    // When
    final VorgangResponse antwort = controller.lesen(4L);

    // Then
    assertThat(antwort.ansprechpartner()).isNull();
  }

  @Test
  void lesen_givenAnAnhang_thenAnswersWithNameAndSize() {
    // Given — Kriterium 15.
    when(lesen.lese(4L))
        .thenReturn(
            gelesen(
                null,
                List.of(
                    new EintragAnsicht(
                        22L,
                        Eintragsart.ANHANG,
                        "Das Angebot",
                        GESCHEHEN,
                        Herkunft.VON_HAND,
                        "Angebot.pdf",
                        4096L,
                        GESCHEHEN))));

    // When
    final VorgangResponse antwort = controller.lesen(4L);

    // Then
    assertThat(antwort.historie())
        .containsExactly(
            new EintragResponse(
                22L,
                Eintragsart.ANHANG,
                "Das Angebot",
                GESCHEHEN,
                Herkunft.VON_HAND,
                "Angebot.pdf",
                4096L,
                GESCHEHEN));
  }

  @Test
  void lesen_givenAnUnknownId_thenAnswersNotFound() throws Exception {
    // Given
    when(lesen.lese(4711L)).thenThrow(new VorgangNichtGefunden());

    // When / Then
    mockMvc.perform(get("/api/vorgaenge/4711")).andExpect(status().isNotFound());
  }

  @Test
  void anlegen_thenPassesTheRequestToTheUseCase() {
    // Given
    when(anlegen.anlegen(erwarteteDaten("Website-Relaunch"))).thenReturn(vorgang());

    // When
    controller.anlegen(anfrage("Website-Relaunch"));

    // Then
    verify(anlegen).anlegen(erwarteteDaten("Website-Relaunch"));
  }

  @Test
  void anlegen_thenAnswersWithIdAndNumber() {
    // Given — Kriterien 8, 9: die Oberflaeche braucht beides fuer den Weg zur Detailansicht.
    when(anlegen.anlegen(erwarteteDaten("Website-Relaunch"))).thenReturn(vorgang());

    // When
    final VorgangAngelegtResponse antwort = controller.anlegen(anfrage("Website-Relaunch"));

    // Then
    assertThat(antwort).isEqualTo(new VorgangAngelegtResponse(4L, 12L));
  }

  @Test
  void anlegen_thenAnswersCreated() throws Exception {
    // Given — E25: das Anlegen ist der einzige Schreibweg mit Rumpf.
    when(anlegen.anlegen(erwarteteDaten("Website-Relaunch"))).thenReturn(vorgang());

    // When / Then
    mockMvc
        .perform(
            post("/api/vorgaenge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("\"Website-Relaunch\"", "7")))
        .andExpect(status().isCreated());
  }

  @Test
  void anlegen_givenABlankTitle_thenNamesTheFieldInTheProblemDetail() throws Exception {
    // When / Then — Kriterium 5: die Meldung steht am Feld.
    mockMvc
        .perform(
            post("/api/vorgaenge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("\"   \"", "7")))
        .andExpect(jsonPath("$.fieldErrors.titel").isArray());
  }

  @Test
  void anlegen_givenNoFirma_thenNamesTheFieldInTheProblemDetail() throws Exception {
    // When / Then — Kriterium 5: die Firma ist Pflicht.
    mockMvc
        .perform(
            post("/api/vorgaenge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("\"Website-Relaunch\"", "null")))
        .andExpect(jsonPath("$.fieldErrors.firmaId").isArray());
  }

  @Test
  void anlegen_givenATitleBeyondTheColumnWidth_thenAnswersBadRequest() throws Exception {
    // When / Then — dieselbe Grenze wie in V3__vorgang_und_historie.sql: 300 Zeichen.
    mockMvc
        .perform(
            post("/api/vorgaenge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("\"" + "x".repeat(301) + "\"", "7")))
        .andExpect(jsonPath("$.fieldErrors.titel").isArray());
  }

  @Test
  void anlegen_givenAProbabilityOutsideTheTenSteps_thenNamesTheFieldInTheProblemDetail()
      throws Exception {
    // When / Then — Kriterium 21: die Meldung steht am Feld.
    mockMvc
        .perform(
            post("/api/vorgaenge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"titel\":\"Website-Relaunch\",\"firmaId\":7,"
                        + "\"abschlusswahrscheinlichkeit\":35}"))
        .andExpect(jsonPath("$.fieldErrors.abschlusswahrscheinlichkeit").isArray());
  }

  @Test
  void anlegen_givenARetiredFirma_thenAnswersBadRequest() throws Exception {
    // Given — E19: die Wahlregel schlaegt als 400 durch, nicht als 500.
    when(anlegen.anlegen(erwarteteDaten("Website-Relaunch"))).thenThrow(new FirmaNichtWaehlbar());

    // When / Then
    mockMvc
        .perform(
            post("/api/vorgaenge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("\"Website-Relaunch\"", "7")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aendern_thenPassesIdAndRequestToTheUseCase() {
    // When — Kriterium 10.
    controller.aendern(4L, anfrage("Neuer Titel"));

    // Then
    verify(aendern).aendern(4L, erwarteteDaten("Neuer Titel"));
  }

  @Test
  void aendern_thenAnswersWithoutContent() throws Exception {
    // When / Then — E25: die Schreibwege antworten ohne Rumpf.
    mockMvc
        .perform(
            put("/api/vorgaenge/4")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("\"Neuer Titel\"", "7")))
        .andExpect(status().isNoContent());
  }

  @Test
  void abschliessen_thenPassesTheIdToTheUseCase() {
    // When — Kriterium 20.
    controller.abschliessen(4L);

    // Then
    verify(abschliessen).abschliessen(4L);
  }

  @Test
  void abschliessen_thenAnswersWithoutContent() throws Exception {
    // When / Then
    mockMvc.perform(post("/api/vorgaenge/4/abschliessen")).andExpect(status().isNoContent());
  }

  @Test
  void wiederEroeffnen_thenPassesTheIdToTheUseCase() {
    // When — Kriterium 20: der Weg zurueck.
    controller.wiederEroeffnen(4L);

    // Then
    verify(abschliessen).wiederEroeffnen(4L);
  }

  @Test
  void wiederEroeffnen_thenAnswersWithoutContent() throws Exception {
    // When / Then
    mockMvc.perform(post("/api/vorgaenge/4/wiedereroeffnen")).andExpect(status().isNoContent());
  }

  @Test
  void abschliessen_givenAnUnknownId_thenAnswersNotFound() throws Exception {
    // Given
    doThrow(new VorgangNichtGefunden()).when(abschliessen).abschliessen(4711L);

    // When / Then
    mockMvc.perform(post("/api/vorgaenge/4711/abschliessen")).andExpect(status().isNotFound());
  }
}
