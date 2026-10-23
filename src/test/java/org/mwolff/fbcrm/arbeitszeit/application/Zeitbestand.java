package org.mwolff.fbcrm.arbeitszeit.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.mwolff.fbcrm.arbeitszeit.domain.Zeiteintrag;
import org.mwolff.fbcrm.arbeitszeit.domain.ZeiteintragRepository;

/**
 * Ein Bestand der Zeiteintraege im Speicher — fuer die Szenarien des Schreibens (Plan #426).
 *
 * <p>Die Szenarien sagen etwas ueber das, was danach im Bestand steht und was „angefallen" daraus
 * rechnet. Ein Mock, der beides vorgibt, bewiese nichts; dieser Bestand legt an, schreibt fort,
 * loescht und summiert wirklich. Er kann genau, was die Anwendungsfaelle dieser Szenarien fragen,
 * und weist alles Uebrige ab, statt eine Antwort zu erfinden.
 */
final class Zeitbestand implements ZeiteintragRepository {

  private final Map<Long, Zeiteintrag> zeilen = new TreeMap<>();
  private long naechsteKennung = 1L;

  /** Alle Zeilen in der Reihenfolge ihrer Kennung. */
  List<Zeiteintrag> alle() {
    return List.copyOf(zeilen.values());
  }

  @Override
  public Optional<Zeiteintrag> findById(final long id) {
    return Optional.ofNullable(zeilen.get(id));
  }

  @Override
  public List<Zeiteintrag> findImZeitraum(final LocalDate von, final LocalDate bis) {
    return zeilen.values().stream()
        .filter(eintrag -> !eintrag.tag().isBefore(von) && !eintrag.tag().isAfter(bis))
        .toList();
  }

  @Override
  public List<Zeiteintrag> findImZeitraumFuerKonto(
      final LocalDate von, final LocalDate bis, final long accountId) {
    return findImZeitraum(von, bis).stream()
        .filter(eintrag -> eintrag.accountId() == accountId)
        .toList();
  }

  @Override
  public Zeiteintrag save(final Zeiteintrag zeiteintrag) {
    final Long id = zeiteintrag.id() == null ? Long.valueOf(naechsteKennung++) : zeiteintrag.id();
    final Zeiteintrag gespeichert =
        new Zeiteintrag(
            id,
            zeiteintrag.accountId(),
            zeiteintrag.angebotPositionId(),
            zeiteintrag.tag(),
            zeiteintrag.von(),
            zeiteintrag.bis(),
            zeiteintrag.createdAt(),
            zeiteintrag.updatedAt());
    zeilen.put(id, gespeichert);
    return gespeichert;
  }

  @Override
  public void delete(final long id) {
    zeilen.remove(id);
  }

  @Override
  public Map<Long, BigDecimal> angefallenJePosition(final Set<Long> angebotPositionIds) {
    return angebotPositionIds.stream()
        .collect(
            Collectors.toMap(
                Function.identity(),
                position ->
                    Zeiteintrag.stundenAus(
                        zeilen.values().stream()
                            .filter(eintrag -> eintrag.angebotPositionId() == position)
                            .mapToLong(Zeiteintrag::minuten)
                            .sum())));
  }

  @Override
  public Map<Long, BigDecimal> stundenJePositionImMonat(
      final Set<Long> angebotPositionIds, final YearMonth monat) {
    throw new UnsupportedOperationException("In den Szenarien nicht gefragt.");
  }

  @Override
  public Map<Long, BigDecimal> alleAngefallenJePosition() {
    throw new UnsupportedOperationException("In den Szenarien nicht gefragt.");
  }

  @Override
  public Map<Long, BigDecimal> alleStundenJePositionImZeitraum(
      final LocalDate von, final LocalDate bis) {
    throw new UnsupportedOperationException("In den Szenarien nicht gefragt.");
  }

  @Override
  public Set<YearMonth> monateMitEintragImZeitraum(final LocalDate von, final LocalDate bis) {
    throw new UnsupportedOperationException("In den Szenarien nicht gefragt.");
  }
}
