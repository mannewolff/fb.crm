package org.mwolff.fbcrm.vorgang.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.firma.domain.Anschrift;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.AnsprechpartnerRepository;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.vorgang.domain.Eintrag;
import org.mwolff.fbcrm.vorgang.domain.EintragRepository;
import org.mwolff.fbcrm.vorgang.domain.Eintragsart;
import org.mwolff.fbcrm.vorgang.domain.Herkunft;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;
import org.mwolff.fbcrm.vorgang.domain.VorgangRepository;

/**
 * Die Detailansicht eines Vorgangs samt Historie (Kriterien 9, 11, 15, 16, 23).
 *
 * <p>Der tragende Nachweis ist Kriterium 23: Firma und Ansprechpartner kommen mit ihrem
 * Stilllegungsstand mit — sonst waere eine stillgelegte Zuordnung in der Ansicht nicht von einer
 * aktiven zu unterscheiden. Dass die Historie in <b>einer</b> Antwort steckt, ist E25.
 *
 * <p>Der Objektschluessel eines Anhangs bleibt draussen: Er ist die interne Adresse im
 * Objektspeicher und hat in einer Antwort nach aussen nichts zu suchen.
 */
@ExtendWith(MockitoExtension.class)
class VorgangLesenUseCaseTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant GESCHEHEN = Instant.parse("2026-09-12T09:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-09-18T12:00:00Z");

  @Mock private VorgangRepository vorgaenge;
  @Mock private EintragRepository eintraege;
  @Mock private FirmaRepository firmen;
  @Mock private AnsprechpartnerRepository ansprechpartner;

  private VorgangLesenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new VorgangLesenUseCase(vorgaenge, eintraege, firmen, ansprechpartner);
  }

  private static Vorgang vorgang(final Long ansprechpartnerId) {
    return new Vorgang(
        4L, 12L, "Website-Relaunch", 7L, ansprechpartnerId, false, ANGELEGT, ANGELEGT);
  }

  private static Firma firma(final boolean aktiv) {
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

  private static Ansprechpartner partner(final boolean aktiv) {
    return new Ansprechpartner(
        3L, 7L, "Max", "Mueller", null, null, null, null, aktiv, ANGELEGT, ANGELEGT);
  }

  @Test
  void lese_thenCarriesTheVorgangItself() {
    // Given
    when(vorgaenge.findById(4L)).thenReturn(Optional.of(vorgang(null)));
    when(firmen.findById(7L)).thenReturn(Optional.of(firma(true)));
    when(eintraege.findByVorgang(4L)).thenReturn(List.of());

    // When
    final VorgangMitHistorie gelesen = useCase.lese(4L);

    // Then
    assertThat(gelesen.vorgang()).isEqualTo(vorgang(null));
  }

  @Test
  void lese_givenARetiredFirma_thenCarriesItWithItsRetiredState() {
    // Given — Kriterium 23.
    when(vorgaenge.findById(4L)).thenReturn(Optional.of(vorgang(null)));
    when(firmen.findById(7L)).thenReturn(Optional.of(firma(false)));
    when(eintraege.findByVorgang(4L)).thenReturn(List.of());

    // When
    final VorgangMitHistorie gelesen = useCase.lese(4L);

    // Then
    assertThat(gelesen.firma()).isEqualTo(firma(false));
  }

  @Test
  void lese_givenARetiredAnsprechpartner_thenCarriesHimWithHisRetiredState() {
    // Given — Kriterium 23.
    when(vorgaenge.findById(4L)).thenReturn(Optional.of(vorgang(Long.valueOf(3L))));
    when(firmen.findById(7L)).thenReturn(Optional.of(firma(true)));
    when(ansprechpartner.findById(3L)).thenReturn(Optional.of(partner(false)));
    when(eintraege.findByVorgang(4L)).thenReturn(List.of());

    // When
    final VorgangMitHistorie gelesen = useCase.lese(4L);

    // Then
    assertThat(gelesen.ansprechpartner()).isEqualTo(partner(false));
  }

  @Test
  void lese_givenAVorgangWithoutAnAnsprechpartner_thenLeavesHimAbsent() {
    // Given
    when(vorgaenge.findById(4L)).thenReturn(Optional.of(vorgang(null)));
    when(firmen.findById(7L)).thenReturn(Optional.of(firma(true)));
    when(eintraege.findByVorgang(4L)).thenReturn(List.of());

    // When
    final VorgangMitHistorie gelesen = useCase.lese(4L);

    // Then — und der Bestand wird dafuer gar nicht erst gefragt.
    assertThat(gelesen.ansprechpartner()).isNull();
    verifyNoInteractions(ansprechpartner);
  }

  @Test
  void lese_thenCarriesBothKindsOfEntriesInTheOrderOfTheRepository() {
    // Given — Kriterien 15, 16: eine Folge, juengstes Geschehen oben, jeder mit seiner Herkunft.
    when(vorgaenge.findById(4L)).thenReturn(Optional.of(vorgang(null)));
    when(firmen.findById(7L)).thenReturn(Optional.of(firma(true)));
    when(eintraege.findByVorgang(4L))
        .thenReturn(
            List.of(
                new Eintrag(
                    22L,
                    4L,
                    Eintragsart.ANHANG,
                    "Das Angebot",
                    GESCHEHEN,
                    Herkunft.VON_HAND,
                    "Angebot.pdf",
                    4096L,
                    "vorgang/4/abc",
                    ANGELEGT,
                    null),
                new Eintrag(
                    21L,
                    4L,
                    Eintragsart.KOMMENTAR,
                    "Angerufen",
                    ANGELEGT,
                    Herkunft.VON_HAND,
                    null,
                    null,
                    null,
                    ANGELEGT,
                    null)));

    // When
    final VorgangMitHistorie gelesen = useCase.lese(4L);

    // Then
    assertThat(gelesen.historie())
        .extracting(EintragAnsicht::art)
        .containsExactly(Eintragsart.ANHANG, Eintragsart.KOMMENTAR);
  }

  @Test
  void lese_givenAnAnhang_thenShowsNameAndSizeButNotTheObjectKey() {
    // Given
    when(vorgaenge.findById(4L)).thenReturn(Optional.of(vorgang(null)));
    when(firmen.findById(7L)).thenReturn(Optional.of(firma(true)));
    when(eintraege.findByVorgang(4L))
        .thenReturn(
            List.of(
                new Eintrag(
                    21L,
                    4L,
                    Eintragsart.ANHANG,
                    "Das Angebot",
                    GESCHEHEN,
                    Herkunft.VON_HAND,
                    "Angebot.pdf",
                    4096L,
                    "vorgang/4/abc",
                    ANGELEGT,
                    GEAENDERT)));

    // When
    final VorgangMitHistorie gelesen = useCase.lese(4L);

    // Then — Kriterium 15 zeigt Name und Groesse; der Schluessel bleibt innen.
    assertThat(gelesen.historie())
        .containsExactly(
            new EintragAnsicht(
                21L,
                Eintragsart.ANHANG,
                "Das Angebot",
                GESCHEHEN,
                Herkunft.VON_HAND,
                "Angebot.pdf",
                4096L,
                GEAENDERT));
  }

  @Test
  void lese_givenAnUnknownId_thenFailsWithVorgangNichtGefunden() {
    // Given
    when(vorgaenge.findById(4711L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.lese(4711L)).isInstanceOf(VorgangNichtGefunden.class);
  }

  @Test
  void lese_givenAVorgangWhoseFirmaIsGone_thenFailsLoudly() {
    // Given — der Fremdschluessel schliesst das aus; traete es ein, waere der Bestand kaputt.
    when(vorgaenge.findById(4L)).thenReturn(Optional.of(vorgang(null)));
    when(firmen.findById(7L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.lese(4L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("7");
  }

  @Test
  void lese_givenAVorgangWhoseAnsprechpartnerIsGone_thenFailsLoudly() {
    // Given
    when(vorgaenge.findById(4L)).thenReturn(Optional.of(vorgang(Long.valueOf(3L))));
    when(firmen.findById(7L)).thenReturn(Optional.of(firma(true)));
    when(ansprechpartner.findById(3L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.lese(4L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("3");
  }
}
