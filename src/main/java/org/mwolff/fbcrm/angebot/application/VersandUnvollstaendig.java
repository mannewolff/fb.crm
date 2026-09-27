package org.mwolff.fbcrm.angebot.application;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.common.Feldfehler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Dem Entwurf fehlen Angaben, die zum Versenden noetig sind (Kriterium 12, E22).
 *
 * <p>Sie traegt <b>alle</b> fehlenden Angaben auf einmal: Wer drei Felder vergessen hat, soll das
 * in einem Durchgang erfahren und nicht dreimal hintereinander. Die Schluessel der Feldliste sind
 * festgelegt — {@code positionen}, {@code gueltigBis}, {@code firma}, {@code eigeneAngaben} —, weil
 * die Angebotsansicht kein Formular mit Feldern hat, an das sie sich schreiben liessen; die
 * Oberflaeche zeigt sie als Liste in der Rueckmeldung.
 *
 * <p>409 und nicht 422: Die Anfrage selbst ist in Ordnung — sie traegt gar keinen Rumpf. Es ist der
 * Zustand des Entwurfs und seiner Nachbarn, der den Versand nicht zulaesst, und der kann sich
 * hinter dem Ruecken des Anwenders geaendert haben.
 *
 * <p>Abgebildet wird sie nicht hier und nicht im Controller, sondern im {@code
 * GlobalExceptionHandler} — ueber den Vertrag {@link Feldfehler}, damit {@code common} kein
 * Fachmodul kennen muss.
 */
@ResponseStatus(code = HttpStatus.CONFLICT, reason = VersandUnvollstaendig.MELDUNG)
public final class VersandUnvollstaendig extends Feldfehler {

  /** Die Meldung an der Schnittstelle; welche Angabe fehlt, steht in der Feldliste. */
  public static final String MELDUNG = "Zum Versenden dieses Angebots fehlen Angaben.";

  private final Map<String, List<String>> fehlendeAngaben;

  /**
   * @param felder die fehlenden Angaben je Feld, in der Reihenfolge, in der sie gelesen werden
   */
  public VersandUnvollstaendig(final Map<String, List<String>> felder) {
    super();
    this.fehlendeAngaben = Collections.unmodifiableMap(new LinkedHashMap<>(felder));
  }

  @Override
  public Map<String, List<String>> felder() {
    return fehlendeAngaben;
  }
}
