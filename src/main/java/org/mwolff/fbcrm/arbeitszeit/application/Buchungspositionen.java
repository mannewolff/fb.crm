package org.mwolff.fbcrm.arbeitszeit.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;

/**
 * Die Anreicherung einer Positionskennung zu einer {@link Buchungsposition} (Plan #194, A20).
 *
 * <p>An einer Stelle, weil beide Lesewege dieses Pakets sie brauchen: die Monatsliste fuer die
 * Positionen ihrer Eintraege und die Auswahlliste fuer alle buchbaren. Zwei Abschriften liefen beim
 * ersten Nachziehen auseinander.
 *
 * <p><b>Die Namen der Firmen kommen in einem Aufruf</b> fuer alle beteiligten Angebote, nicht je
 * Position — dasselbe Muster wie {@code angebot.application.AngeboteUebersichtUseCase}. Das Angebot
 * selbst wird nicht je Position geholt: Der Bestand kennt keinen Zugriff ueber die Kennung einer
 * Position und liefert das Angebot als Ganzes, also durchsucht der Aufrufer die eine Liste, die er
 * schon hat.
 */
final class Buchungspositionen {

  private Buchungspositionen() {}

  /**
   * Beschreibt die gesuchten Positionen, in der Reihenfolge der uebergebenen Angebote.
   *
   * <p>Die Ordnung des Ergebnisses ist die der Eingabe: erst die Angebote in ihrer Reihenfolge,
   * darin die Positionen in der des Angebots. Welche Ordnung die Ansicht zeigt, entscheidet damit
   * der Aufrufer, indem er seine Liste sortiert.
   *
   * @param angebote die Angebote, in denen gesucht wird
   * @param gesucht die Kennungen der Positionen, die beschrieben werden sollen
   * @param firmen Bestand der Firmen, fuer die Namen
   * @return je gesuchte Kennung ihre Beschreibung, in der Reihenfolge der Angebote
   * @throws AngebotNichtGefunden wenn eine gesuchte Kennung zu keiner Position eines Angebots
   *     gehoert — ein Widerspruch im Bestand, denn der Fremdschluessel haelt jede gebuchte Position
   *     fest
   * @throws FirmaNichtGefunden wenn es die Firma eines beteiligten Angebots nicht gibt; Firmen
   *     werden nie geloescht
   */
  static Map<Long, Buchungsposition> beschreibe(
      final List<Angebot> angebote, final Set<Long> gesucht, final FirmaRepository firmen) {
    final List<Angebot> beteiligte =
        angebote.stream().filter(angebot -> traegtEine(angebot, gesucht)).toList();
    final Map<Long, String> namen = namenDerFirmen(beteiligte, firmen);
    final Map<Long, Buchungsposition> beschrieben = new LinkedHashMap<>();
    for (final Angebot angebot : beteiligte) {
      for (final Angebotsposition position : angebot.positionen()) {
        if (gesucht.contains(position.requireId())) {
          beschrieben.put(position.requireId(), beschreibe(angebot, position, namen));
        }
      }
    }
    if (beschrieben.size() != gesucht.size()) {
      throw new AngebotNichtGefunden();
    }
    return beschrieben;
  }

  private static boolean traegtEine(final Angebot angebot, final Set<Long> gesucht) {
    return angebot.positionen().stream()
        .anyMatch(position -> gesucht.contains(position.requireId()));
  }

  private static Map<Long, String> namenDerFirmen(
      final List<Angebot> beteiligte, final FirmaRepository firmen) {
    final Set<Long> firmaIds =
        beteiligte.stream().map(Angebot::firmaId).collect(Collectors.toSet());
    if (firmaIds.isEmpty()) {
      return Map.of();
    }
    return firmen.findAllById(firmaIds).stream()
        .collect(Collectors.toMap(Firma::requireId, Firma::name));
  }

  private static Buchungsposition beschreibe(
      final Angebot angebot, final Angebotsposition position, final Map<Long, String> namen) {
    final String name = namen.get(angebot.firmaId());
    if (name == null) {
      throw new FirmaNichtGefunden();
    }
    return new Buchungsposition(
        position.requireId(),
        position.bezeichnung(),
        angebot.requireId(),
        angebot.angebotDatum(),
        name);
  }
}
