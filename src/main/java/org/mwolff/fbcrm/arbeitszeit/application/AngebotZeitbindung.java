package org.mwolff.fbcrm.arbeitszeit.application;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.mwolff.fbcrm.angebot.application.Zeitbindung;
import org.mwolff.fbcrm.arbeitszeit.domain.ZeiteintragRepository;
import org.springframework.stereotype.Service;

/**
 * Setzt den Port {@link Zeitbindung} des Moduls {@code angebot} um (Issue #228, Plan #218, E6).
 *
 * <p><b>Die Umsetzung liegt hier und nicht dort</b>, wo der Port steht: {@code arbeitszeit} kennt
 * {@code angebot} ohnehin — die Zeit wird auf eine Angebotsposition gebucht —, umgekehrt entstuende
 * ein Paketzyklus. Das Angebot bekommt so seine Auskunft, ohne die Zeiterfassung zu kennen.
 *
 * <p><b>Die umgekehrte Richtung, keine dritte Tuer.</b> Nach draussen fragt dieses Modul weiter nur
 * ueber {@link Arbeitszeitauskunft} und {@link Buchbarkeit}; hier antwortet es auf eine Frage, die
 * ein fremdes Modul stellt.
 *
 * <p>Gefragt wird die vorhandene Summenauskunft {@link
 * ZeiteintragRepository#angefallenJePosition(Set)} und keine eigene Abfrage: Sie nennt jede
 * angefragte Position, auch die ohne Eintrag mit {@code 0.00}, und eine zweite Abfrage auf
 * dieselben Zeilen waere eine zweite Stelle, an der „traegt Arbeitszeit" definiert wird.
 *
 * <p>Ohne eigene Transaktionsgrenze: Gelesen wird in der Transaktion des Anwendungsfalls, der
 * fragt.
 */
@Service
class AngebotZeitbindung implements Zeitbindung {

  private final ZeiteintragRepository zeiten;

  AngebotZeitbindung(final ZeiteintragRepository zeiten) {
    this.zeiten = zeiten;
  }

  @Override
  public Set<Long> mitZeit(final Set<Long> angebotPositionIds) {
    // Ohne Kennungen gibt es nichts zu fragen: Eine Einreichung aus lauter neuen Positionen
    // soll den Bestand nicht bemuehen.
    if (angebotPositionIds.isEmpty()) {
      return Set.of();
    }
    return zeiten.angefallenJePosition(angebotPositionIds).entrySet().stream()
        .filter(eintrag -> eintrag.getValue().signum() > 0)
        .map(Map.Entry::getKey)
        .collect(Collectors.toUnmodifiableSet());
  }
}
