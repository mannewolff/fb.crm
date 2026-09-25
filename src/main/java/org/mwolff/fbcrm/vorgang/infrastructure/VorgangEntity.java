package org.mwolff.fbcrm.vorgang.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Die Zeile der Tabelle {@code vorgang} aus {@code V3__vorgang_und_historie.sql}.
 *
 * <p>Firma und Ansprechpartner stehen als blosse Kennungen und nicht als Beziehungen — dasselbe
 * Muster wie in {@code AnsprechpartnerEntity}: Jede der drei ist eine eigene Wurzel mit eigenem
 * Lebenszyklus, und eine Beziehung laedt das eine mit dem anderen, ohne dass es gebraucht wuerde.
 *
 * <p>Die Phase hat keine Spalte (E4) und der Abschluss keinen Zeitstempel (E5); beides steht so im
 * Schema und im Domaenenmodell.
 */
@Entity
@Table(name = "vorgang")
class VorgangEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "nummer", nullable = false)
  private long nummer;

  @Column(name = "titel", nullable = false, length = 300)
  private String titel;

  @Column(name = "firma_id", nullable = false)
  private long firmaId;

  @Column(name = "ansprechpartner_id")
  private @Nullable Long ansprechpartnerId;

  @Column(name = "abgeschlossen", nullable = false)
  private boolean abgeschlossen;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /*
   * JPA verlangt einen parameterlosen Konstruktor und fuellt die Felder danach selbst; NullAway
   * sieht diesen Weg nicht und meldete sonst nicht initialisierte Felder.
   */
  @SuppressWarnings("NullAway.Init")
  protected VorgangEntity() {
    // Von Hibernate benutzt.
  }

  VorgangEntity(
      final @Nullable Long id,
      final long nummer,
      final String titel,
      final long firmaId,
      final @Nullable Long ansprechpartnerId,
      final boolean abgeschlossen,
      final Instant createdAt,
      final Instant updatedAt) {
    this.id = id;
    this.nummer = nummer;
    this.titel = titel;
    this.firmaId = firmaId;
    this.ansprechpartnerId = ansprechpartnerId;
    this.abgeschlossen = abgeschlossen;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  @Nullable Long getId() {
    return id;
  }

  long getNummer() {
    return nummer;
  }

  String getTitel() {
    return titel;
  }

  long getFirmaId() {
    return firmaId;
  }

  @Nullable Long getAnsprechpartnerId() {
    return ansprechpartnerId;
  }

  boolean isAbgeschlossen() {
    return abgeschlossen;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }
}
