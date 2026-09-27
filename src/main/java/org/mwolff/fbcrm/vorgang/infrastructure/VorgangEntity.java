package org.mwolff.fbcrm.vorgang.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * Die Zeile der Tabelle {@code vorgang} aus {@code V3__vorgang_und_historie.sql}.
 *
 * <p>Firma und Ansprechpartner stehen als blosse Kennungen und nicht als Beziehungen — dasselbe
 * Muster wie in {@code AnsprechpartnerEntity}: Jede der drei ist eine eigene Wurzel mit eigenem
 * Lebenszyklus, und eine Beziehung laedt das eine mit dem anderen, ohne dass es gebraucht wuerde.
 *
 * <p>Die Phase hat keine Spalte (E4) und der Abschluss keinen Zeitstempel (E5); beides steht so im
 * Schema und im Domaenenmodell. Die beiden Pipeline-Spalten kommen aus {@code
 * V7__vorgang_pipeline_felder.sql}; ihren Wertebereich haelt der CHECK dort, nicht diese Klasse.
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

  /*
   * Die Spalte ist smallint — hundert passt in zwei Byte, und der CHECK der Migration haelt die
   * Grenze. Im Fachmodell steht sie als Integer, weil eine Prozentangabe keine Rechenart braucht,
   * die short erzwingt; @JdbcTypeCode sagt Hibernate den Spaltentyp, sonst weist seine
   * Schema-Pruefung beim Start int2 gegen den erwarteten integer ab.
   */
  @Column(name = "abschlusswahrscheinlichkeit")
  @JdbcTypeCode(SqlTypes.SMALLINT)
  private @Nullable Integer abschlusswahrscheinlichkeit;

  @Column(name = "entscheidung_erwartet_am")
  private @Nullable LocalDate entscheidungErwartetAm;

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

  /*
   * PMD.ExcessiveParameterList: Zehn Spalten ergeben zehn Parameter. Ein Zwischenobjekt dafuer waere
   * eine zweite Form derselben Zeile — dieselbe Lage wie in VorgangEintragEntity und FirmaEntity.
   */
  @SuppressWarnings("PMD.ExcessiveParameterList")
  VorgangEntity(
      final @Nullable Long id,
      final long nummer,
      final String titel,
      final long firmaId,
      final @Nullable Long ansprechpartnerId,
      final @Nullable Integer abschlusswahrscheinlichkeit,
      final @Nullable LocalDate entscheidungErwartetAm,
      final boolean abgeschlossen,
      final Instant createdAt,
      final Instant updatedAt) {
    this.id = id;
    this.nummer = nummer;
    this.titel = titel;
    this.firmaId = firmaId;
    this.ansprechpartnerId = ansprechpartnerId;
    this.abschlusswahrscheinlichkeit = abschlusswahrscheinlichkeit;
    this.entscheidungErwartetAm = entscheidungErwartetAm;
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

  @Nullable Integer getAbschlusswahrscheinlichkeit() {
    return abschlusswahrscheinlichkeit;
  }

  @Nullable LocalDate getEntscheidungErwartetAm() {
    return entscheidungErwartetAm;
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
