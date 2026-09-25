package org.mwolff.fbcrm.common;

/**
 * Die eine Grenze fuer hochgeladene Dateien und die eine Meldung dazu (E10, Kriterium 18).
 *
 * <p>Sie steht hier und nicht im Modul {@code vorgang}, weil drei Stellen in zwei Modulen dieselbe
 * Zahl und denselben Satz brauchen: die Bean Validation der Eintragsanfrage, die Schranke dahinter
 * im Anwendungsfall und der Zweig fuer den Riegel des Containers im {@code GlobalExceptionHandler}
 * — und der liegt in {@code common.web}. Eine Kopie je Ort waere ein Satz, der an einer Stelle
 * altert, und ein Riegel, der eine andere Grenze nennt als die Maske davor.
 *
 * <p>Die Meldung ist ein ganzer Satz, weil sie beide Wege gehen muss: als {@code fieldErrors}-Text
 * am Feld {@code datei} und als {@code detail} einer Problem-Details-Antwort, wenn der Rumpf so
 * gross ist, dass die Bean Validation gar nicht erst zum Zuge kommt.
 */
public final class Uploadgrenze {

  /** 25 MiB in Byte — die Grenze aus Kriterium 18. */
  public static final long MAX_BYTE = 26_214_400L;

  /** Der Text, den jede der drei Stellen zurueckgibt. */
  public static final String MELDUNG =
      "Die Datei darf hoechstens 26214400 Byte (25 MiB) gross sein.";

  private Uploadgrenze() {}
}
