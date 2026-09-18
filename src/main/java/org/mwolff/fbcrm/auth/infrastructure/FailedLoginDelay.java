package org.mwolff.fbcrm.auth.infrastructure;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Random;
import org.springframework.stereotype.Component;

/**
 * Zufaellige Verzoegerung nach einer fehlgeschlagenen Anmeldung (CLAUDE-security.md).
 *
 * <p>Sie deckt zu, was die Laufzeit sonst verraet: Ein Konto, das es nicht gibt, spart sonst einen
 * Argon2-Durchlauf und antwortet messbar frueher als ein falsches Passwort. Der Dummy-Hash in
 * {@code LoginUseCase} gleicht die Rechenzeit an, diese Verzoegerung streut den Rest.
 *
 * <p>Die Zufallsquelle steckt im Konstruktor, damit der Test sie ersetzen kann.
 */
@Component
public class FailedLoginDelay {

  private static final int MIN_MS = 200;
  private static final int MAX_MS = 800;
  private static final int SPANNE = MAX_MS - MIN_MS + 1;

  private final Random random;

  /** Nutzt die Zufallsquelle der Plattform. */
  public FailedLoginDelay() {
    this(new SecureRandom());
  }

  FailedLoginDelay(final Random random) {
    this.random = random;
  }

  /**
   * Haelt den aufrufenden Thread 200 bis 800 ms an.
   *
   * <p>Wird der Thread dabei unterbrochen, endet die Verzoegerung frueh; das Unterbrechungszeichen
   * wird wiederhergestellt, damit die Abbruchabsicht nicht verloren geht.
   */
  /*
   * PMD.DoNotUseThreads warnt vor Thread-Handhabung in verwaltetem Code. Genau diese
   * Verzoegerung schreibt CLAUDE-security.md aber vor ("Failed-Login-Delay … via Thread.sleep in
   * einem dedizierten Use-Case"), und sie ist ohne Anhalten des Threads nicht zu haben. Die
   * Ausnahme steht methodengenau und nicht als Abschaltung der Regel im Regelsatz.
   */
  @SuppressWarnings("PMD.DoNotUseThreads")
  public void apply() {
    try {
      Thread.sleep(Duration.ofMillis(MIN_MS + random.nextInt(SPANNE)));
    } catch (final InterruptedException unterbrochen) {
      Thread.currentThread().interrupt();
    }
  }
}
