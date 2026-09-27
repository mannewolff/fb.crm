package org.mwolff.fbcrm.vorgang.domain;

import java.util.Collection;
import java.util.Set;

/**
 * Port auf den Stand der Belege eines Vorgangs; die Umsetzungen liegen in den Belegmodulen.
 *
 * <p><b>Warum der Port hier steht und nicht dort.</b> Die Phase eines Vorgangs haengt an seinen
 * Dokumenten (Kriterium 22, R1), aber {@code vorgang} darf {@code angebot} nicht kennen: Das
 * Angebot liest den Vorgang, und eine Rueckkante waere ein Paketzyklus, den {@code
 * ArchitectureTest.modules_thenFreeOfCycles} abweist. Der Vorgang schreibt deshalb aus, <b>was</b>
 * er wissen muss, und das Belegmodul sagt es ihm (Plan E2).
 *
 * <p>Die Frage lautet <b>fuer eine Menge</b> von Vorgaengen und nicht fuer einen: Beide Listen —
 * die Uebersicht und die Liste an der Firma — brauchen die Antwort fuer jede ihrer Zeilen, und eine
 * Abfrage je Zeile waere die bekannte Abfrage-Lawine.
 *
 * <p>Genau eine abstrakte Methode, aber bewusst kein funktionales Interface: Der Port beschreibt
 * einen Adapter auf den Bestand und wird nie als Lambda geschrieben — wie {@code
 * NummernkreisRepository} daher die Unterdrueckung der PMD-Regel statt der Annotation. Weitere
 * Belegarten kommen mit den Paketen, die sie einfuehren.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface Belegstand {

  /**
   * Die Kennungen derjenigen Vorgaenge, an denen mindestens ein festgeschriebenes Angebot haengt.
   *
   * <p><b>Festgeschrieben, nicht versendet.</b> Festgeschrieben ist ein Angebot, sobald es seine
   * Nummer traegt — ein angenommenes, abgelehntes oder abgeloestes ebenso wie ein versendetes. Die
   * Phase eines Vorgangs faellt darum nach einer Reaktion des Kunden nicht wieder zurueck (F6). Ein
   * reiner Entwurf traegt keine Nummer und zaehlt nicht (Kriterium 6).
   *
   * @param vorgangIds die Kennungen der gefragten Vorgaenge; die leere Menge ist erlaubt und
   *     beantwortet sich ohne Abfrage
   * @return eine Teilmenge von {@code vorgangIds} — nie Kennungen, nach denen niemand gefragt hat
   */
  Set<Long> mitFestgeschriebenemAngebot(Collection<Long> vorgangIds);
}
