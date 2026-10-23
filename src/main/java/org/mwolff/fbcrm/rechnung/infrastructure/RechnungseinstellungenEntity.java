package org.mwolff.fbcrm.rechnung.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Die eine Zeile der Tabelle {@code rechnung_einstellungen} aus {@code
 * V14__rechnung_einstellungen.sql} und {@code V33__version.sql}.
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
   * Der Stand der Zeile, gefuehrt von Hibernate (Plan #415, E1): Jedes Schreiben erhoeht ihn, und
   * ein UPDATE mit einem anderen Stand als dem vorgelegten trifft keine Zeile und scheitert. Ein
   * long und kein Long: Ob die Zeile neu ist, entscheidet so weiter die Kennung.
   */
  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "geaendert_von")
  private @Nullable Long geaendertVon;

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
      final Instant updatedAt,
      final long version,
      final @Nullable Long geaendertVon) {
    this.id = ZEILE;
    this.nummerMuster = nummerMuster;
    this.steuersatz = steuersatz;
    this.zahlungszielTage = zahlungszielTage;
    this.updatedAt = updatedAt;
    this.version = version;
    this.geaendertVon = geaendertVon;
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

  long getVersion() {
    return version;
  }

  @Nullable Long getGeaendertVon() {
    return geaendertVon;
  }
}
