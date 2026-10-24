package org.mwolff.fbcrm.arbeitszeit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

  /** Die zweite Person neben {@link Zeitdoppel#KONTO} (Plan #426, E15). */
  private static final long BEN = 22L;

  /** Anna (das Konto der Doppel) 10 Stunden, Ben 6 Stunden, beide auf die Konzeption. */
  private static Zeitbestand zweiPersonen() {
    final Zeitbestand bestand = new Zeitbestand();
    bestand.save(
        Zeitdoppel.zeiteintrag(1L, Zeitdoppel.KONTO, Zeitdoppel.KONZEPTION_ID, "08:00", "18:00"));
    bestand.save(Zeitdoppel.zeiteintrag(2L, BEN, Zeitdoppel.KONZEPTION_ID, "09:00", "15:00"));
    return bestand;
  }

  @Test
  void angefallen_withEntriesOfTwoPersonen_thenThePositionCarriesTheSumOfBoth() {
    // Given — Kriterium 6 aus #410: „angefallen" zaehlt die Stunden aller Personen.
    when(angebote.findById(Zeitdoppel.ANGEBOT)).thenReturn(Optional.of(Zeitdoppel.angebot()));

    // When
    final Map<Long, BigDecimal> auskunft =
        new Arbeitszeitauskunft(zweiPersonen(), angebote).angefallen(Zeitdoppel.ANGEBOT);

    // Then
    assertThat(auskunft.get(Long.valueOf(Zeitdoppel.KONZEPTION_ID))).isEqualByComparingTo("16");
  }

  @Test
  void imMonat_withEntriesOfTwoPersonen_thenThePositionCarriesTheSumOfBoth() {
    // Given — Kriterium 7 aus #410: der Vorschlag eines Monats ist die Summe aller Personen.
    when(angebote.findById(Zeitdoppel.ANGEBOT)).thenReturn(Optional.of(Zeitdoppel.angebot()));

    // When
    final Map<Long, BigDecimal> auskunft =
        new Arbeitszeitauskunft(zweiPersonen(), angebote).imMonat(Zeitdoppel.ANGEBOT, NOVEMBER);

    // Then
    assertThat(auskunft.get(Long.valueOf(Zeitdoppel.KONZEPTION_ID))).isEqualByComparingTo("16");
  }

  @Test
  void auskunft_thenNoMethodTakesAPerson() {
    // Given — E15: Wer ueber alle Personen rechnet, bekommt keinen Parameter fuer eine. Jede
    // oeffentliche Frage nimmt genau die Parameter, die sie vor Plan #426 nahm.
    final Map<String, List<Class<?>>> erwartet =
        Map.of(
            "imMonat", List.of(long.class, YearMonth.class),
            "angefallen", List.of(long.class),
            "alleAngefallen", List.of(),
            "alleImZeitraum", List.of(LocalDate.class, LocalDate.class),
            "monateMitEintragImZeitraum", List.of(LocalDate.class, LocalDate.class));

    // When
    final Map<String, List<Class<?>>> tatsaechlich =
        Stream.of(Arbeitszeitauskunft.class.getDeclaredMethods())
            .filter(methode -> Modifier.isPublic(methode.getModifiers()))
            .collect(
                Collectors.toMap(Method::getName, methode -> List.of(methode.getParameterTypes())));

    // Then
    assertThat(tatsaechlich).isEqualTo(erwartet);
  }
}
