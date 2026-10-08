package org.mwolff.fbcrm.mail.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Schaltet die Zeitsteuerung ein — und zwar nur dann, wenn es etwas zu takten gibt.
 *
 * <p>{@code @EnableScheduling} haengt an derselben Bedingung wie die beiden Jobs selbst. Stuende es
 * an der Anwendungsklasse, liefe in jedem Anwendungskontext ein Scheduler mit, auch in jedem
 * Integrationstest — und die Tests haetten einen Nebenlaeufer, den niemand bestellt hat.
 *
 * <p>Reines Wiring ohne eigene Entscheidung im Code (CLAUDE-java.md §5.2); dass die Jobs laufen,
 * weisen {@code OutboxDispatcherTest} und {@code OutboxCleanupTest} nach.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "fbcrm.outbox.enabled", havingValue = "true")
public class OutboxSchedulingConfig {}
