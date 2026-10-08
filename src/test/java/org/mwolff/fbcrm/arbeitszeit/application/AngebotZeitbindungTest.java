package org.mwolff.fbcrm.arbeitszeit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.Zeitbindung;
import org.mwolff.fbcrm.arbeitszeit.domain.ZeiteintragRepository;

/**
 * Welche Positionen eines Angebots erfasste Arbeitszeit tragen (Issue #228, Kriterium 8 von #207).
 *
 * <p>Die Antwort geht ueber den Port {@link Zeitbindung} ans Modul {@code angebot} und sperrt dort
 * den Wechsel einer bebuchten Position auf eine Abrechnung, die keine Stunden kennt. Gefragt wird
 * die vorhandene Summenauskunft {@link ZeiteintragRepository#angefallenJePosition(Set)} — sie nennt
 * jede angefragte Position, auch die ohne Eintrag mit {@code 0.00}, und genau diese fallen hier
 * weg.
 *
 * <p>Die leere Anfrage ist der zweite Gegenstand: Ein Angebot, dessen Positionen alle neu sind, hat
 * keine Kennung zu fragen — und dann wird der Bestand gar nicht bemueht.
 */
@ExtendWith(MockitoExtension.class)
class AngebotZeitbindungTest {

  @Mock private ZeiteintragRepository zeiten;

  @Test
  void mitZeit_thenNamesOnlyThePositionenWithHoursAboveZero() {
    // Given — der Bestand antwortet auf jede angefragte Kennung; die Wartung hat keinen Eintrag.
    final Set<Long> angefragt =
        Set.of(
            Long.valueOf(Zeitdoppel.KONZEPTION_ID),
            Long.valueOf(Zeitdoppel.WARTUNG_ID),
            Long.valueOf(Zeitdoppel.SCHULUNG_ID));
    when(zeiten.angefallenJePosition(angefragt))
        .thenReturn(
            Map.of(
                Long.valueOf(Zeitdoppel.KONZEPTION_ID),
                new BigDecimal("12.00"),
                Long.valueOf(Zeitdoppel.WARTUNG_ID),
                new BigDecimal("0.00"),
                Long.valueOf(Zeitdoppel.SCHULUNG_ID),
                new BigDecimal("0.25")));

    // When
    final Set<Long> mitZeit = new AngebotZeitbindung(zeiten).mitZeit(angefragt);

    // Then — die Position mit 0.00 fehlt, die mit einer Viertelstunde ist dabei.
    assertThat(mitZeit)
        .containsExactlyInAnyOrder(
            Long.valueOf(Zeitdoppel.KONZEPTION_ID), Long.valueOf(Zeitdoppel.SCHULUNG_ID));
  }

  @Test
  void mitZeit_givenNoPositionen_thenEmptyWithoutAskingTheBestand() {
    // Given / When — nichts zu fragen: Alle Positionen der Einreichung sind neu.
    final Set<Long> mitZeit = new AngebotZeitbindung(zeiten).mitZeit(Set.of());

    // Then
    assertThat(mitZeit).isEmpty();
    verifyNoInteractions(zeiten);
  }
}
