package org.mwolff.fbcrm.auth;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Die Schalter der Anmeldung, gelesen aus {@code fbcrm.auth.*}.
 *
 * <p>Das Session-Geheimnis hat <b>keinen</b> Default: Ohne gesetzte Umgebungsvariable {@code
 * FBCRM_SESSION_SECRET} scheitert die Bindung und die Anwendung startet nicht. Ein Vorgabewert im
 * Repository waere ein bekanntes Geheimnis und damit gar keines (CLAUDE-security.md).
 *
 * <p>{@code trustedProxies} ist bewusst leer als Default: Nur wenn die Peer-Adresse einer Anfrage
 * hier steht, glaubt die Anwendung dem Kopf {@code X-Forwarded-For} (E9). Ein gefaelschter Kopf
 * hebelte sonst die Zaehlbremse je Absender-IP aus.
 *
 * @param sessionSecret Schluessel der HMAC-Signatur; mindestens 32 Zeichen
 * @param sessionTtl Laufzeit eines Session-Tokens (Default {@code P1D} in der {@code
 *     application.yml})
 * @param cookieName Name des Session-Cookies
 * @param cookieSecure ob das Cookie nur ueber HTTPS gesendet wird
 * @param loginMaxAttempts Zahl der Fehlversuche im Fenster, ab der die Anmeldung 429 antwortet
 * @param loginAttemptWindow Zeitfenster, ueber das Fehlversuche gezaehlt werden
 * @param trustedProxies Peer-Adressen, deren {@code X-Forwarded-For} gilt
 */
@ConfigurationProperties(prefix = "fbcrm.auth")
@Validated
public record AuthProperties(
    @NotBlank @Size(min = 32) String sessionSecret,
    @NotNull Duration sessionTtl,
    @NotBlank String cookieName,
    boolean cookieSecure,
    @Min(1) int loginMaxAttempts,
    @NotNull Duration loginAttemptWindow,
    @DefaultValue @NotNull List<String> trustedProxies) {

  /**
   * Haelt die Liste der vertrauenswuerdigen Gegenstellen sauber.
   *
   * <p>{@code FBCRM_TRUSTED_PROXIES=} bindet zu einer Liste mit einem leeren Eintrag. Ohne diese
   * Normalisierung waere der leere Eintrag eine Adresse, die keine ist — und {@code
   * ClientIpResolver} verglich die Peer-Adresse dagegen.
   */
  public AuthProperties {
    trustedProxies =
        trustedProxies.stream().map(String::trim).filter(eintrag -> !eintrag.isEmpty()).toList();
  }
}
