package org.mwolff.fbcrm.mail;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Die Schalter des Mailversands, gelesen aus {@code fbcrm.mail.*}.
 *
 * <p>{@code enabled} ist mit {@code false} vorbelegt und damit der strengere der beiden Faelle: Wer
 * Mail hinausschicken will, sagt es ausdruecklich. {@code docker-compose.yml} Z. 52 tut das nicht —
 * der lokale Stack laeuft ohne Versand, und die Auftraege bleiben im Postausgangsfach liegen, bis
 * der Betreiber einen Mailserver eingerichtet hat.
 *
 * <p>{@code baseUrl} ist die oeffentliche Adresse der Instanz ({@code FBCRM_BASE_URL}). Aus ihr
 * entsteht der Link in der Reset-Mail; die Anwendung kann ihn nicht aus der Anfrage ableiten, weil
 * die Anforderung eines neuen Passworts von einer anderen Stelle kommen kann als der Klick auf den
 * Link — und weil ein aus Kopfzeilen gebauter Link sich faelschen liesse (CLAUDE-security.md).
 *
 * @param enabled ob ueberhaupt zugestellt wird
 * @param from Absenderadresse jeder Nachricht dieser Instanz
 * @param baseUrl oeffentliche Adresse der Instanz, ohne abschliessenden Schraegstrich
 */
@ConfigurationProperties(prefix = "fbcrm.mail")
@Validated
public record MailProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue @NotBlank String from,
    @DefaultValue @NotBlank String baseUrl) {

  /**
   * Nimmt der oeffentlichen Adresse den abschliessenden Schraegstrich.
   *
   * <p>{@code FBCRM_BASE_URL=https://crm.example.org/} ergaebe sonst einen Link mit doppeltem
   * Trenner, und den nimmt nicht jeder Mailclient als eine Adresse.
   */
  public MailProperties {
    baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }
}
