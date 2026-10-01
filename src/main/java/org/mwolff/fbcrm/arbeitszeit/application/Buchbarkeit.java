package org.mwolff.fbcrm.arbeitszeit.application;

import java.util.Set;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;

/**
 * Worauf Arbeitszeit gebucht werden darf — zwei Begriffe, zwei Regeln (Plan #194, A6).
 *
 * <p><b>Buchbar</b> ist eine Eigenschaft der Position allein: Sie rechnet nach Aufwand in der
 * Einheit Stunde ab (Issue #193, Antworten 3 und 5). Davon haengen die Spalte „Angefallen", das
 * Feld {@code buchbar} der Antwort und der Vorschlag im Rechnungsentwurf ab — <b>unabhaengig vom
 * Status des Angebots</b>. Das ist kein Versehen: {@code RechnungStellenUseCase} setzt das Angebot
 * beim Stellen selbst auf „abgerechnet", sobald nichts mehr offen ist. Hinge die Anzeige am Status,
 * verschwaenden danach die angefallenen Stunden und die Ueberschreitung, die die Kriterien 8 und 11
 * sichtbar halten.
 *
 * <p><b>Buchung zulaessig</b> heisst zusaetzlich: Das Angebot ist bestellt oder erledigt (Antwort
 * 2). Davon haengen nur die Auswahlliste der Positionen und das Anlegen neuer Eintraege ab — ein
 * alter Eintrag an einem inzwischen abgerechneten Angebot bleibt aenderbar und loeschbar (A7).
 *
 * <p>Beide Regeln stehen hier und nur hier, damit zwei Abschriften nicht auseinanderlaufen —
 * dieselbe Ueberlegung wie bei {@code rechnung.application.Abrechenbarkeit}. Die Status sind
 * ausdruecklich aufgezaehlt und nicht ueber die Ordnungszahl von {@link Angebotsstatus} bestimmt:
 * Der Compiler verlangt bei einem neuen Status eine Entscheidung, statt ihn stillschweigend
 * einzureihen.
 */
public final class Buchbarkeit {

  /** Die Status, in denen neue Arbeitszeit gebucht werden darf. */
  static final Set<Angebotsstatus> STATUS =
      Set.of(Angebotsstatus.BESTELLT, Angebotsstatus.ERLEDIGT);

  private Buchbarkeit() {}

  /**
   * Ob die Position Stunden traegt — nach Aufwand und in der Einheit Stunde.
   *
   * <p>Ohne Blick auf das Angebot: Das ist die Eigenschaft, die bleibt.
   *
   * @param position die Position des Angebots
   */
  public static boolean buchbar(final Angebotsposition position) {
    return position.abrechnungsmodus() == Abrechnungsmodus.AUFWAND
        && position.einheit() == Einheit.STUNDE;
  }

  /**
   * Ob auf diese Position <b>jetzt</b> gebucht werden darf — buchbar und das Angebot zugesagt.
   *
   * @param angebot das Angebot, zu dem die Position gehoert
   * @param position die Position des Angebots
   */
  public static boolean buchungZulaessig(final Angebot angebot, final Angebotsposition position) {
    return buchbar(position) && STATUS.contains(angebot.status());
  }
}
