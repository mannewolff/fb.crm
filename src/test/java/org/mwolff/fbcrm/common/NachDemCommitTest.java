package org.mwolff.fbcrm.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Der Nach-Commit-Haken (Plan #259, E14; vorher Plan #150, E9 bei den Anlagen).
 *
 * <p>Gegenstand sind seine beiden Zweige: Laeuft eine Transaktion, wartet der Schritt auf ihren
 * Commit — vor dem Commit ausgefuehrt, liesse ein gescheiterter Commit eine Zeile ohne Objekt
 * zurueck. Laeuft keine, gibt es keinen Commit, auf den zu warten waere, und der Schritt geschieht
 * sofort.
 */
class NachDemCommitTest {

  private final AtomicInteger ausgefuehrt = new AtomicInteger();

  /*
   * Die Synchronisation liegt in einem ThreadLocal und ueberlebte den Test, der sie eroeffnet hat.
   * Die Abfrage davor ist Aufraeumen und keine Logik um eine Behauptung: Nur ein Test eroeffnet sie.
   */
  @AfterEach
  void raeumeDieSynchronisationAuf() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void fuehreAus_withoutATransaction_thenRunsTheStepRightAway() {
    // When
    NachDemCommit.fuehreAus(ausgefuehrt::incrementAndGet);

    // Then
    assertThat(ausgefuehrt).hasValue(1);
  }

  @Test
  void fuehreAus_withinATransaction_thenRunsTheStepOnlyAfterTheCommit() {
    // Given
    TransactionSynchronizationManager.initSynchronization();

    // When
    NachDemCommit.fuehreAus(ausgefuehrt::incrementAndGet);

    // Then — vor dem Commit ist nichts geschehen.
    assertThat(ausgefuehrt).hasValue(0);

    // And — nach dem Commit genau einmal.
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);
    assertThat(ausgefuehrt).hasValue(1);
  }

  @Test
  void fuehreAus_withinATransaction_whenItRollsBack_thenNeverRunsTheStep() {
    // Given
    TransactionSynchronizationManager.initSynchronization();

    // When
    NachDemCommit.fuehreAus(ausgefuehrt::incrementAndGet);
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

    // Then — ohne Commit bleibt der Schritt aus.
    assertThat(ausgefuehrt).hasValue(0);
  }
}
