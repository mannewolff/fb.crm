package org.mwolff.fbcrm.angebot.domain;

/**
 * Der gespeicherte Zustand eines Angebots (Kriterium 17).
 *
 * <p>Jeder Zustand nennt den {@link Angebotsstand}, als der er angezeigt wird. Die Abbildung steht
 * hier und nicht als Verzweigung in {@link Angebot#stand}: Sie ist eine Eigenschaft des Zustands,
 * und ein sechster Zustand muesste seinen Anzeigestand mitbringen, statt eine Verzweigung unbemerkt
 * in den falschen Zweig laufen zu lassen. {@link Angebotsstand#ABGELAUFEN} kommt dabei nicht vor —
 * er entsteht erst aus dem Vergleich mit dem heutigen Tag (E4).
 */
public enum Angebotszustand {

  /** In Arbeit: aenderbar, ohne Nummer, ohne Dokument (Kriterium 6). */
  ENTWURF(Angebotsstand.ENTWURF),

  /** Festgeschrieben und beim Kunden; wartet auf eine Reaktion (Kriterium 10). */
  VERSENDET(Angebotsstand.VERSENDET),

  /** Der Kunde hat zugesagt; endgueltig (Kriterium 17). */
  ANGENOMMEN(Angebotsstand.ANGENOMMEN),

  /** Der Kunde hat abgesagt; endgueltig (Kriterium 17). */
  ABGELEHNT(Angebotsstand.ABGELEHNT),

  /** Durch ein spaeteres Angebot desselben Vorgangs ersetzt — annehmbar bleibt es (F13). */
  ABGELOEST(Angebotsstand.ABGELOEST);

  private final Angebotsstand anzeigestand;

  Angebotszustand(final Angebotsstand anzeigestand) {
    this.anzeigestand = anzeigestand;
  }

  /** Der Stand, als der dieser Zustand angezeigt wird. */
  public Angebotsstand stand() {
    return anzeigestand;
  }
}
