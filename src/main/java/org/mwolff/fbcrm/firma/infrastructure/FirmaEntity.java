package org.mwolff.fbcrm.firma.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Die Zeile der Tabelle {@code firma} aus {@code V2__firma_und_ansprechpartner.sql}. */
@Entity
@Table(name = "firma")
class FirmaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @Column(name = "strasse", length = 200)
  private @Nullable String strasse;

  @Column(name = "plz", length = 20)
  private @Nullable String plz;

  @Column(name = "ort", length = 200)
  private @Nullable String ort;

  @Column(name = "land", length = 100)
  private @Nullable String land;

  @Column(name = "steuernummer", length = 50)
  private @Nullable String steuernummer;

  @Column(name = "umsatzsteuer_id", length = 50)
  private @Nullable String umsatzsteuerId;

  @Column(name = "aktiv", nullable = false)
  private boolean aktiv;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /*
   * JPA verlangt einen parameterlosen Konstruktor und fuellt die Felder danach selbst; NullAway
   * sieht diesen Weg nicht und meldete sonst nicht initialisierte Felder.
   */
  @SuppressWarnings("NullAway.Init")
  protected FirmaEntity() {
    // Von Hibernate benutzt.
  }

  /*
   * Elf Spalten ergeben elf Parameter. Ein Zwischenobjekt zu bauen, nur um die Liste zu kuerzen,
   * verschoebe die Zahl, ohne etwas zu klaeren: Die Zeile ist die Zeile der Tabelle, und genau
   * die uebersetzt JpaFirmaRepository in beide Richtungen.
   */
  @SuppressWarnings("PMD.ExcessiveParameterList")
  FirmaEntity(
      final @Nullable Long id,
      final String name,
      final @Nullable String strasse,
      final @Nullable String plz,
      final @Nullable String ort,
      final @Nullable String land,
      final @Nullable String steuernummer,
      final @Nullable String umsatzsteuerId,
      final boolean aktiv,
      final Instant createdAt,
      final Instant updatedAt) {
    this.id = id;
    this.name = name;
    this.strasse = strasse;
    this.plz = plz;
    this.ort = ort;
    this.land = land;
    this.steuernummer = steuernummer;
    this.umsatzsteuerId = umsatzsteuerId;
    this.aktiv = aktiv;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  @Nullable Long getId() {
    return id;
  }

  String getName() {
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

  @Nullable String getSteuernummer() {
    return steuernummer;
  }

  @Nullable String getUmsatzsteuerId() {
    return umsatzsteuerId;
  }

  boolean isAktiv() {
    return aktiv;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }
}
