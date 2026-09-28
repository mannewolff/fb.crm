package org.mwolff.fbcrm.auftrag.application;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.common.Feldfehler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die gewaehlten Positionen passen nicht zum Angebot (Kriterium 2, F2, Plan E20).
 *
 * <p>Sie nennt die betroffenen Positionen <b>namentlich</b> — {@code positionen[0].platz}, {@code
 * positionen[1].menge}, {@code positionen[0].stundenJePersonentag} — oder {@code positionen} fuer
 * die Liste als Ganzes. Nur so schreibt die Maske die Meldung an die Zeile, um die es geht; eine
 * Sammelmeldung liesse den Anwender suchen.
 *
 * <p>Alle Verstoesse auf einmal, nicht der erste: Wer zwei Zeilen zu hoch gesetzt hat, soll das in
 * einem Durchgang erfahren.
 *
 * <p>409 und nicht 422: Die Anfrage ist formal in Ordnung — Platz, Menge und Stunden haben die
 * richtige Gestalt. Was nicht passt, ist ihr Verhaeltnis zum Angebot, und das kann sich hinter dem
 * Ruecken des Anwenders geaendert haben.
 *
 * <p>Abgebildet wird sie im {@code GlobalExceptionHandler} ueber den Vertrag {@link Feldfehler},
 * damit {@code common} kein Fachmodul kennen muss.
 */
@ResponseStatus(code = HttpStatus.CONFLICT, reason = AuftragsuebernahmeUngueltig.MELDUNG)
public final class AuftragsuebernahmeUngueltig extends Feldfehler {

  /** Die Meldung an der Schnittstelle; welche Position nicht passt, steht in der Feldliste. */
  public static final String MELDUNG = "Die gewaehlten Positionen passen nicht zum Angebot.";

  private final Map<String, List<String>> beanstandungen;

  /**
   * @param felder die Beanstandungen je Feld, in der Reihenfolge, in der sie gelesen werden
   */
  public AuftragsuebernahmeUngueltig(final Map<String, List<String>> felder) {
    super();
    this.beanstandungen = Collections.unmodifiableMap(new LinkedHashMap<>(felder));
  }

  @Override
  public Map<String, List<String>> felder() {
    return beanstandungen;
  }
}
