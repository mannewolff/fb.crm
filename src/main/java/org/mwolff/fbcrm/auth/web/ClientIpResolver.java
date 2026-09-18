package org.mwolff.fbcrm.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.springframework.stereotype.Component;

/**
 * Die Absender-Adresse einer Anfrage, so weit man ihr trauen kann (E9).
 *
 * <p>{@code X-Forwarded-For} ist ein Kopf, den jeder Aufrufer selbst schreiben kann. Er gilt
 * deshalb <b>nur</b>, wenn die Peer-Adresse — also die Gegenstelle der TCP-Verbindung — in {@code
 * FBCRM_TRUSTED_PROXIES} steht. Sonst zaehlt die Peer-Adresse. Ohne diese Klammer schriebe ein
 * Angreifer bei jedem Versuch eine andere Adresse in den Kopf und die Zaehlbremse je IP liefe leer.
 *
 * <p>Aus einer Kette wird der letzte Eintrag genommen, der <b>kein</b> vertrauenswuerdiger Proxy
 * ist: Von rechts nach links stammt jeder Eintrag von einem Proxy, dem wir trauen — der erste
 * fremde Eintrag von rechts ist der aelteste, den niemand faelschen konnte.
 *
 * <p>Warum das Ganze noetig ist, obwohl der {@code Caddyfile} den Kopf ersetzt: In Produktion
 * laeuft Caddy nicht ({@code docker-compose.prod.yml}), dort steht Traefik davor, dessen
 * Vertrauensregel nicht im Repository liegt.
 */
@Component
public class ClientIpResolver {

  private static final String FORWARDED_FOR = "X-Forwarded-For";

  private final Set<String> vertrauenswuerdig;

  public ClientIpResolver(final AuthProperties properties) {
    this.vertrauenswuerdig = Set.copyOf(properties.trustedProxies());
  }

  /** Die Adresse, gegen die gezaehlt wird. */
  public String resolve(final HttpServletRequest request) {
    final String peer = request.getRemoteAddr();
    if (!vertrauenswuerdig.contains(peer)) {
      return peer;
    }
    final @Nullable String kette = request.getHeader(FORWARDED_FOR);
    if (kette == null) {
      return peer;
    }
    final List<String> eintraege =
        Arrays.stream(kette.split(","))
            .map(String::trim)
            .filter(eintrag -> !eintrag.isEmpty())
            .toList();
    return eintraege.reversed().stream()
        .filter(eintrag -> !vertrauenswuerdig.contains(eintrag))
        .findFirst()
        .orElse(peer);
  }
}
