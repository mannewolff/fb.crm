package org.mwolff.fbcrm.arbeitszeit.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.jspecify.annotations.Nullable;

/**
 * Die Zeile der Tabelle {@code arbeitszeit} aus {@code V19__arbeitszeit.sql}.
 *
 * <p>Die Angebotsposition steht als blosse Kennung und nicht als Beziehung, aus demselben Grund wie
 * das Angebot in {@code AngebotKommentarEntity}: Eine Beziehung laedt das Angebot mit jeder
 * erfassten Viertelstunde, ohne dass es gebraucht wuerde.
 *
 * <p><b>Die Zeile ist unveraenderlich</b> — anders als {@code RechnungPositionEntity} und aus
 * demselben Grund wie der Kommentar am Angebot: Ein Zeiteintrag ist eine Zeile fuer sich, und der
 * Adapter schreibt ihn als Ganzes. Eine Dauer traegt die Zeile nicht; sie wird gerechnet (Plan
 * #194, A3).
 */
@Entity
@Table(name = "arbeitszeit")
class ZeiteintragEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "angebot_position_id", nullable = false)
  private long angebotPositionId;

  @Column(name = "tag", nullable = false)
  private LocalDate tag;

  @Column(name = "von", nullable = false)
  private LocalTime von;

  @Column(name = "bis", nullable = false)
  private LocalTime bis;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /*
   * JPA verlangt einen parameterlosen Konstruktor und fuellt die Felder danach selbst; NullAway
   * sieht diesen Weg nicht und meldete sonst nicht initialisierte Felder.
   */
  @SuppressWarnings("NullAway.Init")
  protected ZeiteintragEntity() {
    // Von Hibernate benutzt.
  }

  ZeiteintragEntity(
      final @Nullable Long id,
      final long angebotPositionId,
      final LocalDate tag,
      final LocalTime von,
      final LocalTime bis,
      final Instant createdAt,
      final Instant updatedAt) {
    this.id = id;
    this.angebotPositionId = angebotPositionId;
    this.tag = tag;
    this.von = von;
    this.bis = bis;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  @Nullable Long getId() {
    return id;
  }

  long getAngebotPositionId() {
    return angebotPositionId;
  }

  LocalDate getTag() {
    return tag;
  }

  LocalTime getVon() {
    return von;
  }

  LocalTime getBis() {
    return bis;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }
}
