package org.mwolff.fbcrm.auth.application;

import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Konto der laufenden Sitzung (K13).
 *
 * <p>Geliefert wird das Konto frisch aus dem Bestand und nicht das, was der Filter beim Pruefen der
 * Sitzung gesehen hat: Anzeigename und Adresse sollen aktuell sein, nicht so alt wie das Token.
 */
@Service
public class GetCurrentAccountUseCase {

  private final AccountRepository accounts;

  public GetCurrentAccountUseCase(final AccountRepository accounts) {
    this.accounts = accounts;
  }

  /**
   * Das Konto zu einer Sitzung.
   *
   * @param accountId Konto aus dem geprueften Session-Token
   * @throws UnknownAccount wenn es das Konto nicht mehr gibt
   */
  @Transactional(readOnly = true)
  public Account byId(final long accountId) {
    return accounts.findById(accountId).orElseThrow(UnknownAccount::new);
  }
}
