package org.mwolff.fbcrm.rechnung.application;

import java.util.List;
import java.util.Locale;

/**
 * Schreibt eine Folge von {@link Druckelement}en als Tabelle, eine Zeile je Element.
 *
 * <p>Der Satz der Rechnung ist eine reine Rechnung: Dieselben Daten ergeben immer dieselbe Folge.
 * Ein Pruefbild dieser Folge ist darum der vollstaendige Vertrag der Klasse — es nennt jedes
 * Element, jedes Mass und jede Farbe. Einzelne Zusicherungen koennen das nicht leisten: Sie
 * benennen, was jemandem eingefallen ist, und lassen jede Zahl ungeprueft, an die er nicht gedacht
 * hat. Verschiebt sich eine Grundlinie, faellt eine Linie weg oder kehrt sich ein Vorzeichen um,
 * zeigt das Bild die Zeile.
 *
 * <p>Die Spalten stehen in fester Breite, damit der Unterschied zweier Bilder im Diff an der Stelle
 * sichtbar wird, an der er auftritt, und nicht als verschobene Zeile.
 */
final class Satzbild {

  private Satzbild() {}

  /**
   * Das Bild der Folge; jede Zeile endet mit einem Zeilenumbruch.
   *
   * @param elemente die gesetzten Elemente in ihrer Reihenfolge
   */
  static String von(final List<Druckelement> elemente) {
    final StringBuilder bild = new StringBuilder();
    for (final Druckelement element : elemente) {
      bild.append(zeile(element)).append('\n');
    }
    return bild.toString();
  }

  private static String zeile(final Druckelement element) {
    return switch (element) {
      case Druckelement.Text text ->
          String.format(
              Locale.ROOT,
              "S%d Text    x=%4d y=%4d %-6s %-7s %2d %-14s %s",
              text.seite(),
              text.x(),
              text.y(),
              text.ausrichtung(),
              text.schrift(),
              text.groesse(),
              farbe(text.farbe()),
              text.text());
      case Druckelement.Linie linie ->
          String.format(
              Locale.ROOT,
              "S%d Linie   x=%4d y=%4d bis x=%4d y=%4d staerke %d %s",
              linie.seite(),
              linie.vonX(),
              linie.vonY(),
              linie.bisX(),
              linie.bisY(),
              linie.staerke(),
              farbe(linie.farbe()));
      case Druckelement.Flaeche flaeche ->
          String.format(
              Locale.ROOT,
              "S%d Flaeche x=%4d y=%4d breite %3d hoehe %3d %s",
              flaeche.seite(),
              flaeche.x(),
              flaeche.y(),
              flaeche.breite(),
              flaeche.hoehe(),
              farbe(flaeche.farbe()));
    };
  }

  private static String farbe(final Farbe farbe) {
    return String.format(Locale.ROOT, "%d/%d/%d", farbe.rot(), farbe.gruen(), farbe.blau());
  }
}
