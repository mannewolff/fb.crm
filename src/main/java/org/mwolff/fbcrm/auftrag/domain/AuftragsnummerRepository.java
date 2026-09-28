package org.mwolff.fbcrm.auftrag.domain;

/**
 * Port auf den Nummernkreis der Auftraege; die Umsetzung liegt in {@code auftrag.infrastructure}.
 *
 * <p>Ein eigener Port neben dem des Angebots und keine geteilte Belegnummer (E6): Eine Tabelle, die
 * zwei Module besitzen, hat keinen Eigentuemer.
 *
 * <p>Genau eine abstrakte Methode, aber bewusst kein funktionales Interface: Der Port beschreibt
 * einen Adapter auf den Bestand und wird nie als Lambda geschrieben — wie {@code Identifiable}
 * daher die Unterdrueckung der PMD-Regel statt der Annotation.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface AuftragsnummerRepository {

  /**
   * Zieht die naechste freie Auftragsnummer des Jahres und schreibt den Zaehler fort.
   *
   * <p><b>Zusage:</b> Je Jahr beginnen die Nummern bei 1 und steigen. Der Zug laeuft in der
   * Transaktion des Aufrufers und sperrt die Jahreszeile bis zu deren Ende ({@code SELECT … FOR
   * UPDATE}); ein zurueckgerollter Anlegeversuch gibt die Nummer damit wieder frei. Eine
   * Postgres-Sequenz kann das nicht, sie ist nicht transaktional (E6).
   *
   * <p><b>Luecken sind erlaubt</b>, anders als beim Angebot: Die Nummer faellt beim Anlegen, und
   * ein geloeschter Auftrag gibt sie nicht zurueck (Kriterium 3) — ein Auftrag ist kein
   * steuerlicher Beleg.
   *
   * @param jahr Kalenderjahr des Anlegens in {@code common.Geschaeftszone}, nicht in UTC, und nicht
   *     das Jahr des frei setzbaren Auftragsdatums
   * @return die Nummer in der Schreibweise aus {@link Auftragsnummer}
   */
  String zieheNummer(int jahr);
}
