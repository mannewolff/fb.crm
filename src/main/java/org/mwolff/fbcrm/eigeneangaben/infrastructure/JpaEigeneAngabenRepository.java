package org.mwolff.fbcrm.eigeneangaben.infrastructure;

import java.time.Instant;
import java.util.NoSuchElementException;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngabenRepository;
import org.springframework.stereotype.Repository;

/**
 * Setzt den Port {@link EigeneAngabenRepository} auf JPA um und uebersetzt in beide Richtungen.
 *
 * <p>Die Zeile ist da — {@code V5__eigene_angaben.sql} legt sie an. Fehlt sie doch, ist das kein
 * Fachfall, sondern ein kaputtes Schema; deshalb fliegt hier eine {@link NoSuchElementException}
 * und keine fachliche Ausnahme.
 */
@Repository
class JpaEigeneAngabenRepository implements EigeneAngabenRepository {

  private final SpringDataEigeneAngabenRepository jpa;

  JpaEigeneAngabenRepository(final SpringDataEigeneAngabenRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public EigeneAngaben lies() {
    return toDomain(jpa.findById(EigeneAngabenEntity.ZEILE).orElseThrow());
  }

  @Override
  public void speichere(final EigeneAngaben angaben, final Instant geaendertAm) {
    jpa.save(EigeneAngabenEntity.aus(angaben, geaendertAm));
  }

  private static EigeneAngaben toDomain(final EigeneAngabenEntity zeile) {
    return new EigeneAngaben(
        zeile.getName(),
        zeile.getBerufsbezeichnung(),
        zeile.getAnschrift(),
        zeile.getEmail(),
        zeile.getTelefon(),
        zeile.getWebadresse(),
        zeile.getSteuernummer(),
        zeile.getUmsatzsteuerId(),
        zeile.getBankverbindung());
  }
}
