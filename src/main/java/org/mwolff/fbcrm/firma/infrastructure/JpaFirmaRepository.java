package org.mwolff.fbcrm.firma.infrastructure;

import java.util.List;
import java.util.Optional;
import org.mwolff.fbcrm.firma.domain.Anschrift;
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
  public Firma save(final Firma firma) {
    return toDomain(jpa.save(toEntity(firma)));
  }

  @Override
  public long zaehleAlle() {
    return jpa.count();
  }

  private static Firma toDomain(final FirmaEntity zeile) {
    return new Firma(
        zeile.getId(),
        zeile.getName(),
        new Anschrift(zeile.getStrasse(), zeile.getPlz(), zeile.getOrt(), zeile.getLand()),
        zeile.getSteuernummer(),
        zeile.getUmsatzsteuerId(),
        zeile.isAktiv(),
        zeile.getCreatedAt(),
        zeile.getUpdatedAt());
  }

  private static FirmaEntity toEntity(final Firma firma) {
    final Anschrift anschrift = firma.anschrift();
    return new FirmaEntity(
        firma.id(),
        firma.name(),
        anschrift.strasse(),
        anschrift.plz(),
        anschrift.ort(),
        anschrift.land(),
        firma.steuernummer(),
        firma.umsatzsteuerId(),
        firma.aktiv(),
        firma.createdAt(),
        firma.updatedAt());
  }
}
