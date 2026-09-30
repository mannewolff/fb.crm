package org.mwolff.fbcrm.angebot.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Die Anlage am Angebot als Fachobjekt.
 *
 * <p>Sie traegt keine Regel: Der Dateiname ist bereits gesaeubert ({@link Dateiname}), die
 * Vorschauart bereits am Inhalt erkannt ({@link Vorschauart}), und die Groesse hat der
 * Anwendungsfall geprueft. Geprueft wird darum, was der Record zusagt — die Felder und der
 * Uebergang von der ungespeicherten zur gespeicherten Anlage.
 */
class AngebotsanlageTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-30T08:00:00Z");
  private static final String SCHLUESSEL = "angebot/11/anlage/3f2c";

  private static Angebotsanlage anlage() {
    return new Angebotsanlage(7L, 11L, "Bericht.pdf", 1024L, Vorschauart.PDF, SCHLUESSEL, ANGELEGT);
  }

  @Test
  void anlage_thenCarriesEveryFieldAsHandedIn() {
    // When
    final Angebotsanlage gespeichert = anlage();

    // Then
    assertThat(gespeichert)
        .satisfies(
            a -> assertThat(a.id()).isEqualTo(7L),
            a -> assertThat(a.angebotId()).isEqualTo(11L),
            a -> assertThat(a.dateiName()).isEqualTo("Bericht.pdf"),
            a -> assertThat(a.groesse()).isEqualTo(1024L),
            a -> assertThat(a.vorschauArt()).isEqualTo(Vorschauart.PDF),
            a -> assertThat(a.objektSchluessel()).isEqualTo(SCHLUESSEL),
            a -> assertThat(a.createdAt()).isEqualTo(ANGELEGT));
  }

  @Test
  void requireId_givenASavedAttachment_thenTheId() {
    // When / Then
    assertThat(anlage().requireId()).isEqualTo(7L);
  }

  @Test
  void id_givenAnUnsavedAttachment_thenNull() {
    // Given
    final Angebotsanlage frisch =
        new Angebotsanlage(null, 11L, "Bericht.pdf", 1024L, Vorschauart.PDF, SCHLUESSEL, ANGELEGT);

    // When / Then
    assertThat(frisch.id()).isNull();
  }

  @Test
  void requireId_givenAnUnsavedAttachment_thenThrows() {
    // Given
    final Angebotsanlage frisch =
        new Angebotsanlage(null, 11L, "Bericht.pdf", 1024L, Vorschauart.PDF, SCHLUESSEL, ANGELEGT);

    // When / Then
    assertThatThrownBy(frisch::requireId)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Angebotsanlage");
  }

  @Test
  void vorschauArt_givenAFileThatIsNeitherImageNorPdf_thenNull() {
    // Given — eine Tabelle: keine Vorschau, aber eine gueltige Anlage (Plan #150, E2).
    final Angebotsanlage tabelle =
        new Angebotsanlage(7L, 11L, "Zahlen.xlsx", 4096L, null, SCHLUESSEL, ANGELEGT);

    // When / Then
    assertThat(tabelle.vorschauArt()).isNull();
  }
}
