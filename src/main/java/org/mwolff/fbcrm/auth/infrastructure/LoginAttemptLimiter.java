package org.mwolff.fbcrm.auth.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.springframework.stereotype.Component;

/**
 * Zaehlbremse der Anmeldung (E9; E3, E27 des Plans #395).
 *
 * <p>Gezaehlt wird in gleitenden Fenstern, getrennt nach drei {@link Zaehlart Zaehlarten} mit
 * eigener Grenze und eigenem Fenster:
 *
 * <ul>
 *   <li>{@link Zaehlart#HERKUNFT} — je Absender-IP, gemeinsam fuer Anforderungen und Eingaben
 *       (Vorgabe 10 in 15 Minuten). Sie bremst den, der von einer Stelle aus viele Adressen oder
 *       viele Versuche durchprobiert.
 *   <li>{@link Zaehlart#ADRESSE_STUNDE} — je E-Mail-Adresse (Vorgabe 5 in der Stunde). Sie bremst
 *       den, der dieselbe Adresse aus vielen Netzen angeht, und gilt fuer Adressen mit und ohne
 *       Konto gleich (Kriterium 5).
 *   <li>{@link Zaehlart#ADRESSE_TAG} — je E-Mail-Adresse, die Tagesdecke (Vorgabe 10 in 24 Stunden,
 *       E27). Sie haelt den dauerhaften Rater, den die Stundengrenze allein jede Stunde wieder
 *       hereinliesse.
 * </ul>
 *
 * <p>Im Speicher und nicht in der Datenbank: Die Anwendung laeuft als eine Instanz, und ein
 * zusaetzlicher Dienst braechte keinen Zugewinn. Ein Neustart setzt die Zaehler zurueck — das ist
 * der bewusste Preis.
 *
 * <p>Der Bestand waechst nicht unbegrenzt: Jeder Lesezugriff wirft abgelaufene Eintraege weg und
 * entfernt den Schluessel, sobald nichts mehr im Fenster steht.
 */
/*
 * PMD.TooManyMethods: Mit der Zaehlregel der Code-Anforderung (E8) sind es zwoelf Methoden, die
 * Schwelle der Regel liegt bei zehn. Die Regel gehoert hierher und nicht in jeden der beiden
 * Anwendungsfaelle, die einen Code anfordern: Dort stuende sie zweimal, und genau ihre Reihenfolge —
 * erst pruefen, dann in allen drei Zaehlarten zaehlen, abgewiesen nie — ist die Zusage aus
 * Kriterium 5. Sie in eine eigene Klasse abzuspalten, verschoebe die Zahl, ohne etwas zu klaeren.
 */
@SuppressWarnings("PMD.TooManyMethods")
@Component
public class LoginAttemptLimiter {

  private final Map<Zaehler, List<Instant>> eintraege = new ConcurrentHashMap<>();
  private final AuthProperties properties;
  private final Clock clock;

  public LoginAttemptLimiter(final AuthProperties properties, final Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  /** Ob die Zaehlart unter diesem Schluessel einen weiteren Versuch zulaesst. */
  public boolean isAllowed(final Zaehlart art, final String schluessel) {
    return imFenster(zaehler(art, schluessel)).size() < grenze(art);
  }

  /** Vermerkt einen Versuch unter dieser Zaehlart und diesem Schluessel. */
  public void record(final Zaehlart art, final String schluessel) {
    eintraege.compute(
        zaehler(art, schluessel),
        (unbenutzt, bisher) -> {
          final List<Instant> zusammen =
              new ArrayList<>(bisher == null ? List.of() : gueltige(art, bisher));
          zusammen.add(clock.instant());
          return List.copyOf(zusammen);
        });
  }

  /**
   * Zaehlt eine Code-Anforderung — oder liefert die Wartezeit, wenn eine Zaehlart sperrt (E8).
   *
   * <p>Eine Anforderung zaehlt in allen drei Zaehlarten zugleich: je Herkunft, je Adresse in der
   * Stunde und je Adresse am Tag. Sperrt eine davon, wird <b>nichts</b> gezaehlt und die laengste
   * Restwartezeit geliefert — wer nach der kuerzeren wiederkaeme, liefe in die naechste Bremse.
   * Eine abgewiesene Anforderung zaehlt nicht mit: Sonst stuenden nach der genannten Wartezeit noch
   * immer zu viele Eintraege im Fenster, und die Meldung naennte eine falsche Zeit (E14).
   *
   * <p>Ob es zu der Adresse ein Konto gibt oder der Einmal-Schluessel stimmt, weiss die Bremse
   * nicht; sie wird <b>vor</b> diesen Pruefungen gefragt, damit die Grenze fuer jede Adresse gleich
   * gilt (Kriterium 5).
   *
   * @param clientIp die Absender-Adresse der Anforderung
   * @param email die Adresse, fuer die ein Code angefordert wird
   * @return null, wenn die Anforderung gezaehlt ist, sonst die Restwartezeit
   */
  public Duration zaehleAnforderung(final String clientIp, final String email) {
    final Duration warten =
        Collections.max(
            List.of(
                sperre(Zaehlart.HERKUNFT, clientIp),
                sperre(Zaehlart.ADRESSE_STUNDE, email),
                sperre(Zaehlart.ADRESSE_TAG, email)));
    if (warten.isZero()) {
      record(Zaehlart.HERKUNFT, clientIp);
      record(Zaehlart.ADRESSE_STUNDE, email);
      record(Zaehlart.ADRESSE_TAG, email);
    }
    return warten;
  }

  /** Die Restwartezeit einer Zaehlart, null, wenn sie zulaesst. */
  private Duration sperre(final Zaehlart art, final String schluessel) {
    return isAllowed(art, schluessel) ? Duration.ZERO : restwartezeit(art, schluessel);
  }

  /**
   * Wie lange der aelteste Eintrag im Fenster noch zaehlt, auf ganze Minuten aufgerundet.
   *
   * <p>Aufgerundet, weil die Meldung die Wartezeit in ganzen Minuten nennt (E14): Wer nach der
   * abgerundeten Zeit wiederkaeme, liefe noch einmal in die Bremse. Steht nichts mehr im Fenster,
   * ist sie null.
   */
  public Duration restwartezeit(final Zaehlart art, final String schluessel) {
    final List<Instant> aktuell = imFenster(zaehler(art, schluessel));
    if (aktuell.isEmpty()) {
      return Duration.ZERO;
    }
    final Duration rest = Duration.between(clock.instant(), aktuell.getFirst().plus(fenster(art)));
    return Duration.ofMinutes(rest.minusNanos(1).toMinutes() + 1);
  }

  /**
   * Loescht nach einer gelungenen Anmeldung die Zaehlart {@link Zaehlart#HERKUNFT} dieser IP.
   *
   * <p>Ohne das bliebe jemand, der sich neunmal vertippt und beim zehnten Mal hereinkommt, bis zum
   * Ende des Fensters einen Versuch von der Sperre entfernt. Die Adresszaehler bleiben dagegen
   * stehen (E3): Sonst setzte jede gelungene Anmeldung die Grenzen aus Kriterium 5 zurueck.
   */
  public void recordSuccess(final String clientIp) {
    eintraege.remove(zaehler(Zaehlart.HERKUNFT, clientIp));
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
    return eintraege.size();
  }

  private List<Instant> imFenster(final Zaehler zaehler) {
    final @Nullable List<Instant> aktuell =
        eintraege.computeIfPresent(
            zaehler,
            (unbenutzt, bisher) -> {
              final List<Instant> gueltige = gueltige(zaehler.art(), bisher);
              return gueltige.isEmpty() ? null : gueltige;
            });
    return aktuell == null ? List.of() : aktuell;
  }

  private List<Instant> gueltige(final Zaehlart art, final List<Instant> zeiten) {
    final Instant grenze = clock.instant().minus(fenster(art));
    return zeiten.stream().filter(zeit -> zeit.isAfter(grenze)).toList();
  }

  private int grenze(final Zaehlart art) {
    return switch (art) {
      case HERKUNFT -> properties.loginMaxAttempts();
      case ADRESSE_STUNDE -> properties.loginCodePerAddress();
      case ADRESSE_TAG -> properties.loginCodePerAddressDaily();
    };
  }

  private Duration fenster(final Zaehlart art) {
    return switch (art) {
      case HERKUNFT -> properties.loginAttemptWindow();
      case ADRESSE_STUNDE -> properties.loginCodeAddressWindow();
      case ADRESSE_TAG -> properties.loginCodeDailyWindow();
    };
  }

  /**
   * Damit {@code Manne@Example.org} und {@code manne@example.org} denselben Zaehler treffen.
   *
   * <p>Fuer jede Zaehlart, nicht nur fuer die Adressen: Eine IPv6-Adresse in Hex ist ebenso
   * unabhaengig von der Schreibweise, und eine Regel fuer alle hat keinen Zweig, der fehlen kann.
   */
  private static Zaehler zaehler(final Zaehlart art, final String schluessel) {
    return new Zaehler(art, schluessel.toLowerCase(Locale.ROOT));
  }

  /** Wonach gezaehlt wird; jede Zaehlart hat ihre eigene Grenze und ihr eigenes Fenster. */
  public enum Zaehlart {
    /** Je Absender-IP, Anforderungen und Eingaben zusammen. */
    HERKUNFT,
    /** Je E-Mail-Adresse, die Grenze in der Stunde. */
    ADRESSE_STUNDE,
    /** Je E-Mail-Adresse, die Tagesdecke. */
    ADRESSE_TAG
  }

  private record Zaehler(Zaehlart art, String schluessel) {}
}
