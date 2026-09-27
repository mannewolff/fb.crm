package org.mwolff.fbcrm.eigeneangaben.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Die eine Zeile der Tabelle {@code eigene_angaben} aus {@code V5__eigene_angaben.sql}.
 *
 * <p>Die Kennung wird nicht erzeugt, sondern gesetzt: Es gibt genau die Zeile {@link #ZEILE}, und
 * die Datenbank laesst ueber ihren CHECK keine zweite zu. Dass die Kennung von Anfang an steht,
 * macht aus jedem {@code save} ein UPDATE statt eines INSERT — genau das ist hier gewollt.
 */
@Entity
@Table(name = "eigene_angaben")
class EigeneAngabenEntity {

  /** Die Kennung der einen Zeile. */
  static final Short ZEILE = (short) 1;

  @Id
  @Column(name = "id", nullable = false)
  private Short id;

  @Column(name = "name", length = 200)
  private @Nullable String name;

  @Column(name = "strasse", length = 200)
  private @Nullable String strasse;

  @Column(name = "plz", length = 20)
  private @Nullable String plz;

  @Column(name = "ort", length = 200)
  private @Nullable String ort;

  @Column(name = "land", length = 100)
  private @Nullable String land;

  @Column(name = "email", length = 320)
  private @Nullable String email;

  @Column(name = "telefon", length = 50)
  private @Nullable String telefon;

  @Column(name = "steuernummer", length = 50)
  private @Nullable String steuernummer;

  @Column(name = "umsatzsteuer_id", length = 50)
  private @Nullable String umsatzsteuerId;

  @Column(name = "bankverbindung", length = 200)
  private @Nullable String bankverbindung;

  @Column(name = "zahlungsbedingungen", columnDefinition = "text")
  private @Nullable String zahlungsbedingungen;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /*
   * JPA verlangt einen parameterlosen Konstruktor und fuellt die Felder danach selbst; NullAway
   * sieht diesen Weg nicht und meldete sonst nicht initialisierte Felder.
   */
  @SuppressWarnings("NullAway.Init")
  protected EigeneAngabenEntity() {
    // Von Hibernate benutzt.
  }

  /*
   * PMD.ExcessiveParameterList: Zwoelf Spalten ergeben zwoelf Parameter. Ein Zwischenobjekt zu
   * bauen, nur um die Liste zu kuerzen, verschoebe die Zahl, ohne etwas zu klaeren: Die Zeile ist
   * die Zeile der Tabelle, und genau die uebersetzt JpaEigeneAngabenRepository in beide
   * Richtungen. Die Kennung steht nicht in der Liste — sie ist fest.
   */
  @SuppressWarnings("PMD.ExcessiveParameterList")
  EigeneAngabenEntity(
      final @Nullable String name,
      final @Nullable String strasse,
      final @Nullable String plz,
      final @Nullable String ort,
      final @Nullable String land,
      final @Nullable String email,
      final @Nullable String telefon,
      final @Nullable String steuernummer,
      final @Nullable String umsatzsteuerId,
      final @Nullable String bankverbindung,
      final @Nullable String zahlungsbedingungen,
      final Instant updatedAt) {
    this.id = ZEILE;
    this.name = name;
    this.strasse = strasse;
    this.plz = plz;
    this.ort = ort;
    this.land = land;
    this.email = email;
    this.telefon = telefon;
    this.steuernummer = steuernummer;
    this.umsatzsteuerId = umsatzsteuerId;
    this.bankverbindung = bankverbindung;
    this.zahlungsbedingungen = zahlungsbedingungen;
    this.updatedAt = updatedAt;
  }

  Short getId() {
    return id;
  }

  @Nullable String getName() {
    return name;
  }

  @Nullable String getStrasse() {
    return strasse;
  }

  @Nullable String getPlz() {
    return plz;
  }

  @Nullable String getOrt() {
    return ort;
  }

  @Nullable String getLand() {
    return land;
  }

  @Nullable String getEmail() {
    return email;
  }

  @Nullable String getTelefon() {
    return telefon;
  }

  @Nullable String getSteuernummer() {
    return steuernummer;
  }

  @Nullable String getUmsatzsteuerId() {
    return umsatzsteuerId;
  }

  @Nullable String getBankverbindung() {
    return bankverbindung;
  }

  @Nullable String getZahlungsbedingungen() {
    return zahlungsbedingungen;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }
}
