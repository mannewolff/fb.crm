package org.mwolff.fbcrm.angebot.infrastructure;

import java.util.List;
import java.util.Optional;
import org.mwolff.fbcrm.angebot.domain.Angebotskommentar;
import org.mwolff.fbcrm.angebot.domain.AngebotskommentarRepository;
import org.springframework.stereotype.Repository;

/**
 * Setzt den Port {@link AngebotskommentarRepository} auf JPA um und uebersetzt in beide Richtungen.
 *
 * <p>Jeder Kommentar ist eine Zeile fuer sich: Anlegen, Fortschreiben und Loeschen gehen einzeln,
 * anders als beim Angebot mit seinen Positionen (Plan #141, E2). Geloescht wird wirklich — kein
 * Stilllegen-Merkmal (E6).
 */
@Repository
class JpaAngebotskommentarRepository implements AngebotskommentarRepository {

  private final SpringDataAngebotKommentarRepository zeilen;

  JpaAngebotskommentarRepository(final SpringDataAngebotKommentarRepository zeilen) {
    this.zeilen = zeilen;
  }

  @Override
  public List<Angebotskommentar> findByAngebot(final long angebotId) {
    return zeilen.findByAngebot(angebotId).stream()
        .map(JpaAngebotskommentarRepository::toDomain)
        .toList();
  }

  @Override
  public Optional<Angebotskommentar> findById(final long id) {
    return zeilen.findById(id).map(JpaAngebotskommentarRepository::toDomain);
  }

  @Override
  public Angebotskommentar save(final Angebotskommentar kommentar) {
    return toDomain(zeilen.save(toEntity(kommentar)));
  }

  @Override
  public void deleteById(final long id) {
    zeilen.deleteById(id);
  }

  private static Angebotskommentar toDomain(final AngebotKommentarEntity zeile) {
    return new Angebotskommentar(
        zeile.getId(),
        zeile.getAngebotId(),
        zeile.getText(),
        zeile.getCreatedAt(),
        zeile.getUpdatedAt());
  }

  private static AngebotKommentarEntity toEntity(final Angebotskommentar kommentar) {
    return new AngebotKommentarEntity(
        kommentar.id(),
        kommentar.angebotId(),
        kommentar.text(),
        kommentar.createdAt(),
        kommentar.updatedAt());
  }
}
