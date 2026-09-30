package org.mwolff.fbcrm.rechnung.infrastructure;

import java.time.Instant;
import java.util.NoSuchElementException;
import org.mwolff.fbcrm.rechnung.domain.Nummernmuster;
import org.mwolff.fbcrm.rechnung.domain.Rechnungseinstellungen;
import org.mwolff.fbcrm.rechnung.domain.RechnungseinstellungenRepository;
import org.springframework.stereotype.Repository;

/**
 * Setzt den Port {@link RechnungseinstellungenRepository} auf JPA um und uebersetzt in beide
 * Richtungen.
 *
 * <p>Die Zeile ist da — {@code V14__rechnung_einstellungen.sql} legt sie an. Fehlt sie doch, ist
 * das kein Fachfall, sondern ein kaputtes Schema; deshalb fliegt hier eine {@link
 * NoSuchElementException} und keine fachliche Ausnahme.
 */
@Repository
class JpaRechnungseinstellungenRepository implements RechnungseinstellungenRepository {

  private final SpringDataRechnungseinstellungenRepository jpa;

  JpaRechnungseinstellungenRepository(final SpringDataRechnungseinstellungenRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Rechnungseinstellungen lies() {
    return toDomain(jpa.findById(RechnungseinstellungenEntity.ZEILE).orElseThrow());
  }

  @Override
  public void speichere(final Rechnungseinstellungen einstellungen, final Instant geaendertAm) {
    jpa.save(toEntity(einstellungen, geaendertAm));
  }

  private static Rechnungseinstellungen toDomain(final RechnungseinstellungenEntity zeile) {
    return new Rechnungseinstellungen(
        new Nummernmuster(zeile.getNummerMuster()),
        zeile.getNaechsteNummer(),
        zeile.getSteuersatz(),
        zeile.getZahlungszielTage());
  }

  private static RechnungseinstellungenEntity toEntity(
      final Rechnungseinstellungen einstellungen, final Instant geaendertAm) {
    return new RechnungseinstellungenEntity(
        einstellungen.nummerMuster().text(),
        einstellungen.naechsteNummer(),
        einstellungen.steuersatz(),
        einstellungen.zahlungszielTage(),
        geaendertAm);
  }
}
