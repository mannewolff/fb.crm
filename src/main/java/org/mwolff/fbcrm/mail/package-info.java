/**
 * Das transaktionale Postausgangsfach als Modul (E7).
 *
 * <p>Es loest ein Problem, das der direkte Versand im Request nicht loesen kann: Ein Zustellauftrag
 * entsteht in <b>derselben</b> Transaktion wie das, was ihn ausloest. Wird die zurueckgerollt,
 * verschwindet der Auftrag mit ihr — statt eine Mail zu einem Token hinauszuschicken, das es nie
 * gab. Umgekehrt haengt keine Antwortzeit mehr an der Erreichbarkeit des Mailservers.
 *
 * <p>{@code MailProperties} und {@code OutboxProperties} liegen hier und nicht in einer der
 * Schichten, weil mehrere von ihnen lesen — die Anwendungsschicht nichts, die Infrastruktur den
 * Takt, die Grenzen und die Absenderadresse, und der Passwort-Reset im Modul {@code auth} die
 * oeffentliche Adresse fuer den Link.
 */
@NullMarked
package org.mwolff.fbcrm.mail;

import org.jspecify.annotations.NullMarked;
