package org.mwolff.fbcrm.config;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Die Start-Validierung der Betriebsschalter (E27).
 *
 * <p>Sie loest eine Zusage ein, die sonst nur als Kommentar in {@code docker-compose.yml} Z. 36 ff.
 * stuende: Mit {@code FBCRM_DEV_MODE=true} laeuft die Anwendung mit dem voreingestellten
 * Signaturgeheimnis und <b>warnt</b>; mit {@code false} — dem festen Wert des Produktions-Overlays
 * — startet sie gar nicht erst. Ohne diese Pruefung signierte eine Produktionsinstanz ihre
 * Sitzungen mit einem Geheimnis, das im Repository steht, und die Kommentare in den Compose-Dateien
 * waeren eine Unwahrheit.
 *
 * <p>Die Laengenschwelle steht hier ein zweites Mal: {@link AuthProperties} zaehlt Zeichen
 * ({@code @Size(min = 32)}), diese Pruefung zaehlt die Bytes, die die HMAC-Signatur tatsaechlich an
 * Schluesselmaterial bekommt. Dass beide Riegel dasselbe Ziel haben, ist Absicht — ein gelockertes
 * Bindungs-Constraint duerfte die Schwelle nicht stillschweigend mitnehmen.
 *
 * <p>Der Log-Text ist Teil der Zusage: Eine Warnung, die das Geheimnis mitschreibt, traegt es in
 * jedes Log-Archiv und jeden Support-Auszug. Sie nennt deshalb nur die Variable, nie den Wert.
 */
@Component
public class StartupValidator {

  /**
   * Das Vorgabegeheimnis aus {@code docker-compose.yml} Z. 44.
   *
   * <p>Es steht im Repository und ist damit oeffentlich bekannt — deshalb ist es genau der Wert,
   * den eine Produktionsinstanz nicht tragen darf.
   */
  static final String DEV_DEFAULT_SESSION_SECRET = "dev-only-insecure-secret-change-me";

  /** Die Mindestlaenge des Signaturgeheimnisses in Byte. */
  static final int MIN_SECRET_BYTES = 32;

  private static final Logger LOG = LoggerFactory.getLogger(StartupValidator.class);

  private final AuthProperties auth;
  private final boolean devMode;

  public StartupValidator(final AuthProperties auth, final OperationsProperties betrieb) {
    this.auth = auth;
    this.devMode = betrieb.devMode();
  }

  /**
   * Prueft die Schalter, bevor die Anwendung Anfragen annimmt.
   *
   * @throws IllegalStateException wenn eine Produktionsinstanz unsicher signieren wuerde
   */
  @PostConstruct
  public void validate() {
    if (devMode) {
      warneVorDemVorgabegeheimnis();
      return;
    }
    verweigereUnsicheresGeheimnis();
  }

  private void warneVorDemVorgabegeheimnis() {
    if (DEV_DEFAULT_SESSION_SECRET.equals(auth.sessionSecret())) {
      LOG.warn(
          "Entwicklungsbetrieb: Die Sitzungen werden mit dem voreingestellten Geheimnis aus dem"
              + " Repository signiert. Vor dem Produktivbetrieb FBCRM_SESSION_SECRET auf einen"
              + " eigenen, zufaelligen Wert von mindestens {} Byte setzen.",
          MIN_SECRET_BYTES);
    }
  }

  private void verweigereUnsicheresGeheimnis() {
    final String geheimnis = auth.sessionSecret();
    if (DEV_DEFAULT_SESSION_SECRET.equals(geheimnis)) {
      throw new IllegalStateException(
          "FBCRM_SESSION_SECRET traegt das voreingestellte Geheimnis aus dem Repository. Es ist"
              + " oeffentlich bekannt und taugt ausserhalb von FBCRM_DEV_MODE=true nicht. Bitte"
              + " einen eigenen, zufaelligen Wert setzen.");
    }
    if (geheimnis.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
      throw new IllegalStateException(
          "FBCRM_SESSION_SECRET ist kuerzer als "
              + MIN_SECRET_BYTES
              + " Byte und damit als Schluessel einer HMAC-Signatur zu schwach.");
    }
  }
}
