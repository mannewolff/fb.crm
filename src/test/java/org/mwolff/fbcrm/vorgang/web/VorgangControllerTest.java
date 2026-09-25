package org.mwolff.fbcrm.vorgang.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.mwolff.fbcrm.firma.domain.Anschrift;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.vorgang.application.EintragAnsicht;
import org.mwolff.fbcrm.vorgang.application.VorgaengeUebersicht;
import org.mwolff.fbcrm.vorgang.application.VorgaengeUebersichtUseCase;
import org.mwolff.fbcrm.vorgang.application.VorgangLesenUseCase;
import org.mwolff.fbcrm.vorgang.application.VorgangMitHistorie;
import org.mwolff.fbcrm.vorgang.application.VorgangNichtGefunden;
import org.mwolff.fbcrm.vorgang.application.VorgangZeile;
import org.mwolff.fbcrm.vorgang.domain.Eintragsart;
import org.mwolff.fbcrm.vorgang.domain.Herkunft;
import org.mwolff.fbcrm.vorgang.domain.Phase;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen Anwendungsfall und HTTP fuer die beiden Lesewege des Vorgangs.
 *
 * <p>Zwei Blickwinkel in einer Klasse, wie bei {@code FirmaControllerTest}: Die Abbildung wird
 * direkt an den Methoden geprueft, und die beiden Faelle, bei denen die <b>Form</b> der Antwort die
 * Aussage ist (Standardwerte der Parameter und unbekannte Kennung), laufen durch eine schlanke
 * MockMvc-Strecke mit dem echten {@link GlobalExceptionHandler}.
 *
 * <p>Die Nummer geht als Zahl hinaus; das {@code #} setzt die Oberflaeche.
 */
@ExtendWith(MockitoExtension.class)
class VorgangControllerTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant GESCHEHEN = Instant.parse("2026-09-12T09:00:00Z");

  @Mock private VorgaengeUebersichtUseCase uebersicht;
  @Mock private VorgangLesenUseCase lesen;

  private VorgangController controller;
  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    controller = new VorgangController(uebersicht, lesen);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
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

  private static VorgangMitHistorie gelesen(
      final Ansprechpartner partner, final List<EintragAnsicht> historie) {
    return new VorgangMitHistorie(
        new Vorgang(4L, 12L, "Website-Relaunch", 7L, null, false, ANGELEGT, ANGELEGT),
        adlerAg(true),
        partner,
        historie);
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
                new Vorgang(4L, 12L, "Website-Relaunch", 7L, null, false, ANGELEGT, ANGELEGT),
                adlerAg(false),
                maxMueller("Max", false),
                List.of()));

    // When
    final VorgangResponse antwort = controller.lesen(4L);

    // Then
    assertThat(antwort)
        .extracting(a -> a.firma().aktiv(), a -> a.ansprechpartner().aktiv())
        .containsExactly(false, false);
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
}
