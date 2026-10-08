package org.mwolff.fbcrm.angebot.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Der Kommentar am Angebot als Fachobjekt.
 *
 * <p>Gegenstand ist die eine Regel, die er traegt: {@code geaendert} wechselt Text und
 * Aenderungszeitpunkt und laesst alles andere stehen. Was er <b>nicht</b> tut, steht hier ebenso:
 * Er bereinigt den Text nicht — das ist Sache des Anwendungsfalls (Plan E7).
 */
class AngebotskommentarTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-28T08:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-09-30T14:05:00Z");
  private static final Instant SPAETER = Instant.parse("2026-10-01T09:15:00Z");

  private static Angebotskommentar kommentar() {
    return new Angebotskommentar(7L, 11L, "Kunde ruft zurueck.", ANGELEGT, GEAENDERT);
  }

  @Test
  void geaendert_thenCarriesTheNewTextAndTheNewChangeTime() {
    // When
    final Angebotskommentar geaendert = kommentar().geaendert("Kunde hat zugesagt.", SPAETER);

    // Then
    assertThat(geaendert)
        .satisfies(
            k -> assertThat(k.text()).isEqualTo("Kunde hat zugesagt."),
            k -> assertThat(k.updatedAt()).isEqualTo(SPAETER));
  }

  @Test
  void geaendert_thenLeavesIdOfferAndCreationTimeUntouched() {
    // When
    final Angebotskommentar geaendert = kommentar().geaendert("Kunde hat zugesagt.", SPAETER);

    // Then
    assertThat(geaendert)
        .satisfies(
            k -> assertThat(k.id()).isEqualTo(7L),
            k -> assertThat(k.angebotId()).isEqualTo(11L),
            k -> assertThat(k.createdAt()).isEqualTo(ANGELEGT));
  }

  @Test
  void geaendert_thenReturnsANewInstanceAndLeavesTheOriginalStanding() {
    // Given
    final Angebotskommentar ausgangspunkt = kommentar();

    // When
    ausgangspunkt.geaendert("Kunde hat zugesagt.", SPAETER);

    // Then
    assertThat(ausgangspunkt.text()).isEqualTo("Kunde ruft zurueck.");
  }

  @Test
  void geaendert_givenTextWithSurroundingWhitespace_thenKeepsItAsHandedIn() {
    // When — bereinigt wird im Anwendungsfall, nicht hier (Plan E7).
    final Angebotskommentar geaendert = kommentar().geaendert("  noch offen  ", SPAETER);

    // Then
    assertThat(geaendert.text()).isEqualTo("  noch offen  ");
  }

  @Test
  void id_givenAnUnsavedComment_thenNull() {
    // Given
    final Angebotskommentar frisch =
        new Angebotskommentar(null, 11L, "Kunde ruft zurueck.", ANGELEGT, ANGELEGT);

    // When / Then
    assertThat(frisch.id()).isNull();
  }
}
