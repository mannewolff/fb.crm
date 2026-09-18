package org.mwolff.fbcrm.auth.infrastructure;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.springframework.stereotype.Component;

/**
 * Zaehlbremse gegen das Erraten von Passwoertern (E9).
 *
 * <p>Gezaehlt wird in einem gleitenden Fenster <b>getrennt</b> nach Absender-IP und nach
 * E-Mail-Adresse. Beide Zaehler sind noetig: Der IP-Zaehler bremst den, der eine Adresse mit vielen
 * Passwoertern angeht; der Adress-Zaehler bremst den, der dieselbe Adresse aus vielen Netzen
 * angeht. Die Schwelle ist fuer beide dieselbe ({@code fbcrm.auth.login-max-attempts}).
 *
 * <p>Im Speicher und nicht in der Datenbank: Die Anwendung laeuft als eine Instanz, und ein
 * zusaetzlicher Dienst braechte keinen Zugewinn. Ein Neustart setzt die Zaehler zurueck — das ist
 * der bewusste Preis.
 *
 * <p>Der Bestand waechst nicht unbegrenzt: Jeder Lesezugriff wirft abgelaufene Eintraege weg und
 * entfernt den Schluessel, sobald nichts mehr im Fenster steht.
 */
@Component
public class LoginAttemptLimiter {

  private static final String IP = "ip:";
  private static final String MAIL = "mail:";

  private final Map<String, List<Instant>> fehlversuche = new ConcurrentHashMap<>();
  private final AuthProperties properties;
  private final Clock clock;

  public LoginAttemptLimiter(final AuthProperties properties, final Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  /** Ob ein weiterer Anmeldeversuch von dieser Adresse fuer diese E-Mail zulaessig ist. */
  public boolean isAllowed(final String clientIp, final String email) {
    return imFenster(IP + clientIp) < properties.loginMaxAttempts()
        && imFenster(MAIL + normalisiert(email)) < properties.loginMaxAttempts();
  }

  /** Vermerkt einen Fehlversuch auf beiden Zaehlern. */
  public void recordFailure(final String clientIp, final String email) {
    merke(IP + clientIp);
    merke(MAIL + normalisiert(email));
  }

  /**
   * Loescht beide Zaehler nach einer gelungenen Anmeldung.
   *
   * <p>Ohne das bliebe jemand, der sich neunmal vertippt und beim zehnten Mal hereinkommt, bis zum
   * Ende des Fensters einen Fehlversuch von der Sperre entfernt.
   */
  public void recordSuccess(final String clientIp, final String email) {
    fehlversuche.remove(IP + clientIp);
    fehlversuche.remove(MAIL + normalisiert(email));
  }

  /**
   * Wie viele Schluessel der Bestand gerade fuehrt.
   *
   * <p>Sichtbar, weil die Aufraeumregel sonst unpruefbar waere: Dass ein Schluessel verschwindet,
   * sobald nichts mehr im Fenster steht, aendert an keiner Antwort der Bremse etwas — es ist allein
   * der Unterschied zwischen einem begrenzten und einem unbegrenzt wachsenden Bestand. Wer von
   * aussen Adressen durchprobiert, legte sonst mit jedem Versuch einen Schluessel an, der nie
   * wieder verschwindet.
   */
  int trackedCounters() {
    return fehlversuche.size();
  }

  private int imFenster(final String schluessel) {
    final @Nullable List<Instant> aktuell =
        fehlversuche.computeIfPresent(
            schluessel,
            (unbenutzt, bisher) -> {
              final List<Instant> gueltige = gueltige(bisher);
              return gueltige.isEmpty() ? null : gueltige;
            });
    return aktuell == null ? 0 : aktuell.size();
  }

  private void merke(final String schluessel) {
    fehlversuche.compute(
        schluessel,
        (unbenutzt, bisher) -> {
          final List<Instant> zusammen =
              new ArrayList<>(bisher == null ? List.of() : gueltige(bisher));
          zusammen.add(clock.instant());
          return List.copyOf(zusammen);
        });
  }

  private List<Instant> gueltige(final List<Instant> zeiten) {
    final Instant grenze = clock.instant().minus(properties.loginAttemptWindow());
    return zeiten.stream().filter(zeit -> zeit.isAfter(grenze)).toList();
  }

  /** Damit {@code Manne@Example.org} und {@code manne@example.org} denselben Zaehler treffen. */
  private static String normalisiert(final String email) {
    return email.toLowerCase(Locale.ROOT);
  }
}
