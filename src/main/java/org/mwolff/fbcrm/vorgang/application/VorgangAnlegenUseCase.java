package org.mwolff.fbcrm.vorgang.application;

import java.time.Clock;
import java.time.Instant;
import org.mwolff.fbcrm.vorgang.domain.NummernkreisRepository;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;
import org.mwolff.fbcrm.vorgang.domain.VorgangRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Anlegen eines Vorgangs (Kriterien 5, 6, 8).
 *
 * <p><b>Die Nummer wird zuletzt gezogen.</b> Erst der Titel, dann die Wahlregel aus E19, dann die
 * Nummer: Kriterium 8 sagt zu, dass ein abgewiesenes Anlegen keine Nummer verbraucht. Der Zug
 * laeuft in der Transaktion dieser Methode und sperrt die Zaehlerzeile bis zu ihrem Ende — ein
 * Ruecklauf gibt die Nummer wieder frei (E3). Genau darum ist der Nummernkreis keine
 * Postgres-Sequenz.
 *
 * <p>Ein neuer Vorgang ist offen, und beide Zeitstempel kommen aus derselben Uhr — nicht aus zwei
 * Aufrufen von {@code Instant.now()}, die um Millisekunden auseinanderliegen koennten.
 */
@Service
@Transactional
public class VorgangAnlegenUseCase {

  private final VorgangRepository vorgaenge;
  private final NummernkreisRepository nummernkreis;
  private final Zuordnungswahl wahl;
  private final Clock clock;

  public VorgangAnlegenUseCase(
      final VorgangRepository vorgaenge,
      final NummernkreisRepository nummernkreis,
      final Zuordnungswahl wahl,
      final Clock clock) {
    this.vorgaenge = vorgaenge;
    this.nummernkreis = nummernkreis;
    this.wahl = wahl;
    this.clock = clock;
  }

  /**
   * Legt den Vorgang an und liefert ihn mit Kennung und Nummer aus dem Bestand zurueck.
   *
   * @param daten die eingereichten Angaben; normalisiert werden sie hier (E9)
   * @throws IllegalArgumentException wenn vom Titel nur Leerraum uebrig bleibt
   * @throws FirmaNichtWaehlbar wenn die Firma unbekannt oder stillgelegt ist
   * @throws AnsprechpartnerNichtWaehlbar wenn der Ansprechpartner unbekannt, stillgelegt oder von
   *     einer anderen Firma ist
   */
  public Vorgang anlegen(final VorgangDaten daten) {
    final VorgangDaten sauber = daten.normalisiert();
    wahl.pruefe(sauber, null);
    final long nummer = nummernkreis.naechsteNummer();
    final Instant jetzt = clock.instant();
    return vorgaenge.save(
        new Vorgang(
            null,
            nummer,
            sauber.titel(),
            sauber.firmaId(),
            sauber.ansprechpartnerId(),
            sauber.abschlusswahrscheinlichkeit(),
            sauber.entscheidungErwartetAm(),
            false,
            jetzt,
            jetzt));
  }
}
