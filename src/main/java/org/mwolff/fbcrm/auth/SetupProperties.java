package org.mwolff.fbcrm.auth;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Der Schalter der Einrichtung, gelesen aus {@code fbcrm.setup.*}.
 *
 * <p>Der Default ist der <b>Leerstring</b>, nicht {@code null}: {@code docker-compose.yml} Z. 47 f.
 * sagt „leer = deaktiviert" zu, und {@code SetupAccountUseCase} loest diese Zusage ein, indem es
 * bei leerem Wert jeden Aufruf abweist, <b>ohne</b> zu vergleichen. Ein konstanter Vergleich gegen
 * den Leerstring naehme sonst genau die leere Eingabe an und kehrte die Zusage ins Gegenteil.
 *
 * @param bootstrapToken Einmal-Schluessel fuer den ersten Plattform-Admin; leer heisst deaktiviert
 */
@ConfigurationProperties(prefix = "fbcrm.setup")
@Validated
public record SetupProperties(@DefaultValue("") @NotNull String bootstrapToken) {}
