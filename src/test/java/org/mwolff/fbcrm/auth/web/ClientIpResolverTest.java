package org.mwolff.fbcrm.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Wem die Anwendung ihre Absender-Adresse glaubt (E9).
 *
 * <p>Der Kopf {@code X-Forwarded-For} ist frei beschreibbar. Die Probe besteht deshalb vor allem
 * aus Faellen, in denen er <b>nicht</b> gilt.
 */
class ClientIpResolverTest {

  private static final String PROXY = "10.0.0.1";
  private static final String ZWEITER_PROXY = "10.0.0.2";
  private static final String CLIENT = "203.0.113.7";
  private static final String FREMD = "198.51.100.9";

  private static ClientIpResolver resolver(final String... vertrauenswuerdig) {
    return new ClientIpResolver(
        new AuthProperties(
            "geheimnis-mit-mindestens-32-zeichen-laenge",
            Duration.ofDays(1),
            Duration.ofHours(1),
            "fbcrm_session",
            true,
            10,
            Duration.ofMinutes(15),
            List.of(vertrauenswuerdig)));
  }

  private static MockHttpServletRequest anfrage(final String peer, final String forwardedFor) {
    final MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(peer);
    request.addHeader("X-Forwarded-For", forwardedFor);
    return request;
  }

  private static MockHttpServletRequest anfrageOhneKopf(final String peer) {
    final MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(peer);
    return request;
  }

  @Test
  void resolve_givenNoTrustedProxyAtAll_thenTakesThePeerAddress() {
    // When — Default-Konfiguration: die Liste ist leer.
    final String adresse = resolver().resolve(anfrage(CLIENT, FREMD));

    // Then
    assertThat(adresse).isEqualTo(CLIENT);
  }

  @Test
  void resolve_givenAPeerOutsideTheTrustedList_thenIgnoresTheForwardedHeader() {
    // When
    final String adresse = resolver(PROXY).resolve(anfrage(FREMD, CLIENT));

    // Then
    assertThat(adresse).isEqualTo(FREMD);
  }

  @Test
  void resolve_givenATrustedPeer_thenTakesTheLastForeignEntryOfTheChain() {
    // When
    final String adresse =
        resolver(PROXY, ZWEITER_PROXY).resolve(anfrage(PROXY, CLIENT + ", " + ZWEITER_PROXY));

    // Then — von rechts gelesen ist ZWEITER_PROXY vertrauenswuerdig, CLIENT der erste fremde.
    assertThat(adresse).isEqualTo(CLIENT);
  }

  @Test
  void resolve_givenATrustedPeerAndAForgedChain_thenTakesTheRightmostForeignEntry() {
    // When — der Client hat selbst etwas in den Kopf geschrieben; Caddy haengt an.
    final String adresse = resolver(PROXY).resolve(anfrage(PROXY, "1.2.3.4, " + CLIENT));

    // Then
    assertThat(adresse).isEqualTo(CLIENT);
  }

  @Test
  void resolve_givenATrustedPeerWithoutAForwardedHeader_thenTakesThePeerAddress() {
    // When
    final String adresse = resolver(PROXY).resolve(anfrageOhneKopf(PROXY));

    // Then
    assertThat(adresse).isEqualTo(PROXY);
  }

  @Test
  void resolve_givenAChainOfNothingButTrustedProxies_thenTakesThePeerAddress() {
    // When
    final String adresse =
        resolver(PROXY, ZWEITER_PROXY).resolve(anfrage(PROXY, ZWEITER_PROXY + ", " + PROXY));

    // Then
    assertThat(adresse).isEqualTo(PROXY);
  }

  @Test
  void resolve_givenAnEmptyForwardedHeader_thenTakesThePeerAddress() {
    // When
    final String adresse = resolver(PROXY).resolve(anfrage(PROXY, "  ,  "));

    // Then
    assertThat(adresse).isEqualTo(PROXY);
  }

  @Test
  void resolve_givenBlanksAroundTheEntries_thenTrimsThem() {
    // When
    final String adresse = resolver(PROXY).resolve(anfrage(PROXY, "   " + CLIENT + "   "));

    // Then
    assertThat(adresse).isEqualTo(CLIENT);
  }
}
