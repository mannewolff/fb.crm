package org.mwolff.fbcrm.mail.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.mail.MailProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Der duenne Adapter auf den Mailversand von Spring.
 *
 * <p>Er entscheidet nichts ausser der Absenderadresse: Sie kommt aus {@code FBCRM_MAIL_FROM} und
 * nicht vom Aufrufer, damit jede Nachricht dieser Instanz unter derselben Adresse hinausgeht.
 */
@ExtendWith(MockitoExtension.class)
class JavaMailGatewayTest {

  @Mock private JavaMailSender sender;

  @Captor private ArgumentCaptor<SimpleMailMessage> versandte;

  private JavaMailGateway gateway() {
    return new JavaMailGateway(
        sender, new MailProperties(true, "no-reply@fbcrm.local", "https://crm.example.org"));
  }

  private SimpleMailMessage nachricht() {
    verify(sender).send(versandte.capture());
    return versandte.getValue();
  }

  @Test
  void send_thenUsesTheConfiguredSenderAddress() {
    // When
    gateway().send("manne@example.org", "Betreff", "Rumpf");

    // Then
    assertThat(nachricht().getFrom()).isEqualTo("no-reply@fbcrm.local");
  }

  @Test
  void send_thenCarriesRecipientSubjectAndBody() {
    // When
    gateway().send("manne@example.org", "Betreff", "Rumpf");

    // Then
    assertThat(nachricht())
        .satisfies(
            mail -> assertThat(mail.getTo()).containsExactly("manne@example.org"),
            mail -> assertThat(mail.getSubject()).isEqualTo("Betreff"),
            mail -> assertThat(mail.getText()).isEqualTo("Rumpf"));
  }
}
