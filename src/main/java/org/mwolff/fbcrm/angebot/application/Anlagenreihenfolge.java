package org.mwolff.fbcrm.angebot.application;

import java.util.Comparator;
import org.mwolff.fbcrm.angebot.domain.Angebotsanlage;

/**
 * Die eine Reihenfolge jeder Anlagenliste: neueste zuerst (Issue #148, Kriterium 4).
 *
 * <p>Gezaehlt wird der Zeitpunkt des Hochladens absteigend; bei Gleichstand entscheidet die hoehere
 * Kennung, damit zwei Aufrufe dieselbe Liste liefern. Der Gleichstand ist hier kein Randfall: Zwei
 * Anlagen, die in derselben Sekunde ankommen, tragen denselben Zeitpunkt aus derselben Uhr.
 *
 * <p>Sortiert wird in der Anwendungsschicht und nicht im Bestand — dieselbe Aufteilung wie bei
 * {@link Angebotsreihenfolge} und {@link Kommentarreihenfolge} (Plan #150, E10): Die Ordnung ist
 * eine Aussage der Ansicht, keine Eigenschaft der Zeilen.
 */
final class Anlagenreihenfolge {

  /** Neueste zuerst, bei gleichem Zeitpunkt die hoehere Kennung. */
  static final Comparator<Angebotsanlage> NEUESTE_ZUERST =
      Comparator.comparing(Angebotsanlage::createdAt, Comparator.reverseOrder())
          .thenComparing(Angebotsanlage::requireId, Comparator.reverseOrder());

  private Anlagenreihenfolge() {}
}
