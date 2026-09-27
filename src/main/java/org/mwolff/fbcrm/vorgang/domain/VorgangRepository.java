package org.mwolff.fbcrm.vorgang.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Port auf den Bestand der Vorgaenge; die Umsetzung liegt in {@code vorgang.infrastructure}. */
public interface VorgangRepository {

  /**
   * Die Vorgaenge der Uebersicht, sortiert nach dem juengsten Eintragszeitpunkt absteigend; ein
   * Vorgang ohne Eintrag zaehlt mit seinem Anlagezeitpunkt, und bei gleichem Zeitpunkt steht der
   * zuletzt angelegte oben — damit zwei Aufrufe dieselbe Reihenfolge liefern (E16).
   *
   * @param suche Teil des Titels oder des Firmennamens; der Leerstring trifft jeden Vorgang. Der
   *     Text wird ohne Ruecksicht auf Gross- und Kleinschreibung als Text gesucht, nicht als Muster
   *     — {@code %} und {@code _} treffen nur sich selbst (E17).
   * @param nummer die gesuchte Vorgangsnummer, oder {@code null}, wenn der Suchtext keine Zahl ist.
   *     Ist sie gesetzt, trifft ein Vorgang auch ueber seine Nummer allein (E17); das fuehrende
   *     {@code #} schneidet die Anwendungsschicht ab, nicht der Bestand.
   * @param auchAbgeschlossene {@code true}, wenn auch abgeschlossene Vorgaenge erscheinen sollen
   */
  List<Vorgang> uebersicht(String suche, @Nullable Long nummer, boolean auchAbgeschlossene);

  /**
   * Alle Vorgaenge einer Firma — offene und abgeschlossene —, in derselben Reihenfolge wie die
   * Uebersicht (E16). Die Trennung nach offen und abgeschlossen macht die Anwendungsschicht.
   *
   * @param firmaId Kennung der Firma
   */
  List<Vorgang> findByFirma(long firmaId);

  /**
   * Die Vorgaenge zu einer Menge technischer Ids — ohne zugesagte Reihenfolge und ohne Platzhalter
   * fuer eine unbekannte Kennung.
   *
   * <p>Fuer Auswertungen, die zu vielen Zeilen den tragenden Vorgang brauchen (Kriterium 23): Ein
   * {@link #findById} je Zeile waere die bekannte Abfrage-Lawine. Der Aufrufer ordnet die Antwort
   * selbst seinen Zeilen zu und entscheidet, was eine fehlende Kennung bedeutet.
   *
   * @param ids die Kennungen der gefragten Vorgaenge; die leere Menge ist erlaubt und beantwortet
   *     sich ohne Abfrage
   */
  List<Vorgang> findByIds(Collection<Long> ids);

  /** Der Vorgang zu einer technischen Id, oder leer. */
  Optional<Vorgang> findById(long id);

  /** Legt den Vorgang an oder schreibt ihn fort und liefert ihn mit gesetzter Id zurueck. */
  Vorgang save(Vorgang vorgang);

  /** Die Zahl aller Vorgaenge — abgeschlossene zaehlen mit. */
  long zaehleAlle();
}
