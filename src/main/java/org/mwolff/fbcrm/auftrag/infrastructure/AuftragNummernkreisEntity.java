package org.mwolff.fbcrm.auftrag.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Eine Jahreszeile der Tabelle {@code auftrag_nummernkreis} aus {@code V8__auftrag.sql}.
 *
 * <p>Je Jahr genau eine Zeile, und das Jahr ist der Primaerschluessel — deshalb traegt die Kennung
 * keinen {@code GeneratedValue}. Angelegt wird die Zeile beim ersten Zug des Jahres mit {@code
 * INSERT … ON CONFLICT DO NOTHING}: Welches Jahr das erste ist, weiss die Migration nicht.
 *
 * <p>Der Zaehler ist die einzige veraenderliche Entity des Moduls, und das mit Absicht — dieselbe
 * Begruendung wie bei {@code AngebotNummernkreisEntity}: Das Fortschreiben <b>muss</b> auf der
 * geladenen, gesperrten Zeile geschehen. Ein Massenupdate ginge an der Sitzung vorbei und liesse
 * einen zweiten Zug in derselben Transaktion dieselbe Nummer erneut ziehen.
 */
@Entity
@Table(name = "auftrag_nummernkreis")
class AuftragNummernkreisEntity {

  @Id
  @Column(name = "jahr", nullable = false)
  private int jahr;

  @Column(name = "naechste", nullable = false)
  private long naechste;

  protected AuftragNummernkreisEntity() {
    // Von Hibernate benutzt.
  }

  AuftragNummernkreisEntity(final int jahr, final long naechste) {
    this.jahr = jahr;
    this.naechste = naechste;
  }

  long getNaechste() {
    return naechste;
  }

  void setNaechste(final long naechste) {
    this.naechste = naechste;
  }
}
