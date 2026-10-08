package org.mwolff.fbcrm.angebot.infrastructure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.springframework.stereotype.Repository;

/**
 * Setzt den Port {@link AngebotRepository} auf JPA um und uebersetzt in beide Richtungen.
 *
 * <p>Das Angebot ist mit seinen Positionen eine Einheit. Geschrieben wird es darum als Ganzes, aber
 * seine Positionszeilen werden <b>fortgeschrieben</b> und nicht ersetzt (Plan #169, E2): Eine
 * eingereichte Position mit Kennung findet ihre Zeile wieder und uebernimmt den neuen Stand samt
 * Platz, eine ohne Kennung wird eingefuegt, und die Zeilen des Angebots, die in der neuen Liste
 * fehlen, werden geloescht. So bleibt eine Position derselbe Gegenstand, auch wenn das Angebot
 * umgeordnet oder umbenannt wird — darauf beruft sich spaeter eine Rechnung (#160, Kriterium 28).
 * Die Plaetze entstehen weiter lueckenlos ab 1 aus der Reihenfolge der Liste (E24), und gelesen
 * wird in derselben Ordnung zurueck. Geloescht wird ein Angebot nie (Issue #127).
 *
 * <p><b>Warum der Tausch zweier Plaetze hier gutgeht.</b> Werden zwei Positionen umgeordnet, traegt
 * der Zwischenstand zwangslaeufig zweimal denselben Platz. Seit {@code
 * V15__angebot_position_kennung.sql} ist {@code angebot_position_reihenfolge} aufschiebbar; die
 * Datenbank prueft erst beim Commit, und das Ergebnis ist wieder eindeutig.
 *
 * <p><b>Eine fremde Kennung ist hier ein Programmierfehler.</b> Gearbeitet wird ausschliesslich
 * gegen die Zeilen <i>dieses</i> Angebots; eine Kennung ausserhalb wirft {@link
 * IllegalArgumentException} und keine Zeile wird geschrieben. Die fachliche Abweisung mit 422 liegt
 * im Anwendungsfall ({@code AngebotAendernUseCase}) — sie laeuft vor dem Schreiben und nennt das
 * Feld der Maske.
 *
 * <p>Jede Liste holt ihre Positionen in <b>einer</b> zweiten Abfrage und ordnet sie danach den
 * Angeboten zu; je Zeile einzeln nachzuladen waere die bekannte Abfrage-Lawine. Die Angebotsliste
 * einer Firma und die Uebersicht aller Angebote gehen diesen Weg.
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

  @Override
  public List<Angebot> findAlle(final Optional<Angebotsstatus> status) {
    return mitPositionen(status.map(angebote::findByStatus).orElseGet(angebote::findAll));
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
    final AngebotEntity zeile = angebote.save(AngebotEntity.aus(angebot));
    final long angebotId = Objects.requireNonNull(zeile.getId());
    return toDomain(zeile, fortgeschrieben(angebotId, angebot.positionen()));
  }

  /*
   * Die Positionszeilen des Angebots auf den Stand der eingereichten Liste bringen. Der Platz
   * entsteht aus der Stelle in der Liste (E24); die Reihenfolge der zurueckgegebenen Zeilen ist die
   * der Liste, damit toDomain sie unveraendert weitergeben kann.
   */
  private List<AngebotPositionEntity> fortgeschrieben(
      final long angebotId, final List<Angebotsposition> eingereicht) {
    final Map<Long, AngebotPositionEntity> vorhanden = new LinkedHashMap<>();
    for (final AngebotPositionEntity zeile : positionen.findByAngebot(angebotId)) {
      vorhanden.put(Objects.requireNonNull(zeile.getId()), zeile);
    }
    final List<AngebotPositionEntity> zeilen = new ArrayList<>(eingereicht.size());
    short platz = 1;
    for (final Angebotsposition position : eingereicht) {
      zeilen.add(zeile(angebotId, platz, position, vorhanden));
      platz++;
    }
    // Was die neue Liste nicht mehr nennt, faellt weg — die Positionen sind Teil des Angebots.
    positionen.deleteAll(vorhanden.values());
    return positionen.saveAll(zeilen);
  }

  /*
   * Die Zeile zu einer eingereichten Position: die vorhandene mit ihrer Kennung, sonst eine neue.
   * Eine gefundene Zeile wird aus 'vorhanden' entnommen — was darin zurueckbleibt, ist zu loeschen.
   */
  private static AngebotPositionEntity zeile(
      final long angebotId,
      final short platz,
      final Angebotsposition position,
      final Map<Long, AngebotPositionEntity> vorhanden) {
    final Long kennung = position.id();
    if (kennung == null) {
      return AngebotPositionEntity.aus(position, angebotId, platz);
    }
    final AngebotPositionEntity zeile = vorhanden.remove(kennung);
    if (zeile == null) {
      throw new IllegalArgumentException(
          "Position " + kennung + " gehoert nicht zu Angebot " + angebotId);
    }
    zeile.uebernehme(
        platz,
        position.bezeichnung(),
        position.abrechnungsmodus(),
        position.menge(),
        position.einheit(),
        position.einzelpreis());
    return zeile;
  }

  private static Angebot toDomain(
      final AngebotEntity zeile, final List<AngebotPositionEntity> positionszeilen) {
    return new Angebot(
        zeile.getId(),
        zeile.getFirmaId(),
        zeile.getAnsprechpartnerId(),
        zeile.isIntern(),
        zeile.getStatus(),
        zeile.getAngebotDatum(),
        zeile.getBeschreibung(),
        positionszeilen.stream().map(JpaAngebotRepository::toDomain).toList(),
        zeile.getCreatedAt(),
        zeile.getUpdatedAt());
  }

  private static Angebotsposition toDomain(final AngebotPositionEntity zeile) {
    return new Angebotsposition(
        zeile.getId(),
        zeile.getBezeichnung(),
        zeile.getAbrechnungsmodus(),
        zeile.getMenge(),
        zeile.getEinheit(),
        zeile.getEinzelpreis());
  }
}
