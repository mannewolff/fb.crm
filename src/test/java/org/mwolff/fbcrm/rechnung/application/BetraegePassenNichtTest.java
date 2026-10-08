package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Ein Feldfehler der nachgetragenen Rechnung: 422 am Feld {@code brutto} (Plan #259, E23). */
class BetraegePassenNichtTest {

  @Test
  void status_thenUnprocessableEntity() {
    // When
    final ResponseStatus status = BetraegePassenNicht.class.getAnnotation(ResponseStatus.class);

    // Then
    assertThat(status.code()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(status.reason()).isEqualTo(BetraegePassenNicht.MELDUNG);
  }

  @Test
  void felder_thenTheMeldungStandsAtItsField() {
    // When / Then
    assertThat(new BetraegePassenNicht().felder())
        .containsExactlyEntriesOf(Map.of("brutto", List.of(BetraegePassenNicht.MELDUNG)));
  }
}
