package org.mwolff.fbcrm.common.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Die Verwaltungsspalten einer gefuehrten Zeile — Anlage, letzte Aenderung, Stand und Urheber
 * (Issue #489).
 *
 * <p>Mehrere Tabellen fuehren dieselben vier Spalten aus {@code V33__version.sql}; je Entity
 * abgeschrieben waren sie das Duplikat, das SonarCloud am Backend fand. Eine {@code
 * MappedSuperclass} und kein {@code Embeddable} wie {@link AnschriftSpalten}: {@code @Version} darf
 * nach JPA nur in der Entity oder in einer Mapped Superclass stehen. Spaltennamen, Nullbarkeit und
 * Typen sind die der Tabellen; das Schema aendert sich dadurch nicht.
 *
 * <p>Die Getter sind oeffentlich, weil die Repositories im Paket der jeweiligen Entity liegen und
 * nicht in diesem.
 */
/*
 * Abstrakt ohne abstrakte Methode mit Absicht: Eine gefuehrte Zeile gibt es nur als Teil einer
 * Entity, nie fuer sich.
 */
@SuppressWarnings("PMD.AbstractClassWithoutAbstractMethod")
@MappedSuperclass
public abstract class GefuehrteZeile {

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /*
   * Der Stand der Zeile, gefuehrt von Hibernate (Plan #415, E1): Jedes Schreiben erhoeht ihn, und
   * ein UPDATE mit einem anderen Stand als dem gelesenen trifft keine Zeile und scheitert. Er kommt
   * aus dem Fachobjekt mit, damit eine Zeile, die auf einem alten Stand beruht, nicht still
   * geschrieben wird. Ein long und kein Long: Ob die Zeile neu ist, entscheidet so weiter die
   * Kennung, und eine neue Zeile beginnt bei 0 wie die Vorbelegung der Spalte (V33).
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
  protected GefuehrteZeile() {
    // Von Hibernate benutzt.
  }

  /**
   * Die Verwaltungswerte einer Zeile, wie sie das Fachobjekt traegt.
   *
   * @param createdAt Zeitpunkt der Anlage
   * @param updatedAt Zeitpunkt der letzten Aenderung
   * @param version der Stand, auf dem das Fachobjekt beruht
   * @param geaendertVon das Konto, das zuletzt geaendert hat, oder {@code null}
   */
  protected GefuehrteZeile(
      final Instant createdAt,
      final Instant updatedAt,
      final long version,
      final @Nullable Long geaendertVon) {
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.version = version;
    this.geaendertVon = geaendertVon;
  }

  /**
   * Zeitpunkt der Anlage.
   *
   * @return der Zeitpunkt
   */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /**
   * Zeitpunkt der letzten Aenderung.
   *
   * @return der Zeitpunkt
   */
  public Instant getUpdatedAt() {
    return updatedAt;
  }

  /**
   * Der Stand der Zeile.
   *
   * @return der Stand
   */
  public long getVersion() {
    return version;
  }

  /**
   * Das Konto, das zuletzt geaendert hat.
   *
   * @return die Kennung des Kontos, oder {@code null}, wenn keines bekannt ist
   */
  public @Nullable Long getGeaendertVon() {
    return geaendertVon;
  }
}
