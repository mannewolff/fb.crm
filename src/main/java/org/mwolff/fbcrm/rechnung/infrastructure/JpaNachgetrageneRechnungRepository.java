package org.mwolff.fbcrm.rechnung.infrastructure;

import java.util.List;
import java.util.Optional;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnung;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnungRepository;
import org.springframework.stereotype.Repository;

/**
 * Setzt den Port {@link NachgetrageneRechnungRepository} auf JPA um und uebersetzt in beide
 * Richtungen (Plan #259).
 *
 * <p>Anders als bei {@link JpaRechnungRepository} gibt es keine Positionen: Die nachgetragene
 * Rechnung ist eine Zeile, und jede Methode reicht an Spring Data durch.
 */
@Repository
class JpaNachgetrageneRechnungRepository implements NachgetrageneRechnungRepository {

  private final SpringDataNachgetrageneRechnungRepository zeilen;

  JpaNachgetrageneRechnungRepository(final SpringDataNachgetrageneRechnungRepository zeilen) {
    this.zeilen = zeilen;
  }

  @Override
  public Optional<NachgetrageneRechnung> findById(final long id) {
    return zeilen.findById(id).map(JpaNachgetrageneRechnungRepository::toDomain);
  }

  @Override
  public List<NachgetrageneRechnung> findAlle() {
    return zeilen.findAll().stream().map(JpaNachgetrageneRechnungRepository::toDomain).toList();
  }

  @Override
  public NachgetrageneRechnung save(final NachgetrageneRechnung rechnung) {
    return toDomain(zeilen.save(NachgetrageneRechnungEntity.aus(rechnung)));
  }

  @Override
  public void delete(final long id) {
    zeilen.deleteById(id);
  }

  @Override
  public boolean existiertNummer(final String nummer) {
    return zeilen.existiertNummer(nummer);
  }

  @Override
  public boolean existiertNummerAusser(final String nummer, final long id) {
    return zeilen.existiertNummerAusser(nummer, id);
  }

  private static NachgetrageneRechnung toDomain(final NachgetrageneRechnungEntity zeile) {
    return new NachgetrageneRechnung(
        zeile.getId(),
        zeile.getFirmaId(),
        zeile.getNummer(),
        zeile.getRechnungDatum(),
        zeile.getNetto(),
        zeile.getBrutto(),
        zeile.getZustand(),
        zeile.getPdfSchluessel(),
        zeile.getCreatedAt(),
        zeile.getUpdatedAt());
  }
}
