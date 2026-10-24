package org.mwolff.fbcrm.auth.application;

import org.mwolff.fbcrm.mail.MailProperties;
import org.mwolff.fbcrm.mail.application.EnqueueMailUseCase;
import org.springframework.stereotype.Service;

/**
 * Die Mail an eine Person, fuer die ein C-Level gerade ein Konto angelegt hat (Plan #414, A13;
 * fachliche Quelle #409, Kriterium 4).
 *
 * <p>Sie nennt die Adresse der Instanz und sagt, dass die Anmeldung dort mit der eigenen
 * E-Mail-Adresse geht. Einen Code traegt sie nicht: Den fordert die Person auf der Anmeldeseite
 * selbst an, und er gilt nur wenige Minuten — eine Begruessung, die erst am naechsten Morgen
 * gelesen wird, truege sonst einen verfallenen Code.
 *
 * <p><b>Nur bei eingeschaltetem Versand.</b> Bei ausgeschaltetem entsteht kein Auftrag — er bliebe
 * im Postausgangsfach liegen, bis jemand einen Mailserver einrichtet, und ginge dann Tage spaeter
 * hinaus. Der Anwendungsfall meldet stattdessen {@code false}, und die Oberflaeche sagt, dass keine
 * Mail hinausging.
 *
 * <p>Der Zustellauftrag gehoert in die Transaktion des Aufrufers ({@link EnqueueMailUseCase} ist
 * {@code MANDATORY}): Scheitert die Anlage des Kontos, geht auch keine Begruessung hinaus.
 */
@Service
public class BegruessungsmailUseCase {

  /** Der Betreff der Begruessung. */
  static final String BETREFF = "fb.crm: dein Konto ist eingerichtet";

  /**
   * Der Text der Mail, in Klartext wie die Mail mit dem Code.
   *
   * <p>Die Platzhalter werden ersetzt, nicht formatiert — aus demselben Grund wie in {@link
   * RequestLoginCodeUseCase}. Die Anmeldeadresse steht bewusst nicht darin: Sie ist die, an die die
   * Mail ging, und ein zweiter Platzhalter mit einer Eingabe des Menschen koennte den ersten mit
   * einem eigenen {@code {name}} unterwandern.
   */
  private static final String VORLAGE =
      """
      Hallo {name},

      fuer dich ist ein Konto bei fb.crm eingerichtet. Du findest fb.crm unter

      {instanz}

      Melde dich dort mit der E-Mail-Adresse an, an die diese Nachricht ging. Den Code fuer die
      Anmeldung forderst du auf der Anmeldeseite an; er kommt per Mail.
      """;

  private final EnqueueMailUseCase postausgang;
  private final MailProperties mail;

  public BegruessungsmailUseCase(final EnqueueMailUseCase postausgang, final MailProperties mail) {
    this.postausgang = postausgang;
    this.mail = mail;
  }

  /**
   * Stellt die Begruessung ins Postausgangsfach, sofern der Versand eingeschaltet ist.
   *
   * @param email Anmeldeadresse des neuen Kontos, an die die Mail geht
   * @param name Anzeigename fuer die Anrede
   * @return ob ein Zustellauftrag entstanden ist
   */
  public boolean sende(final String email, final String name) {
    if (!mail.enabled()) {
      return false;
    }
    postausgang.enqueue(email, BETREFF, text(name));
    return true;
  }

  private String text(final String name) {
    // Der Name kommt vom Menschen und wird zuletzt eingesetzt: Enthielte er selbst einen
    // Platzhalter, ersetzte ihn danach niemand mehr.
    return VORLAGE.replace("{instanz}", mail.baseUrl()).replace("{name}", name);
  }
}
