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
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
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

  /**
   * Die Zeile zu einer Angebotsposition (Plan #238, A5).
   *
   * <p>Angebot und Platz stehen nicht am Fachobjekt und kommen darum dazu: Das Angebot ist der
   * Fremdschluessel der Zeile, und der Platz entsteht aus der Reihenfolge der Liste (E24). Die
   * Kennung ist die der Position und damit {@code null}, solange die Position neu ist; eine
   * bekannte Zeile wird fortgeschrieben ({@link #uebernehme}) und nicht neu gebaut.
   *
   * @param position die Position
   * @param angebotId das Angebot, zu dem die Zeile gehoert
   * @param platz der Platz in der Reihenfolge, lueckenlos ab 1
   * @return die Zeile
   */
  static AngebotPositionEntity aus(
      final Angebotsposition position, final long angebotId, final short platz) {
    return new AngebotPositionEntity(position, angebotId, platz);
  }

  private AngebotPositionEntity(
      final Angebotsposition position, final long angebotId, final short platz) {
    this.id = position.id();
    this.angebotId = angebotId;
    this.position = platz;
    this.bezeichnung = position.bezeichnung();
    this.abrechnungsmodus = position.abrechnungsmodus();
    this.menge = position.menge();
    this.einheit = position.einheit();
    this.einzelpreis = position.einzelpreis();
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
