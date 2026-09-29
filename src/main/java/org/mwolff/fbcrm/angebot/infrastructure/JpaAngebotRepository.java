package org.mwolff.fbcrm.angebot.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.springframework.stereotype.Repository;

/**
 * Setzt den Port {@link AngebotRepository} auf JPA um und uebersetzt in beide Richtungen.
 *
 * <p>Das Angebot ist mit seinen Positionen eine Einheit. Geschrieben wird es darum als Ganzes: Die
 * alten Positionszeilen fallen weg, und die neuen entstehen mit den Plaetzen 1 bis n in der
 * Reihenfolge der Liste (E24). Gelesen wird in derselben Ordnung zurueck — damit ist die
 * Reihenfolge eine Zusage des Bestands und nicht der Zufall der Einfuegereihenfolge. Geloescht wird
 * ein Angebot nie (Issue #127).
 *
 * <p>Jede Liste holt ihre Positionen in <b>einer</b> zweiten Abfrage und ordnet sie danach den
 * Angeboten zu; je Zeile einzeln nachzuladen waere die bekannte Abfrage-Lawine. Die Angebotsliste
 * einer Firma geht diesen Weg.
 */
@Repository
class JpaAngebotRepository implements AngebotRepository {

  private final SpringDataAngebotRepository angebote;
  private final SpringDataAngebotPositionRepository positionen;

  JpaAngebotRepository(
      final SpringDataAngebotRepository angebote,
      final SpringDataAngebotPositionRepository positionen) {
    this.angebote = angebote;
    this.positionen = positionen;
  }

  @Override
  public Optional<Angebot> findById(final long id) {
    return angebote.findById(id).map(zeile -> toDomain(zeile, positionen.findByAngebot(id)));
  }

  @Override
  public List<Angebot> findByFirma(final long firmaId) {
    return mitPositionen(angebote.findByFirma(firmaId));
  }

  /*
   * Die Positionen mehrerer Angebote kommen in EINER zweiten Abfrage und werden danach zugeordnet;
   * je Zeile nachzuladen waere die bekannte Abfrage-Lawine. Ohne sie liesse sich die Summe nicht
   * rechnen (E5).
   */
  private List<Angebot> mitPositionen(final List<AngebotEntity> zeilen) {
    if (zeilen.isEmpty()) {
      // Ohne diesen Zweig liefe eine Abfrage mit leerer IN-Liste los — kein gueltiges SQL.
      return List.of();
    }
    final Map<Long, List<AngebotPositionEntity>> jeAngebot =
        positionen
            .findByAngebote(
                zeilen.stream().map(zeile -> Objects.requireNonNull(zeile.getId())).toList())
            .stream()
            .collect(Collectors.groupingBy(AngebotPositionEntity::getAngebotId));
    return zeilen.stream()
        .map(
            zeile ->
                toDomain(
                    zeile,
                    jeAngebot.getOrDefault(Objects.requireNonNull(zeile.getId()), List.of())))
        .toList();
  }

  @Override
  public Angebot save(final Angebot angebot) {
    final AngebotEntity zeile = angebote.save(toEntity(angebot));
    final long angebotId = Objects.requireNonNull(zeile.getId());
    positionen.loescheZuAngebot(angebotId);
    return toDomain(zeile, positionen.saveAll(positionszeilen(angebotId, angebot.positionen())));
  }

  private static List<AngebotPositionEntity> positionszeilen(
      final long angebotId, final List<Angebotsposition> positionen) {
    final List<AngebotPositionEntity> zeilen = new ArrayList<>(positionen.size());
    short platz = 1;
    for (final Angebotsposition position : positionen) {
      zeilen.add(
          new AngebotPositionEntity(
              null,
              angebotId,
              platz,
              position.bezeichnung(),
              position.abrechnungsmodus(),
              position.menge(),
              position.einheit(),
              position.einzelpreis()));
      platz++;
    }
    return zeilen;
  }

  private static Angebot toDomain(
      final AngebotEntity zeile, final List<AngebotPositionEntity> positionszeilen) {
    return new Angebot(
        zeile.getId(),
        zeile.getFirmaId(),
        zeile.getAnsprechpartnerId(),
        zeile.getStatus(),
        zeile.getAngebotDatum(),
        zeile.getBeschreibung(),
        positionszeilen.stream().map(JpaAngebotRepository::toDomain).toList(),
        zeile.getCreatedAt(),
        zeile.getUpdatedAt());
  }

  private static Angebotsposition toDomain(final AngebotPositionEntity zeile) {
    return new Angebotsposition(
        zeile.getBezeichnung(),
        zeile.getAbrechnungsmodus(),
        zeile.getMenge(),
        zeile.getEinheit(),
        zeile.getEinzelpreis());
  }

  private static AngebotEntity toEntity(final Angebot angebot) {
    return new AngebotEntity(
        angebot.id(),
        angebot.firmaId(),
        angebot.ansprechpartnerId(),
        angebot.status(),
        angebot.angebotDatum(),
        angebot.beschreibung(),
        angebot.createdAt(),
        angebot.updatedAt());
  }
}
