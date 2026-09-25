package org.mwolff.fbcrm.vorgang.domain;

/**
 * Port auf den Nummernkreis der Vorgaenge; die Umsetzung liegt in {@code vorgang.infrastructure}.
 *
 * <p>Genau eine abstrakte Methode, aber bewusst kein funktionales Interface: Der Port beschreibt
 * einen Adapter auf den Bestand und wird nie als Lambda geschrieben — wie {@code Identifiable}
 * daher die Unterdrueckung der PMD-Regel statt der Annotation.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface NummernkreisRepository {

  /**
   * Zieht die naechste freie Vorgangsnummer und schreibt den Zaehler fort.
   *
   * <p><b>Zusage:</b> Die Nummern beginnen bei 1 und haben keine Luecke. Dafuer laeuft der Zug in
   * der Transaktion des Aufrufers und sperrt die eine Zeile des Zaehlers bis zu deren Ende ({@code
   * SELECT … FOR UPDATE}); ein zurueckgerollter Zug gibt die Nummer wieder frei. Eine
   * Postgres-Sequenz kann das nicht — sie ist nicht transaktional (E3).
   *
   * <p>Die Serialisierung kostet nichts: Die Anwendung kennt ein Konto.
   */
  long naechsteNummer();
}
