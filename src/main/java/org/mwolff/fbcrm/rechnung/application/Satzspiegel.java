package org.mwolff.fbcrm.rechnung.application;

import java.util.List;

/**
 * Die Masse, die Positionstabelle und Summenblock brauchen, in ganzen Punkten auf A4 mit dem
 * Ursprung unten links wie bei {@link Druckelement} (Plan #363, E7).
 *
 * <p>Jedes Layout bringt seinen eigenen Satzspiegel mit: die Kupferwolke ihre an der Vorlage
 * abgenommenen Masse, der Briefbogen die eingestellten Raender. Die {@link Positionstabelle} setzt
 * in beiden nach derselben Regel.
 *
 * @param links linke Kante der Tabelle, zugleich die Kante vor der Spalte „Anzahl"
 * @param kantePosition Kante zwischen Anzahl und Position
 * @param kanteEinzelpreis Kante zwischen Position und Einzelpreis
 * @param kanteGesamtpreis Kante zwischen Einzelpreis und Gesamtpreis
 * @param rechts rechte Kante der Tabelle
 * @param tabelleUntergrenze tiefster Punkt, den eine Tabellenzeile samt dem, was sie fuer den
 *     Summenblock zuruecklegt, erreichen darf
 * @param inhaltUntergrenze tiefster Punkt, den der Block unter der Tabelle erreichen darf
 * @param folgeseiteOben oberste Grundlinie auf einem neuen Blatt
 */
record Satzspiegel(
    int links,
    int kantePosition,
    int kanteEinzelpreis,
    int kanteGesamtpreis,
    int rechts,
    int tabelleUntergrenze,
    int inhaltUntergrenze,
    int folgeseiteOben) {

  /**
   * Die Spaltenkanten von links nach rechts; an jeder steht eine Senkrechte des Rahmens.
   *
   * @return die fuenf Kanten, die letzte ist die rechte Kante der Tabelle
   */
  List<Integer> spaltenkanten() {
    return List.of(links, kantePosition, kanteEinzelpreis, kanteGesamtpreis, rechts);
  }

  /**
   * Die Breite der Spalte „Position" von Kante zu Kante; an ihr bricht der Positionstext um.
   *
   * @return der Abstand der Kanten vor und hinter der Spalte
   */
  int positionsbreite() {
    return kanteEinzelpreis - kantePosition;
  }
}
