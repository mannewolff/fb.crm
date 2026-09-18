package org.mwolff.fbcrm.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * Das Session-Token-Format {@code base64url(payload).base64url(hmacSha256)} (E2).
 *
 * <p>Geprueft wird vor allem, was <b>nicht</b> als gueltig durchgeht: manipulierte Signatur,
 * fremdes Geheimnis, abgelaufener Zeitstempel und unlesbare Formen.
 */
class SessionTokenCodecTest {

  private static final String GEHEIMNIS = "geheimnis-mit-mindestens-32-zeichen-laenge";
  private static final String FREMDES_GEHEIMNIS = "ein-anderes-geheimnis-mit-genug-zeichen-drin";
  private static final Instant AUSGESTELLT = Instant.parse("2026-09-18T10:00:00Z");
  private static final Duration LAUFZEIT = Duration.ofDays(1);
  private static final Instant LAEUFT_AB = AUSGESTELLT.plus(LAUFZEIT);
  private static final long KONTO = 42L;
  private static final long GENERATION = 3L;

  private static SessionTokenCodec codec(final String geheimnis, final Instant jetzt) {
    return new SessionTokenCodec(
        new AuthProperties(geheimnis, LAUFZEIT, "fbcrm_session", true),
        Clock.fixed(jetzt, ZoneOffset.UTC));
  }

  private static String frischesToken() {
    return codec(GEHEIMNIS, AUSGESTELLT).encode(KONTO, GENERATION, AUSGESTELLT, LAUFZEIT);
  }

  /**
   * Ersetzt das erste Zeichen der Signatur. Es traegt volle sechs Bit und veraendert damit
   * garantiert das dekodierte Byte-Feld — anders als das letzte Zeichen, dessen ueberzaehlige Bits
   * der Base64-Decoder verwirft.
   */
  private static String mitVeraenderterSignatur(final String token) {
    final int trenner = token.indexOf('.');
    final char original = token.charAt(trenner + 1);
    final char ersetzt = original == 'A' ? 'B' : 'A';
    return token.substring(0, trenner + 1) + ersetzt + token.substring(trenner + 2);
  }

  @Test
  void encode_thenCarriesPayloadAndSignatureSeparatedByADot() {
    // When
    final String token = frischesToken();

    // Then
    assertThat(token).matches("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$");
  }

  @Test
  void decode_givenAFreshToken_thenValidWithAccountGenerationAndLifetime() {
    // Given
    final String token = frischesToken();

    // When
    final SessionTokenDecoding ergebnis = codec(GEHEIMNIS, AUSGESTELLT).decode(token);

    // Then
    assertThat(ergebnis)
        .isEqualTo(new SessionTokenDecoding.Valid(KONTO, GENERATION, AUSGESTELLT, LAEUFT_AB));
  }

  @Test
  void decode_givenATamperedSignature_thenSignatureMismatch() {
    // Given
    final String manipuliert = mitVeraenderterSignatur(frischesToken());

    // When
    final SessionTokenDecoding ergebnis = codec(GEHEIMNIS, AUSGESTELLT).decode(manipuliert);

    // Then
    assertThat(ergebnis).isEqualTo(new SessionTokenDecoding.SignatureMismatch());
  }

  @Test
  void decode_givenATokenShortenedByOneCharacter_thenNotValid() {
    // Given
    final String token = frischesToken();
    final String gekuerzt = token.substring(0, token.length() - 1);

    // When
    final SessionTokenDecoding ergebnis = codec(GEHEIMNIS, AUSGESTELLT).decode(gekuerzt);

    // Then
    assertThat(ergebnis).isNotInstanceOf(SessionTokenDecoding.Valid.class);
  }

  @Test
  void decode_givenATokenSignedWithAnotherSecret_thenSignatureMismatch() {
    // Given
    final String token = frischesToken();

    // When
    final SessionTokenDecoding ergebnis = codec(FREMDES_GEHEIMNIS, AUSGESTELLT).decode(token);

    // Then
    assertThat(ergebnis).isEqualTo(new SessionTokenDecoding.SignatureMismatch());
  }

  @Test
  void decode_givenATokenPastItsLifetime_thenExpired() {
    // Given
    final String token = frischesToken();

    // When
    final SessionTokenDecoding ergebnis = codec(GEHEIMNIS, LAEUFT_AB.plusSeconds(1)).decode(token);

    // Then
    assertThat(ergebnis).isEqualTo(new SessionTokenDecoding.Expired());
  }

  @Test
  void decode_givenATokenAtTheExpiryInstant_thenExpired() {
    // Given
    final String token = frischesToken();

    // When
    final SessionTokenDecoding ergebnis = codec(GEHEIMNIS, LAEUFT_AB).decode(token);

    // Then
    assertThat(ergebnis).isEqualTo(new SessionTokenDecoding.Expired());
  }

  @Test
  void decode_givenATokenOneSecondBeforeExpiry_thenStillValid() {
    // Given
    final String token = frischesToken();

    // When
    final SessionTokenDecoding ergebnis = codec(GEHEIMNIS, LAEUFT_AB.minusSeconds(1)).decode(token);

    // Then
    assertThat(ergebnis)
        .isEqualTo(new SessionTokenDecoding.Valid(KONTO, GENERATION, AUSGESTELLT, LAEUFT_AB));
  }

  @Test
  void decode_givenATokenWithoutSeparator_thenMalformed() {
    // When
    final SessionTokenDecoding ergebnis = codec(GEHEIMNIS, AUSGESTELLT).decode("ohnetrennzeichen");

    // Then
    assertThat(ergebnis).isEqualTo(new SessionTokenDecoding.Malformed());
  }

  @Test
  void decode_givenATokenWithTwoSeparators_thenMalformed() {
    // Given
    final String token = frischesToken() + ".angehaengt";

    // When
    final SessionTokenDecoding ergebnis = codec(GEHEIMNIS, AUSGESTELLT).decode(token);

    // Then
    assertThat(ergebnis).isEqualTo(new SessionTokenDecoding.Malformed());
  }

  @Test
  void decode_givenPartsThatAreNoBase64_thenMalformed() {
    // When
    final SessionTokenDecoding ergebnis = codec(GEHEIMNIS, AUSGESTELLT).decode("!!!.???");

    // Then
    assertThat(ergebnis).isEqualTo(new SessionTokenDecoding.Malformed());
  }

  @Test
  void decode_givenAPayloadOfTheWrongLength_thenMalformed() {
    // Given
    final String zuKurzerPayload =
        Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[8]);
    final String token = zuKurzerPayload + "." + zuKurzerPayload;

    // When
    final SessionTokenDecoding ergebnis = codec(GEHEIMNIS, AUSGESTELLT).decode(token);

    // Then
    assertThat(ergebnis).isEqualTo(new SessionTokenDecoding.Malformed());
  }
}
