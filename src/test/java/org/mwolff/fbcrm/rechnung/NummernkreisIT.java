package org.mwolff.fbcrm.rechnung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.rechnung.domain.Nummernkreis;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Der Nummernkreis je Zaehlerjahr gegen eine echte PostgreSQL-Instanz (#160, Kriterien 15 bis 17).
 *
 * <p>Der Gegenstand haengt an der Datenbank und ist deshalb nur hier pruefbar: die Sperre auf der
 * Jahreszeile, das Anlegen der fehlenden Zeile im selben Zug und die Zusage, dass ein
 * zurueckgerollter Zug seine Nummer wieder freigibt. Gegen ein gemocktes Spring-Data-Repository
 * waere davon nichts zu sehen.
 *
 * <p>Der Zug laeuft in der Transaktion des Aufrufers — der Port oeffnet keine eigene. Deshalb
 * fuehrt der Test seine Zuege ueber ein {@link TransactionTemplate}: Ohne Transaktionsgrenze
 * scheiterte schon die Sperre.
 *
 * <p>Die Tabelle wird vor jeder Methode geleert; die Datenbank der Suite ist geteilt, und ein
 * Zaehlerjahr ohne Zeile steht ohnehin bei 1.
 */
class NummernkreisIT extends AbstractIntegrationTest {

  private static final int JAHR = 2026;
  private static final int ANDERES_JAHR = 2027;
  private static final Duration GEDULD = Duration.ofSeconds(30);

  private final Nummernkreis nummernkreis;
  private final TransactionTemplate transaktion;
  private final JdbcTemplate jdbc;

  @Autowired
  NummernkreisIT(
      final Nummernkreis nummernkreis,
      final TransactionTemplate transaktion,
      final JdbcTemplate jdbc) {
    this.nummernkreis = nummernkreis;
    this.transaktion = transaktion;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereDenNummernkreis() {
    jdbc.execute("DELETE FROM rechnung_nummernkreis");
  }

  /** Ein Zug in einer eigenen, abgeschlossenen Transaktion. */
  private int ziehe(final int jahr) {
    return transaktion.execute(status -> Integer.valueOf(nummernkreis.ziehe(jahr))).intValue();
  }

  private void setze(final int jahr, final int nummer) {
    transaktion.executeWithoutResult(status -> nummernkreis.setze(jahr, nummer));
  }

  private int lies(final int jahr) {
    return transaktion.execute(status -> Integer.valueOf(nummernkreis.lies(jahr))).intValue();
  }

  /** Zwei Zuege desselben Jahres aus zwei wirklich gleichzeitigen Transaktionen. */
  private List<Integer> zweiZuegeGleichzeitig(final int jahr) throws Exception {
    final CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      final Future<Integer> erster = pool.submit(() -> zieheNachDemStartschuss(start, jahr));
      final Future<Integer> zweiter = pool.submit(() -> zieheNachDemStartschuss(start, jahr));
      start.countDown();
      return List.of(
          erster.get(GEDULD.toSeconds(), TimeUnit.SECONDS),
          zweiter.get(GEDULD.toSeconds(), TimeUnit.SECONDS));
    }
  }

  private Integer zieheNachDemStartschuss(final CountDownLatch start, final int jahr)
      throws InterruptedException {
    start.await();
    return Integer.valueOf(ziehe(jahr));
  }

  @Test
  void lies_givenAYearWithoutARow_thenAnswersWithOne() {
    // When / Then — ein Jahr, aus dem noch keine Nummer gezogen wurde, steht vor seiner ersten.
    assertThat(lies(JAHR)).isEqualTo(1);
  }

  @Test
  void ziehe_givenAYearWithoutARow_thenCountsFromOne() {
    // When / Then — Kriterium 16: ein neues Jahr beginnt bei 1.
    assertThat(ziehe(JAHR)).isEqualTo(1);
    assertThat(ziehe(JAHR)).isEqualTo(2);
  }

  @Test
  void setze_thenTheNextDrawStartsAtTheSetValue() {
    // Given — Kriterium 15: eine selbst gesetzte Nummer gilt.
    setze(JAHR, 4);

    // When / Then
    assertThat(ziehe(JAHR)).isEqualTo(4);
    assertThat(ziehe(JAHR)).isEqualTo(5);
  }

  @Test
  void setze_givenAYearWithoutARow_thenCreatesIt() {
    // When — ein Muster ohne Jahres-Platzhalter setzt auf ein Zaehlerjahr ohne Zeile.
    setze(JAHR, 7);

    // Then
    assertThat(lies(JAHR)).isEqualTo(7);
  }

  @Test
  void ziehe_givenTwoYears_thenTheirCountersAreIndependent() {
    // Given — Kriterium 17: das alte Jahr zaehlt weiter, waehrend das neue bei 1 beginnt.
    ziehe(JAHR);
    ziehe(JAHR);

    // When / Then
    assertThat(ziehe(ANDERES_JAHR)).isEqualTo(1);
    assertThat(ziehe(JAHR)).isEqualTo(3);
  }

  @Test
  void ziehe_givenARolledBackTransaction_thenTheNumberIsFreeAgain() {
    // Given — eine Sequenz koennte das nicht: Sie ist nicht transaktional.
    assertThatThrownBy(
            () ->
                transaktion.executeWithoutResult(
                    status -> {
                      nummernkreis.ziehe(JAHR);
                      throw new IllegalStateException("Ruecklauf");
                    }))
        .isInstanceOf(IllegalStateException.class);

    // When / Then — der Kreis bleibt lueckenlos.
    assertThat(ziehe(JAHR)).isEqualTo(1);
  }

  @Test
  void ziehe_givenTwoConcurrentDrawsOnAYearWithoutARow_thenBothSucceedWithOneAndTwo()
      throws Exception {
    // When — der scharfe Fall: Ein FOR UPDATE allein sperrt die fehlende Zeile nicht, beide Zuege
    // laesen „nicht da", und der zweite scheiterte am Primaerschluessel.
    final List<Integer> gezogen = zweiZuegeGleichzeitig(JAHR);

    // Then
    assertThat(gezogen).containsExactlyInAnyOrder(Integer.valueOf(1), Integer.valueOf(2));
  }

  @Test
  void ziehe_givenTwoConcurrentDrawsOnAYearWithARow_thenBothSucceedWithoutAGap() throws Exception {
    // Given — dasselbe Jahr, diesmal mit bestehender Zeile.
    setze(JAHR, 4);

    // When
    final List<Integer> gezogen = zweiZuegeGleichzeitig(JAHR);

    // Then
    assertThat(gezogen).containsExactlyInAnyOrder(Integer.valueOf(4), Integer.valueOf(5));
  }

  @Test
  void nummernkreis_givenACounterBelowOne_thenRejectedByTheDatabase() {
    // When / Then — der CHECK der Migration haelt gegen; eine laufende Nummer beginnt bei 1.
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO rechnung_nummernkreis (jahr, naechste_nummer) VALUES (?, 0)",
                    JAHR))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
