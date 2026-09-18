package org.mwolff.fbcrm.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.Role;

/** Das Konto der laufenden Sitzung. */
@ExtendWith(MockitoExtension.class)
class GetCurrentAccountUseCaseTest {

  private static final Instant JETZT = Instant.parse("2026-09-18T10:00:00Z");
  private static final long KONTO_ID = 42L;

  @Mock private AccountRepository accounts;

  @InjectMocks private GetCurrentAccountUseCase useCase;

  private static Account konto() {
    return new Account(KONTO_ID, "manne@example.org", "Manne", "hash", Role.ADMIN, 3, JETZT, JETZT);
  }

  @Test
  void byId_givenAKnownAccount_thenReturnsIt() {
    // Given
    when(accounts.findById(KONTO_ID)).thenReturn(Optional.of(konto()));

    // When
    final Account gefunden = useCase.byId(KONTO_ID);

    // Then
    assertThat(gefunden).isEqualTo(konto());
  }

  @Test
  void byId_givenAnAccountThatIsGone_thenTheSessionIsWorthless() {
    // Given — das Konto wurde geloescht, das Token gilt formal noch.
    when(accounts.findById(KONTO_ID)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.byId(KONTO_ID)).isInstanceOf(UnknownAccount.class);
  }
}
