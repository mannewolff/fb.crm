package org.mwolff.fbcrm.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.auth.Konten;
import org.mwolff.fbcrm.common.Urheber;

/**
 * Die Abbildung einer Konto-Zeile auf den Urheber, gegen ein gemocktes Spring-Data-Repository.
 *
 * <p>Das Zusammenspiel mit der Datenbank weist {@code KontoUrheberauskunftIT} nach; hier steht die
 * Abbildung selbst, damit auch der Mutationstest sie sieht.
 */
@ExtendWith(MockitoExtension.class)
class KontoUrheberauskunftTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");

  @Mock private SpringDataAccountRepository jpa;

  @InjectMocks private KontoUrheberauskunft auskunft;

  @Test
  void zu_givenAKnownAccount_thenNamesItsDisplayName() {
    // Given
    when(jpa.findById(9L))
        .thenReturn(
            Optional.of(
                AccountEntity.aus(
                    Konten.gespeichert(9L, "bernd@example.org", 0, ANGELEGT)
                        .mitNamen("Bernd Muster", ANGELEGT))));

    // When / Then
    assertThat(auskunft.zu(9L)).contains(new Urheber(9L, "Bernd Muster"));
  }

  @Test
  void zu_givenAnUnknownAccount_thenIsEmpty() {
    // Given
    when(jpa.findById(9L)).thenReturn(Optional.empty());

    // When / Then
    assertThat(auskunft.zu(9L)).isEmpty();
  }
}
