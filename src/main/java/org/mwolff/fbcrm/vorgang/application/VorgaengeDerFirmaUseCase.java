package org.mwolff.fbcrm.vorgang.application;

import java.util.List;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.vorgang.domain.EintragRepository;
import org.mwolff.fbcrm.vorgang.domain.VorgangRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Vorgaenge einer Firma, getrennt nach offen und abgeschlossen (Kriterium 12).
 *
 * <p>Dieser Anwendungsfall gehoert dem Modul {@code vorgang} und nicht {@code firma} (E2): So kommt
 * die Liste in die Detailansicht der Firma, ohne dass {@code firma} etwas vom Vorgang wuesste —
 * eine Einbettung in die Firmenantwort waere ein Paketzyklus.
 *
 * <p>Eine Abfrage, zwei Listen: Der Bestand liefert offene und abgeschlossene gemeinsam in der
 * Reihenfolge der Uebersicht (E16), die Trennung macht diese Schicht.
 *
 * <p>Eine unbekannte Firma ist hier kein Fehler, sondern eine Firma ohne Vorgang: Ob es sie gibt,
 * beantwortet ihre eigene Detailansicht, und zwei Wege mit zwei Antworten auf dieselbe Frage waeren
 * eine zweite Wahrheit.
 */
@Service
@Transactional(readOnly = true)
public class VorgaengeDerFirmaUseCase {

  private final VorgangRepository bestand;
  private final VorgangZeilen zeilen;

  public VorgaengeDerFirmaUseCase(
      final VorgangRepository bestand,
      final EintragRepository eintraege,
      final FirmaRepository firmen) {
    this.bestand = bestand;
    this.zeilen = new VorgangZeilen(eintraege, firmen);
  }

  /**
   * Die offenen und die abgeschlossenen Vorgaenge einer Firma.
   *
   * @param firmaId Kennung der Firma
   */
  public VorgaengeDerFirma vorgaenge(final long firmaId) {
    final List<VorgangZeile> alle = zeilen.zu(bestand.findByFirma(firmaId));
    return new VorgaengeDerFirma(
        alle.stream().filter(zeile -> !zeile.abgeschlossen()).toList(),
        alle.stream().filter(VorgangZeile::abgeschlossen).toList());
  }
}
