package org.mwolff.fbcrm.rechnung.application;

import java.util.Set;
import java.util.stream.Collectors;
import org.mwolff.fbcrm.angebot.application.Positionsverwendung;
import org.mwolff.fbcrm.angebot.application.Rechnungsbindung;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungsposition;
import org.springframework.stereotype.Service;

/**
 * Setzt die Ports {@link Positionsverwendung} und {@link Rechnungsbindung} des Moduls {@code
 * angebot} um (#160, Kriterium 28; Issue #227, E5).
 *
 * <p><b>Die Umsetzung liegt hier und nicht dort</b>, wo der Port steht: {@code rechnung} kennt
 * {@code angebot} ohnehin, umgekehrt entstuende ein Paketzyklus (Plan #169, E1, E12). Das Angebot
 * bekommt so seine Auskunft, ohne die Rechnung zu kennen.
 *
 * <p>Gefragt wird ueber {@link RechnungRepository#findByAngebot} und nicht mit einer eigenen
 * Abfrage auf {@code rechnung_position}: Der Port liest dieselben Zeilen, die der Bestand ohnehin
 * je Angebot in zwei Zuegen liefert, und eine zweite Abfrage auf dieselbe Tabelle waere eine zweite
 * Stelle, an der „steht in einer Rechnung" definiert wird.
 *
 * <p>Entwuerfe zaehlen wie gestellte Rechnungen — ein Entwurf verloere seinen Bezug genauso. Eine
 * Position ueber die Menge 0 traegt die Rechnung nicht (Kriterium 5); sie bindet darum auch nichts.
 *
 * <p><b>Zwei Schnittstellen an einer Klasse</b> und nicht eine Methode mehr am ersten Port: Die
 * beiden Fragen sind verschieden — „welche Positionen" und „ob eine Rechnung" —, und ein leerer
 * Kennungssatz ist nicht dasselbe wie „keine Rechnung" (E5). Dieselbe Klasse beantwortet sie
 * trotzdem, weil beide aus denselben Zeilen entstehen und so nur eine Stelle sagt, was „steht in
 * einer Rechnung" heisst.
 *
 * <p>Ohne eigene Transaktionsgrenze: Gelesen wird in der Transaktion des Anwendungsfalls, der
 * fragt.
 */
@Service
class RechnungsPositionsverwendung implements Positionsverwendung, Rechnungsbindung {

  private final RechnungRepository rechnungen;

  RechnungsPositionsverwendung(final RechnungRepository rechnungen) {
    this.rechnungen = rechnungen;
  }

  @Override
  public Set<Long> verwendeteKennungen(final long angebotId) {
    return rechnungen.findByAngebot(angebotId).stream()
        .flatMap(rechnung -> rechnung.positionen().stream())
        .map(Rechnungsposition::angebotPositionId)
        .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public boolean rechnungVorhanden(final long angebotId) {
    return !rechnungen.findByAngebot(angebotId).isEmpty();
  }
}
