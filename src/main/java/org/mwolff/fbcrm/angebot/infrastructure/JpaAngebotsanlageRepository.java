package org.mwolff.fbcrm.angebot.infrastructure;

import java.util.List;
import java.util.Optional;
import org.mwolff.fbcrm.angebot.domain.Angebotsanlage;
import org.mwolff.fbcrm.angebot.domain.AngebotsanlageRepository;
import org.springframework.stereotype.Repository;

/**
 * Setzt den Port {@link AngebotsanlageRepository} auf JPA um und uebersetzt in beide Richtungen.
 *
 * <p>Jede Anlage ist eine Zeile fuer sich: Anlegen und Loeschen gehen einzeln, anders als beim
 * Angebot mit seinen Positionen (Plan #150, E1). Geloescht wird seit Plan #415 (E7, E17) mit
 * Kennzeichen an der Zeile; die Liste laesst gekennzeichnete Zeilen aus.
 *
 * <p>Der Inhalt der Anlage steht nicht in dieser Zeile, sondern im Objektspeicher; hier steht nur
 * sein Schluessel.
 */
@Repository
class JpaAngebotsanlageRepository implements AngebotsanlageRepository {

  private final SpringDataAngebotAnlageRepository zeilen;

  JpaAngebotsanlageRepository(final SpringDataAngebotAnlageRepository zeilen) {
    this.zeilen = zeilen;
  }

  @Override
  public List<Angebotsanlage> findByAngebot(final long angebotId) {
    return zeilen.findByAngebot(angebotId).stream()
        .map(JpaAngebotsanlageRepository::toDomain)
        .toList();
  }

  @Override
  public Optional<Angebotsanlage> findById(final long id) {
    return zeilen.findById(id).map(JpaAngebotsanlageRepository::toDomain);
  }

  @Override
  public Angebotsanlage save(final Angebotsanlage anlage) {
    return toDomain(zeilen.save(new AngebotAnlageEntity(anlage)));
  }

  private static Angebotsanlage toDomain(final AngebotAnlageEntity zeile) {
    return new Angebotsanlage(
        zeile.getId(),
        zeile.getAngebotId(),
        zeile.getDateiName(),
        zeile.getGroesse(),
        zeile.getVorschauArt(),
        zeile.getObjektSchluessel(),
        zeile.getCreatedAt(),
        zeile.getGeloeschtAm(),
        zeile.getGeloeschtVon());
  }
}
