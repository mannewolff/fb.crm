package org.mwolff.fbcrm.vorgang.domain;

import java.util.Collection;
import java.util.Set;

/**
 * Port auf den Stand <b>einer</b> Belegart an einem Vorgang; die Umsetzungen liegen in den
 * Belegmodulen.
 *
 * <p><b>Warum der Port hier steht und nicht dort.</b> Die Phase eines Vorgangs haengt an seinen
 * Dokumenten (Kriterien 10, 22, R1), aber {@code vorgang} darf {@code angebot} und {@code auftrag}
 * nicht kennen: Die Belege lesen den Vorgang, und eine Rueckkante waere ein Paketzyklus, den {@code
 * ArchitectureTest.modules_thenFreeOfCycles} abweist. Der Vorgang schreibt deshalb aus, <b>was</b>
 * er wissen muss, und die Belegmodule sagen es ihm (Plan E2).
 *
 * <p><b>Mehrfach besetzbar.</b> Jede Belegart bringt eine eigene Umsetzung mit, und die Anwendung
 * fragt sie alle. Darum nennt der Port keine Belegart beim Namen: Jede Umsetzung spricht nur von
 * <b>ihrem</b> Beleg, und welcher das ist, sagt sie ueber {@link #phase()}. Ein zweiter boolescher
 * Parameter je Belegart — der Weg vor diesem Umbau — haette am Ende die ganze Kette in einer
 * Signatur getragen.
 *
 * <p>Auch <b>was</b> als Beleg zaehlt, gehoert in die Umsetzung und nicht hierher: Beim Angebot ist
 * es die Festschreibung (F6), beim Auftrag genuegt sein Dasein. Wer die Regel hier auszuschreiben
 * versuchte, muesste sie mit jeder Belegart umschreiben.
 *
 * <p>Die Frage lautet <b>fuer eine Menge</b> von Vorgaengen und nicht fuer einen: Beide Listen —
 * die Uebersicht und die Liste an der Firma — brauchen die Antwort fuer jede ihrer Zeilen, und eine
 * Abfrage je Zeile waere die bekannte Abfrage-Lawine.
 */
public interface Belegstand {

  /**
   * Die Phase, die ein Beleg dieser Art begruendet.
   *
   * <p>Reine Auskunft ueber die Umsetzung, keine Abfrage: Der Aufrufer darf sie beliebig oft
   * stellen, ohne den Bestand zu beruehren.
   */
  Phase phase();

  /**
   * Die Kennungen derjenigen Vorgaenge, an denen mindestens ein Beleg dieser Art haengt.
   *
   * @param vorgangIds die Kennungen der gefragten Vorgaenge; die leere Menge ist erlaubt und
   *     beantwortet sich ohne Abfrage
   * @return eine Teilmenge von {@code vorgangIds} — nie Kennungen, nach denen niemand gefragt hat
   */
  Set<Long> mitBeleg(Collection<Long> vorgangIds);
}
