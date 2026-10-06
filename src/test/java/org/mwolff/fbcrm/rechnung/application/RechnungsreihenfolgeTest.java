package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;

/**
 * Die Gesamtordnung der gemischten Rechnungsliste (Plan #259, E19; #254, Kriterium 6).
 *
 * <p>Rechnungsdatum absteigend, bei gleichem Datum die von fb.crm geschriebene vor der
 * nachgetragenen, bei gleicher Art die hoehere Kennung. Ohne die Art waere die Ordnung bei gleichem
 * Datum und gleicher Kennung in beiden Raeumen nicht eindeutig.
 */
class RechnungsreihenfolgeTest {

  private static final LocalDate FRUEHER = LocalDate.of(2026, 9, 1);
  private static final LocalDate SPAETER = LocalDate.of(2026, 9, 30);

  private static Rechnungslistenzeile zeile(
      final boolean nachgetragen, final long id, final LocalDate datum) {
    return new Rechnungslistenzeile(
        nachgetragen,
        id,
        "R-" + id,
        Rechnungsdoppel.FIRMA,
        "Adler AG",
        datum,
        BigDecimal.ONE,
        Rechnungszustand.GESTELLT,
        false);
  }

  private static List<Rechnungslistenzeile> sortiert(final List<Rechnungslistenzeile> zeilen) {
    return zeilen.stream().sorted(Rechnungsreihenfolge.GEMISCHT_NEUESTE_ZUERST).toList();
  }

  @Test
  void gemischt_thenTheNewerDatumComesFirstRegardlessOfArtAndId() {
    // Given — die nachgetragene ist neuer, traegt aber die kleinere Kennung.
    final var alt = zeile(false, 9L, FRUEHER);
    final var neu = zeile(true, 1L, SPAETER);

    // When / Then
    assertThat(sortiert(List.of(alt, neu))).containsExactly(neu, alt);
  }

  @Test
  void gemischt_atTheSameDatum_thenTheGeschriebeneComesBeforeTheNachgetragene() {
    // Given — am selben Tag; die nachgetragene traegt die hoehere Kennung.
    final var geschrieben = zeile(false, 1L, SPAETER);
    final var nachgetragen = zeile(true, 9L, SPAETER);

    // When / Then
    assertThat(sortiert(List.of(nachgetragen, geschrieben)))
        .containsExactly(geschrieben, nachgetragen);
  }

  @Test
  void gemischt_atTheSameDatumAndTheSameId_thenTheArtDecides() {
    // Given — dieselbe Kennung in beiden Raeumen am selben Tag.
    final var geschrieben = zeile(false, 3L, SPAETER);
    final var nachgetragen = zeile(true, 3L, SPAETER);

    // When / Then
    assertThat(sortiert(List.of(nachgetragen, geschrieben)))
        .containsExactly(geschrieben, nachgetragen);
  }

  @Test
  void gemischt_atTheSameDatumAndArt_thenTheHigherIdComesFirst() {
    // Given
    final var kleiner = zeile(true, 2L, SPAETER);
    final var hoeher = zeile(true, 5L, SPAETER);

    // When / Then
    assertThat(sortiert(List.of(kleiner, hoeher))).containsExactly(hoeher, kleiner);
  }

  @Test
  void gemischt_thenTwoCallsGiveTheSameList() {
    // Given — sechs Zeilen, zweimal unterschiedlich gemischt.
    final List<Rechnungslistenzeile> zeilen =
        List.of(
            zeile(false, 1L, FRUEHER),
            zeile(true, 1L, FRUEHER),
            zeile(false, 2L, SPAETER),
            zeile(true, 2L, SPAETER),
            zeile(true, 7L, SPAETER),
            zeile(false, 7L, FRUEHER));
    final List<Rechnungslistenzeile> umgekehrt = new ArrayList<>(zeilen);
    Collections.reverse(umgekehrt);

    // When / Then
    assertThat(sortiert(umgekehrt))
        .isEqualTo(sortiert(zeilen))
        .containsExactly(
            zeile(false, 2L, SPAETER),
            zeile(true, 7L, SPAETER),
            zeile(true, 2L, SPAETER),
            zeile(false, 7L, FRUEHER),
            zeile(false, 1L, FRUEHER),
            zeile(true, 1L, FRUEHER));
  }
}
