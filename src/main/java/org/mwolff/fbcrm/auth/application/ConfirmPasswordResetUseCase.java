package org.mwolff.fbcrm.auth.application;

import java.time.Clock;
import java.time.Instant;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.mwolff.fbcrm.auth.domain.PasswordResetToken;
import org.mwolff.fbcrm.auth.domain.PasswordResetTokenRepository;
import org.mwolff.fbcrm.common.SecureTokens;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Pruefen und Einloesen eines Reset-Links (K7, E24).
 *
 * <p><b>Zwei Wege, eine Regel.</b> {@link #ensureRedeemable} beantwortet die Frage, die die
 * Oberflaeche <b>vor</b> dem Rendern stellt — K7 verlangt, dass ein benutzter oder abgelaufener
 * Link eine Meldung statt eines Formulars zeigt, und das entscheidet sich, bevor etwas gezeichnet
 * wird. Sie veraendert dabei nichts: Wer nur nachsieht, verbraucht den Link nicht. {@link #confirm}
 * loest ihn ein.
 *
 * <p><b>Die Sitzungs-Generation ist der Hebel.</b> {@code Account#changePassword} zaehlt sie hoch,
 * und damit gilt jedes vor dem Reset ausgestellte Cookie nicht mehr — auf jedem Geraet (K7). Wer
 * sein Passwort zuruecksetzt, weil ihm jemand zugesehen hat, will genau das.
 *
 * <p>Unbekannt, abgelaufen und bereits benutzt fuehren zur selben {@link PasswordResetLinkInvalid}.
 */
@Service
public class ConfirmPasswordResetUseCase {

  private final AccountRepository accounts;
  private final PasswordResetTokenRepository resetTokens;
  private final PasswordHasher hasher;
  private final SecureTokens tokens;
  private final Clock clock;

  public ConfirmPasswordResetUseCase(
      final AccountRepository accounts,
      final PasswordResetTokenRepository resetTokens,
      final PasswordHasher hasher,
      final SecureTokens tokens,
      final Clock clock) {
    this.accounts = accounts;
    this.resetTokens = resetTokens;
    this.hasher = hasher;
    this.tokens = tokens;
    this.clock = clock;
  }

  /**
   * Prueft, ob der Link noch gilt — ohne ihn anzuruehren (E24).
   *
   * @param token das Token aus dem Link
   * @throws PasswordResetLinkInvalid wenn der Link unbekannt, abgelaufen oder benutzt ist
   */
  @Transactional(readOnly = true)
  public void ensureRedeemable(final String token) {
    einloesbarer(token);
  }

  /**
   * Setzt das neue Passwort und beendet jede bestehende Sitzung des Kontos (K7).
   *
   * @param token das Token aus dem Link
   * @param neuesPasswort das neue Passwort im Klartext; gespeichert wird nur sein Hash
   * @throws PasswordResetLinkInvalid wenn der Link unbekannt, abgelaufen oder benutzt ist
   */
  @Transactional
  public void confirm(final String token, final String neuesPasswort) {
    final PasswordResetToken eingeloest = einloesbarer(token);
    final Account konto =
        accounts.findById(eingeloest.accountId()).orElseThrow(PasswordResetLinkInvalid::new);

    final Instant jetzt = clock.instant();
    accounts.save(konto.changePassword(hasher.hash(neuesPasswort), jetzt));
    resetTokens.save(eingeloest.redeemed(jetzt));
  }

  private PasswordResetToken einloesbarer(final String token) {
    return resetTokens
        .findByTokenHash(tokens.hash(token))
        .filter(vorhandener -> vorhandener.isRedeemable(clock.instant()))
        .orElseThrow(PasswordResetLinkInvalid::new);
  }
}
