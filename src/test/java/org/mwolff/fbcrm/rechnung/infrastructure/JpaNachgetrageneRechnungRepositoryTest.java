package org.mwolff.fbcrm.rechnung.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnung;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;

/**
 * Die Uebersetzung zwischen nachgetragener Rechnung und Zeile — in beide Richtungen (Plan #259).
 *
 * <p>Der Adapter steht hier gegen ein gemocktes Spring-Data-Repository, damit die Abbildung und das
 * Durchreichen selbst geprueft sind. Ob die beiden Nummernfragen die Schreibweise wirklich
 * uebergehen, kann ein Mock nicht zeigen — das belegt {@code NachgetrageneRechnungPersistenceIT}
 * gegen Postgres.
 */
@ExtendWith(MockitoExtension.class)
class JpaNachgetrageneRechnungRepositoryTest {

  private static final long ID = 21L;
  private static final LocalDate RECHNUNGSDATUM = LocalDate.of(2026, 2, 15);
  private static final Instant ANGELEGT = Instant.parse("2026-10-06T08:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-10-07T09:15:00Z");

  private static final NachgetrageneRechnung RECHNUNG =
      new NachgetrageneRechnung(
          ID,
          4L,
          "RE-9",
          RECHNUNGSDATUM,
          new BigDecimal("1000.00"),
          new BigDecimal("1190.00"),
          Rechnungszustand.ABGESCHRIEBEN,
          "nachgetragen/21/original.pdf",
          ANGELEGT,
          GEAENDERT);

  @Mock private SpringDataNachgetrageneRechnungRepository zeilen;

  @Captor private ArgumentCaptor<NachgetrageneRechnungEntity> gespeicherte;

  @InjectMocks private JpaNachgetrageneRechnungRepository repository;

  @Test
  void findById_givenAStoredRow_thenReadsEveryFieldBack() {
    // Given
    when(zeilen.findById(ID)).thenReturn(Optional.of(NachgetrageneRechnungEntity.aus(RECHNUNG)));

    // When / Then — hin und zurueck ergibt dieselbe Rechnung, Feld fuer Feld.
    assertThat(repository.findById(ID)).contains(RECHNUNG);
  }

  @Test
  void findById_givenAnUnknownId_thenEmpty() {
    // Given
    when(zeilen.findById(4711L)).thenReturn(Optional.empty());

    // When / Then
    assertThat(repository.findById(4711L)).isEmpty();
  }

  @Test
  void findAlle_thenTranslatesEveryRow() {
    // Given
    when(zeilen.findAll()).thenReturn(List.of(NachgetrageneRechnungEntity.aus(RECHNUNG)));

    // When / Then
    assertThat(repository.findAlle()).containsExactly(RECHNUNG);
  }

  @Test
  void save_thenWritesTheRowAndReturnsTheWrittenRechnung() {
    // Given — die Datenbank vergibt die Kennung; zurueck kommt die geschriebene Zeile.
    final NachgetrageneRechnung neu =
        new NachgetrageneRechnung(
            null,
            4L,
            "RE-9",
            RECHNUNGSDATUM,
            new BigDecimal("1000.00"),
            new BigDecimal("1190.00"),
            Rechnungszustand.GESTELLT,
            null,
            ANGELEGT,
            ANGELEGT);
    when(zeilen.save(any(NachgetrageneRechnungEntity.class)))
        .thenReturn(NachgetrageneRechnungEntity.aus(RECHNUNG));

    // When
    final NachgetrageneRechnung geschrieben = repository.save(neu);

    // Then
    verify(zeilen).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getId()).isNull(),
            zeile -> assertThat(zeile.getZustand()).isEqualTo(Rechnungszustand.GESTELLT));
    assertThat(geschrieben).isEqualTo(RECHNUNG);
  }

  @Test
  void delete_thenDeletesTheRowById() {
    // When
    repository.delete(ID);

    // Then
    verify(zeilen).deleteById(ID);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void existiertNummer_thenPassesTheAnswerOn(final boolean vergeben) {
    // Given
    when(zeilen.existiertNummer("re-9")).thenReturn(vergeben);

    // When / Then
    assertThat(repository.existiertNummer("re-9")).isEqualTo(vergeben);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void existiertNummerAusser_thenPassesTheAnswerOn(final boolean vergeben) {
    // Given
    when(zeilen.existiertNummerAusser("re-9", ID)).thenReturn(vergeben);

    // When / Then
    assertThat(repository.existiertNummerAusser("re-9", ID)).isEqualTo(vergeben);
  }
}
