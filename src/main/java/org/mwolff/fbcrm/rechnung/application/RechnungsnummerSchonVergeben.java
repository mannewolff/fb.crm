package org.mwolff.fbcrm.rechnung.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die gezogene Rechnungsnummer traegt schon eine andere Rechnung (#160, Kriterium 18).
 *
 * <p>Zwei Wege fuehren hierher, und beide sagen dasselbe. Erstens die Vorabpruefung des Stellens:
 * Wer den Zaehler von Hand zurueckgesetzt hat (Kriterium 15), trifft mit dem naechsten Zug eine
 * Nummer, die bereits auf einem Beleg steht. Zweitens das Schreiben selbst — zwischen Frage und
 * Antwort kann eine zweite Sitzung dieselbe Nummer geschrieben haben, und dann haelt {@code UNIQUE}
 * in {@code V18__rechnung.sql} dagegen.
 *
 * <p><b>Die Meldung nennt die Nummer</b>, weil der Anwender sonst nicht wuesste, welche: Er hat sie
 * nicht eingegeben, der Zaehler hat sie gezogen. Sie verraet nichts, was er nicht ohnehin in seiner
 * Rechnungsliste sieht.
 *
 * <p>409 und nicht 422: Die Anfrage ist wohlgeformt, nur der Stand des Nummernkreises passt nicht
 * zum Bestand — und er kann sich hinter dem Ruecken des Anwenders geaendert haben.
 */
@ResponseStatus(code = HttpStatus.CONFLICT)
public final class RechnungsnummerSchonVergeben extends RuntimeException {

  /** Der Satz vor der Nummer; zusammen sind sie das {@code detail} der Antwort. */
  public static final String MELDUNG = "Diese Rechnungsnummer ist schon vergeben: ";

  /**
   * Dieselbe Lage, von der Vorabpruefung des Stellens entdeckt.
   *
   * @param nummer die gezogene Rechnungsnummer
   */
  RechnungsnummerSchonVergeben(final String nummer) {
    super(MELDUNG + nummer);
  }

  /**
   * Dieselbe Lage, von der Datenbank gemeldet.
   *
   * @param nummer die gezogene Rechnungsnummer
   * @param ursache die Verletzung der Eindeutigkeit, die sie aufgedeckt hat
   */
  RechnungsnummerSchonVergeben(final String nummer, final Throwable ursache) {
    super(MELDUNG + nummer, ursache);
  }
}
