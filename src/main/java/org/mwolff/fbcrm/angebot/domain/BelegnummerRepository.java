package org.mwolff.fbcrm.angebot.domain;

/**
 * Port auf den Nummernkreis der Belege; die Umsetzung liegt in {@code angebot.infrastructure}.
 *
 * <p>Genau eine abstrakte Methode, aber bewusst kein funktionales Interface: Der Port beschreibt
 * einen Adapter auf den Bestand und wird nie als Lambda geschrieben — wie {@code Identifiable}
 * daher die Unterdrueckung der PMD-Regel statt der Annotation.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface BelegnummerRepository {

  /**
   * Zieht die naechste freie Belegnummer des Jahres und schreibt den Zaehler fort.
   *
   * <p><b>Zusage:</b> Je Jahr beginnen die Nummern bei 1 und haben keine Luecke (Kriterium 11).
   * Dafuer laeuft der Zug in der Transaktion des Aufrufers und sperrt die Jahreszeile bis zu deren
   * Ende ({@code SELECT … FOR UPDATE}); ein zurueckgerollter Zug gibt die Nummer wieder frei — ein
   * verworfener Entwurf reisst keine Luecke. Eine Postgres-Sequenz kann das nicht, sie ist nicht
   * transaktional (E6).
   *
   * @param jahr Kalenderjahr in {@code common.Geschaeftszone}, nicht in UTC (E12)
   * @return die Nummer in der Schreibweise aus {@link Angebotsnummer}
   */
  String zieheNummer(int jahr);
}
