package org.mwolff.fbcrm.auth.application;

import java.time.Clock;
import java.time.Instant;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordResetToken;
import org.mwolff.fbcrm.auth.domain.PasswordResetTokenRepository;
import org.mwolff.fbcrm.common.SecureTokens;
import org.mwolff.fbcrm.mail.MailProperties;
import org.mwolff.fbcrm.mail.application.EnqueueMailUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Anforderung eines neuen Passworts (K7, E7, E24).
 *
 * <p><b>Die unbekannte Adresse ist der interessante Fall.</b> Sie fuehrt zu <b>nichts</b>: kein
 * Token, kein Zustellauftrag, keine Exception. Nach aussen sind beide Wege nicht zu unterscheiden —
 * sonst verriete die Antwort, welche Adressen auf dieser Instanz ein Konto haben. Der einzige
 * Unterschied liegt im Postausgangsfach, und dorthin sieht nur der Betreiber.
 *
 * <p><b>Token und Zustellauftrag entstehen in einer Transaktion</b> (E7). Faellt das Speichern des
 * Tokens aus, verschwindet der Auftrag mit ihm — statt eine Mail zu einem Token hinauszuschicken,
 * das nie entstanden ist. {@code EnqueueMailUseCase} erzwingt das mit {@code
 * Propagation.MANDATORY}.
 *
 * <p>Der Link wird <b>nicht</b> protokolliert (CLAUDE-security.md, {@code NoTokenInLogTest}): Ein
 * Log-Archiv ueberlebt die Stunde, die er gilt, bei weitem.
 */
@Service
public class RequestPasswordResetUseCase {

  /** Der Betreff jeder Reset-Mail dieser Instanz. */
  static final String BETREFF = "fb.crm: neues Passwort setzen";

  /** Der Pfad der Seite, die den Token aus der Abfragezeichenfolge liest. */
  static final String RESET_PFAD = "/passwort-neu?token=";

  /**
   * Der Text der Reset-Mail.
   *
   * <p>Die Platzhalter werden ersetzt, nicht formatiert: Ein Format-String mit {@code %s} zwaenge
   * zu {@code %n} als Zeilenende (SpotBugs), und das waere auf einem Windows-Server ein {@code
   * \r\n} im Rumpf einer Mail, die ihre Zeilenenden selbst mitbringt.
   */
  private static final String VORLAGE =
      """
      Hallo {name},

      fuer dein Konto bei fb.crm wurde ein neues Passwort angefordert. Ueber diesen Link
      setzt du es:

      {link}

      Der Link gilt einmal und laeuft nach {minuten} Minuten ab. Kam die Anforderung nicht
      von dir, ist nichts geschehen — dann kannst du diese Nachricht ignorieren.
      """;

  private final AccountRepository accounts;
  private final PasswordResetTokenRepository resetTokens;
  private final EnqueueMailUseCase postausgang;
  private final SecureTokens tokens;
  private final AuthProperties auth;
  private final MailProperties mail;
  private final Clock clock;

  public RequestPasswordResetUseCase(
      final AccountRepository accounts,
      final PasswordResetTokenRepository resetTokens,
      final EnqueueMailUseCase postausgang,
      final SecureTokens tokens,
      final AuthProperties auth,
      final MailProperties mail,
      final Clock clock) {
    this.accounts = accounts;
    this.resetTokens = resetTokens;
    this.postausgang = postausgang;
    this.tokens = tokens;
    this.auth = auth;
    this.mail = mail;
    this.clock = clock;
  }

  /**
   * Nimmt die Anforderung entgegen — fuer jede Adresse gleich.
   *
   * @param email die eingegebene Adresse; ist sie unbekannt, geschieht nichts
   */
  @Transactional
  public void request(final String email) {
    accounts.findByEmail(email).ifPresent(this::stelleTokenAus);
  }

  private void stelleTokenAus(final Account konto) {
    final Instant jetzt = clock.instant();
    final String token = tokens.newToken();
    resetTokens.save(
        PasswordResetToken.issued(
            konto.requireId(), tokens.hash(token), jetzt, auth.passwordResetTtl()));
    postausgang.enqueue(konto.email(), BETREFF, text(konto, token));
  }

  private String text(final Account konto, final String token) {
    // Der Anzeigename kommt vom Menschen und wird zuletzt eingesetzt: Enthielte er selbst einen
    // Platzhalter, ersetzte ihn danach niemand mehr.
    return VORLAGE
        .replace("{link}", mail.baseUrl() + RESET_PFAD + token)
        .replace("{minuten}", String.valueOf(auth.passwordResetTtl().toMinutes()))
        .replace("{name}", konto.displayName());
  }
}
