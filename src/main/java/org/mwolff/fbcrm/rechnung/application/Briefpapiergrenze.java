package org.mwolff.fbcrm.rechnung.application;

/**
 * Die Grenze fuer ein hochgeladenes Briefpapier und die Meldung dazu (Issue #361, Kriterium 2; Plan
 * #363, E17).
 *
 * <p><b>Eine eigene Grenze und nicht {@code common.Uploadgrenze}.</b> Jene traegt ausdruecklich
 * <i>die eine</i> Grenze von 25 MB, die Riegel, Containergrenze und Maske der Anlagen und
 * Nachtraege teilen; ein zweiter Wert darin truebe genau diese Zusage. Kriterium 2 nennt 5 MB nur
 * fuer das Briefpapier, darum stehen Zahl und Satz hier im Modul. Oberhalb der Containergrenze
 * antwortet weiter der Containerriegel mit dem 25-MB-Satz; die Maske prueft diese Grenze darum
 * schon vor dem Senden.
 *
 * <p>Binaer gerechnet und mit „MB" beschriftet, aus demselben Grund wie bei {@code Uploadgrenze}:
 * Die Oberflaeche rechnet Groessen binaer und nennt sie MB.
 */
public final class Briefpapiergrenze {

  /** 5 MB in Byte, binaer gerechnet. */
  public static final long MAX_BYTE = 5_242_880L;

  /** Der Satz, den die Abweisung am Feld {@code datei} nennt. */
  public static final String MELDUNG =
      "Das Briefpapier darf höchstens 5242880 Byte (5 MB) groß sein.";

  private Briefpapiergrenze() {}
}
