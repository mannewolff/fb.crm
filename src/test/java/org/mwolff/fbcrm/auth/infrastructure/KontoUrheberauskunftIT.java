package org.mwolff.fbcrm.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.auth.Konten;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.common.Urheber;
import org.mwolff.fbcrm.common.Urheberauskunft;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die Auskunft ueber den Urheber einer Aenderung gegen eine echte PostgreSQL-Instanz (Plan #415,
 * E6).
 *
 * <p>Die zweite Zeile ist der Fall, um den es geht: Mehrere Personen arbeiten mit fb.crm, und der
 * Name in der Meldung muss der des Kontos sein, das geaendert hat — nicht der des ersten Kontos.
 */
class KontoUrheberauskunftIT extends AbstractIntegrationTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");

  private final Urheberauskunft auskunft;
  private final JpaAccountRepository konten;
  private final JdbcTemplate jdbc;

  @Autowired
  KontoUrheberauskunftIT(
      final Urheberauskunft auskunft, final JpaAccountRepository konten, final JdbcTemplate jdbc) {
    this.auskunft = auskunft;
    this.konten = konten;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereFachtabellen() {
    leereKonten(jdbc);
  }

  private Account speichereMitNamen(final String email, final String name) {
    return konten.save(Konten.neu(email, ANGELEGT).mitNamen(name, ANGELEGT));
  }

  @Test
  void zu_givenTheSecondAccount_thenNamesItsDisplayName() {
    // Given
    speichereMitNamen("anna@example.org", "Anna Beispiel");
    final Account bernd = speichereMitNamen("bernd@example.org", "Bernd Muster");

    // When / Then
    assertThat(auskunft.zu(bernd.requireId()))
        .contains(new Urheber(bernd.requireId(), "Bernd Muster"));
  }

  @Test
  void zu_givenAnUnknownAccount_thenIsEmpty() {
    // Given
    final Account anna = speichereMitNamen("anna@example.org", "Anna Beispiel");

    // When / Then
    assertThat(auskunft.zu(anna.requireId() + 1)).isEmpty();
  }
}
