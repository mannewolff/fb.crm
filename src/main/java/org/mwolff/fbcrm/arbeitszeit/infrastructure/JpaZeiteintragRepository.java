package org.mwolff.fbcrm.arbeitszeit.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.mwolff.fbcrm.arbeitszeit.domain.Zeiteintrag;
import org.mwolff.fbcrm.arbeitszeit.domain.ZeiteintragRepository;
import org.springframework.stereotype.Repository;

/**
 * Setzt den Port {@link ZeiteintragRepository} auf JPA um und uebersetzt in beide Richtungen.
 *
 * <p>Jeder Zeiteintrag ist eine Zeile fuer sich: Anlegen, Fortschreiben und Loeschen gehen einzeln,
 * wie beim Kommentar am Angebot und anders als bei den Positionen eines Belegs.
 *
 * <p><b>Die Summen rechnet der Adapter, nicht die Datenbank.</b> Beide Abfragen holen die Zeilen
 * der angefragten Positionen, und addiert werden die <i>Minuten</i> der Eintraege; erst die Summe
 * wird in Stunden umgerechnet ({@code Zeiteintrag.stundenAus}). So steht die Umrechnung an einer
 * Stelle (Plan #194, E5): Eine Zeitarithmetik in der Abfrage waere eine zweite Formel fuer dieselbe
 * Dauer, die beim ersten Nachziehen auseinanderliefe — und sie liesse sich nur gegen eine echte
 * Datenbank pruefen.
 *
 * <p>Jede angefragte Position steht im Ergebnis, auch die ohne Eintrag: Die Abbildung beginnt mit 0
 * je Position, und die geladenen Zeilen heben nur die an, zu denen es Zeit gibt — dasselbe Muster
 * wie {@code JpaAnsprechpartnerRepository.zaehleAktiveJeFirma}. Ein Waechter fuer die leere Anfrage
 * steht hier nicht: Hibernate uebersetzt eine leere {@code in}-Liste in eine Bedingung, die keine
 * Zeile trifft, und das Ergebnis ist die leere Abbildung.
 *
 * <p>Die beiden Summen ueber <b>alle</b> Angebote (Issue #211) gehen durch denselben Summierer, nur
 * mit leerer Vorbelegung: Es gibt keine angefragte Position, also steht am Ende genau die darin, zu
 * der eine Zeile gefunden wurde. Sie brauchen auch keine eigene Abfrage — die Zeilen liefern {@code
 * findAll} beziehungsweise das vorhandene {@code findImZeitraum}, das schon die Monatsliste der
 * Ansicht bedient.
 */
/*
 * Acht Methoden des Ports und drei private Helfer ergeben elf. Sie weiter aufzuteilen verschoebe
 * die Zahl, ohne etwas zu klaeren: Die Breite kommt vom Port — vier Summen in zwei Paaren —, und
 * der Adapter ist die eine Stelle, an der Zeiteintrag und Zeile ineinander uebergehen. Dasselbe
 * Vorgehen wie bei {@code JpaRechnungRepository}.
 */
@SuppressWarnings("PMD.TooManyMethods")
@Repository
class JpaZeiteintragRepository implements ZeiteintragRepository {

  private final SpringDataZeiteintragRepository zeilen;

  JpaZeiteintragRepository(final SpringDataZeiteintragRepository zeilen) {
    this.zeilen = zeilen;
  }

  @Override
  public Optional<Zeiteintrag> findById(final long id) {
    return zeilen.findById(id).map(JpaZeiteintragRepository::toDomain);
  }

  @Override
  public List<Zeiteintrag> findImZeitraum(final LocalDate von, final LocalDate bis) {
    return zeilen.findImZeitraum(von, bis).stream()
        .map(JpaZeiteintragRepository::toDomain)
        .toList();
  }

  @Override
  public Zeiteintrag save(final Zeiteintrag zeiteintrag) {
    return toDomain(zeilen.save(toEntity(zeiteintrag)));
  }

  @Override
  public void delete(final long id) {
    zeilen.deleteById(id);
  }

  @Override
  public Map<Long, BigDecimal> angefallenJePosition(final Set<Long> angebotPositionIds) {
    return stundenJePosition(
        angebotPositionIds, zeilen.findByPositionen(List.copyOf(angebotPositionIds)));
  }

  @Override
  public Map<Long, BigDecimal> stundenJePositionImMonat(
      final Set<Long> angebotPositionIds, final YearMonth monat) {
    return stundenJePosition(
        angebotPositionIds,
        zeilen.findByPositionenImZeitraum(
            List.copyOf(angebotPositionIds), monat.atDay(1), monat.atEndOfMonth()));
  }

  @Override
  public Map<Long, BigDecimal> alleAngefallenJePosition() {
    return stundenJePosition(Set.of(), zeilen.findAll());
  }

  @Override
  public Map<Long, BigDecimal> alleStundenJePositionImMonat(final YearMonth monat) {
    return stundenJePosition(Set.of(), zeilen.findImZeitraum(monat.atDay(1), monat.atEndOfMonth()));
  }

  /*
   * Erst die Minuten je Position addieren, dann umrechnen. Die Reihenfolge ist nicht beliebig: Die
   * Summe der Stunden einzelner Eintraege kann um Rundungsreste von der Summe ihrer Minuten
   * abweichen, und gemeint ist die gearbeitete Zeit, nicht die Addition ihrer Anzeigewerte.
   */
  private static Map<Long, BigDecimal> stundenJePosition(
      final Set<Long> angebotPositionIds, final List<ZeiteintragEntity> gefunden) {
    final Map<Long, Long> minuten = new LinkedHashMap<>();
    angebotPositionIds.forEach(positionId -> minuten.put(positionId, 0L));
    gefunden.forEach(
        zeile -> minuten.merge(zeile.getAngebotPositionId(), toDomain(zeile).minuten(), Long::sum));
    final Map<Long, BigDecimal> stunden = new LinkedHashMap<>();
    minuten.forEach((positionId, summe) -> stunden.put(positionId, Zeiteintrag.stundenAus(summe)));
    return stunden;
  }

  private static Zeiteintrag toDomain(final ZeiteintragEntity zeile) {
    return new Zeiteintrag(
        zeile.getId(),
        zeile.getAngebotPositionId(),
        zeile.getTag(),
        zeile.getVon(),
        zeile.getBis(),
        zeile.getCreatedAt(),
        zeile.getUpdatedAt());
  }

  private static ZeiteintragEntity toEntity(final Zeiteintrag zeiteintrag) {
    return new ZeiteintragEntity(
        zeiteintrag.id(),
        zeiteintrag.angebotPositionId(),
        zeiteintrag.tag(),
        zeiteintrag.von(),
        zeiteintrag.bis(),
        zeiteintrag.createdAt(),
        zeiteintrag.updatedAt());
  }
}
