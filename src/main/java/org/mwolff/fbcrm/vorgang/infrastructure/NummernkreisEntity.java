package org.mwolff.fbcrm.vorgang.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Die eine Zeile der Tabelle {@code vorgang_nummernkreis} aus {@code V3__vorgang_und_historie.sql}.
 *
 * <p>Sie wird nie angelegt und nie geloescht: Die Migration schreibt sie mit {@code naechste = 1},
 * ein Check der Tabelle macht eine zweite unmoeglich. Deshalb traegt die Id keinen {@code
 * GeneratedValue} — sie ist die Konstante 1.
 *
 * <p>Der Zaehler ist die einzige veraenderliche Entity des Moduls, und das mit Absicht: Das
 * Fortschreiben <b>muss</b> auf der geladenen, gesperrten Zeile geschehen. Ein Massenupdate ginge
 * an der Sitzung vorbei und liesse eine bereits geladene Zeile veraltet zuruecksehen — ein zweiter
 * Zug in derselben Transaktion zoege dann dieselbe Nummer erneut.
 */
@Entity
@Table(name = "vorgang_nummernkreis")
class NummernkreisEntity {

  @Id
  @Column(name = "id", nullable = false)
  private short id;

  @Column(name = "naechste", nullable = false)
  private long naechste;

  protected NummernkreisEntity() {
    // Von Hibernate benutzt.
  }

  NummernkreisEntity(final short id, final long naechste) {
    this.id = id;
    this.naechste = naechste;
  }

  long getNaechste() {
    return naechste;
  }

  void setNaechste(final long naechste) {
    this.naechste = naechste;
  }
}
