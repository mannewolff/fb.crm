package org.mwolff.fbcrm.auftrag.application;

import java.util.Comparator;
import java.util.List;
import org.mwolff.fbcrm.auftrag.domain.Auftrag;
import org.mwolff.fbcrm.auftrag.domain.AuftragRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Auftragsliste eines Vorgangs (Kriterium 9, Plan E13).
 *
 * <p><b>Zwei Schluessel, nicht einer.</b> Kriterium 9 nennt nur „der juengste oben" — das
 * Auftragsdatum absteigend. Ohne einen zweiten Schluessel lieferten zwei Aufrufe verschiedene
 * Listen, sobald zwei Auftraege denselben Tag tragen; deshalb entscheidet danach die hoehere
 * Kennung. Das Datum allein taugt nicht als Ordnung, weil es frei setzbar ist (Kriterium 3).
 *
 * <p>Sortiert wird hier und nicht im Bestand: Die Ordnung ist eine Aussage der Ansicht und keine
 * Eigenschaft der Zeilen — dieselbe Aufteilung wie bei {@code
 * angebot.application.AngeboteDesVorgangsUseCase}.
 *
 * <p>Geliefert werden die Auftraege selbst und keine eigene Zeilensicht: Was die Liste zeigt — auch
 * die gerechnete Summe —, ist eine reine Funktion des Auftrags und braucht nichts von aussen (E11).
 * Insbesondere braucht sie die Nummer des Quell-Angebots nicht, und je Zeile ein Angebot zu laden
 * waere Gewicht ohne Nutzen.
 *
 * <p>Ein unbekannter Vorgang ist hier kein Fehler, sondern ein Vorgang ohne Auftrag; dieselbe
 * Abwaegung wie bei {@code AngeboteDesVorgangsUseCase}: Ob es ihn gibt, beantwortet seine eigene
 * Detailansicht.
 */
@Service
@Transactional(readOnly = true)
public class AuftraegeDesVorgangsUseCase {

  private static final Comparator<Auftrag> REIHENFOLGE =
      Comparator.comparing(Auftrag::auftragDatum, Comparator.reverseOrder())
          .thenComparing(Auftrag::requireId, Comparator.reverseOrder());

  private final AuftragRepository bestand;

  public AuftraegeDesVorgangsUseCase(final AuftragRepository bestand) {
    this.bestand = bestand;
  }

  /**
   * Die Auftraege eines Vorgangs in der Reihenfolge aus Kriterium 9.
   *
   * @param vorgangId Kennung des Vorgangs
   */
  public List<Auftrag> auftraege(final long vorgangId) {
    return bestand.findByVorgang(vorgangId).stream().sorted(REIHENFOLGE).toList();
  }
}
