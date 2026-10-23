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
 * anders als beim Angebot mit seinen Positionen (Plan #141, E2). Geloescht wird seit Plan #415
 * (E17) mit Kennzeichen an der Zeile; die Liste laesst gekennzeichnete Zeilen aus.
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

  /*
   * saveAndFlush statt save, aus demselben Grund wie in JpaAngebotRepository (Plan #415, E1): Den
   * Zaehler version erhoeht Hibernate erst beim Schreiben der Zeile, und die Antwort soll den neuen
   * Stand tragen, den die Ansicht beim naechsten Aendern vorlegt.
   */
  @Override
  public Angebotskommentar save(final Angebotskommentar kommentar) {
    return toDomain(zeilen.saveAndFlush(new AngebotKommentarEntity(kommentar)));
  }

  private static Angebotskommentar toDomain(final AngebotKommentarEntity zeile) {
    return new Angebotskommentar(
        zeile.getId(),
        zeile.getAngebotId(),
        zeile.getVerfasserId(),
        zeile.getText(),
        zeile.getCreatedAt(),
        zeile.getUpdatedAt(),
        zeile.getVersion(),
        zeile.getGeaendertVon(),
        zeile.getGeloeschtAm(),
        zeile.getGeloeschtVon());
  }
}
