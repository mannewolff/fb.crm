package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.util.Set;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.rechnung.domain.Abrechnungsstand;
import org.mwolff.fbcrm.rechnung.domain.Positionsstand;

/**
 * Aus welchen Angeboten eine Rechnung entstehen darf und worueber (#160, Kriterium 2; Frage 2;
 * #330, Kriterium 7; Plan #332, E8).
 *
 * <p>Ab „bestellt": Vorher hat der Kunde nicht zugesagt, und es gibt nichts abzurechnen. Die Regel
 * steht an einer Stelle, weil drei Anwendungsfaelle sie brauchen — die Liste der abrechenbaren
 * Angebote, das Anlegen eines Entwurfs und die Taste „Rechnung schreiben" am Angebot. Zwei
 * Abschriften liefen beim ersten Nachziehen auseinander, und die Oberflaeche zeigte dann Angebote
 * zur Wahl an, die der Server abweist.
 *
 * <p><b>Worueber</b> sagt {@link #abrechenbareMenge(Angebot, Positionsstand, BigDecimal)}: ohne
 * Storno die offene Menge, am stornierten Angebot die nicht abgerechneten Stunden. Ein Storno
 * streicht nur den Rest, der nicht mehr kommt; gearbeitete Zeit bleibt abrechenbar.
 *
 * <p><b>Die Grenze der Regel:</b> Sie greift am Anlegen und an der Taste, <b>nicht</b> am Aendern
 * und nicht am Stellen. {@code RechnungAendernUseCase} laesst mehr abzurechnen als offen ist
 * bewusst zu — das Angebot ist ein Merkzettel und kein Vertrag (#160, Frage 4) —, {@code
 * RechnungStellenUseCase} prueft keine Menge gegen das Angebot, und ein vor dem Storno angelegter
 * Entwurf bleibt, wie er ist; ihn zu loeschen ist die Sache des Anwenders.
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

  /**
   * Die Menge einer Position, die in eine neue Rechnung darf.
   *
   * <p>Gerechnet wird je Position im {@link Positionsstand}, damit {@link Abrechnungsstand} sie
   * ueber alle Positionen summieren kann, ohne die Regel ein zweites Mal zu schreiben (E18).
   *
   * @param angebot das Angebot, zu dem die Position gehoert
   * @param stand der Abrechnungsstand der Position
   * @param angefallen die zu dieser Position insgesamt erfassten Stunden
   */
  static BigDecimal abrechenbareMenge(
      final Angebot angebot, final Positionsstand stand, final BigDecimal angefallen) {
    return stand.abrechenbareMenge(angebot, angefallen);
  }
}
