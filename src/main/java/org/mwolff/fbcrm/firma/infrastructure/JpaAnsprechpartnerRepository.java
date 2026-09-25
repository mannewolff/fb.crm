package org.mwolff.fbcrm.firma.infrastructure;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.AnsprechpartnerRepository;
import org.springframework.stereotype.Repository;

/**
 * Setzt den Port {@link AnsprechpartnerRepository} auf JPA um und uebersetzt in beide Richtungen.
 */
@Repository
class JpaAnsprechpartnerRepository implements AnsprechpartnerRepository {

  private final SpringDataAnsprechpartnerRepository jpa;

  JpaAnsprechpartnerRepository(final SpringDataAnsprechpartnerRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<Ansprechpartner> findById(final long id) {
    return jpa.findById(id).map(JpaAnsprechpartnerRepository::toDomain);
  }

  @Override
  public Ansprechpartner save(final Ansprechpartner ansprechpartner) {
    return toDomain(jpa.save(toEntity(ansprechpartner)));
  }

  @Override
  public List<Ansprechpartner> findByFirma(final long firmaId) {
    return jpa.findByFirma(firmaId).stream().map(JpaAnsprechpartnerRepository::toDomain).toList();
  }

  @Override
  public Map<Long, Long> zaehleAktiveJeFirma(final Collection<Long> firmaIds) {
    // Zuerst steht jede angefragte Firma mit 0 in der Abbildung; die Zaehlung hebt nur die an,
    // die tatsaechlich aktive Ansprechpartner haben. Ein Waechter fuer die leere Anfrage steht
    // hier bewusst nicht: Er spart eine Abfrage, die es ohnehin nicht gibt, und waere ein Zweig,
    // den kein Test von seinem Gegenteil unterscheiden koennte.
    final Map<Long, Long> anzahlen = new LinkedHashMap<>();
    firmaIds.forEach(firmaId -> anzahlen.put(firmaId, 0L));
    jpa.zaehleAktiveJeFirma(List.copyOf(anzahlen.keySet()))
        .forEach(zeile -> anzahlen.put(zeile.getFirmaId(), zeile.getAnzahl()));
    return anzahlen;
  }

  private static Ansprechpartner toDomain(final AnsprechpartnerEntity zeile) {
    return new Ansprechpartner(
        zeile.getId(),
        zeile.getFirmaId(),
        zeile.getVorname(),
        zeile.getNachname(),
        zeile.getRolle(),
        zeile.getEmail(),
        zeile.getTelefonFestnetz(),
        zeile.getTelefonMobil(),
        zeile.isAktiv(),
        zeile.getCreatedAt(),
        zeile.getUpdatedAt());
  }

  private static AnsprechpartnerEntity toEntity(final Ansprechpartner ansprechpartner) {
    return new AnsprechpartnerEntity(
        ansprechpartner.id(),
        ansprechpartner.firmaId(),
        ansprechpartner.vorname(),
        ansprechpartner.nachname(),
        ansprechpartner.rolle(),
        ansprechpartner.email(),
        ansprechpartner.telefonFestnetz(),
        ansprechpartner.telefonMobil(),
        ansprechpartner.aktiv(),
        ansprechpartner.createdAt(),
        ansprechpartner.updatedAt());
  }
}
