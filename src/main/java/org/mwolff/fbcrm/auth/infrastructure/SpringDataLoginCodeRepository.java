package org.mwolff.fbcrm.auth.infrastructure;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring-Data-Zugriff auf {@code login_code}.
 *
 * <p>Suche und Loeschen ueber die Adresse ignorieren Gross- und Kleinschreibung und treffen damit
 * denselben Schluessel wie der eindeutige Index {@code login_code_email_key} auf {@code
 * lower(email)}.
 *
 * <p>Das abgeleitete Loeschen laedt die Zeile und entfernt sie danach; ohne eigene Transaktion
 * scheiterte es, sobald kein Aufrufer eine mitbringt.
 */
interface SpringDataLoginCodeRepository extends JpaRepository<LoginCodeEntity, Long> {

  Optional<LoginCodeEntity> findByEmailIgnoreCase(String email);

  @Transactional
  void deleteByEmailIgnoreCase(String email);
}
