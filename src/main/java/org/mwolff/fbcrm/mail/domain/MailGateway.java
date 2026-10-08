package org.mwolff.fbcrm.mail.domain;

/**
 * Port auf den Weg einer Nachricht nach draussen.
 *
 * <p>Er existiert aus demselben Grund wie {@code PasswordHasher}: Der Hintergrundjob uebersetzt
 * gegen diesen Namen, nicht gegen ein Mail-Framework (CLAUDE-java.md §6.1). Was dahintersteht,
 * entscheidet die Infrastruktur — heute SMTP ueber {@code JavaMailSender}.
 *
 * <p>Die Absenderadresse steht bewusst <b>nicht</b> in der Signatur: Sie ist eine Eigenschaft der
 * Instanz ({@code FBCRM_MAIL_FROM}), keine Entscheidung des Aufrufers.
 *
 * <p>Genau eine abstrakte Methode, aber bewusst kein funktionales Interface: Ein Port beschreibt
 * eine Rolle, die ein benannter Adapter uebernimmt, und wird nie als Lambda geschrieben.
 * {@code @FunctionalInterface} behauptete eine Absicht, die nicht besteht — daher die
 * Unterdrueckung der PMD-Regel statt der Annotation, wie bei {@code Identifiable}.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface MailGateway {

  /**
   * Stellt eine Nachricht zu.
   *
   * @param recipient Empfaengeradresse
   * @param subject Betreff
   * @param body Rumpf der Nachricht
   */
  void send(String recipient, String subject, String body);
}
