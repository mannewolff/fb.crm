package org.mwolff.fbcrm.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.security.SecureRandom;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SecureTokensTest {

  /** Bekannter Vektor: SHA-256 von {@code fb.crm-token}, Base64url ohne Auffuellzeichen. */
  private static final String KNOWN_TOKEN = "fb.crm-token";

  private static final String KNOWN_HASH = "qcqDyQJpQR49e7aMBgqdHXtJCxb4445x0KmA8X6ZBRA";

  /** 32 Nullbytes in Base64url ergeben 43-mal das Zeichen A. */
  private static final String ALL_ZERO_TOKEN = "A".repeat(43);

  @Test
  void newToken_givenFixedRandomSource_thenEncodesItsBytesAsBase64UrlWithoutPadding() {
    // Given
    final SecureRandom random = mock(SecureRandom.class);
    doAnswer(
            invocation -> {
              Arrays.fill(invocation.<byte[]>getArgument(0), (byte) 0);
              return null;
            })
        .when(random)
        .nextBytes(any(byte[].class));

    // When
    final String token = new SecureTokens(random).newToken();

    // Then
    assertThat(token).isEqualTo(ALL_ZERO_TOKEN);
  }

  @Test
  void newToken_givenDefaultRandomSource_thenEveryTokenIsFreshAnd256BitsWide() {
    // Given
    final SecureTokens tokens = new SecureTokens();

    // When
    final String first = tokens.newToken();
    final String second = tokens.newToken();

    // Then
    assertThat(first).hasSize(43).isNotEqualTo(second);
  }

  @Test
  void hash_givenKnownToken_thenReturnsSha256AsBase64Url() {
    // Given
    final SecureTokens tokens = new SecureTokens();

    // When
    final String hash = tokens.hash(KNOWN_TOKEN);

    // Then
    assertThat(hash).isEqualTo(KNOWN_HASH);
  }

  @Test
  void matches_givenTokenBelongingToHash_thenTrue() {
    // Given
    final SecureTokens tokens = new SecureTokens();

    // When
    final boolean matches = tokens.matches(KNOWN_TOKEN, KNOWN_HASH);

    // Then
    assertThat(matches).isTrue();
  }

  @Test
  void matches_givenForeignToken_thenFalse() {
    // Given
    final SecureTokens tokens = new SecureTokens();

    // When
    final boolean matches = tokens.matches("ein-anderes-token", KNOWN_HASH);

    // Then
    assertThat(matches).isFalse();
  }
}
