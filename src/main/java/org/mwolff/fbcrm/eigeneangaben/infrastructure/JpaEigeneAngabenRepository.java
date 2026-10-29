package org.mwolff.fbcrm.eigeneangaben.infrastructure;

import java.time.Instant;
import java.util.NoSuchElementException;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngabenRepository;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngabenStand;
import org.springframework.stereotype.Repository;

/**
 * Setzt den Port {@link EigeneAngabenRepository} auf JPA um und uebersetzt in beide Richtungen.
 *
 * <p>Die Zeile ist da — {@code V5__eigene_angaben.sql} legt sie an. Fehlt sie doch, ist das kein
 * Fachfall, sondern ein kaputtes Schema; deshalb fliegt hier eine {@link NoSuchElementException}
 * und keine fachliche Ausnahme.
 *
 * <p>Geschrieben wird mit {@code saveAndFlush}: Erst das Schreiben erhoeht die Version, und der
 * neue Stand geht mit der Antwort hinaus (Plan #415, E2).
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
  public EigeneAngabenStand stand() {
    return standVon(jpa.findById(EigeneAngabenEntity.ZEILE).orElseThrow());
  }

  @Override
  public EigeneAngabenStand speichere(
      final EigeneAngaben angaben,
      final Instant geaendertAm,
      final long version,
      final long konto) {
    return standVon(
        jpa.saveAndFlush(EigeneAngabenEntity.aus(angaben, geaendertAm, version, konto)));
  }

  private static EigeneAngabenStand standVon(final EigeneAngabenEntity zeile) {
    return new EigeneAngabenStand(
        zeile.getVersion(), zeile.getGeaendertVon(), zeile.getUpdatedAt());
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
        zeile.getIban(),
        zeile.getBic(),
        zeile.getKontoinhaber(),
        zeile.getBankverbindung());
  }
}
