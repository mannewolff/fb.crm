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
 * <p>{@code sessionTtl} ist bewusst <b>nicht</b> eng: 30 Tage statt der „Default eng, z. B. 8 h"
 * aus CLAUDE-security.md. Die Abweichung ist begruendet (E21): Jede Neuanmeldung kostet kuenftig
 * einen Zugriff aufs Postfach, und 30 Tage ab der letzten Nutzung hat der PO entschieden (Kriterium
 * 9). Getragen wird der laengere Lauf von {@code HttpOnly}, {@code SameSite=Strict}, {@code
 * Secure}, der kontogebundenen Sitzungs-Generation und ihrem Hochzaehlen beim Abmelden (E7).
 * Gemessen wird die Laufzeit ab der letzten Nutzung, weil jede Anfrage ein frisches Token bekommt
 * ({@code SessionAuthenticationFilter}).
 *
 * <p>{@code passwordResetTtl} steht bewusst neben {@code sessionTtl} und nicht bei den Schaltern
 * des Postausgangsfachs: Wie lange ein Reset-Link gilt, ist eine Frage der Anmeldung, keine der
 * Zustellung. Er ist deutlich kuerzer als eine Sitzung — der Link liegt in einem Postfach, und ein
 * Postfach wird mitgelesen (CLAUDE-security.md).
 *
 * @param sessionSecret Schluessel der HMAC-Signatur; mindestens 32 Zeichen
 * @param sessionTtl Laufzeit eines Session-Tokens ab der letzten Nutzung (Default {@code P30D} in
 *     der {@code application.yml})
 * @param passwordResetTtl Laufzeit eines Reset-Links (Default {@code PT1H} in der {@code
 *     application.yml})
 * @param cookieName Name des Session-Cookies
 * @param cookieSecure ob das Cookie nur ueber HTTPS gesendet wird
 * @param loginMaxAttempts Zaehlart {@code HERKUNFT}: Zahl der Versuche je Herkunft im Fenster, ab
 *     der die Bremse 429 antwortet (Default {@code 10})
 * @param loginAttemptWindow Zaehlart {@code HERKUNFT}: Zeitfenster, ueber das die Versuche je
 *     Herkunft gezaehlt werden (Default {@code PT15M})
 * @param loginCodePerAddress Zaehlart {@code ADRESSE_STUNDE}: Zahl der Anforderungen je Adresse im
 *     Fenster, ab der die Bremse 429 antwortet (Default {@code 5}, Kriterium 5)
 * @param loginCodeAddressWindow Zaehlart {@code ADRESSE_STUNDE}: Zeitfenster der Grenze je Adresse
 *     (Default {@code PT1H})
 * @param loginCodePerAddressDaily Zaehlart {@code ADRESSE_TAG}: Zahl der Anforderungen je Adresse
 *     im Fenster der Tagesdecke, ab der die Bremse 429 antwortet (Default {@code 10}, E27)
 * @param loginCodeDailyWindow Zaehlart {@code ADRESSE_TAG}: Zeitfenster der Tagesdecke (Default
 *     {@code PT24H})
 * @param loginCodeTtl Laufzeit eines Einmalcodes ab seiner Ausstellung (Default {@code PT10M},
 *     Kriterium 4, E21)
 * @param loginCodeMaxAttempts Zahl der Fehleingaben, nach der ein Einmalcode verfaellt (Default
 *     {@code 5}, Kriterium 4, E21)
 * @param trustedProxies Peer-Adressen, deren {@code X-Forwarded-For} gilt
 */
@ConfigurationProperties(prefix = "fbcrm.auth")
@Validated
public record AuthProperties(
    @NotBlank @Size(min = 32) String sessionSecret,
    @NotNull Duration sessionTtl,
    @NotNull Duration passwordResetTtl,
    @NotBlank String cookieName,
    boolean cookieSecure,
    @Min(1) int loginMaxAttempts,
    @NotNull Duration loginAttemptWindow,
    @Min(1) int loginCodePerAddress,
    @NotNull Duration loginCodeAddressWindow,
    @Min(1) int loginCodePerAddressDaily,
    @NotNull Duration loginCodeDailyWindow,
    @NotNull Duration loginCodeTtl,
    @Min(1) int loginCodeMaxAttempts,
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
