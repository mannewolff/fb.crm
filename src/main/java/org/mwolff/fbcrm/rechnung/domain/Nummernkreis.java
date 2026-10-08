package org.mwolff.fbcrm.rechnung.domain;

/**
 * Port auf den Nummernkreis der Rechnungen; die Umsetzung liegt in {@code rechnung.infrastructure}.
 *
 * <p>Ein Kreis je <b>Zaehlerjahr</b> ({@link Nummernmuster#zaehlerjahr(int)}): Traegt das Muster
 * ein Jahr, beginnt jedes Jahr wieder bei 1 (#160, Kriterium 16); traegt es keines, laeuft ein
 * einziger Kreis unter {@link Nummernmuster#OHNE_JAHR} durch. Welches Jahr gemeint ist, entscheidet
 * der Aufrufer — eine Rechnung mit Dezemberdatum zaehlt im Januar im alten Jahr weiter (Kriterium
 * 17), und das weiss der Zaehler nicht.
 *
 * <p>Der Kreis ist eine Zaehlertabelle und keine Sequenz, aus demselben Grund wie beim frueheren
 * Nummernkreis des Angebots (E6 in {@code V6__angebot.sql}): Eine Sequenz ist nicht transaktional,
 * und eine zurueckgerollte Rechnung risse eine Luecke.
 */
public interface Nummernkreis {

  /**
   * Der Zaehlerstand eines Zaehlerjahrs, ohne ihn zu verbrauchen.
   *
   * <p>Fehlt die Zeile, ist die Antwort 1: Ein Jahr, aus dem noch keine Nummer gezogen wurde, steht
   * vor seiner ersten. Die Maske zeigt so auch auf einer frischen Instanz eine Zahl und keine
   * Leerstelle.
   *
   * @param zaehlerjahr das Zaehlerjahr des Musters
   */
  int lies(int zaehlerjahr);

  /**
   * Setzt den Zaehlerstand eines Zaehlerjahrs auf einen selbst gewaehlten Wert (#160, Kriterium
   * 15).
   *
   * <p>Legt die Zeile an, wenn sie fehlt: Ein Muster ohne Jahres-Platzhalter zaehlt unter {@link
   * Nummernmuster#OHNE_JAHR}, und dieses Zaehlerjahr hat vor dem ersten Zug keine Zeile.
   *
   * @param zaehlerjahr das Zaehlerjahr des Musters
   * @param naechsteNummer die naechste laufende Nummer, ab 1
   */
  void setze(int zaehlerjahr, int naechsteNummer);

  /**
   * Zieht die naechste Nummer eines Zaehlerjahrs und schreibt den Zaehler fort.
   *
   * <p><b>Zusage:</b> Die Nummern eines Zaehlerjahrs beginnen bei 1 und haben keine Luecke. Dafuer
   * laeuft der Zug in der Transaktion des Aufrufers und sperrt die Jahreszeile bis zu deren Ende;
   * ein zurueckgerollter Zug gibt die Nummer wieder frei.
   *
   * @param zaehlerjahr das Zaehlerjahr des Musters
   */
  int ziehe(int zaehlerjahr);
}
