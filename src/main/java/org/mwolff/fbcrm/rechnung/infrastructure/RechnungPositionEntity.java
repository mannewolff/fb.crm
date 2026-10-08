package org.mwolff.fbcrm.rechnung.infrastructure;

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
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.rechnung.domain.Rechnungsposition;

/**
 * Die Zeile der Tabelle {@code rechnung_position} aus {@code V18__rechnung.sql}.
 *
 * <p>{@code position} ist der Platz in der Reihenfolge und steht nur hier: Am Fachobjekt ist die
 * Reihenfolge die der Liste, und {@code JpaRechnungRepository} vergibt die Plaetze beim Schreiben
 * lueckenlos ab 1 (E24). Der Betrag hat keine Spalte — er wird gerechnet (E5).
 *
 * <p><b>Die Zeile ist veraenderlich</b> — wie {@code AngebotPositionEntity} und aus demselben
 * Grund: Der Bestand schreibt eine bekannte Zeile fort, statt sie zu ersetzen, und dafuer nimmt
 * {@link #uebernehme} den neuen Stand auf. Wiedererkannt wird sie an ihrer Angebotsposition, denn
 * je Rechnung gibt es zu jeder hoechstens eine Zeile. Eine neue Instanz mit derselben Kennung waere
 * fuer Hibernate eine abgeloeste Kopie, die erst wieder eingelesen werden muesste.
 */
@Entity
@Table(name = "rechnung_position")
class RechnungPositionEntity {

  /*
   * Ohne Getter: Die Kennung der Zeile braucht ausserhalb dieser Klasse niemand. Eine
   * Rechnungsposition wird an ihrer Angebotsposition wiedererkannt, nicht an dieser Zahl (siehe
   * Rechnungsposition), und ein Getter ohne Aufrufer waere ungeprueftes Beiwerk.
   */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "rechnung_id", nullable = false)
  private long rechnungId;

  @Column(name = "angebot_position_id", nullable = false)
  private long angebotPositionId;

  @Column(name = "position", nullable = false)
  private short position;

  @Column(name = "bezeichnung", nullable = false, length = 300)
  private String bezeichnung;

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
  protected RechnungPositionEntity() {
    // Von Hibernate benutzt.
  }

  /**
   * Eine neue Zeile zu einer Rechnungsposition (Plan #238, A5).
   *
   * <p>Rechnung und Platz stehen nicht am Fachobjekt und kommen darum dazu: Die Rechnung ist der
   * Fremdschluessel der Zeile, und der Platz entsteht aus der Reihenfolge der Liste (E24). Die
   * Kennung bleibt leer — eine bekannte Zeile wird fortgeschrieben ({@link #uebernehme}) und nicht
   * neu gebaut.
   *
   * @param position die abzurechnende Position
   * @param rechnungId die Rechnung, zu der die Zeile gehoert
   * @param platz der Platz in der Reihenfolge, lueckenlos ab 1
   * @return die neue Zeile
   */
  static RechnungPositionEntity aus(
      final Rechnungsposition position, final long rechnungId, final short platz) {
    return new RechnungPositionEntity(position, rechnungId, platz);
  }

  private RechnungPositionEntity(
      final Rechnungsposition position, final long rechnungId, final short platz) {
    this.rechnungId = rechnungId;
    this.angebotPositionId = position.angebotPositionId();
    this.position = platz;
    this.bezeichnung = position.bezeichnung();
    this.menge = position.menge();
    this.einheit = position.einheit();
    this.einzelpreis = position.einzelpreis();
  }

  /**
   * Nimmt den neuen Stand dieser Position auf — alles ausser Kennung, Rechnung und
   * Angebotsposition.
   *
   * @param position der Platz in der Reihenfolge, lueckenlos ab 1
   * @param bezeichnung die Leistung
   * @param menge abgerechnete Menge in der angegebenen Einheit
   * @param einheit Einheit der Menge
   * @param einzelpreis Netto-Preis je Einheit
   */
  void uebernehme(
      final short position,
      final String bezeichnung,
      final BigDecimal menge,
      final Einheit einheit,
      final BigDecimal einzelpreis) {
    this.position = position;
    this.bezeichnung = bezeichnung;
    this.menge = menge;
    this.einheit = einheit;
    this.einzelpreis = einzelpreis;
  }

  long getRechnungId() {
    return rechnungId;
  }

  long getAngebotPositionId() {
    return angebotPositionId;
  }

  short getPosition() {
    return position;
  }

  String getBezeichnung() {
    return bezeichnung;
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
