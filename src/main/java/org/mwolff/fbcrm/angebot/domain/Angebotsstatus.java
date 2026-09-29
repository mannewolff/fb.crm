package org.mwolff.fbcrm.angebot.domain;

import java.util.Optional;

/**
 * Wie weit ein Angebot gediehen ist (Issue #127, Kriterium 3).
 *
 * <p>Ein Angebot geht eine Stufe weiter oder eine zurueck, und beides ist frei (Kriterium 4). Die
 * Nachbarn stehen ausdruecklich als {@code switch} und nicht ueber die Ordnungszahl: Der Compiler
 * verlangt dann fuer einen neuen Status eine Entscheidung, statt ihn stillschweigend einzureihen.
 * An den Enden gibt es keinen Nachbarn — {@link #weiter()} und {@link #zurueck()} sind dann leer,
 * und wer den Wechsel verlangt, bekommt die Ausnahme aus {@link Angebot}.
 *
 * <p>Die Werte gehen als Text in die Datenbank; der CHECK in {@code V11__angebot_ohne_beleg.sql}
 * nennt dieselben fuenf.
 */
public enum Angebotsstatus {

  /** Erfasst, dem Kunden noch nicht genannt. */
  ANGELEGT,

  /** Dem Kunden genannt, seine Zusage steht aus. */
  ABGEGEBEN,

  /** Der Kunde hat zugesagt; die Arbeit laeuft. */
  BESTELLT,

  /** Die Arbeit ist getan; die Rechnung steht aus. */
  ERLEDIGT,

  /** Die Rechnung ist gestellt. */
  ABGERECHNET;

  /** Der naechste Status, oder leer bei {@link #ABGERECHNET}. */
  public Optional<Angebotsstatus> weiter() {
    return switch (this) {
      case ANGELEGT -> Optional.of(ABGEGEBEN);
      case ABGEGEBEN -> Optional.of(BESTELLT);
      case BESTELLT -> Optional.of(ERLEDIGT);
      case ERLEDIGT -> Optional.of(ABGERECHNET);
      case ABGERECHNET -> Optional.empty();
    };
  }

  /** Der vorherige Status, oder leer bei {@link #ANGELEGT}. */
  public Optional<Angebotsstatus> zurueck() {
    return switch (this) {
      case ANGELEGT -> Optional.empty();
      case ABGEGEBEN -> Optional.of(ANGELEGT);
      case BESTELLT -> Optional.of(ABGEGEBEN);
      case ERLEDIGT -> Optional.of(BESTELLT);
      case ABGERECHNET -> Optional.of(ERLEDIGT);
    };
  }
}
