package org.mwolff.fbcrm.angebot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;

/**
 * Die Uebersicht aller Angebote (Issue #127, Kriterium 8).
 *
 * <p>Gegenstand sind der Filter, die Reihenfolge — neueste zuerst, bei gleichem Datum die hoehere
 * Kennung — und der Name der Firma an jeder Zeile. Die Namen kommen in <b>einem</b> Aufruf fuer
 * alle beteiligten Firmen und nicht je Zeile.
 */
@ExtendWith(MockitoExtension.class)
class AngeboteUebersichtUseCaseTest {

  private static final LocalDate FRUEH = LocalDate.of(2026, 9, 20);
  private static final LocalDate SPAET = LocalDate.of(2026, 9, 25);
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");

  @Mock private AngebotRepository angebote;
  @Mock private FirmaRepository firmen;

  @Captor private ArgumentCaptor<Set<Long>> gefragteFirmen;

  private AngeboteUebersichtUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new AngeboteUebersichtUseCase(angebote, firmen);
  }

  private static Firma firma(final long id, final String name) {
    return new Firma(
        Long.valueOf(id),
        name,
        new Anschrift(null, null, null, null),
        null,
        null,
        true,
        ANGELEGT,
        ANGELEGT);
  }

  @Test
  void angebote_withoutAFilter_thenEveryAngebotNewestFirstWithItsFirmaName() {
    // Given — zwei Firmen, drei Angebote, zwei davon am selben Tag.
    when(angebote.findAlle(Optional.empty()))
        .thenReturn(
            List.of(
                Angebotsdoppel.angebot(1L, Angebotsdoppel.FIRMA, Angebotsstatus.ANGELEGT, FRUEH),
                Angebotsdoppel.angebot(
                    2L, Angebotsdoppel.FREMDE_FIRMA, Angebotsstatus.BESTELLT, SPAET),
                Angebotsdoppel.angebot(3L, Angebotsdoppel.FIRMA, Angebotsstatus.ERLEDIGT, FRUEH)));
    when(firmen.findAllById(any()))
        .thenReturn(
            List.of(
                firma(Angebotsdoppel.FIRMA, "Adler AG"),
                firma(Angebotsdoppel.FREMDE_FIRMA, "Biber GmbH")));

    // When
    final List<AngebotMitFirma> zeilen = useCase.angebote(Optional.empty());

    // Then
    assertThat(zeilen)
        .extracting(zeile -> zeile.angebot().requireId(), AngebotMitFirma::firmaName)
        .containsExactly(tuple(2L, "Biber GmbH"), tuple(3L, "Adler AG"), tuple(1L, "Adler AG"));
    verify(firmen).findAllById(gefragteFirmen.capture());
    assertThat(gefragteFirmen.getValue())
        .containsExactlyInAnyOrder(Angebotsdoppel.FIRMA, Angebotsdoppel.FREMDE_FIRMA);
  }

  @Test
  void angebote_withAStatus_thenPassesTheFilterToTheBestand() {
    // Given
    when(angebote.findAlle(Optional.of(Angebotsstatus.BESTELLT)))
        .thenReturn(
            List.of(
                Angebotsdoppel.angebot(2L, Angebotsdoppel.FIRMA, Angebotsstatus.BESTELLT, SPAET)));
    when(firmen.findAllById(any())).thenReturn(List.of(firma(Angebotsdoppel.FIRMA, "Adler AG")));

    // When
    final List<AngebotMitFirma> zeilen = useCase.angebote(Optional.of(Angebotsstatus.BESTELLT));

    // Then
    assertThat(zeilen)
        .singleElement()
        .satisfies(
            zeile -> assertThat(zeile.angebot().status()).isEqualTo(Angebotsstatus.BESTELLT),
            zeile -> assertThat(zeile.firmaName()).isEqualTo("Adler AG"));
  }

  @Test
  void angebote_whenThereAreNone_thenAnswersEmptyWithoutAskingForFirmen() {
    // Given
    when(angebote.findAlle(Optional.empty())).thenReturn(List.of());

    // When / Then
    assertThat(useCase.angebote(Optional.empty())).isEmpty();
    verifyNoInteractions(firmen);
  }

  @Test
  void angebote_whenTheFirmaOfAnAngebotIsMissing_thenRejects() {
    // Given — ein Widerspruch im Bestand: Firmen werden nie geloescht.
    when(angebote.findAlle(Optional.empty()))
        .thenReturn(
            List.of(
                Angebotsdoppel.angebot(1L, Angebotsdoppel.FIRMA, Angebotsstatus.ANGELEGT, FRUEH)));
    when(firmen.findAllById(any())).thenReturn(List.of());

    // When / Then
    assertThatThrownBy(() -> useCase.angebote(Optional.empty()))
        .isInstanceOf(FirmaNichtGefunden.class);
  }
}
