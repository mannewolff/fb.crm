package org.mwolff.fbcrm.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Die Abweisung einer vergebenen Adresse nennt das Feld und laesst die Ursache im Haus.
 *
 * <p>{@code getMessage()} bleibt leer: {@code GlobalExceptionHandler} gibt sie nach aussen, und der
 * Text der Datenbank — Tabelle, Index, mitunter die Adresse — gehoert nicht in die Antwort.
 */
class AdresseBereitsVergebenTest {

  @Test
  void felder_thenNamesTheAddressFieldWithTheSentence() {
    // When
    final Map<String, List<String>> felder = new AdresseBereitsVergeben().felder();

    // Then
    assertThat(felder).containsExactly(Map.entry("email", List.of(AdresseBereitsVergeben.MELDUNG)));
  }

  @Test
  void construct_withTheIndexViolation_thenKeepsTheCauseButNoMessage() {
    // Given
    final DataIntegrityViolationException index =
        new DataIntegrityViolationException("duplicate key value violates account_email_key");

    // When
    final AdresseBereitsVergeben abweisung = new AdresseBereitsVergeben(index);

    // Then
    assertThat(abweisung.getCause()).isSameAs(index);
    assertThat(abweisung.getMessage()).isNull();
  }
}
