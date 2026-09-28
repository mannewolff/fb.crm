package org.mwolff.fbcrm.auftrag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.auftrag.domain.Auftrag;
import org.mwolff.fbcrm.auftrag.domain.AuftragRepository;

/**
 * Die Auftragsliste eines Vorgangs (Kriterium 9, Plan E13).
 *
 * <p>Kriterium 9 nennt nur den ersten Schluessel — „der juengste oben". Ohne einen zweiten
 * lieferten zwei Aufrufe verschiedene Listen, sobald zwei Auftraege denselben Tag tragen; deshalb
 * entscheidet danach die hoehere Kennung.
 *
 * <p>Sortiert wird hier und nicht im Bestand: Die Ordnung ist eine Aussage der Ansicht und keine
 * Eigenschaft der Zeilen.
 */
@ExtendWith(MockitoExtension.class)
class AuftraegeDesVorgangsUseCaseTest {

  private static final long VORGANG = 3L;

  @Mock private AuftragRepository auftraege;

  @InjectMocks private AuftraegeDesVorgangsUseCase useCase;

  private static Auftrag am(final long id, final LocalDate tag) {
    final Auftrag vorlage = Auftragsdoppel.auftrag(id);
    return vorlage.gepflegt(
        tag,
        vorlage.kundenbestellnummer(),
        vorlage.leistungAb(),
        vorlage.leistungBis(),
        vorlage.status(),
        vorlage.updatedAt());
  }

  @Test
  void auftraege_thenOrdersByDatumDescendingAndThenById() {
    // Given — Kriterium 9 nennt den ersten Schluessel, Plan E13 den zweiten.
    final Auftrag aelter = am(1L, LocalDate.of(2026, 9, 1));
    final Auftrag gleicherTagNiedrig = am(2L, LocalDate.of(2026, 9, 20));
    final Auftrag gleicherTagHoch = am(5L, LocalDate.of(2026, 9, 20));
    when(auftraege.findByVorgang(VORGANG))
        .thenReturn(List.of(aelter, gleicherTagNiedrig, gleicherTagHoch));

    // When — zweimal, weil die Zusage auch die Wiederholbarkeit ist.
    final List<Auftrag> erster = useCase.auftraege(VORGANG);
    final List<Auftrag> zweiter = useCase.auftraege(VORGANG);

    // Then
    assertThat(erster).containsExactly(gleicherTagHoch, gleicherTagNiedrig, aelter);
    assertThat(zweiter).isEqualTo(erster);
  }

  @Test
  void auftraege_givenAVorgangWithoutAny_thenAnswersAnEmptyList() {
    // Given — ein Vorgang ohne Auftrag ist kein Fehler.
    when(auftraege.findByVorgang(VORGANG)).thenReturn(List.of());

    // When / Then
    assertThat(useCase.auftraege(VORGANG)).isEmpty();
  }
}
