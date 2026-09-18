package org.mwolff.fbcrm.auth.domain;

import java.util.Optional;

/** Port auf den Bestand der Konten; die Umsetzung liegt in {@code auth.infrastructure}. */
public interface AccountRepository {

  /** Das Konto zu einer technischen Id, oder leer. */
  Optional<Account> findById(long id);

  /** Das Konto zu einer Anmeldeadresse ohne Ruecksicht auf Gross- und Kleinschreibung. */
  Optional<Account> findByEmail(String email);

  /** Ob es bereits ein Konto mit der Rolle {@link Role#ADMIN} gibt. */
  boolean existsAnyAdmin();

  /** Legt das Konto an oder schreibt es fort und liefert es mit gesetzter Id zurueck. */
  Account save(Account account);
}
