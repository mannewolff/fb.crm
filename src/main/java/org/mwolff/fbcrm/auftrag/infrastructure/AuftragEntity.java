package org.mwolff.fbcrm.auftrag.infrastructure;

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
import org.mwolff.fbcrm.auftrag.domain.Auftragsstatus;

/**
 * Die Zeile der Tabelle {@code auftrag} aus {@code V8__auftrag.sql}.
 *
 * <p>Vorgang und Angebot stehen als blosse Kennungen und nicht als Beziehungen — dasselbe Muster
 * wie in {@code AngebotEntity}: Alle drei sind eigene Wurzeln mit eigenem Lebenszyklus, und eine
 * Beziehung laedt das eine mit dem anderen, ohne dass es gebraucht wuerde. Die Positionen liegen
 * aus demselben Grund in {@link AuftragPositionEntity} mit blosser Kennung; ihre Reihenfolge fuehrt
 * {@code JpaAuftragRepository}.
 *
 * <p>{@code status} geht als Text in die Datenbank ({@link EnumType#STRING}) und nicht als
 * Ordnungszahl: Der Check der Migration nennt die Werte im Klartext, und ein vierter Status darf
 * die Bedeutung der bestehenden Zeilen nicht verschieben.
 */
@Entity
@Table(name = "auftrag")
class AuftragEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "vorgang_id", nullable = false)
  private long vorgangId;

  @Column(name = "angebot_id", nullable = false)
  private long angebotId;

  @Column(name = "nummer", nullable = false, length = 20)
  private String nummer;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private Auftragsstatus status;

  @Column(name = "auftrag_datum", nullable = false)
  private LocalDate auftragDatum;

  @Column(name = "kundenbestellnummer", length = 100)
  private @Nullable String kundenbestellnummer;

  @Column(name = "leistung_ab")
  private @Nullable LocalDate leistungAb;

  @Column(name = "leistung_bis")
  private @Nullable LocalDate leistungBis;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /*
   * JPA verlangt einen parameterlosen Konstruktor und fuellt die Felder danach selbst; NullAway
   * sieht diesen Weg nicht und meldete sonst nicht initialisierte Felder.
   */
  @SuppressWarnings("NullAway.Init")
  protected AuftragEntity() {
    // Von Hibernate benutzt.
  }

  /*
   * PMD.ExcessiveParameterList: Elf Spalten ergeben elf Parameter — dieselbe Lage wie bei
   * AngebotEntity. Die Zeile ist die Zeile der Tabelle, nicht mehr; Teile davon in einen eigenen
   * Werttyp zu heben, machte aus einer Zeile zwei, die immer zusammen gelesen werden.
   */
  @SuppressWarnings("PMD.ExcessiveParameterList")
  AuftragEntity(
      final @Nullable Long id,
      final long vorgangId,
      final long angebotId,
      final String nummer,
      final Auftragsstatus status,
      final LocalDate auftragDatum,
      final @Nullable String kundenbestellnummer,
      final @Nullable LocalDate leistungAb,
      final @Nullable LocalDate leistungBis,
      final Instant createdAt,
      final Instant updatedAt) {
    this.id = id;
    this.vorgangId = vorgangId;
    this.angebotId = angebotId;
    this.nummer = nummer;
    this.status = status;
    this.auftragDatum = auftragDatum;
    this.kundenbestellnummer = kundenbestellnummer;
    this.leistungAb = leistungAb;
    this.leistungBis = leistungBis;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  @Nullable Long getId() {
    return id;
  }

  long getVorgangId() {
    return vorgangId;
  }

  long getAngebotId() {
    return angebotId;
  }

  String getNummer() {
    return nummer;
  }

  Auftragsstatus getStatus() {
    return status;
  }

  LocalDate getAuftragDatum() {
    return auftragDatum;
  }

  @Nullable String getKundenbestellnummer() {
    return kundenbestellnummer;
  }

  @Nullable LocalDate getLeistungAb() {
    return leistungAb;
  }

  @Nullable LocalDate getLeistungBis() {
    return leistungBis;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }
}
