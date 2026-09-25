package org.mwolff.fbcrm.vorgang.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.firma.domain.Anschrift;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.vorgang.domain.EintragRepository;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;
import org.mwolff.fbcrm.vorgang.domain.VorgangRepository;

/**
 * Die Vorgaenge einer Firma, getrennt nach offen und abgeschlossen (Kriterium 12).
 *
 * <p>Der eigene Leseweg gehoert diesem Modul und nicht {@code firma} (E2): {@code vorgang} liest
 * die Firma, die Firma weiss nichts vom Vorgang — sonst entstuende ein Paketzyklus.
 *
 * <p>Getrennt wird hier, sortiert im Bestand: Beide Listen kommen aus <b>einer</b> Abfrage und
 * behalten deren Reihenfolge (E16).
 */
@ExtendWith(MockitoExtension.class)
class VorgaengeDerFirmaUseCaseTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant GESCHEHEN = Instant.parse("2026-09-12T09:00:00Z");

  @Mock private VorgangRepository vorgaenge;
  @Mock private EintragRepository eintraege;
  @Mock private FirmaRepository firmen;

  private VorgaengeDerFirmaUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new VorgaengeDerFirmaUseCase(vorgaenge, eintraege, firmen);
  }

  private static Vorgang vorgang(final long id, final long nummer, final boolean abgeschlossen) {
    return new Vorgang(id, nummer, "Website-Relaunch", 7L, null, abgeschlossen, ANGELEGT, ANGELEGT);
  }

  private static Firma adlerAg() {
    return new Firma(
        7L,
        "Adler AG",
        new Anschrift(null, null, null, null),
        null,
        null,
        true,
        ANGELEGT,
        ANGELEGT);
  }

  @Test
  void vorgaenge_thenSeparatesOpenFromClosed() {
    // Given — Kriterium 12.
    when(vorgaenge.findByFirma(7L))
        .thenReturn(List.of(vorgang(4L, 12L, false), vorgang(5L, 13L, true)));
    when(eintraege.juengstesGeschehenJeVorgang(List.of(4L, 5L))).thenReturn(Map.of());
    when(firmen.findById(7L)).thenReturn(Optional.of(adlerAg()));

    // When
    final VorgaengeDerFirma gefunden = useCase.vorgaenge(7L);

    // Then
    assertThat(gefunden.offene()).extracting(VorgangZeile::id).containsExactly(4L);
    assertThat(gefunden.abgeschlossene()).extracting(VorgangZeile::id).containsExactly(5L);
  }

  @Test
  void vorgaenge_thenKeepsTheOrderOfTheRepositoryWithinEachList() {
    // Given — dieselbe Reihenfolge wie die Uebersicht (E16).
    when(vorgaenge.findByFirma(7L))
        .thenReturn(
            List.of(
                vorgang(9L, 1L, false),
                vorgang(8L, 2L, true),
                vorgang(3L, 3L, false),
                vorgang(2L, 4L, true)));
    when(eintraege.juengstesGeschehenJeVorgang(List.of(9L, 8L, 3L, 2L))).thenReturn(Map.of());
    when(firmen.findById(7L)).thenReturn(Optional.of(adlerAg()));

    // When
    final VorgaengeDerFirma gefunden = useCase.vorgaenge(7L);

    // Then
    assertThat(gefunden.offene()).extracting(VorgangZeile::id).containsExactly(9L, 3L);
    assertThat(gefunden.abgeschlossene()).extracting(VorgangZeile::id).containsExactly(8L, 2L);
  }

  @Test
  void vorgaenge_thenCarriesNumberTitlePhaseAndTheDayPerRow() {
    // Given
    when(vorgaenge.findByFirma(7L)).thenReturn(List.of(vorgang(4L, 12L, false)));
    when(eintraege.juengstesGeschehenJeVorgang(List.of(4L))).thenReturn(Map.of(4L, GESCHEHEN));
    when(firmen.findById(7L)).thenReturn(Optional.of(adlerAg()));

    // When
    final VorgaengeDerFirma gefunden = useCase.vorgaenge(7L);

    // Then
    assertThat(gefunden.offene())
        .singleElement()
        .extracting(VorgangZeile::nummer, VorgangZeile::titel, VorgangZeile::letzteAktivitaet)
        .containsExactly(12L, "Website-Relaunch", GESCHEHEN);
  }

  @Test
  void vorgaenge_givenAFirmaWithoutAnyVorgang_thenTwoEmptyLists() {
    // Given — Kriterium 12: „Gibt es keinen, sagt die Liste das."
    when(vorgaenge.findByFirma(7L)).thenReturn(List.of());
    when(eintraege.juengstesGeschehenJeVorgang(List.of())).thenReturn(Map.of());

    // When
    final VorgaengeDerFirma gefunden = useCase.vorgaenge(7L);

    // Then — und ohne Vorgang wird die Firma gar nicht erst geholt.
    assertThat(gefunden.offene()).isEmpty();
    assertThat(gefunden.abgeschlossene()).isEmpty();
    verifyNoInteractions(firmen);
  }
}
