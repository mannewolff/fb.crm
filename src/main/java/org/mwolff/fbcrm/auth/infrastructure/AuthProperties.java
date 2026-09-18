package org.mwolff.fbcrm.auth.infrastructure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Die Schalter der Anmeldung, gelesen aus {@code fbcrm.auth.*}.
 *
 * <p>Das Session-Geheimnis hat <b>keinen</b> Default: Ohne gesetzte Umgebungsvariable {@code
 * FBCRM_SESSION_SECRET} scheitert die Bindung und die Anwendung startet nicht. Ein Vorgabewert im
 * Repository waere ein bekanntes Geheimnis und damit gar keines (CLAUDE-security.md).
 *
 * @param sessionSecret Schluessel der HMAC-Signatur; mindestens 32 Zeichen
 * @param sessionTtl Laufzeit eines Session-Tokens (Default {@code P1D} in der {@code
 *     application.yml})
 * @param cookieName Name des Session-Cookies
 * @param cookieSecure ob das Cookie nur ueber HTTPS gesendet wird
 */
@ConfigurationProperties(prefix = "fbcrm.auth")
@Validated
public record AuthProperties(
    @NotBlank @Size(min = 32) String sessionSecret,
    @NotNull Duration sessionTtl,
    @NotBlank String cookieName,
    boolean cookieSecure) {}
