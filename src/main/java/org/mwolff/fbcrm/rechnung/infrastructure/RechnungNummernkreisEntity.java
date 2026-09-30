package org.mwolff.fbcrm.rechnung.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Eine Zeile der Tabelle {@code rechnung_nummernkreis} aus {@code V17__rechnung_nummernkreis.sql}.
 *
 * <p>Je Zaehlerjahr genau eine Zeile, und das Zaehlerjahr ist der Primaerschluessel — deshalb
 * traegt die Kennung keinen {@code GeneratedValue}. Angelegt wird die Zeile beim ersten Zug des
 * Jahres, und zwar mit {@code INSERT … ON CONFLICT DO NOTHING}: Welches Jahr das erste ist, weiss
 * die Migration nicht.
 *
 * <p>Der Zaehler ist die einzige veraenderliche Entity des Moduls, und das mit Absicht: Das
 * Fortschreiben <b>muss</b> auf der geladenen, gesperrten Zeile geschehen. Ein Massenupdate ginge
 * an der Sitzung vorbei und liesse einen zweiten Zug in derselben Transaktion dieselbe Nummer
 * erneut ziehen.
 */
@Entity
@Table(name = "rechnung_nummernkreis")
class RechnungNummernkreisEntity {

  @Id
  @Column(name = "jahr", nullable = false)
  private int jahr;

  @Column(name = "naechste_nummer", nullable = false)
  private int naechsteNummer;

  protected RechnungNummernkreisEntity() {
    // Von Hibernate benutzt.
  }

  RechnungNummernkreisEntity(final int jahr, final int naechsteNummer) {
    this.jahr = jahr;
    this.naechsteNummer = naechsteNummer;
  }

  int getNaechsteNummer() {
    return naechsteNummer;
  }

  void setNaechsteNummer(final int naechsteNummer) {
    this.naechsteNummer = naechsteNummer;
  }
}
