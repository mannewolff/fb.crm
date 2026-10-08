package org.mwolff.fbcrm.angebot.application;

import java.util.Set;

/**
 * Welche Positionen eines Angebots in einer Rechnung stehen (#160, Kriterium 28).
 *
 * <p>Der Port haengt die Auskunft <b>hier</b> auf und nicht dort, wo sie entsteht: Die Umsetzung
 * liegt im Modul {@code rechnung}, das das Angebot ohnehin kennt. Wuerde das Angebot umgekehrt die
 * Rechnung fragen, zeigten beide Module aufeinander — ein Paketzyklus, den {@code ArchitectureTest}
 * verbietet (Plan #169, E1, E12). So bleibt die Richtung eine: {@code rechnung} → {@code angebot}.
 *
 * <p>Gezaehlt werden Entwuerfe wie gestellte Rechnungen. Ein Entwurf, der eine Position schon
 * traegt, verloere seinen Bezug genauso, wenn sie am Angebot verschwaende — dass er noch keine
 * Nummer hat, aendert daran nichts.
 *
 * <p>Ohne eigene Transaktionsgrenze: Gelesen wird in der Transaktion des Anwendungsfalls.
 */
/*
 * Genau eine Methode, aber bewusst kein @FunctionalInterface: Der Port beschreibt eine Auskunft,
 * die ein Modul gibt, und wird nie als Lambda geschrieben — dieselbe Ueberlegung wie bei
 * common.Identifiable und rechnung.application.Belegdrucker.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface Positionsverwendung {

  /**
   * Die Kennungen der Positionen dieses Angebots, die in einer Rechnung stehen.
   *
   * @param angebotId Kennung des Angebots
   * @return die Kennungen; leer, wenn aus dem Angebot noch nichts berechnet wurde
   */
  Set<Long> verwendeteKennungen(long angebotId);
}
