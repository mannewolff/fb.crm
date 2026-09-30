package org.mwolff.fbcrm.rechnung.application;

import java.util.Set;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;

/**
 * Aus welchen Angeboten eine Rechnung entstehen darf (#160, Kriterium 2; Frage 2).
 *
 * <p>Ab „bestellt": Vorher hat der Kunde nicht zugesagt, und es gibt nichts abzurechnen. Die Menge
 * steht an einer Stelle, weil zwei Anwendungsfaelle sie brauchen — die Liste der abrechenbaren
 * Angebote und das Anlegen eines Entwurfs. Zwei Abschriften liefen beim ersten Nachziehen
 * auseinander, und die Oberflaeche zeigte dann Angebote zur Wahl an, die der Server abweist.
 *
 * <p>Ausdruecklich aufgezaehlt und nicht ueber die Ordnungszahl von {@link Angebotsstatus}: Der
 * Compiler verlangt bei einem neuen Status eine Entscheidung, statt ihn stillschweigend
 * einzureihen.
 */
final class Abrechenbarkeit {

  /** Die Status, in denen ein Angebot abgerechnet werden darf. */
  static final Set<Angebotsstatus> STATUS =
      Set.of(Angebotsstatus.BESTELLT, Angebotsstatus.ERLEDIGT, Angebotsstatus.ABGERECHNET);

  private Abrechenbarkeit() {}
}
