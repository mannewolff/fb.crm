package org.mwolff.fbcrm.auftrag.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;

/**
 * Die Zeile der Tabelle {@code auftrag_position} aus {@code V8__auftrag.sql}.
 *
 * <p>{@code position} ist der Platz in der Reihenfolge und steht nur hier: Am Fachobjekt ist die
 * Reihenfolge die der Liste, und {@code JpaAuftragRepository} vergibt die Plaetze beim Schreiben
 * lueckenlos ab 1. Der Betrag hat keine Spalte — er wird gerechnet (E11).
 *
 * <p>{@code stundenJePersonentag} ist die einzige Spalte, die fehlen darf, und ihr Fehlen ist eine
 * Aussage: Sie steht genau an einer Aufwandsposition und dort ueber null, beim Festpreis nie (E10).
 * Zwei gegenlaeufige Checks der Migration halten das fest.
 */
@Entity
@Table(name = "auftrag_position")
class AuftragPositionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "auftrag_id", nullable = false)
  private long auftragId;

  @Column(name = "position", nullable = false)
  private short position;

  @Column(name = "bezeichnung", nullable = false, length = 300)
  private String bezeichnung;

  @Enumerated(EnumType.STRING)
  @Column(name = "abrechnungsmodus", nullable = false, length = 20)
  private Abrechnungsmodus abrechnungsmodus;

  @Column(name = "menge", nullable = false, precision = 12, scale = 2)
  private BigDecimal menge;

  @Enumerated(EnumType.STRING)
  @Column(name = "einheit", nullable = false, length = 20)
  private Einheit einheit;

  @Column(name = "einzelpreis", nullable = false, precision = 12, scale = 2)
  private BigDecimal einzelpreis;

  @Column(name = "stunden_je_personentag", precision = 4, scale = 2)
  private @Nullable BigDecimal stundenJePersonentag;

  /*
   * JPA verlangt einen parameterlosen Konstruktor und fuellt die Felder danach selbst; NullAway
   * sieht diesen Weg nicht und meldete sonst nicht initialisierte Felder.
   */
  @SuppressWarnings("NullAway.Init")
  protected AuftragPositionEntity() {
    // Von Hibernate benutzt.
  }

  AuftragPositionEntity(
      final @Nullable Long id,
      final long auftragId,
      final short position,
      final String bezeichnung,
      final Abrechnungsmodus abrechnungsmodus,
      final BigDecimal menge,
      final Einheit einheit,
      final BigDecimal einzelpreis,
      final @Nullable BigDecimal stundenJePersonentag) {
    this.id = id;
    this.auftragId = auftragId;
    this.position = position;
    this.bezeichnung = bezeichnung;
    this.abrechnungsmodus = abrechnungsmodus;
    this.menge = menge;
    this.einheit = einheit;
    this.einzelpreis = einzelpreis;
    this.stundenJePersonentag = stundenJePersonentag;
  }

  @Nullable Long getId() {
    return id;
  }

  long getAuftragId() {
    return auftragId;
  }

  short getPosition() {
    return position;
  }

  String getBezeichnung() {
    return bezeichnung;
  }

  Abrechnungsmodus getAbrechnungsmodus() {
    return abrechnungsmodus;
  }

  BigDecimal getMenge() {
    return menge;
  }

  Einheit getEinheit() {
    return einheit;
  }

  BigDecimal getEinzelpreis() {
    return einzelpreis;
  }

  @Nullable BigDecimal getStundenJePersonentag() {
    return stundenJePersonentag;
  }
}
