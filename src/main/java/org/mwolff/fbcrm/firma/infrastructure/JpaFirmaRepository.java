package org.mwolff.fbcrm.firma.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.springframework.stereotype.Repository;

/** Setzt den Port {@link FirmaRepository} auf JPA um und uebersetzt in beide Richtungen. */
@Repository
class JpaFirmaRepository implements FirmaRepository {

  private final SpringDataFirmaRepository jpa;

  JpaFirmaRepository(final SpringDataFirmaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public List<Firma> uebersicht(final String suche, final boolean auchStillgelegte) {
    return jpa.uebersicht(suche, auchStillgelegte).stream()
        .map(JpaFirmaRepository::toDomain)
        .toList();
  }

  @Override
  public Optional<Firma> findById(final long id) {
    return jpa.findById(id).map(JpaFirmaRepository::toDomain);
  }

  @Override
  public List<Firma> findAllById(final Collection<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return jpa.findAllById(ids).stream().map(JpaFirmaRepository::toDomain).toList();
  }

  @Override
  public Firma save(final Firma firma) {
    return toDomain(jpa.save(FirmaEntity.aus(firma)));
  }

  @Override
  public long zaehleAlle() {
    return jpa.count();
  }

  private static Firma toDomain(final FirmaEntity zeile) {
    return new Firma(
        zeile.getId(),
        zeile.getName(),
        zeile.getAnschrift(),
        zeile.getSteuernummer(),
        zeile.getUmsatzsteuerId(),
        zeile.isAktiv(),
        zeile.getCreatedAt(),
        zeile.getUpdatedAt());
  }
}
