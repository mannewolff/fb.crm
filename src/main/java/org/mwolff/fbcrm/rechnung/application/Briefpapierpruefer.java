package org.mwolff.fbcrm.rechnung.application;

/**
 * Port auf die Pruefung und Reinigung eines hochgeladenen Briefpapiers; die Umsetzung liegt in
 * {@code rechnung.infrastructure} und spricht PDFBox (Plan #363, E4 und E5).
 *
 * <p>Ein Briefpapier ist eine Datei von aussen. Bevor sie in den Objektspeicher geht, entscheidet
 * der Pruefer, ob fb.crm sie annimmt, und nimmt ihr alles, was handeln kann. Abgelegt wird allein,
 * was er zurueckgibt: Was zu sehen ist, bleibt zu sehen; was handelt, ist schon im Objektspeicher
 * nicht mehr da.
 *
 * <p>Die drei inhaltsunabhaengigen Riegel — leer, zu gross, kein PDF an der Signatur — stehen davor
 * im Anwendungsfall; der Pruefer bekommt nur, was sie passiert hat.
 *
 * <p>Die Richtung ist die von CLAUDE-java.md §6.1 verlangte, wie bei {@link Belegdrucker}: Der
 * Adapter kennt die Anwendungsschicht, nicht umgekehrt; kein Anwendungsfall nennt die Umsetzung.
 * Genau eine abstrakte Methode, aber bewusst kein funktionales Interface: Ein Port beschreibt eine
 * Rolle, die ein benannter Adapter uebernimmt, und wird nie als Lambda geschrieben.
 */
/*
 * PMD.ImplicitFunctionalInterface: {@code @FunctionalInterface} behauptete eine Absicht, die nicht
 * besteht — siehe den letzten Absatz des Klassenkommentars.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface Briefpapierpruefer {

  /**
   * Prueft das Briefpapier und liefert es gereinigt.
   *
   * <p>Abgewiesen wird in dieser Reihenfolge: nicht ladbar oder verschluesselt, mehr als zwei
   * Seiten, eine Seite nicht A4 hochkant.
   *
   * @param pdf die hochgeladenen Bytes, schon als PDF an der Signatur erkannt
   * @return die gereinigten Bytes samt Seitenzahl
   * @throws BriefpapierNichtAnnehmbar wenn das PDF nicht ladbar oder verschluesselt ist, mehr als
   *     zwei Seiten hat oder eine Seite nicht A4 hochkant ist
   */
  Gereinigt pruefe(byte[] pdf);

  /**
   * Ein angenommenes Briefpapier, von aktiven Inhalten befreit.
   *
   * <p>Eine Klasse und kein Record: Ein {@code byte[]} als Record-Komponente weisen Error Prone
   * ({@code ArrayRecordComponent}) und PMD ({@code MethodReturnsInternalArray}) zurueck, weil der
   * Accessor eine veraenderliche Referenz herausgaebe. Hier wird beim Hineingeben und beim
   * Herausgeben kopiert; das Ergebnis bleibt, was der Pruefer geliefert hat.
   */
  final class Gereinigt {

    private final byte[] bytes;

    private final int seitenzahl;

    /**
     * @param inhalt die gereinigten Bytes; sie werden kopiert
     * @param seiten die Zahl der Seiten, 1 oder 2
     */
    public Gereinigt(final byte[] inhalt, final int seiten) {
      this.bytes = inhalt.clone();
      this.seitenzahl = seiten;
    }

    /** Die gereinigten Bytes, als Kopie. */
    public byte[] inhalt() {
      return bytes.clone();
    }

    /** Die Zahl der Seiten, 1 oder 2. */
    public int seiten() {
      return seitenzahl;
    }
  }
}
