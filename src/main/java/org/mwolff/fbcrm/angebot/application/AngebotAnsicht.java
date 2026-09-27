package org.mwolff.fbcrm.angebot.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsstand;
import org.mwolff.fbcrm.common.Geschaeftszone;

/**
 * Ein Angebot mit dem Stand, als der es heute angezeigt wird (Kriterium 18).
 *
 * <p>Der Stand steht in keiner Spalte: „abgelaufen" entsteht aus dem Vergleich der Gueltigkeit mit
 * dem heutigen Tag (E4). Welcher Tag heute ist, entscheidet die Geschaeftszone und nicht UTC (E12)
 * — und weil die Domaene keine Uhr kennt, wird sie hier hineingereicht. Deshalb dieses Paar: Die
 * Antwortschicht bekommt den Stand, ohne selbst an eine Uhr zu kommen.
 *
 * <p>Die Summe und die Positionsbetraege fehlen hier — sie sind reine Funktionen des Angebots und
 * brauchen nichts von aussen (E5).
 *
 * @param angebot das Angebot samt seinen Positionen
 * @param stand der Stand, als der es heute angezeigt wird
 */
public record AngebotAnsicht(Angebot angebot, Angebotsstand stand) {

  /** Die Ansicht eines Angebots am heutigen Tag der Geschaeftszone. */
  static AngebotAnsicht of(final Angebot angebot, final Clock clock) {
    return new AngebotAnsicht(angebot, angebot.stand(heute(clock)));
  }

  /** Die Ansichten mehrerer Angebote — alle gegen denselben Tag. */
  static List<AngebotAnsicht> of(final List<Angebot> angebote, final Clock clock) {
    final LocalDate heute = heute(clock);
    return angebote.stream()
        .map(angebot -> new AngebotAnsicht(angebot, angebot.stand(heute)))
        .toList();
  }

  /** Der heutige Tag in der Geschaeftszone (E12). */
  static LocalDate heute(final Clock clock) {
    return LocalDate.now(clock.withZone(Geschaeftszone.ZONE));
  }
}
