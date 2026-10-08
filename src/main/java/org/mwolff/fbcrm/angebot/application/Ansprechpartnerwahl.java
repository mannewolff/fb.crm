package org.mwolff.fbcrm.angebot.application;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.AnsprechpartnerRepository;
import org.springframework.stereotype.Service;

/**
 * Ob ein Ansprechpartner fuer ein Angebot zur Wahl steht — die eine Regel fuer Anlegen und Aendern.
 *
 * <p>Ein neu gewaehlter Ansprechpartner muss zur Firma des Angebots gehoeren und aktiv sein. Der
 * bereits gespeicherte bleibt wählbar, auch wenn er inzwischen stillgelegt ist: Sonst schlüge jede
 * Aenderung am Text eines Angebots fehl, dessen Ansprechpartner seither stillgelegt wurde (Plan
 * #131). Kein Ansprechpartner ist immer erlaubt (Frage 8).
 *
 * <p>Ohne eigene Transaktionsgrenze: Gelesen wird in der Transaktion des Anwendungsfalls.
 */
@Service
class Ansprechpartnerwahl {

  private final AnsprechpartnerRepository personen;

  Ansprechpartnerwahl(final AnsprechpartnerRepository personen) {
    this.personen = personen;
  }

  /**
   * Prueft die Wahl.
   *
   * @param firmaId Kennung der Firma des Angebots
   * @param gewaehlt Kennung des gewaehlten Ansprechpartners, oder {@code null}
   * @param bisher Kennung des bisher gespeicherten Ansprechpartners, oder {@code null} beim Anlegen
   * @throws AnsprechpartnerNichtWaehlbar wenn der gewaehlte unbekannt ist, zu einer anderen Firma
   *     gehoert oder — als neue Wahl — stillgelegt ist
   */
  void pruefe(final long firmaId, final @Nullable Long gewaehlt, final @Nullable Long bisher) {
    if (gewaehlt == null) {
      return;
    }
    final Ansprechpartner person =
        personen.findById(gewaehlt).orElseThrow(AnsprechpartnerNichtWaehlbar::new);
    final boolean unveraendert = Objects.equals(gewaehlt, bisher);
    if (person.firmaId() != firmaId || !(person.aktiv() || unveraendert)) {
      throw new AnsprechpartnerNichtWaehlbar();
    }
  }
}
