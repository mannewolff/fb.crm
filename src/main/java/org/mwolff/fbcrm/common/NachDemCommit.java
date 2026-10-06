package org.mwolff.fbcrm.common;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Der Haken, der einen Schritt erst nach dem Commit der laufenden Transaktion ausfuehrt (Plan #150,
 * E9; Plan #259, E14).
 *
 * <p>Gebraucht wird er, wo eine Zeile in der Datenbank und ein Objekt im Speicher zusammengehoeren
 * und die Zeile geloescht wird: Das Objekt darf erst fort sein, wenn die Zeile es sicher ist. Vor
 * dem Commit geloescht, liesse ein gescheiterter Commit eine Zeile ohne Objekt zurueck.
 *
 * <p>Er steht hier und nicht im Modul {@code angebot}, weil die Anlagen am Angebot und die
 * nachgetragenen Rechnungen denselben Haken brauchen; eine Abschrift derselben Zeilen je Modul
 * waere der doppelte Block, den Sonar meldet.
 */
public final class NachDemCommit {

  private NachDemCommit() {}

  /**
   * Fuehrt den Schritt nach dem Commit der laufenden Transaktion aus.
   *
   * <p>Laeuft keine — ein Aufruf ohne Transaktionsgrenze, etwa im Unit-Test —, gibt es keinen
   * Commit, auf den zu warten waere; dann geschieht er sofort. Endet die Transaktion mit einem
   * Rollback, bleibt er aus.
   *
   * @param schritt was nach dem Commit geschehen soll
   */
  public static void fuehreAus(final Runnable schritt) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      schritt.run();
      return;
    }
    /*
     * TransactionSynchronization ist kein funktionales Interface (alle Methoden haben eine
     * Vorgabe), daher die anonyme Klasse statt eines Lambdas.
     */
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            schritt.run();
          }
        });
  }
}
