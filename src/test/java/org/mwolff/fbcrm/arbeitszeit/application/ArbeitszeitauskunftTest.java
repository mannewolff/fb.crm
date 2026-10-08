package org.mwolff.fbcrm.arbeitszeit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.arbeitszeit.domain.ZeiteintragRepository;

/**
 * Was die Zeiterfassung anderen Modulen ueber ein Angebot sagt (Plan #194, A1; Issue #193,
 * Kriterium 9).
 *
 * <p>Gegenstand sind zwei Zusagen. Erstens wird mit <b>allen</b> Positionen des Angebots in einem
 * Zug gefragt, auch mit der nicht buchbaren: Welche Position Stunden tragen darf, entscheidet der
 * Leser an {@link Buchbarkeit} und nicht diese Auskunft — eine zweite Abschrift derselben Regel
 * liefe beim ersten Nachziehen auseinander. Zweitens wird die Antwort des Bestands unveraendert
 * weitergegeben; die Auskunft rechnet nichts dazu.
 *
 * <p>Beides gilt fuer beide Auskuenfte — die Stunden eines Monats fuer den Rechnungsentwurf
 * (Kriterium 9) und die insgesamt angefallenen fuer die Spalte „Angefallen" am Angebot (Kriterium
 * 7). Sie unterscheiden sich nur im Zeitraum, und jede fragt ihren eigenen Weg im Bestand.
 *
 * <p>Dazu die eine Abweisung: Ein Angebot, das es nicht gibt, fragt im Bestand der Zeiten gar nicht
 * nach.
 *
 * <p>Die Auskuenfte <b>ueber alle Angebote</b> (Issue #211) stehen daneben: Sie reichen die Antwort
 * des Bestands durch und ziehen die Angebote gar nicht erst heran — es gibt keine Positionsmenge
 * zusammenzutragen.
 */
@ExtendWith(MockitoExtension.class)
class ArbeitszeitauskunftTest {

  /** Der Monat des Beispiels aus #193. */
  private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);

  /** Alle drei Positionen des Angebots — die beiden buchbaren und die Pauschale. */
  private static final Set<Long> ALLE_POSITIONEN =
      Set.of(Zeitdoppel.KONZEPTION_ID, Zeitdoppel.WARTUNG_ID, Zeitdoppel.SCHULUNG_ID);

  @Mock private ZeiteintragRepository zeiten;
  @Mock private AngebotRepository angebote;

  private Arbeitszeitauskunft auskunft() {
    return new Arbeitszeitauskunft(zeiten, angebote);
  }

  @Test
  void imMonat_thenAsksForEveryPositionOfTheAngebotAtOnceAndAnswersWhatTheBestandSays() {
    // Given — gefragt wird mit allen Kennungen, nicht nur mit denen der buchbaren Positionen.
    when(angebote.findById(Zeitdoppel.ANGEBOT)).thenReturn(Optional.of(Zeitdoppel.angebot()));
    final Map<Long, BigDecimal> gemeldet =
        Map.of(
            Long.valueOf(Zeitdoppel.KONZEPTION_ID),
            new BigDecimal("12.00"),
            Long.valueOf(Zeitdoppel.WARTUNG_ID),
            new BigDecimal("0.00"),
            Long.valueOf(Zeitdoppel.SCHULUNG_ID),
            new BigDecimal("0.00"));
    when(zeiten.stundenJePositionImMonat(ALLE_POSITIONEN, NOVEMBER)).thenReturn(gemeldet);

    // When
    final Map<Long, BigDecimal> auskunft = auskunft().imMonat(Zeitdoppel.ANGEBOT, NOVEMBER);

    // Then
    assertThat(auskunft).isEqualTo(gemeldet);
  }

  @Test
  void angefallen_thenAsksForEveryPositionOfTheAngebotAtOnceAndAnswersWhatTheBestandSays() {
    // Given — ueber alle Monate und mit allen Kennungen, auch der nicht buchbaren.
    when(angebote.findById(Zeitdoppel.ANGEBOT)).thenReturn(Optional.of(Zeitdoppel.angebot()));
    final Map<Long, BigDecimal> gemeldet =
        Map.of(
            Long.valueOf(Zeitdoppel.KONZEPTION_ID),
            new BigDecimal("22.00"),
            Long.valueOf(Zeitdoppel.WARTUNG_ID),
            new BigDecimal("0.00"),
            Long.valueOf(Zeitdoppel.SCHULUNG_ID),
            new BigDecimal("0.00"));
    when(zeiten.angefallenJePosition(ALLE_POSITIONEN)).thenReturn(gemeldet);

    // When
    final Map<Long, BigDecimal> auskunft = auskunft().angefallen(Zeitdoppel.ANGEBOT);

    // Then
    assertThat(auskunft).isEqualTo(gemeldet);
  }

  @Test
  void angefallen_withAnUnknownAngebot_thenTheBestandOfTheZeitenIsNotAsked() {
    // Given — dieselbe Abweisung wie bei den Stunden eines Monats.
    when(angebote.findById(Zeitdoppel.ANGEBOT)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> auskunft().angefallen(Zeitdoppel.ANGEBOT))
        .isInstanceOf(AngebotNichtGefunden.class);
    verifyNoInteractions(zeiten);
  }

  @Test
  void imMonat_withAnUnknownAngebot_thenTheBestandOfTheZeitenIsNotAsked() {
    // Given — ohne das Angebot ist nicht bekannt, nach welchen Positionen zu fragen waere.
    when(angebote.findById(Zeitdoppel.ANGEBOT)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> auskunft().imMonat(Zeitdoppel.ANGEBOT, NOVEMBER))
        .isInstanceOf(AngebotNichtGefunden.class);
    verifyNoInteractions(zeiten);
  }

  @Test
  void alleAngefallen_thenAnswersWhatTheBestandSaysWithoutAskingForAnAngebot() {
    // Given — ueber alle Angebote: Die Kennung eines Angebots kommt hier nicht vor.
    final Map<Long, BigDecimal> gemeldet =
        Map.of(Long.valueOf(Zeitdoppel.KONZEPTION_ID), new BigDecimal("22.00"));
    when(zeiten.alleAngefallenJePosition()).thenReturn(gemeldet);

    // When
    final Map<Long, BigDecimal> auskunft = auskunft().alleAngefallen();

    // Then
    assertThat(auskunft).isEqualTo(gemeldet);
    verifyNoInteractions(angebote);
  }

  @Test
  void alleImZeitraum_thenAnswersWhatTheBestandSaysWithoutAskingForAnAngebot() {
    // Given — derselbe Weg, nur auf einen Zeitraum begrenzt.
    final Map<Long, BigDecimal> gemeldet =
        Map.of(Long.valueOf(Zeitdoppel.KONZEPTION_ID), new BigDecimal("12.00"));
    when(zeiten.alleStundenJePositionImZeitraum(NOVEMBER.atDay(1), NOVEMBER.atEndOfMonth()))
        .thenReturn(gemeldet);

    // When
    final Map<Long, BigDecimal> auskunft =
        auskunft().alleImZeitraum(NOVEMBER.atDay(1), NOVEMBER.atEndOfMonth());

    // Then
    assertThat(auskunft).isEqualTo(gemeldet);
    verifyNoInteractions(angebote);
  }

  @Test
  void monateMitEintragImZeitraum_thenAnswersWhatTheBestandSaysIncludingBothBounds() {
    // Given — Eintraege genau auf dem ersten und dem letzten Tag des Jahres.
    final LocalDate von = LocalDate.of(2026, 1, 1);
    final LocalDate bis = LocalDate.of(2026, 12, 31);
    final Set<YearMonth> gemeldet = Set.of(YearMonth.from(von), NOVEMBER, YearMonth.from(bis));
    when(zeiten.monateMitEintragImZeitraum(von, bis)).thenReturn(gemeldet);

    // When
    final Set<YearMonth> auskunft = auskunft().monateMitEintragImZeitraum(von, bis);

    // Then
    assertThat(auskunft).isEqualTo(gemeldet);
    verifyNoInteractions(angebote);
  }
}
