package org.mwolff.fbcrm.auth.domain;

import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Identifiable;

/**
 * Der Einmalcode der Anmeldung (E1, E2).
 *
 * <p>Er haengt an der <b>Adresse</b>, nicht am Konto: Bei der Einrichtung gibt es im Augenblick der
 * Anforderung noch kein Konto. Je Adresse gibt es hoechstens einen Code.
 *
 * <p>Gespeichert wird nie der Code selbst, sondern sein Argon2id-Hash ({@link SecretHasher}) — wer
 * die Tabelle liest, kann daraus keinen benutzbaren Code gewinnen. Ein schneller Hash genuegte
 * nicht: Ein sechsstelliger Code hat nur 10^6 Vorbilder (E2).
 *
 * <p>Einloesbar ist er bis vor seine Ablaufzeit. Im Augenblick des Ablaufs gilt er <b>nicht</b>
 * mehr; ein {@code isBefore} statt eines {@code isAfter} auf der anderen Seite haenge die
 * Gueltigkeit sonst an der Aufloesung der Uhr. Verfallen ist er, sobald die Fehleingaben die Grenze
 * erreichen.
 *
 * <p>Unveraenderlich: {@link #mitFehlversuch} liefert einen neuen Code, statt diesen zu aendern.
 * Der Zeitpunkt kommt von aussen, weil die Domaene keine Uhr kennt (CLAUDE-java.md §6.2).
 *
 * @param id technische Id — {@code null}, solange der Code nicht gespeichert ist
 * @param email Adresse, an die der Code ging; eindeutig ohne Ruecksicht auf Gross- und
 *     Kleinschreibung
 * @param codeHash Argon2id-Hash des Codes; nie der Code selbst
 * @param expiresAt Zeitpunkt, ab dem der Code nicht mehr gilt
 * @param failedAttempts Zahl der bisherigen Fehleingaben
 * @param createdAt Zeitpunkt der Ausstellung
 */
public record LoginCode(
    @Nullable Long id,
    String email,
    String codeHash,
    Instant expiresAt,
    int failedAttempts,
    Instant createdAt)
    implements Identifiable {

  /**
   * Ein frisch ausgestellter Code ohne Fehleingaben.
   *
   * @param email Adresse, an die er geht
   * @param codeHash Argon2id-Hash des ausgegebenen Codes
   * @param jetzt Zeitpunkt der Ausstellung
   * @param laufzeit Zeitspanne, nach der er nicht mehr gilt
   */
  public static LoginCode ausgestellt(
      final String email, final String codeHash, final Instant jetzt, final Duration laufzeit) {
    return new LoginCode(null, email, codeHash, jetzt.plus(laufzeit), 0, jetzt);
  }

  /**
   * Ob der Code jetzt noch eingeloest werden kann.
   *
   * @param jetzt Zeitpunkt der Pruefung
   */
  public boolean istEinloesbar(final Instant jetzt) {
    return jetzt.isBefore(expiresAt);
  }

  /** Derselbe Code mit einer Fehleingabe mehr. */
  public LoginCode mitFehlversuch() {
    return new LoginCode(id, email, codeHash, expiresAt, failedAttempts + 1, createdAt);
  }

  /**
   * Ob der Code nach seinen Fehleingaben verfallen ist (Kriterium 4).
   *
   * @param grenze Zahl der Fehleingaben, ab der er verfaellt
   */
  public boolean istVerfallen(final int grenze) {
    return failedAttempts >= grenze;
  }
}
