package org.mwolff.fbcrm.common;

import java.util.Optional;

/**
 * Loest ein Konto zu dem Namen auf, den die Meldung eines Bearbeitungskonflikts nennt (Plan #415,
 * E6).
 *
 * <p><b>Warum der Port hier steht.</b> Den Namen braucht {@code common.web.GlobalExceptionHandler},
 * die Konten aber gehoeren {@code auth}, und {@code auth} zeigt schon auf {@code common}. Griffe
 * die Abbildung selbst auf {@code auth} zu, entstuende ein Paketzyklus ({@code ArchitectureTest},
 * {@code modules_thenFreeOfCycles}). So kennt {@code common} nur diesen Vertrag, {@code auth} setzt
 * ihn um, und die Richtung bleibt eine — dasselbe Muster, mit dem {@code rechnung} ueber {@code
 * Positionsverwendung} auf {@code angebot} zeigt.
 */
@FunctionalInterface
public interface Urheberauskunft {

  /**
   * Der Urheber zu einem Konto.
   *
   * @param konto die Kennung des Kontos
   * @return der Urheber, oder leer, wenn es das Konto nicht gibt
   */
  Optional<Urheber> zu(long konto);
}
