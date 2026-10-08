package org.mwolff.fbcrm.auth.application;

import org.mwolff.fbcrm.auth.domain.Account;

/**
 * Das Ergebnis einer erfolgreichen Anmeldung.
 *
 * @param account das angemeldete Konto
 * @param cookie das Session-Cookie, das der Aufrufer mitbekommt
 */
public record LoginResult(Account account, SessionCookie cookie) {}
