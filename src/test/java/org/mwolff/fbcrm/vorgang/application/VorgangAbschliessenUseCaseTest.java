package org.mwolff.fbcrm.vorgang.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;

/**
 * Abschliessen und Wiedereroeffnen eines Vorgangs (Kriterien 20, 21).
 *
 * <p>Beide Richtungen in einer Klasse, weil es dieselbe Umschaltung ist — dasselbe Muster wie
 * {@code FirmaStilllegenUseCase}. Einen Grund verlangt die Anwendung nicht, und geloescht wird
 * nichts: Der Abschluss ist ein Schalter (E5).
 */
class VorgangAbschliessenUseCaseTest {

  private static final long FIRMA = 7L;
  private static final long PARTNER = 3L;
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant JETZT = Instant.parse("2026-09-25T09:30:00Z");

  private final Ports.Vorgaenge vorgaenge = new Ports.Vorgaenge();

  private VorgangAbschliessenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new VorgangAbschliessenUseCase(vorgaenge, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private long vorhanden(final boolean abgeschlossen) {
    return vorgaenge
        .save(
            new Vorgang(
                null,
                12L,
                "Website-Relaunch",
                FIRMA,
                Long.valueOf(PARTNER),
                abgeschlossen,
                ANGELEGT,
                ANGELEGT))
        .requireId();
  }

  private Vorgang gespeicherter(final long id) {
    return vorgaenge.findById(id).orElseThrow();
  }

  @Test
  void abschliessen_thenTheVorgangIsClosed() {
    // Given — Kriterium 20.
    final long id = vorhanden(false);

    // When
    useCase.abschliessen(id);

    // Then
    assertThat(gespeicherter(id).abgeschlossen()).isTrue();
  }

  @Test
  void abschliessen_thenTakesTheTimeFromTheClock() {
    // Given
    final long id = vorhanden(false);

    // When
    useCase.abschliessen(id);

    // Then
    assertThat(gespeicherter(id).updatedAt()).isEqualTo(JETZT);
  }

  @Test
  void abschliessen_thenKeepsNumberTitleAndAssignment() {
    // Given — Kriterium 21: ein abgeschlossener Vorgang behaelt seine Zuordnung.
    final long id = vorhanden(false);

    // When
    useCase.abschliessen(id);

    // Then
    assertThat(gespeicherter(id))
        .extracting(Vorgang::nummer, Vorgang::titel, Vorgang::firmaId, Vorgang::ansprechpartnerId)
        .containsExactly(12L, "Website-Relaunch", FIRMA, Long.valueOf(PARTNER));
  }

  @Test
  void abschliessen_givenAnUnknownId_thenThrowsVorgangNichtGefunden() {
    // When / Then
    assertThatThrownBy(() -> useCase.abschliessen(4711L)).isInstanceOf(VorgangNichtGefunden.class);
  }

  @Test
  void wiederEroeffnen_thenTheVorgangIsOpenAgain() {
    // Given — Kriterium 20: der Weg zurueck.
    final long id = vorhanden(true);

    // When
    useCase.wiederEroeffnen(id);

    // Then
    assertThat(gespeicherter(id).abgeschlossen()).isFalse();
  }

  @Test
  void wiederEroeffnen_thenTakesTheTimeFromTheClock() {
    // Given
    final long id = vorhanden(true);

    // When
    useCase.wiederEroeffnen(id);

    // Then
    assertThat(gespeicherter(id).updatedAt()).isEqualTo(JETZT);
  }

  @Test
  void wiederEroeffnen_givenAnUnknownId_thenThrowsVorgangNichtGefunden() {
    // When / Then
    assertThatThrownBy(() -> useCase.wiederEroeffnen(4711L))
        .isInstanceOf(VorgangNichtGefunden.class);
  }
}
