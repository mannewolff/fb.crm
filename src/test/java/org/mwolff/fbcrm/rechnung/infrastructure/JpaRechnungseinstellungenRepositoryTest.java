package org.mwolff.fbcrm.rechnung.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.rechnung.domain.Nummernmuster;
import org.mwolff.fbcrm.rechnung.domain.Rechnungseinstellungen;

/**
 * Die Uebersetzung zwischen Einstellungen und Zeile — in beide Richtungen.
 *
 * <p>Der Adapter steht hier gegen ein gemocktes Spring-Data-Repository, damit die Abbildung selbst
 * geprueft ist und nicht nur ihr Zusammenspiel mit der Datenbank.
 */
@ExtendWith(MockitoExtension.class)
class JpaRechnungseinstellungenRepositoryTest {

  private static final Instant GEAENDERT = Instant.parse("2026-09-30T12:00:00Z");

  @Mock private SpringDataRechnungseinstellungenRepository jpa;

  @Captor private ArgumentCaptor<RechnungseinstellungenEntity> gespeicherte;

  @InjectMocks private JpaRechnungseinstellungenRepository repository;

  private static Rechnungseinstellungen einstellungen() {
    return new Rechnungseinstellungen(
        new Nummernmuster("R{JJ}-{NNNN}"), 4, new BigDecimal("19.50"), 14);
  }

  @Test
  void lies_thenTranslatesTheRow() {
    // Given
    when(jpa.findById(RechnungseinstellungenEntity.ZEILE))
        .thenReturn(
            Optional.of(
                new RechnungseinstellungenEntity(
                    "R{JJ}-{NNNN}", 4, new BigDecimal("19.50"), 14, GEAENDERT)));

    // When
    final Rechnungseinstellungen gelesen = repository.lies();

    // Then
    assertThat(gelesen).isEqualTo(einstellungen());
  }

  @Test
  void lies_givenTheFreshRow_thenDeliversTheDefaults() {
    // Given — nach der Migration steht die eine Zeile mit den Vorbelegungen da.
    when(jpa.findById(RechnungseinstellungenEntity.ZEILE))
        .thenReturn(
            Optional.of(
                new RechnungseinstellungenEntity(
                    "{NNNN}-{JJJJ}", 1, new BigDecimal("19.00"), 10, GEAENDERT)));

    // When
    final Rechnungseinstellungen gelesen = repository.lies();

    // Then
    assertThat(gelesen)
        .isEqualTo(
            new Rechnungseinstellungen(
                new Nummernmuster("{NNNN}-{JJJJ}"), 1, new BigDecimal("19.00"), 10));
  }

  @Test
  void speichere_thenWritesEveryFieldIntoTheRow() {
    // When
    repository.speichere(einstellungen(), GEAENDERT);

    // Then
    verify(jpa).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getId()).isEqualTo(RechnungseinstellungenEntity.ZEILE),
            zeile -> assertThat(zeile.getNummerMuster()).isEqualTo("R{JJ}-{NNNN}"),
            zeile -> assertThat(zeile.getNaechsteNummer()).isEqualTo(4),
            zeile -> assertThat(zeile.getSteuersatz()).isEqualByComparingTo("19.50"),
            zeile -> assertThat(zeile.getZahlungszielTage()).isEqualTo(14),
            zeile -> assertThat(zeile.getUpdatedAt()).isEqualTo(GEAENDERT));
  }
}
