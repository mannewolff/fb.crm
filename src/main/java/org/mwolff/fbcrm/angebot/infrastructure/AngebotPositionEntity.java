package org.mwolff.fbcrm.angebot.infrastructure;

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
 * Die Zeile der Tabelle {@code angebot_position} aus {@code V6__angebot.sql}.
 *
 * <p>{@code position} ist der Platz in der Reihenfolge und steht nur hier: Am Fachobjekt ist die
 * Reihenfolge die der Liste, und {@code JpaAngebotRepository} vergibt die Plaetze beim Schreiben
 * lueckenlos ab 1 (E24). Der Betrag hat keine Spalte — er wird gerechnet (E5).
 *
 * <p><b>Die Zeile ist veraenderlich</b> — anders als die Fachobjekte des Projekts. Seit Plan #169,
 * E2 traegt die Position eine dauerhafte Kennung, und der Bestand schreibt eine bekannte Zeile fort
 * statt sie zu ersetzen; dafuer nimmt {@link #uebernehme} den neuen Stand auf. Eine neue Instanz
 * mit derselben Kennung waere fuer Hibernate eine abgeloeste Kopie, die erst wieder eingelesen
 * werden muesste.
 */
@Entity
@Table(name = "angebot_position")
class AngebotPositionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "angebot_id", nullable = false)
  private long angebotId;

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

  /*
   * JPA verlangt einen parameterlosen Konstruktor und fuellt die Felder danach selbst; NullAway
   * sieht diesen Weg nicht und meldete sonst nicht initialisierte Felder.
   */
  @SuppressWarnings("NullAway.Init")
  protected AngebotPositionEntity() {
    // Von Hibernate benutzt.
  }

  AngebotPositionEntity(
      final @Nullable Long id,
      final long angebotId,
      final short position,
      final String bezeichnung,
      final Abrechnungsmodus abrechnungsmodus,
      final BigDecimal menge,
      final Einheit einheit,
      final BigDecimal einzelpreis) {
    this.id = id;
    this.angebotId = angebotId;
    this.position = position;
    this.bezeichnung = bezeichnung;
    this.abrechnungsmodus = abrechnungsmodus;
    this.menge = menge;
    this.einheit = einheit;
    this.einzelpreis = einzelpreis;
  }

  /**
   * Nimmt den neuen Stand dieser Position auf — alles ausser Kennung und Angebot (Plan #169, E2).
   *
   * @param position der Platz in der Reihenfolge, lueckenlos ab 1
   * @param bezeichnung die Leistung
   * @param abrechnungsmodus nach Aufwand oder zum Festpreis
   * @param menge Menge in der angegebenen Einheit
   * @param einheit Einheit der Menge
   * @param einzelpreis Netto-Preis je Einheit
   */
  void uebernehme(
      final short position,
      final String bezeichnung,
      final Abrechnungsmodus abrechnungsmodus,
      final BigDecimal menge,
      final Einheit einheit,
      final BigDecimal einzelpreis) {
    this.position = position;
    this.bezeichnung = bezeichnung;
    this.abrechnungsmodus = abrechnungsmodus;
    this.menge = menge;
    this.einheit = einheit;
    this.einzelpreis = einzelpreis;
  }

  @Nullable Long getId() {
    return id;
  }

  long getAngebotId() {
    return angebotId;
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
}
