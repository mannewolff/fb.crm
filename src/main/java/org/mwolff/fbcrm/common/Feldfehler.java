package org.mwolff.fbcrm.common;

import java.util.List;
import java.util.Map;

/**
 * Ein fachlicher Fehler, der die betroffenen Felder namentlich nennt.
 *
 * <p>Die Anwendung meldet Feldverletzungen an einer einzigen Form: RFC-9457 Problem Details mit der
 * Erweiterung {@code fieldErrors}. Bisher entstand die allein aus der Bean Validation (400) — ein
 * fachlicher Fehler trug nur {@code detail}. Manche fachliche Pruefung nennt aber ebenfalls Felder,
 * etwa die Versandpruefung des Angebots, die alle fehlenden Angaben auf einmal aufzaehlt; ohne
 * diesen Vertrag muesste die Oberflaeche zwei Formen fuer dieselbe Aussage lesen.
 *
 * <p><b>Warum der Vertrag hier steht und nicht im Fachmodul.</b> Abgebildet wird er in {@code
 * common.web.GlobalExceptionHandler}, der einzigen Stelle fuer Fehlerabbildung (CLAUDE-java.md
 * §6.3). Kennte die Abbildung die Ausnahme des Fachmoduls, zeigte {@code common} auf das Fachmodul
 * und das Fachmodul auf {@code common} — ein Paketzyklus ({@code ArchitectureTest}, {@code
 * modules_thenFreeOfCycles}). So kennt {@code common} nur diesen Vertrag, und die Richtung bleibt
 * eine.
 *
 * <p>Framework-frei und ohne Statuscode: Welchen Code die Lage traegt, sagt die Unterklasse mit
 * {@code @ResponseStatus} — dieselbe Schreibweise wie jede andere fachliche Ausnahme im Projekt.
 */
public abstract class Feldfehler extends RuntimeException {

  /**
   * Die betroffenen Felder und je Feld die Meldungen dazu.
   *
   * <p>Die Schluessel sind die Feldnamen, die die Oberflaeche kennt; die Reihenfolge ist die, in
   * der die Meldungen gelesen werden sollen. Eine Unterklasse gibt eine unveraenderliche Karte
   * zurueck — die Abbildung schreibt nichts hinein.
   */
  public abstract Map<String, List<String>> felder();
}
