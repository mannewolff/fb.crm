package org.mwolff.fbcrm.vorgang.application;

import java.time.Clock;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;
import org.mwolff.fbcrm.vorgang.domain.VorgangRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Aendern von Titel, Firma und Ansprechpartner eines Vorgangs (Kriterien 10, 23).
 *
 * <p>Nummer, Abschlussstand und Anlagezeitpunkt bleiben, wie sie waren: Die Nummer aendert sich nie
 * (Kriterium 8), und den Abschluss schaltet ein eigener Weg. Ein abgeschlossener Vorgang bleibt
 * aenderbar — der Abschlussstand wirkt auf die Uebersicht und nicht als Schreibsperre (E26).
 *
 * <p>Die Wahlregel ist dieselbe wie beim Anlegen, bekommt hier aber den bisherigen Stand mit: An
 * ihm haengt die Ausnahme aus Kriterium 23 fuer eine inzwischen stillgelegte Zuordnung ({@link
 * Zuordnungswahl}).
 *
 * <p>Die Normalisierung steht <b>vor</b> dem Zugriff auf den Bestand — eine Eingabe, die ohnehin
 * abgewiesen wird, soll keine Abfrage kosten.
 */
@Service
@Transactional
public class VorgangAendernUseCase {

  private final VorgangRepository vorgaenge;
  private final Zuordnungswahl wahl;
  private final Clock clock;

  public VorgangAendernUseCase(
      final VorgangRepository vorgaenge, final Zuordnungswahl wahl, final Clock clock) {
    this.vorgaenge = vorgaenge;
    this.wahl = wahl;
    this.clock = clock;
  }

  /**
   * Schreibt die neuen Angaben fort.
   *
   * @param id technische Id des Vorgangs
   * @param daten die eingereichten Angaben; normalisiert werden sie hier (E9)
   * @throws IllegalArgumentException wenn vom Titel nur Leerraum uebrig bleibt
   * @throws VorgangNichtGefunden wenn es den Vorgang nicht gibt
   * @throws FirmaNichtWaehlbar wenn die Firma unbekannt oder stillgelegt und nicht die bisherige
   *     ist
   * @throws AnsprechpartnerNichtWaehlbar wenn der Ansprechpartner unbekannt ist, zu einer anderen
   *     Firma gehoert, oder stillgelegt und nicht der bisherige ist
   */
  public void aendern(final long id, final VorgangDaten daten) {
    final VorgangDaten sauber = daten.normalisiert();
    final Vorgang vorhanden = vorgaenge.findById(id).orElseThrow(VorgangNichtGefunden::new);
    wahl.pruefe(sauber, vorhanden);
    vorgaenge.save(
        vorhanden.geaendert(
            sauber.titel(), sauber.firmaId(), sauber.ansprechpartnerId(), clock.instant()));
  }
}
