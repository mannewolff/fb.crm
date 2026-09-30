package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;

/**
 * Die Angebote, aus denen eine Rechnung entstehen darf (#160, Kriterium 2; Frage 2).
 *
 * <p>Gegenstand sind die beiden Bedingungen: der Status ab „bestellt" und mindestens eine Position,
 * an der noch etwas offen ist. Entwuerfe zaehlen dabei als abgerechnet (Kriterium 6) — ein Angebot,
 * dessen Rest schon in einem Entwurf steht, steht nicht mehr zur Wahl.
 */
@ExtendWith(MockitoExtension.class)
class AbrechenbareAngeboteUseCaseTest {

  @Mock private AngebotRepository angebote;
  @Mock private RechnungRepository rechnungen;
  @Mock private FirmaRepository firmen;

  @Captor private ArgumentCaptor<Collection<Long>> gefragteFirmen;

  private AbrechenbareAngeboteUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new AbrechenbareAngeboteUseCase(angebote, rechnungen, firmen);
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotsstatus.class,
      names = {"BESTELLT", "ERLEDIGT", "ABGERECHNET"})
  void abrechenbare_withAnOffenePosition_thenTheAngebotStandsToChoose(final Angebotsstatus status) {
    // Given — nichts abgerechnet: 160 Stunden zu 100,00 € und die Pauschale zu 1.200,00 €.
    when(angebote.findAlle(Optional.empty())).thenReturn(List.of(Rechnungsdoppel.angebot(status)));
    when(rechnungen.findAlle()).thenReturn(List.of());
    when(firmen.findAllById(any()))
        .thenReturn(List.of(Rechnungsdoppel.firma(Rechnungsdoppel.FIRMA, "Adler AG")));

    // When
    final List<AbrechenbaresAngebot> zeilen = useCase.abrechenbare();

    // Then
    assertThat(zeilen)
        .singleElement()
        .satisfies(
            zeile -> assertThat(zeile.angebot().requireId()).isEqualTo(Rechnungsdoppel.ANGEBOT),
            zeile -> assertThat(zeile.firmaName()).isEqualTo("Adler AG"),
            zeile -> assertThat(zeile.angebot().angebotDatum()).isEqualTo("2026-09-20"),
            zeile -> assertThat(zeile.offenerBetrag()).isEqualByComparingTo("17200.00"));
    verify(firmen).findAllById(gefragteFirmen.capture());
    assertThat(gefragteFirmen.getValue()).containsExactly(Rechnungsdoppel.FIRMA);
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotsstatus.class,
      names = {"ANGELEGT", "ABGEGEBEN"})
  void abrechenbare_withAnAngebotBeforeBestellt_thenItIsLeftOut(final Angebotsstatus status) {
    // Given — vor „bestellt" gibt es nichts abzurechnen, auch wenn alles offen ist.
    when(angebote.findAlle(Optional.empty())).thenReturn(List.of(Rechnungsdoppel.angebot(status)));

    // When
    final List<AbrechenbaresAngebot> zeilen = useCase.abrechenbare();

    // Then
    assertThat(zeilen).isEmpty();
    verifyNoInteractions(rechnungen, firmen);
  }

  @Test
  void abrechenbare_withNothingLeftOpen_thenTheAngebotIsLeftOut() {
    // Given — beide Positionen vollstaendig in einer gestellten Rechnung.
    when(angebote.findAlle(Optional.empty())).thenReturn(List.of(Rechnungsdoppel.angebot()));
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L,
                    "R26-0001",
                    List.of(Rechnungsdoppel.beratung("160.00"), Rechnungsdoppel.pauschale("1")))));

    // When
    final List<AbrechenbaresAngebot> zeilen = useCase.abrechenbare();

    // Then
    assertThat(zeilen).isEmpty();
    verifyNoInteractions(firmen);
  }

  @Test
  void abrechenbare_withAnEntwurfCoveringTheRest_thenTheAngebotIsLeftOut() {
    // Given — Entwuerfe zaehlen als abgerechnet (Kriterium 6, Frage 3).
    when(angebote.findAlle(Optional.empty())).thenReturn(List.of(Rechnungsdoppel.angebot()));
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L, "R26-0001", List.of(Rechnungsdoppel.beratung("80.00"))),
                Rechnungsdoppel.entwurf(
                    2L,
                    List.of(Rechnungsdoppel.beratung("80.00"), Rechnungsdoppel.pauschale("1")))));

    // When
    final List<AbrechenbaresAngebot> zeilen = useCase.abrechenbare();

    // Then
    assertThat(zeilen).isEmpty();
  }

  @Test
  void abrechenbare_withAPartlyAbgerechnetesAngebot_thenTheOffenerBetragIsTheRest() {
    // Given — 80 der 160 Stunden gestellt; offen bleiben 80 Stunden und die Pauschale.
    when(angebote.findAlle(Optional.empty())).thenReturn(List.of(Rechnungsdoppel.angebot()));
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L, "R26-0001", List.of(Rechnungsdoppel.beratung("80.00")))));
    when(firmen.findAllById(any()))
        .thenReturn(List.of(Rechnungsdoppel.firma(Rechnungsdoppel.FIRMA, "Adler AG")));

    // When
    final List<AbrechenbaresAngebot> zeilen = useCase.abrechenbare();

    // Then
    assertThat(zeilen)
        .singleElement()
        .extracting(AbrechenbaresAngebot::offenerBetrag)
        .isEqualTo(new BigDecimal("9200.00"));
  }

  @Test
  void abrechenbare_whenThereAreNoAngebote_thenAnswersEmptyWithoutAskingFurther() {
    // Given
    when(angebote.findAlle(Optional.empty())).thenReturn(List.of());

    // When
    final List<AbrechenbaresAngebot> zeilen = useCase.abrechenbare();

    // Then
    assertThat(zeilen).isEmpty();
    verifyNoInteractions(rechnungen, firmen);
  }

  @Test
  void abrechenbare_whenTheFirmaOfAnAngebotIsMissing_thenItIsAContradictionInTheBestand() {
    // Given — Firmen werden nie geloescht; fehlt der Name, stimmt der Bestand nicht.
    when(angebote.findAlle(Optional.empty())).thenReturn(List.of(Rechnungsdoppel.angebot()));
    when(rechnungen.findAlle()).thenReturn(List.of());
    when(firmen.findAllById(any())).thenReturn(List.of());

    // When / Then
    assertThatThrownBy(() -> useCase.abrechenbare()).isInstanceOf(FirmaNichtGefunden.class);
  }
}
