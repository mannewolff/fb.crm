package org.mwolff.fbcrm.rechnung.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import org.mwolff.fbcrm.rechnung.domain.Zeilenstand;

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
        new Nummernmuster("R{JJ}-{NNNN}"), new BigDecimal("19.50"), 14);
  }

  @Test
  void lies_thenTranslatesTheRow() {
    // Given
    when(jpa.findById(RechnungseinstellungenEntity.ZEILE))
        .thenReturn(
            Optional.of(
                new RechnungseinstellungenEntity(
                    "R{JJ}-{NNNN}", new BigDecimal("19.50"), 14, GEAENDERT, 4L, 30L)));

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
                    "{NNNN}-{JJJJ}", new BigDecimal("19.00"), 10, GEAENDERT, 0L, null)));

    // When
    final Rechnungseinstellungen gelesen = repository.lies();

    // Then
    assertThat(gelesen)
        .isEqualTo(
            new Rechnungseinstellungen(
                new Nummernmuster("{NNNN}-{JJJJ}"), new BigDecimal("19.00"), 10));
  }

  @Test
  void stand_thenTranslatesVersionUrheberAndTime() {
    // Given
    when(jpa.findById(RechnungseinstellungenEntity.ZEILE))
        .thenReturn(
            Optional.of(
                new RechnungseinstellungenEntity(
                    "R{JJ}-{NNNN}", new BigDecimal("19.50"), 14, GEAENDERT, 4L, 30L)));

    // When / Then — Plan #415, E1, E14.
    assertThat(repository.stand()).isEqualTo(new Zeilenstand(4L, 30L, GEAENDERT));
  }

  @Test
  void speichere_thenWritesEveryFieldIntoTheRow() {
    // Given — das Schreiben erhoeht die Version; so liefert sie die Zeile danach.
    when(jpa.saveAndFlush(any()))
        .thenReturn(
            new RechnungseinstellungenEntity(
                "R{JJ}-{NNNN}", new BigDecimal("19.50"), 14, GEAENDERT, 5L, 31L));

    // When
    final Zeilenstand neu = repository.speichere(einstellungen(), GEAENDERT, 4L, 31L);

    // Then — sofort geschrieben, mit dem vorgelegten Stand, damit die Sperre greift (E1).
    assertThat(neu).isEqualTo(new Zeilenstand(5L, 31L, GEAENDERT));
    verify(jpa).saveAndFlush(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getId()).isEqualTo(RechnungseinstellungenEntity.ZEILE),
            zeile -> assertThat(zeile.getNummerMuster()).isEqualTo("R{JJ}-{NNNN}"),
            zeile -> assertThat(zeile.getSteuersatz()).isEqualByComparingTo("19.50"),
            zeile -> assertThat(zeile.getZahlungszielTage()).isEqualTo(14),
            zeile -> assertThat(zeile.getUpdatedAt()).isEqualTo(GEAENDERT),
            zeile -> assertThat(zeile.getVersion()).isEqualTo(4L),
            zeile -> assertThat(zeile.getGeaendertVon()).isEqualTo(31L));
  }
}
