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
  private static final long VERFASSER = 3L;
  private static final long BEN = 4L;

  private static Angebotskommentar kommentar() {
    return new Angebotskommentar(7L, 11L, VERFASSER, "Kunde ruft zurueck.", ANGELEGT, GEAENDERT);
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
  void geaendert_thenLeavesIdOfferVerfasserAndCreationTimeUntouched() {
    // When
    final Angebotskommentar geaendert = kommentar().geaendert("Kunde hat zugesagt.", SPAETER);

    // Then — der Kommentar gehoert weiter seinem Verfasser, gleich wer ihn aendert (A7).
    assertThat(geaendert)
        .satisfies(
            k -> assertThat(k.id()).isEqualTo(7L),
            k -> assertThat(k.angebotId()).isEqualTo(11L),
            k -> assertThat(k.verfasserId()).isEqualTo(VERFASSER),
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
        new Angebotskommentar(null, 11L, VERFASSER, "Kunde ruft zurueck.", ANGELEGT, ANGELEGT);

    // When / Then
    assertThat(frisch.id()).isNull();
  }

  @Test
  void neu_thenStartsAtVersionZeroWithoutUrheberAndNotDeleted() {
    // Given — ein neuer Kommentar hat noch keinen Stand und kein Kennzeichen (Plan #415, E8).
    final Angebotskommentar frisch =
        new Angebotskommentar(null, 11L, VERFASSER, "Kunde ruft zurueck.", ANGELEGT, ANGELEGT);

    // When / Then
    assertThat(frisch)
        .satisfies(
            k -> assertThat(k.version()).isZero(),
            k -> assertThat(k.geaendertVon()).isNull(),
            k -> assertThat(k.geloeschtAm()).isNull(),
            k -> assertThat(k.geloeschtVon()).isNull());
  }

  @Test
  void durch_thenSetsTheUrheberAndKeepsEverythingElse() {
    // Given
    final Angebotskommentar ausgangspunkt =
        new Angebotskommentar(
            7L, 11L, VERFASSER, "Kunde ruft zurueck.", ANGELEGT, GEAENDERT, 5L, null, null, null);

    // When — E14: wer aendert, steht danach als Urheber am Kommentar.
    final Angebotskommentar geaendert = ausgangspunkt.geaendert("Neu", SPAETER).durch(BEN);

    // Then — die Version zaehlt der Bestand, nicht das Fachobjekt.
    assertThat(geaendert)
        .isEqualTo(
            new Angebotskommentar(
                7L, 11L, VERFASSER, "Neu", ANGELEGT, SPAETER, 5L, BEN, null, null));
  }

  @Test
  void geloescht_thenCarriesTheKennzeichenAndKeepsTheRest() {
    // Given — E17: geloescht wird mit Kennzeichen, die Zeile bleibt.
    final Angebotskommentar ausgangspunkt =
        new Angebotskommentar(
            7L,
            11L,
            VERFASSER,
            "Kunde ruft zurueck.",
            ANGELEGT,
            GEAENDERT,
            2L,
            VERFASSER,
            null,
            null);

    // When
    final Angebotskommentar geloescht = ausgangspunkt.geloescht(BEN, SPAETER);

    // Then
    assertThat(geloescht)
        .isEqualTo(
            new Angebotskommentar(
                7L,
                11L,
                VERFASSER,
                "Kunde ruft zurueck.",
                ANGELEGT,
                GEAENDERT,
                2L,
                VERFASSER,
                SPAETER,
                BEN));
    assertThat(ausgangspunkt.geloeschtAm()).isNull();
  }

  @Test
  void geaendert_thenKeepsVersionUrheberAndKennzeichen() {
    // Given
    final Angebotskommentar ausgangspunkt =
        new Angebotskommentar(
            7L, 11L, VERFASSER, "Alt", ANGELEGT, GEAENDERT, 4L, BEN, SPAETER, BEN);

    // When
    final Angebotskommentar geaendert = ausgangspunkt.geaendert("Neu", SPAETER);

    // Then
    assertThat(geaendert)
        .satisfies(
            k -> assertThat(k.version()).isEqualTo(4L),
            k -> assertThat(k.geaendertVon()).isEqualTo(BEN),
            k -> assertThat(k.geloeschtAm()).isEqualTo(SPAETER),
            k -> assertThat(k.geloeschtVon()).isEqualTo(BEN));
  }
}
