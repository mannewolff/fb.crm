package org.mwolff.fbcrm.firma.domain;

import java.util.List;
import java.util.Optional;

/** Port auf den Bestand der Firmen; die Umsetzung liegt in {@code firma.infrastructure}. */
public interface FirmaRepository {

  /**
   * Die Firmen der Uebersicht, sortiert nach Namen ohne Ruecksicht auf Gross- und Kleinschreibung
   * und bei gleichem Namen nach Id — damit zwei Aufrufe dieselbe Reihenfolge liefern (E6).
   *
   * @param suche Teil des Namens; der Leerstring trifft jede Firma. Der Text wird als Text gesucht,
   *     nicht als Muster — {@code %} und {@code _} treffen nur sich selbst (E5).
   * @param auchStillgelegte {@code true}, wenn auch stillgelegte Firmen erscheinen sollen
   */
  List<Firma> uebersicht(String suche, boolean auchStillgelegte);

  /** Die Firma zu einer technischen Id, oder leer. */
  Optional<Firma> findById(long id);

  /** Legt die Firma an oder schreibt sie fort und liefert sie mit gesetzter Id zurueck. */
  Firma save(Firma firma);

  /** Die Zahl aller Firmen — stillgelegte zaehlen mit. */
  long zaehleAlle();
}
