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
 * <p><b>Zwei Reihen, nicht eine</b> (Issue #226, Kriterium 1 von #207): Das Angebot an einen Kunden
 * geht durch die fuenf Status von {@link #ANGELEGT} bis {@link #ABGERECHNET}, die interne Arbeit
 * nur durch {@link #LAEUFT} und {@link #ABGESCHLOSSEN} — angeboten und abgerechnet wird sie nicht.
 * Jede Reihe hat ihre eigenen Enden; {@link #weiter()} und {@link #zurueck()} fuehren nie von der
 * einen in die andere. Den Wechsel der Art macht allein {@link #fuerArt(boolean)}, und {@link
 * #intern()} sagt, in welcher Reihe ein Wert steht.
 *
 * <p>Die Werte gehen als Text in die Datenbank; der CHECK in {@code V20__angebot_intern.sql} nennt
 * dieselben sieben.
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
  ABGERECHNET,

  /** Die interne Arbeit laeuft. */
  LAEUFT,

  /** Die interne Arbeit ist getan. */
  ABGESCHLOSSEN;

  /** Der naechste Status seiner Reihe, oder leer an ihrem Ende. */
  public Optional<Angebotsstatus> weiter() {
    return switch (this) {
      case ANGELEGT -> Optional.of(ABGEGEBEN);
      case ABGEGEBEN -> Optional.of(BESTELLT);
      case BESTELLT -> Optional.of(ERLEDIGT);
      case ERLEDIGT -> Optional.of(ABGERECHNET);
      case ABGERECHNET -> Optional.empty();
      case LAEUFT -> Optional.of(ABGESCHLOSSEN);
      case ABGESCHLOSSEN -> Optional.empty();
    };
  }

  /** Der vorherige Status seiner Reihe, oder leer an ihrem Anfang. */
  public Optional<Angebotsstatus> zurueck() {
    return switch (this) {
      case ANGELEGT -> Optional.empty();
      case ABGEGEBEN -> Optional.of(ANGELEGT);
      case BESTELLT -> Optional.of(ABGEGEBEN);
      case ERLEDIGT -> Optional.of(BESTELLT);
      case ABGERECHNET -> Optional.of(ERLEDIGT);
      case LAEUFT -> Optional.empty();
      case ABGESCHLOSSEN -> Optional.of(LAEUFT);
    };
  }

  /** Ob dieser Status zur internen Arbeit gehoert und nicht zum Angebot an einen Kunden. */
  public boolean intern() {
    return switch (this) {
      case LAEUFT, ABGESCHLOSSEN -> true;
      case ANGELEGT, ABGEGEBEN, BESTELLT, ERLEDIGT, ABGERECHNET -> false;
    };
  }

  /**
   * Der Status, der diesem hier in der Reihe der gewuenschten Art entspricht (E4).
   *
   * <p><b>Total und idempotent:</b> Jeder der sieben Werte hat ein Gegenueber, und wer zweimal auf
   * dieselbe Art umstellt, verschiebt nichts mehr — {@code s.fuerArt(x).intern() == x} gilt immer.
   * Nach innen fallen die drei Stufen vor der Arbeit auf {@link #LAEUFT} zusammen und die zwei
   * danach auf {@link #ABGESCHLOSSEN}; nach aussen geht es auf {@link #BESTELLT} beziehungsweise
   * {@link #ERLEDIGT} zurueck. Der Weg hinein ist damit nicht umkehrbar: Wer ein abgegebenes
   * Angebot nach innen und zurueck stellt, landet in {@code BESTELLT}. Angeboten wird die interne
   * Arbeit nicht, also gibt es innen keine Stufe, die sich {@code ABGEGEBEN} merken koennte.
   *
   * @param intern ob der gesuchte Status zur internen Arbeit gehoeren soll
   */
  public Angebotsstatus fuerArt(final boolean intern) {
    return intern ? alsInterneArbeit() : alsKundenangebot();
  }

  /*
   * Je Richtung ein eigener switch statt eines mit sieben Zweigen und einer Fallunterscheidung in
   * jedem: Beide Reihen lesen sich dann als Abbildung, und keine Methode ueberschreitet die
   * Komplexitaetsgrenze, die PMD zieht.
   */
  private Angebotsstatus alsInterneArbeit() {
    return switch (this) {
      case ANGELEGT, ABGEGEBEN, BESTELLT -> LAEUFT;
      case ERLEDIGT, ABGERECHNET -> ABGESCHLOSSEN;
      case LAEUFT, ABGESCHLOSSEN -> this;
    };
  }

  private Angebotsstatus alsKundenangebot() {
    return switch (this) {
      case LAEUFT -> BESTELLT;
      case ABGESCHLOSSEN -> ERLEDIGT;
      case ANGELEGT, ABGEGEBEN, BESTELLT, ERLEDIGT, ABGERECHNET -> this;
    };
  }
}
