package org.mwolff.fbcrm.angebot.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;

/**
 * Die Zeile der Tabelle {@code angebot} nach {@code V11__angebot_ohne_beleg.sql}.
 *
 * <p>Firma und Ansprechpartner stehen als blosse Kennungen und nicht als Beziehung: Angebot und
 * Firma sind eigene Wurzeln mit eigenem Lebenszyklus, und eine Beziehung laedt das eine mit dem
 * anderen, ohne dass es gebraucht wuerde (V10). Die Positionen liegen aus demselben Grund in {@link
 * AngebotPositionEntity} mit blosser Kennung; ihre Reihenfolge fuehrt {@code JpaAngebotRepository}.
 *
 * <p>{@code status} geht als Text in die Datenbank ({@link EnumType#STRING}) und nicht als
 * Ordnungszahl: Der CHECK der Migration nennt die Werte im Klartext, und ein neuer Status darf die
 * Bedeutung der bestehenden Zeilen nicht verschieben. Die Spalte der Beschreibung heisst aus der
 * Geschichte des Schemas {@code leistungsbeschreibung}.
 */
@Entity
@Table(name = "angebot")
class AngebotEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "firma_id", nullable = false)
  private long firmaId;

  @Column(name = "ansprechpartner_id")
  private @Nullable Long ansprechpartnerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private Angebotsstatus status;

  @Column(name = "angebot_datum", nullable = false)
  private LocalDate angebotDatum;

  @Column(name = "leistungsbeschreibung", columnDefinition = "text")
  private @Nullable String beschreibung;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /*
   * JPA verlangt einen parameterlosen Konstruktor und fuellt die Felder danach selbst; NullAway
   * sieht diesen Weg nicht und meldete sonst nicht initialisierte Felder.
   */
  @SuppressWarnings("NullAway.Init")
  protected AngebotEntity() {
    // Von Hibernate benutzt.
  }

  AngebotEntity(
      final @Nullable Long id,
      final long firmaId,
      final @Nullable Long ansprechpartnerId,
      final Angebotsstatus status,
      final LocalDate angebotDatum,
      final @Nullable String beschreibung,
      final Instant createdAt,
      final Instant updatedAt) {
    this.id = id;
    this.firmaId = firmaId;
    this.ansprechpartnerId = ansprechpartnerId;
    this.status = status;
    this.angebotDatum = angebotDatum;
    this.beschreibung = beschreibung;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  @Nullable Long getId() {
    return id;
  }

  long getFirmaId() {
    return firmaId;
  }

  @Nullable Long getAnsprechpartnerId() {
    return ansprechpartnerId;
  }

  Angebotsstatus getStatus() {
    return status;
  }

  LocalDate getAngebotDatum() {
    return angebotDatum;
  }

  @Nullable String getBeschreibung() {
    return beschreibung;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }
}
