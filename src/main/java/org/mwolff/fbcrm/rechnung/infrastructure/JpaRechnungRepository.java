package org.mwolff.fbcrm.rechnung.infrastructure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungsposition;
import org.springframework.stereotype.Repository;

/**
 * Setzt den Port {@link RechnungRepository} auf JPA um und uebersetzt in beide Richtungen.
 *
 * <p>Die Rechnung ist mit ihren Positionen eine Einheit. Geschrieben wird sie darum als Ganzes,
 * aber ihre Positionszeilen werden <b>fortgeschrieben</b> und nicht ersetzt — wie beim Angebot seit
 * Plan #169, E2. Wiedererkannt wird eine Zeile an ihrer Angebotsposition: Je Rechnung gibt es zu
 * jeder hoechstens eine ({@code rechnung_position_je_angebotsposition}), also ist sie der
 * Schluessel. Eine eingereichte Position zu einer bekannten Angebotsposition uebernimmt den neuen
 * Stand samt Platz, eine zu einer neuen wird eingefuegt, und die Zeilen der Rechnung, die in der
 * neuen Liste fehlen, werden geloescht. Die Plaetze entstehen lueckenlos ab 1 aus der Reihenfolge
 * der Liste (E24), und gelesen wird in derselben Ordnung zurueck.
 *
 * <p><b>Warum das Aufruecken hier gutgeht.</b> Faellt eine Position weg, ruecken die folgenden auf,
 * und der Zwischenstand traegt zwangslaeufig zweimal denselben Platz. {@code
 * rechnung_position_reihenfolge} ist darum {@code DEFERRABLE INITIALLY DEFERRED}; die Datenbank
 * prueft erst beim Commit, und das Ergebnis ist wieder eindeutig.
 *
 * <p>Jede Liste holt ihre Positionen in <b>einer</b> zweiten Abfrage und ordnet sie danach den
 * Rechnungen zu; je Zeile einzeln nachzuladen waere die bekannte Abfrage-Lawine.
 *
 * <p>Geloescht wird eine Rechnung mit ihren Positionen: erst die Zeilen, dann die Rechnung — der
 * Fremdschluessel traegt kein {@code ON DELETE}. Dass nur ein Entwurf geloescht werden darf, haelt
 * der Anwendungsfall fest.
 */
/*
 * Neun Methoden des Ports und fuenf private Uebersetzer ergeben vierzehn. Sie weiter aufzuteilen
 * verschoebe die Zahl, ohne etwas zu klaeren: Der Adapter ist die eine Stelle, an der Rechnung und
 * Zeile ineinander uebergehen, und genau darum stehen beide Richtungen hier beieinander.
 */
@SuppressWarnings("PMD.TooManyMethods")
@Repository
class JpaRechnungRepository implements RechnungRepository {

  private final SpringDataRechnungRepository rechnungen;
  private final SpringDataRechnungPositionRepository positionen;

  JpaRechnungRepository(
      final SpringDataRechnungRepository rechnungen,
      final SpringDataRechnungPositionRepository positionen) {
    this.rechnungen = rechnungen;
    this.positionen = positionen;
  }

  @Override
  public Optional<Rechnung> findById(final long id) {
    return rechnungen.findById(id).map(zeile -> toDomain(zeile, positionen.findByRechnung(id)));
  }

  @Override
  public Optional<Rechnung> findByIdMitSperre(final long id) {
    return rechnungen
        .sperreUndLies(id)
        .map(zeile -> toDomain(zeile, positionen.findByRechnung(id)));
  }

  @Override
  public List<Rechnung> findAlle() {
    return mitPositionen(rechnungen.findAll());
  }

  @Override
  public List<Rechnung> findByAngebot(final long angebotId) {
    return mitPositionen(rechnungen.findByAngebot(angebotId));
  }

  /*
   * Die Positionen mehrerer Rechnungen kommen in EINER zweiten Abfrage und werden danach
   * zugeordnet; je Zeile nachzuladen waere die bekannte Abfrage-Lawine. Ohne sie liesse sich die
   * Summe nicht rechnen (E5).
   */
  private List<Rechnung> mitPositionen(final List<RechnungEntity> zeilen) {
    if (zeilen.isEmpty()) {
      // Ohne diesen Zweig liefe eine Abfrage mit leerer IN-Liste los — kein gueltiges SQL.
      return List.of();
    }
    final Map<Long, List<RechnungPositionEntity>> jeRechnung =
        positionen
            .findByRechnungen(
                zeilen.stream().map(zeile -> Objects.requireNonNull(zeile.getId())).toList())
            .stream()
            .collect(Collectors.groupingBy(RechnungPositionEntity::getRechnungId));
    return zeilen.stream()
        .map(
            zeile ->
                toDomain(
                    zeile,
                    jeRechnung.getOrDefault(Objects.requireNonNull(zeile.getId()), List.of())))
        .toList();
  }

  @Override
  public Rechnung save(final Rechnung rechnung) {
    return mitZeilen(rechnungen.save(RechnungEntity.aus(rechnung)), rechnung.positionen());
  }

  @Override
  public Rechnung saveAndFlush(final Rechnung rechnung) {
    return mitZeilen(rechnungen.saveAndFlush(RechnungEntity.aus(rechnung)), rechnung.positionen());
  }

  /*
   * Die geschriebene Zeile samt ihren fortgeschriebenen Positionszeilen, zurueck in der Sprache der
   * Domaene. Beide Schreibwege gehen hier zusammen; sie unterscheiden sich nur darin, wann die
   * Rechnungszeile die Datenbank erreicht.
   */
  private Rechnung mitZeilen(
      final RechnungEntity zeile, final List<Rechnungsposition> eingereicht) {
    final long rechnungId = Objects.requireNonNull(zeile.getId());
    return toDomain(zeile, fortgeschrieben(rechnungId, eingereicht));
  }

  @Override
  public void delete(final long id) {
    positionen.deleteAll(positionen.findByRechnung(id));
    rechnungen.deleteById(id);
  }

  @Override
  public boolean existiertNummer(final String nummer) {
    return rechnungen.existiertNummer(nummer);
  }

  /*
   * Die Positionszeilen der Rechnung auf den Stand der eingereichten Liste bringen. Der Platz
   * entsteht aus der Stelle in der Liste (E24); die Reihenfolge der zurueckgegebenen Zeilen ist die
   * der Liste, damit toDomain sie unveraendert weitergeben kann.
   */
  private List<RechnungPositionEntity> fortgeschrieben(
      final long rechnungId, final List<Rechnungsposition> eingereicht) {
    final Map<Long, RechnungPositionEntity> vorhanden = new LinkedHashMap<>();
    for (final RechnungPositionEntity zeile : positionen.findByRechnung(rechnungId)) {
      vorhanden.put(zeile.getAngebotPositionId(), zeile);
    }
    final List<RechnungPositionEntity> zeilen = new ArrayList<>(eingereicht.size());
    short platz = 1;
    for (final Rechnungsposition position : eingereicht) {
      zeilen.add(zeile(rechnungId, platz, position, vorhanden));
      platz++;
    }
    // Was die neue Liste nicht mehr nennt, faellt weg — die Positionen sind Teil der Rechnung.
    positionen.deleteAll(vorhanden.values());
    return positionen.saveAll(zeilen);
  }

  /*
   * Die Zeile zu einer eingereichten Position: die vorhandene zu ihrer Angebotsposition, sonst eine
   * neue. Eine gefundene Zeile wird aus 'vorhanden' entnommen — was darin zurueckbleibt, ist zu
   * loeschen.
   */
  private static RechnungPositionEntity zeile(
      final long rechnungId,
      final short platz,
      final Rechnungsposition position,
      final Map<Long, RechnungPositionEntity> vorhanden) {
    final RechnungPositionEntity zeile = vorhanden.remove(position.angebotPositionId());
    if (zeile == null) {
      return RechnungPositionEntity.aus(position, rechnungId, platz);
    }
    zeile.uebernehme(
        platz,
        position.bezeichnung(),
        position.menge(),
        position.einheit(),
        position.einzelpreis());
    return zeile;
  }

  private static Rechnung toDomain(
      final RechnungEntity zeile, final List<RechnungPositionEntity> positionszeilen) {
    return new Rechnung(
        zeile.getId(),
        zeile.getAngebotId(),
        zeile.getZustand(),
        zeile.getRechnungDatum(),
        zeile.getLeistungszeitraum(),
        positionszeilen.stream().map(JpaRechnungRepository::toDomain).toList(),
        zeile.getNummer(),
        zeile.getSteuersatz(),
        zeile.getZahlungszielTage(),
        zeile.getGestelltAm(),
        zeile.getPdfSchluessel(),
        zeile.getEmpfaenger(),
        zeile.getAbsender(),
        zeile.getCreatedAt(),
        zeile.getUpdatedAt());
  }

  private static Rechnungsposition toDomain(final RechnungPositionEntity zeile) {
    return new Rechnungsposition(
        zeile.getAngebotPositionId(),
        zeile.getBezeichnung(),
        zeile.getMenge(),
        zeile.getEinheit(),
        zeile.getEinzelpreis());
  }
}
