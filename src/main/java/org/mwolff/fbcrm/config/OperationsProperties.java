package org.mwolff.fbcrm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Der Betriebsschalter der Anwendung, gelesen aus {@code fbcrm.dev-mode}.
 *
 * <p>Der Default ist {@code false} und damit der strengere der beiden Faelle. Wer den
 * Entwicklungsmodus will, sagt es ausdruecklich — {@code docker-compose.yml} Z. 39 tut das, das
 * Produktions-Overlay setzt den Wert fest auf {@code false}. Ein Default von {@code true} liesse
 * eine Instanz ohne gesetzte Variable mit den unsicheren Vorgaben laufen; welche Folgen das haette,
 * steht in {@link StartupValidator}.
 *
 * @param devMode ob die Anwendung im Entwicklungsbetrieb laeuft (E27)
 */
@ConfigurationProperties(prefix = "fbcrm")
@Validated
public record OperationsProperties(@DefaultValue("false") boolean devMode) {}
