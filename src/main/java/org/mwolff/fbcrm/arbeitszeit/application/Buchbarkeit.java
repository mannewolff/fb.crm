package org.mwolff.fbcrm.arbeitszeit.application;

import java.util.Set;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;

/**
 * Worauf Arbeitszeit gebucht werden darf — zwei Begriffe, zwei Regeln je Art des Angebots (Plan
 * #194, A6; Plan #218, E11, E12).
 *
 * <p><b>Buchbar</b> heisst: Die Position traegt Stunden. Am Angebot an einen Kunden ist das eine
 * Eigenschaft der Position allein — sie rechnet nach Aufwand in der Einheit Stunde ab (Issue #193,
 * Antworten 3 und 5). An der <b>internen Arbeit</b> traegt jede Position Stunden (Kriterium 5 von
 * #207, E11): Nach dem Wechsel extern → intern bleiben Festpreis- und Pauschalpositionen
 * gespeichert (Kriterium 8), und auch auf sie wird Zeit erfasst. Darum braucht die Regel das
 * Angebot und nicht nur die Position.
 *
 * <p>Davon haengen die Spalte „Angefallen", das Feld {@code buchbar} der Antwort und der Vorschlag
 * im Rechnungsentwurf ab — <b>unabhaengig vom Status des Angebots</b>. Das ist kein Versehen:
 * {@code RechnungStellenUseCase} setzt das Angebot beim Stellen selbst auf „abgerechnet", sobald
 * nichts mehr offen ist, und die interne Arbeit wird irgendwann abgeschlossen. Hinge die Anzeige am
 * Status, verschwaenden danach die angefallenen Stunden und die Ueberschreitung, die die Kriterien
 * 8 und 11 sichtbar halten.
 *
 * <p><b>Buchung zulaessig</b> heisst zusaetzlich: Das Angebot steht im richtigen Status — bestellt
 * oder erledigt am Kundenangebot (Antwort 2), laufend an der internen Arbeit (E12). Davon haengen
 * nur die Auswahlliste der Positionen und das Anlegen neuer Eintraege ab — ein alter Eintrag an
 * einem inzwischen abgerechneten oder abgeschlossenen Angebot bleibt aenderbar und loeschbar (A7).
 *
 * <p>Beide Regeln stehen hier und nur hier, damit zwei Abschriften nicht auseinanderlaufen —
 * dieselbe Ueberlegung wie bei {@code rechnung.application.Abrechenbarkeit}. Die Status sind
 * ausdruecklich aufgezaehlt und nicht ueber die Ordnungszahl von {@link Angebotsstatus} bestimmt:
 * Der Compiler verlangt bei einem neuen Status eine Entscheidung, statt ihn stillschweigend
 * einzureihen.
 */
public final class Buchbarkeit {

  /** Die Status, in denen am Angebot an einen Kunden neue Arbeitszeit gebucht werden darf. */
  static final Set<Angebotsstatus> STATUS =
      Set.of(Angebotsstatus.BESTELLT, Angebotsstatus.ERLEDIGT);

  private Buchbarkeit() {}

  /**
   * Ob die Position Stunden traegt — an der internen Arbeit jede, sonst nach Aufwand in Stunden.
   *
   * <p>Ohne Blick auf den Status: Das ist die Eigenschaft, die bleibt.
   *
   * @param angebot das Angebot, zu dem die Position gehoert
   * @param position die Position des Angebots
   */
  public static boolean buchbar(final Angebot angebot, final Angebotsposition position) {
    return angebot.intern()
        || (position.abrechnungsmodus() == Abrechnungsmodus.AUFWAND
            && position.einheit() == Einheit.STUNDE);
  }

  /**
   * Ob auf diese Position <b>jetzt</b> gebucht werden darf — buchbar und das Angebot im richtigen
   * Status.
   *
   * @param angebot das Angebot, zu dem die Position gehoert
   * @param position die Position des Angebots
   */
  public static boolean buchungZulaessig(final Angebot angebot, final Angebotsposition position) {
    return buchbar(angebot, position) && statusErlaubtBuchung(angebot);
  }

  private static boolean statusErlaubtBuchung(final Angebot angebot) {
    return angebot.intern()
        ? angebot.status() == Angebotsstatus.LAEUFT
        : STATUS.contains(angebot.status());
  }
}
