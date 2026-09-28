package org.mwolff.fbcrm.auftrag.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.mwolff.fbcrm.auftrag.domain.Auftrag;
import org.mwolff.fbcrm.auftrag.domain.AuftragRepository;
import org.mwolff.fbcrm.auftrag.domain.Auftragsposition;
import org.springframework.stereotype.Repository;

/**
 * Setzt den Port {@link AuftragRepository} auf JPA um und uebersetzt in beide Richtungen.
 *
 * <p>Der Auftrag ist mit seinen Positionen eine Einheit. Geschrieben wird er darum als Ganzes: Die
 * alten Positionszeilen fallen weg, und die neuen entstehen mit den Plaetzen 1 bis n in der
 * Reihenfolge der Liste. Gelesen wird in derselben Ordnung zurueck — damit ist die Reihenfolge eine
 * Zusage des Bestands und nicht der Zufall der Einfuegereihenfolge. Geloescht wird nach derselben
 * Regel: erst die Positionen, dann die Zeile, die sie traegt (Kriterium 15).
 *
 * <p>Die Liste eines Vorgangs holt ihre Positionen in <b>einer</b> zweiten Abfrage und ordnet sie
 * danach den Auftraegen zu; je Zeile einzeln nachzuladen waere die bekannte Abfrage-Lawine.
 */
@Repository
class JpaAuftragRepository implements AuftragRepository {

  private final SpringDataAuftragRepository auftraege;
  private final SpringDataAuftragPositionRepository positionen;

  JpaAuftragRepository(
      final SpringDataAuftragRepository auftraege,
      final SpringDataAuftragPositionRepository positionen) {
    this.auftraege = auftraege;
    this.positionen = positionen;
  }

  @Override
  public Optional<Auftrag> findById(final long id) {
    return auftraege.findById(id).map(this::mitSeinenPositionen);
  }

  @Override
  public Optional<Auftrag> findByAngebot(final long angebotId) {
    return auftraege.findByAngebot(angebotId).map(this::mitSeinenPositionen);
  }

  /* Eine einzelne Zeile holt ihre Positionen selbst — nach Platz geordnet, wie sie gespeichert sind. */
  private Auftrag mitSeinenPositionen(final AuftragEntity zeile) {
    final long auftragId = Objects.requireNonNull(zeile.getId());
    return toDomain(zeile, positionen.findByAuftrag(auftragId));
  }

  @Override
  public List<Auftrag> findByVorgang(final long vorgangId) {
    final List<AuftragEntity> zeilen = auftraege.findByVorgang(vorgangId);
    if (zeilen.isEmpty()) {
      // Ohne diesen Zweig liefe eine Abfrage mit leerer IN-Liste los — kein gueltiges SQL.
      return List.of();
    }
    final Map<Long, List<AuftragPositionEntity>> jeAuftrag =
        positionen
            .findByAuftraege(
                zeilen.stream().map(zeile -> Objects.requireNonNull(zeile.getId())).toList())
            .stream()
            .collect(Collectors.groupingBy(AuftragPositionEntity::getAuftragId));
    return zeilen.stream()
        .map(
            zeile ->
                toDomain(
                    zeile,
                    jeAuftrag.getOrDefault(Objects.requireNonNull(zeile.getId()), List.of())))
        .toList();
  }

  @Override
  public Auftrag save(final Auftrag auftrag) {
    final AuftragEntity zeile = auftraege.save(toEntity(auftrag));
    final long auftragId = Objects.requireNonNull(zeile.getId());
    positionen.loescheZuAuftrag(auftragId);
    return toDomain(zeile, positionen.saveAll(positionszeilen(auftragId, auftrag.positionen())));
  }

  @Override
  public void loesche(final long id) {
    // Erst die Positionen: Der Fremdschluessel auftrag_position.auftrag_id traegt kein ON DELETE,
    // und das soll er auch nicht — eine Zeile verschwindet nur, wenn jemand es ausdruecklich sagt.
    positionen.loescheZuAuftrag(id);
    auftraege.deleteById(id);
  }

  private static List<AuftragPositionEntity> positionszeilen(
      final long auftragId, final List<Auftragsposition> positionen) {
    final List<AuftragPositionEntity> zeilen = new ArrayList<>(positionen.size());
    short platz = 1;
    for (final Auftragsposition position : positionen) {
      zeilen.add(
          new AuftragPositionEntity(
              null,
              auftragId,
              platz,
              position.bezeichnung(),
              position.abrechnungsmodus(),
              position.menge(),
              position.einheit(),
              position.einzelpreis(),
              position.stundenJePersonentag()));
      platz++;
    }
    return zeilen;
  }

  private static Auftrag toDomain(
      final AuftragEntity zeile, final List<AuftragPositionEntity> positionszeilen) {
    return new Auftrag(
        zeile.getId(),
        zeile.getVorgangId(),
        zeile.getAngebotId(),
        zeile.getNummer(),
        zeile.getStatus(),
        zeile.getAuftragDatum(),
        zeile.getKundenbestellnummer(),
        zeile.getLeistungAb(),
        zeile.getLeistungBis(),
        positionszeilen.stream().map(JpaAuftragRepository::toDomain).toList(),
        zeile.getCreatedAt(),
        zeile.getUpdatedAt());
  }

  private static Auftragsposition toDomain(final AuftragPositionEntity zeile) {
    return new Auftragsposition(
        zeile.getBezeichnung(),
        zeile.getAbrechnungsmodus(),
        zeile.getMenge(),
        zeile.getEinheit(),
        zeile.getEinzelpreis(),
        zeile.getStundenJePersonentag());
  }

  private static AuftragEntity toEntity(final Auftrag auftrag) {
    return new AuftragEntity(
        auftrag.id(),
        auftrag.vorgangId(),
        auftrag.angebotId(),
        auftrag.nummer(),
        auftrag.status(),
        auftrag.auftragDatum(),
        auftrag.kundenbestellnummer(),
        auftrag.leistungAb(),
        auftrag.leistungBis(),
        auftrag.createdAt(),
        auftrag.updatedAt());
  }
}
