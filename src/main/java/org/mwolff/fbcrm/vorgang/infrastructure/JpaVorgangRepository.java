package org.mwolff.fbcrm.vorgang.infrastructure;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;
import org.mwolff.fbcrm.vorgang.domain.VorgangRepository;
import org.springframework.stereotype.Repository;

/** Setzt den Port {@link VorgangRepository} auf JPA um und uebersetzt in beide Richtungen. */
@Repository
class JpaVorgangRepository implements VorgangRepository {

  /** Kein Suchtext: Der Leerstring steht an Position 1 jedes Titels und trifft jeden Vorgang. */
  private static final String OHNE_SUCHE = "";

  private final SpringDataVorgangRepository jpa;

  JpaVorgangRepository(final SpringDataVorgangRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public List<Vorgang> uebersicht(
      final String suche, final @Nullable Long nummer, final boolean auchAbgeschlossene) {
    return uebersetze(jpa.uebersicht(suche, nummer, auchAbgeschlossene, null));
  }

  @Override
  public List<Vorgang> findByFirma(final long firmaId) {
    // Dieselbe Abfrage und damit dieselbe Reihenfolge wie die Uebersicht (E16): alles zu dieser
    // einen Firma, offen und abgeschlossen. Die Trennung macht die Anwendungsschicht.
    return uebersetze(jpa.uebersicht(OHNE_SUCHE, null, true, firmaId));
  }

  @Override
  public Optional<Vorgang> findById(final long id) {
    return jpa.findById(id).map(JpaVorgangRepository::toDomain);
  }

  @Override
  public Vorgang save(final Vorgang vorgang) {
    return toDomain(jpa.save(toEntity(vorgang)));
  }

  @Override
  public long zaehleAlle() {
    return jpa.count();
  }

  private static List<Vorgang> uebersetze(final List<VorgangEntity> zeilen) {
    return zeilen.stream().map(JpaVorgangRepository::toDomain).toList();
  }

  private static Vorgang toDomain(final VorgangEntity zeile) {
    return new Vorgang(
        zeile.getId(),
        zeile.getNummer(),
        zeile.getTitel(),
        zeile.getFirmaId(),
        zeile.getAnsprechpartnerId(),
        zeile.isAbgeschlossen(),
        zeile.getCreatedAt(),
        zeile.getUpdatedAt());
  }

  private static VorgangEntity toEntity(final Vorgang vorgang) {
    return new VorgangEntity(
        vorgang.id(),
        vorgang.nummer(),
        vorgang.titel(),
        vorgang.firmaId(),
        vorgang.ansprechpartnerId(),
        vorgang.abgeschlossen(),
        vorgang.createdAt(),
        vorgang.updatedAt());
  }
}
