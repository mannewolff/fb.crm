package org.mwolff.fbcrm.firma.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Die Zeile der Tabelle {@code ansprechpartner} aus {@code V2__firma_und_ansprechpartner.sql}.
 *
 * <p>Die Firma steht als blosse Kennung und nicht als Beziehung: Ansprechpartner und Firma sind
 * zwei Wurzeln mit je eigenem Lebenszyklus (E2), und eine Beziehung laedt das eine mit dem anderen,
 * ohne dass es je gebraucht wuerde.
 */
@Entity
@Table(name = "ansprechpartner")
class AnsprechpartnerEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "firma_id", nullable = false)
  private long firmaId;

  @Column(name = "vorname", length = 200)
  private @Nullable String vorname;

  @Column(name = "nachname", nullable = false, length = 200)
  private String nachname;

  @Column(name = "rolle", length = 200)
  private @Nullable String rolle;

  @Column(name = "email", length = 320)
  private @Nullable String email;

  @Column(name = "telefon_festnetz", length = 50)
  private @Nullable String telefonFestnetz;

  @Column(name = "telefon_mobil", length = 50)
  private @Nullable String telefonMobil;

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
  protected AnsprechpartnerEntity() {
    // Von Hibernate benutzt.
  }

  /*
   * Elf Spalten ergeben elf Parameter — dieselbe Begruendung wie bei FirmaEntity: Die Zeile ist
   * die Zeile der Tabelle, und ein Zwischenobjekt verschoebe die Zahl nur.
   */
  @SuppressWarnings("PMD.ExcessiveParameterList")
  AnsprechpartnerEntity(
      final @Nullable Long id,
      final long firmaId,
      final @Nullable String vorname,
      final String nachname,
      final @Nullable String rolle,
      final @Nullable String email,
      final @Nullable String telefonFestnetz,
      final @Nullable String telefonMobil,
      final boolean aktiv,
      final Instant createdAt,
      final Instant updatedAt) {
    this.id = id;
    this.firmaId = firmaId;
    this.vorname = vorname;
    this.nachname = nachname;
    this.rolle = rolle;
    this.email = email;
    this.telefonFestnetz = telefonFestnetz;
    this.telefonMobil = telefonMobil;
    this.aktiv = aktiv;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  @Nullable Long getId() {
    return id;
  }

  long getFirmaId() {
    return firmaId;
  }

  @Nullable String getVorname() {
    return vorname;
  }

  String getNachname() {
    return nachname;
  }

  @Nullable String getRolle() {
    return rolle;
  }

  @Nullable String getEmail() {
    return email;
  }

  @Nullable String getTelefonFestnetz() {
    return telefonFestnetz;
  }

  @Nullable String getTelefonMobil() {
    return telefonMobil;
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
