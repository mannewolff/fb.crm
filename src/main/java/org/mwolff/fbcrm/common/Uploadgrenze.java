package org.mwolff.fbcrm.common;

/**
 * Die eine Grenze fuer hochgeladene Dateien und die eine Meldung dazu (Issue #148, Kriterium 6;
 * Plan #150, E8).
 *
 * <p>Sie steht hier und nicht im Modul {@code angebot}, weil drei Stellen in zwei Modulen dieselbe
 * Zahl und denselben Satz brauchen: der Riegel im Anwendungsfall der Anlagen ({@code
 * angebot.application.AnlageZuGross}), der Zweig fuer den Riegel des Containers im {@code
 * GlobalExceptionHandler} — und der liegt in {@code common.web} — und die Oberflaeche, die vor dem
 * Senden prueft. Eine Kopie je Ort waere ein Satz, der an einer Stelle altert, und ein Riegel, der
 * eine andere Grenze nennt als die Maske davor.
 *
 * <p>Die Meldung ist ein ganzer Satz, weil sie beide Wege gehen muss: als {@code fieldErrors}-Text
 * am Feld {@code datei} und als {@code detail} einer Problem-Details-Antwort, wenn der Rumpf so
 * gross ist, dass der Anwendungsfall gar nicht erst zum Zuge kommt.
 *
 * <p>Der Satz nennt <b>„25 MB" und nicht „25 MiB"</b> (E12). Die Oberflaeche rechnet Groessen
 * binaer und beschriftet sie mit KB und MB; eine Datei der Grenzgroesse steht dort als „25 MB".
 * Zwei Einheiten fuer dieselbe Zahl waeren am Bildschirm ein Widerspruch.
 */
public final class Uploadgrenze {

  /** 25 MB in Byte, binaer gerechnet — die Grenze aus Kriterium 6. */
  public static final long MAX_BYTE = 26_214_400L;

  /** Der Text, den jede der drei Stellen zurueckgibt. */
  public static final String MELDUNG =
      "Die Datei darf hoechstens 26214400 Byte (25 MB) gross sein.";

  private Uploadgrenze() {}
}
