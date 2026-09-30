package org.mwolff.fbcrm.rechnung.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Die eine Zeile der Tabelle {@code rechnung_einstellungen} aus {@code
 * V14__rechnung_einstellungen.sql}.
 *
 * <p>Die Kennung wird nicht erzeugt, sondern gesetzt: Es gibt genau die Zeile {@link #ZEILE}, und
 * die Datenbank laesst ueber ihren CHECK keine zweite zu. Dass die Kennung von Anfang an steht,
 * macht aus jedem {@code save} ein UPDATE statt eines INSERT — genau das ist hier gewollt.
 */
@Entity
@Table(name = "rechnung_einstellungen")
class RechnungseinstellungenEntity {

  /** Die Kennung der einen Zeile. */
  static final Short ZEILE = (short) 1;

  @Id
  @Column(name = "id", nullable = false)
  private Short id;

  @Column(name = "nummer_muster", nullable = false, length = 50)
  private String nummerMuster;

  @Column(name = "steuersatz", nullable = false, precision = 5, scale = 2)
  private BigDecimal steuersatz;

  @Column(name = "zahlungsziel_tage", nullable = false)
  private int zahlungszielTage;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /*
   * JPA verlangt einen parameterlosen Konstruktor und fuellt die Felder danach selbst; NullAway
   * sieht diesen Weg nicht und meldete sonst nicht initialisierte Felder.
   */
  @SuppressWarnings("NullAway.Init")
  protected RechnungseinstellungenEntity() {
    // Von Hibernate benutzt.
  }

  RechnungseinstellungenEntity(
      final String nummerMuster,
      final BigDecimal steuersatz,
      final int zahlungszielTage,
      final Instant updatedAt) {
    this.id = ZEILE;
    this.nummerMuster = nummerMuster;
    this.steuersatz = steuersatz;
    this.zahlungszielTage = zahlungszielTage;
    this.updatedAt = updatedAt;
  }

  Short getId() {
    return id;
  }

  String getNummerMuster() {
    return nummerMuster;
  }

  BigDecimal getSteuersatz() {
    return steuersatz;
  }

  int getZahlungszielTage() {
    return zahlungszielTage;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }
}
