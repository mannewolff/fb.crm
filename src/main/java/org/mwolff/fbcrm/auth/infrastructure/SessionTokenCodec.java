package org.mwolff.fbcrm.auth.infrastructure;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.mwolff.fbcrm.auth.domain.SessionTokenDecoding;
import org.mwolff.fbcrm.auth.domain.SessionTokens;
import org.mwolff.fbcrm.common.ExcludeFromJacocoGeneratedReport;
import org.springframework.stereotype.Component;

/**
 * Das Session-Token: {@code base64url(payload).base64url(hmacSha256)} (E2).
 *
 * <p>Der Payload ist ein festes Feld aus vier {@code long}-Werten — Konto, Sitzungs-Generation,
 * Ausstellung und Ablauf, beide sekundengenau. Weil die Laenge fest ist und <b>vor</b> der
 * Signaturpruefung geprueft wird, kann das Auslesen der Felder danach nicht mehr scheitern; es gibt
 * keinen Pfad, auf dem ungepruefte Bytes ausgewertet wuerden.
 *
 * <p>Bewusst kein JWT: Ein einziger Anwendungsfall rechtfertigt keine Bibliothek, deren bekannteste
 * Fehlerklasse ({@code alg: none}, Schluesselverwechslung) hier gar nicht erst entstehen kann.
 * Verglichen wird ausschliesslich mit {@link MessageDigest#isEqual} — ein frueh abbrechender
 * Vergleich verriete ueber die Laufzeit, wie weit eine geratene Signatur stimmte.
 */
@Component
public final class SessionTokenCodec implements SessionTokens {

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final int PAYLOAD_BYTES = 4 * Long.BYTES;
  private static final String SEPARATOR = ".";
  private static final Pattern SEPARATOR_PATTERN = Pattern.compile(Pattern.quote(SEPARATOR));
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

  private final byte[] secret;
  private final Clock clock;

  public SessionTokenCodec(final AuthProperties properties, final Clock clock) {
    this.secret = properties.sessionSecret().getBytes(StandardCharsets.UTF_8);
    this.clock = clock;
  }

  /**
   * Ein signiertes Token fuer eine Sitzung.
   *
   * @param accountId Konto, dem die Sitzung gehoert
   * @param sessionGeneration Generation des Kontos zum Zeitpunkt der Ausstellung
   * @param issuedAt Ausstellungszeitpunkt
   * @param ttl Laufzeit ab Ausstellung
   */
  @Override
  public String encode(
      final long accountId,
      final long sessionGeneration,
      final Instant issuedAt,
      final Duration ttl) {
    final byte[] payload =
        ByteBuffer.allocate(PAYLOAD_BYTES)
            .putLong(accountId)
            .putLong(sessionGeneration)
            .putLong(issuedAt.getEpochSecond())
            .putLong(issuedAt.plus(ttl).getEpochSecond())
            .array();
    return ENCODER.encodeToString(payload) + SEPARATOR + ENCODER.encodeToString(sign(payload));
  }

  /**
   * Prueft ein vorgelegtes Token.
   *
   * @return einer der vier Faelle aus {@link SessionTokenDecoding} — nie {@code null}
   */
  @Override
  public SessionTokenDecoding decode(final String token) {
    final String[] teile = SEPARATOR_PATTERN.split(token, -1);
    if (teile.length != 2) {
      return new SessionTokenDecoding.Malformed();
    }

    final byte[] payload;
    final byte[] signature;
    try {
      payload = DECODER.decode(teile[0]);
      signature = DECODER.decode(teile[1]);
    } catch (final IllegalArgumentException unlesbar) {
      return new SessionTokenDecoding.Malformed();
    }
    if (payload.length != PAYLOAD_BYTES) {
      return new SessionTokenDecoding.Malformed();
    }
    if (!MessageDigest.isEqual(sign(payload), signature)) {
      return new SessionTokenDecoding.SignatureMismatch();
    }

    final ByteBuffer felder = ByteBuffer.wrap(payload);
    final long accountId = felder.getLong();
    final long sessionGeneration = felder.getLong();
    final Instant issuedAt = Instant.ofEpochSecond(felder.getLong());
    final Instant expiresAt = Instant.ofEpochSecond(felder.getLong());
    if (!clock.instant().isBefore(expiresAt)) {
      return new SessionTokenDecoding.Expired();
    }
    return new SessionTokenDecoding.Valid(accountId, sessionGeneration, issuedAt, expiresAt);
  }

  private byte[] sign(final byte[] payload) {
    return mac().doFinal(payload);
  }

  /*
   * Nicht erreichbare Zweige: HmacSHA256 ist fuer jede JCA-Implementierung Pflicht, und ein
   * Schluessel aus mindestens 32 Byte ist fuer HMAC immer gueltig. Methodengenaue Ausnahme nach
   * CLAUDE-java.md §5.4 — der Rest der Klasse bleibt in Abdeckung und Mutationstest.
   */
  @ExcludeFromJacocoGeneratedReport
  private Mac mac() {
    try {
      final Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
      return mac;
    } catch (final NoSuchAlgorithmException | InvalidKeyException ursache) {
      throw new IllegalStateException(
          HMAC_ALGORITHM + " ist in dieser JVM nicht verfuegbar.", ursache);
    }
  }
}
