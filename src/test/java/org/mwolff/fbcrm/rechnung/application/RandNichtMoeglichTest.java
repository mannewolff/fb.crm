package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mwolff.fbcrm.rechnung.application.Randgrenzen.Rand;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Ein Rand, der nicht mehr passt: 422 am Feld des geaenderten Randes (Plan #363, E9). */
class RandNichtMoeglichTest {

  @Test
  void status_thenUnprocessableEntity() {
    // When
    final ResponseStatus status = RandNichtMoeglich.class.getAnnotation(ResponseStatus.class);

    // Then
    assertThat(status.code()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @ParameterizedTest(name = "{0} Zehntel-Millimeter: {1} mm")
  @CsvSource({"1483, '148,3'", "700, '70,0'", "1, '0,1'", "0, '0,0'"})
  void felder_givenEinenGrenzwert_thenNenntDieMeldungIhnInMillimetern(
      final int zehntelMm, final String millimeter) {
    // When
    final RandNichtMoeglich fehler =
        new RandNichtMoeglich(Rand.INHALT_OBEN, OptionalInt.of(zehntelMm));

    // Then
    final String meldung =
        "Bei den übrigen eingestellten Rändern darf dieser Rand höchstens "
            + millimeter
            + " mm betragen.";
    assertThat(fehler.getMessage()).isEqualTo(meldung);
    assertThat(fehler.felder()).containsExactlyEntriesOf(Map.of("inhaltOben", List.of(meldung)));
  }

  @Test
  void felder_givenKeinenGrenzwert_thenNenntDieMeldungDenGrund() {
    // When
    final RandNichtMoeglich fehler = new RandNichtMoeglich(Rand.FOLGE_UNTEN, OptionalInt.empty());

    // Then
    assertThat(fehler.getMessage())
        .isEqualTo("Bei den übrigen eingestellten Rändern passt kein Wert dieses Randes mehr.")
        .isEqualTo(RandNichtMoeglich.MELDUNG_KEIN_WERT);
    assertThat(fehler.felder())
        .containsExactlyEntriesOf(
            Map.of("folgeUnten", List.of(RandNichtMoeglich.MELDUNG_KEIN_WERT)));
  }
}
