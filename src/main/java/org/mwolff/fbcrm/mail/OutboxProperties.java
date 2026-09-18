package org.mwolff.fbcrm.mail;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Die Schalter des Postausgangsfachs, gelesen aus {@code fbcrm.outbox.*}.
 *
 * <p>Jeder Wert hat eine untere Schranke, weil die Null bei jedem von ihnen etwas anderes bedeutet
 * als „aus": Ein Takt von 0 liesse den Hintergrundjob ohne Pause laufen, eine Versuchszahl von 0
 * gaebe jeden Auftrag auf, bevor er je hinausging, und eine Aufbewahrung von 0 Tagen loeschte
 * zugestellte Auftraege noch in derselben Stunde. Abgeschaltet wird ueber {@code enabled}.
 *
 * @param enabled ob der Hintergrundjob ueberhaupt laeuft ({@code FBCRM_OUTBOX_ENABLED})
 * @param pollIntervalMs Takt des Hintergrundjobs in Millisekunden
 * @param maxAttempts Zahl der Zustellversuche, nach der ein Auftrag liegen bleibt
 * @param retentionDays Tage, die ein zugestellter Auftrag aufgehoben wird
 */
@ConfigurationProperties(prefix = "fbcrm.outbox")
@Validated
public record OutboxProperties(
    @DefaultValue("false") boolean enabled,
    @Min(1) long pollIntervalMs,
    @Min(1) int maxAttempts,
    @Min(1) int retentionDays) {

  /**
   * Der Abstand bis zum ersten Wiederholungsversuch.
   *
   * <p>Er ist der Takt des Hintergrundjobs: Frueher als beim naechsten Durchgang koennte ohnehin
   * nichts geschehen. Von dort an verdoppelt {@code OutboxMessage#failed} ihn.
   */
  public Duration retryBackoffBase() {
    return Duration.ofMillis(pollIntervalMs);
  }

  /** Die Aufbewahrungsfrist zugestellter Auftraege. */
  public Duration retention() {
    return Duration.ofDays(retentionDays);
  }
}
