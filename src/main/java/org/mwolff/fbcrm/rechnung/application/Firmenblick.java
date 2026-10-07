package org.mwolff.fbcrm.rechnung.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnung;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;

/**
 * Der Weg von einer Rechnung zu ihrer Firma, in zwei Zuegen fuer viele Rechnungen (Plan #288, E6).
 *
 * <p>Bei der von fb.crm geschriebenen Rechnung haengt die Firma nicht an ihr, sondern an ihrem
 * Angebot, bei der nachgetragenen an ihr selbst. Gefragt wird darum einmal nach allen Angeboten und
 * einmal nach den Firmen, die die Rechnungen brauchen — je Rechnung nachzufragen waere die bekannte
 * Abfrage-Lawine.
 *
 * <p>Der Weg steht an einer Stelle, weil zwei Tueren von {@link Rechnungsauskunft} ihn gehen: die
 * offenen Rechnungen ({@link OffenePosten}) und die gestellten Rechnungen einzeln. Zwei Abschriften
 * derselben Aufloesung liefen beim ersten Nachziehen auseinander. Paket-privat und ohne eigene
 * Testklasse — geprueft wird er ueber {@code RechnungsauskunftTest}.
 *
 * <p><b>Ohne Rechnung wird gar nicht gefragt.</b> Zwei Abfragen fuer eine leere Antwort zahlte
 * jeder Aufruf mit, und eine leere Antwort ist der Regelfall eines bezahlten Bestands.
 *
 * <p>Fehlt ein Angebot oder eine Firma, ist das ein Widerspruch im Bestand und keine Zeile ohne
 * Namen: Weder Angebote noch Firmen werden geloescht.
 */
final class Firmenblick {

  private final Map<Long, Long> firmaJeAngebot;
  private final Map<Long, String> namen;

  private Firmenblick(final Map<Long, Long> firmaJeAngebot, final Map<Long, String> namen) {
    this.firmaJeAngebot = firmaJeAngebot;
    this.namen = namen;
  }

  /**
   * Der Blick auf die Firmen dieser Rechnungen.
   *
   * @param eigene die von fb.crm geschriebenen Rechnungen
   * @param nachgetragene die nachgetragenen Rechnungen
   * @param angebote der Weg zum Angebot einer geschriebenen Rechnung
   * @param firmen der Weg zum Namen einer Firma
   * @return der Blick, aus dem Firma und Name jeder dieser Rechnungen kommen
   * @throws AngebotNichtGefunden wenn es das Angebot einer geschriebenen Rechnung nicht gibt
   */
  static Firmenblick fuer(
      final List<Rechnung> eigene,
      final List<NachgetrageneRechnung> nachgetragene,
      final AngebotRepository angebote,
      final FirmaRepository firmen) {
    if (eigene.isEmpty() && nachgetragene.isEmpty()) {
      return new Firmenblick(Map.of(), Map.of());
    }
    final Map<Long, Long> firmaJeAngebot =
        angebote.findAlle(Optional.empty()).stream()
            .collect(Collectors.toMap(Angebot::requireId, Angebot::firmaId));
    final Set<Long> firmaIds =
        Stream.concat(
                eigene.stream().map(rechnung -> firmaVon(firmaJeAngebot, rechnung)),
                nachgetragene.stream().map(NachgetrageneRechnung::firmaId))
            .collect(Collectors.toSet());
    final Map<Long, String> namen =
        firmen.findAllById(firmaIds).stream()
            .collect(Collectors.toMap(Firma::requireId, Firma::name));
    return new Firmenblick(firmaJeAngebot, namen);
  }

  /**
   * Die Firma einer geschriebenen Rechnung — die ihres Angebots.
   *
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   */
  long firmaVon(final Rechnung rechnung) {
    return firmaVon(firmaJeAngebot, rechnung);
  }

  /**
   * Der heutige Name einer Firma.
   *
   * @throws FirmaNichtGefunden wenn es die Firma nicht gibt
   */
  String nameVon(final long firmaId) {
    final String name = namen.get(firmaId);
    if (name == null) {
      throw new FirmaNichtGefunden();
    }
    return name;
  }

  private static long firmaVon(final Map<Long, Long> firmaJeAngebot, final Rechnung rechnung) {
    final Long firmaId = firmaJeAngebot.get(rechnung.angebotId());
    if (firmaId == null) {
      throw new AngebotNichtGefunden();
    }
    return firmaId;
  }
}
