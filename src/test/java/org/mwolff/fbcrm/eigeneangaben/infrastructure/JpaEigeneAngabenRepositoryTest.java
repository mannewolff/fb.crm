package org.mwolff.fbcrm.eigeneangaben.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;

/**
 * Die Uebersetzung zwischen Selbstauskunft und Zeile — in beide Richtungen.
 *
 * <p>Der Adapter steht hier gegen ein gemocktes Spring-Data-Repository, damit die Abbildung selbst
 * geprueft ist und nicht nur ihr Zusammenspiel mit der Datenbank ({@code
 * EigeneAngabenPersistenceIT}).
 */
@ExtendWith(MockitoExtension.class)
class JpaEigeneAngabenRepositoryTest {

  private static final Instant GEAENDERT = Instant.parse("2026-09-27T12:00:00Z");

  @Mock private SpringDataEigeneAngabenRepository jpa;

  @Captor private ArgumentCaptor<EigeneAngabenEntity> gespeicherte;

  @InjectMocks private JpaEigeneAngabenRepository repository;

  private static EigeneAngabenEntity zeile() {
    return new EigeneAngabenEntity(
        "Manfred Wolff",
        "Am Wall 1",
        "28195",
        "Bremen",
        "Deutschland",
        "manne@example.org",
        "0421 1234",
        "75/123/45678",
        "DE123456789",
        "DE02120300000000202051",
        "Zahlbar innerhalb von 14 Tagen ohne Abzug.",
        GEAENDERT);
  }

  private static EigeneAngaben angaben() {
    return new EigeneAngaben(
        "Manfred Wolff",
        new Anschrift("Am Wall 1", "28195", "Bremen", "Deutschland"),
        "manne@example.org",
        "0421 1234",
        "75/123/45678",
        "DE123456789",
        "DE02120300000000202051",
        "Zahlbar innerhalb von 14 Tagen ohne Abzug.");
  }

  private static EigeneAngaben leer() {
    return new EigeneAngaben(
        null, new Anschrift(null, null, null, null), null, null, null, null, null, null);
  }

  @Test
  void lies_thenTranslatesTheRow() {
    // Given
    when(jpa.findById(EigeneAngabenEntity.ZEILE)).thenReturn(Optional.of(zeile()));

    // When
    final EigeneAngaben gelesen = repository.lies();

    // Then
    assertThat(gelesen).isEqualTo(angaben());
  }

  @Test
  void lies_givenTheFreshRow_thenEveryValueIsAbsent() {
    // Given — nach der Migration steht die eine Zeile mit lauter NULL da.
    final EigeneAngabenEntity frisch =
        new EigeneAngabenEntity(
            null, null, null, null, null, null, null, null, null, null, null, GEAENDERT);
    when(jpa.findById(EigeneAngabenEntity.ZEILE)).thenReturn(Optional.of(frisch));

    // When
    final EigeneAngaben gelesen = repository.lies();

    // Then
    assertThat(gelesen).isEqualTo(leer());
  }

  @Test
  void speichere_thenWritesEveryFieldIntoTheRow() {
    // When
    repository.speichere(angaben(), GEAENDERT);

    // Then
    verify(jpa).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getId()).isEqualTo(EigeneAngabenEntity.ZEILE),
            zeile -> assertThat(zeile.getName()).isEqualTo("Manfred Wolff"),
            zeile -> assertThat(zeile.getStrasse()).isEqualTo("Am Wall 1"),
            zeile -> assertThat(zeile.getPlz()).isEqualTo("28195"),
            zeile -> assertThat(zeile.getOrt()).isEqualTo("Bremen"),
            zeile -> assertThat(zeile.getLand()).isEqualTo("Deutschland"),
            zeile -> assertThat(zeile.getEmail()).isEqualTo("manne@example.org"),
            zeile -> assertThat(zeile.getTelefon()).isEqualTo("0421 1234"),
            zeile -> assertThat(zeile.getSteuernummer()).isEqualTo("75/123/45678"),
            zeile -> assertThat(zeile.getUmsatzsteuerId()).isEqualTo("DE123456789"),
            zeile -> assertThat(zeile.getBankverbindung()).isEqualTo("DE02120300000000202051"),
            zeile ->
                assertThat(zeile.getZahlungsbedingungen())
                    .isEqualTo("Zahlbar innerhalb von 14 Tagen ohne Abzug."),
            zeile -> assertThat(zeile.getUpdatedAt()).isEqualTo(GEAENDERT));
  }

  @Test
  void speichere_givenAbsentValues_thenWritesThemAsAbsent() {
    // When — E9: „nicht angegeben" geht als NULL in die Zeile, nicht als Leerstring.
    repository.speichere(leer(), GEAENDERT);

    // Then
    verify(jpa).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getName()).isNull(),
            zeile -> assertThat(zeile.getStrasse()).isNull(),
            zeile -> assertThat(zeile.getPlz()).isNull(),
            zeile -> assertThat(zeile.getOrt()).isNull(),
            zeile -> assertThat(zeile.getLand()).isNull(),
            zeile -> assertThat(zeile.getEmail()).isNull(),
            zeile -> assertThat(zeile.getTelefon()).isNull(),
            zeile -> assertThat(zeile.getSteuernummer()).isNull(),
            zeile -> assertThat(zeile.getUmsatzsteuerId()).isNull(),
            zeile -> assertThat(zeile.getBankverbindung()).isNull(),
            zeile -> assertThat(zeile.getZahlungsbedingungen()).isNull());
  }
}
