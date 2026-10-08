package org.mwolff.fbcrm.mail.infrastructure;

import org.mwolff.fbcrm.mail.MailProperties;
import org.mwolff.fbcrm.mail.domain.MailGateway;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Setzt den Port {@link MailGateway} auf den Mailversand von Spring um.
 *
 * <p>Der Adapter entscheidet nichts ausser der Absenderadresse: Sie kommt aus {@code
 * FBCRM_MAIL_FROM} und nicht vom Aufrufer, damit jede Nachricht dieser Instanz unter derselben
 * Adresse hinausgeht.
 *
 * <p>Fehler werden bewusst <b>nicht</b> gefangen: Ob ein vergeblicher Versuch gezaehlt wird oder
 * nicht, entscheidet {@link OutboxDispatcher} — hier waere die Entscheidung an der Stelle
 * vergraben, an der sie niemand sucht.
 */
@Component
class JavaMailGateway implements MailGateway {

  private final JavaMailSender sender;
  private final MailProperties properties;

  JavaMailGateway(final JavaMailSender sender, final MailProperties properties) {
    this.sender = sender;
    this.properties = properties;
  }

  @Override
  public void send(final String recipient, final String subject, final String body) {
    final SimpleMailMessage nachricht = new SimpleMailMessage();
    nachricht.setFrom(properties.from());
    nachricht.setTo(recipient);
    nachricht.setSubject(subject);
    nachricht.setText(body);
    sender.send(nachricht);
  }
}
