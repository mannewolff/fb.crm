package org.mwolff.fbcrm.firma.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;

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

  /**
   * Die Zeile zu einem Ansprechpartner — jedes Feld kommt aus dem Fachobjekt (Plan #238, A5).
   *
   * <p>Dieselbe Form wie bei {@link FirmaEntity}: Die Zeile holt sich ihren Stand selbst, statt ihn
   * als Liste von elf Parametern zu bekommen. Die Firma steht am Ansprechpartner selbst und kommt
   * darum nicht als eigener Parameter dazu.
   *
   * @param ansprechpartner der Ansprechpartner, dessen Stand die Zeile tragen soll
   * @return die Zeile; ihre Kennung ist die des Ansprechpartners und damit {@code null}, solange er
   *     noch nicht geschrieben wurde
   */
  static AnsprechpartnerEntity aus(final Ansprechpartner ansprechpartner) {
    return new AnsprechpartnerEntity(ansprechpartner);
  }

  private AnsprechpartnerEntity(final Ansprechpartner ansprechpartner) {
    this.id = ansprechpartner.id();
    this.firmaId = ansprechpartner.firmaId();
    this.vorname = ansprechpartner.vorname();
    this.nachname = ansprechpartner.nachname();
    this.rolle = ansprechpartner.rolle();
    this.email = ansprechpartner.email();
    this.telefonFestnetz = ansprechpartner.telefonFestnetz();
    this.telefonMobil = ansprechpartner.telefonMobil();
    this.aktiv = ansprechpartner.aktiv();
    this.createdAt = ansprechpartner.createdAt();
    this.updatedAt = ansprechpartner.updatedAt();
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
