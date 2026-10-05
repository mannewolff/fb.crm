package org.mwolff.fbcrm.firma.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.infrastructure.AnschriftSpalten;
import org.mwolff.fbcrm.firma.domain.Firma;

/** Die Zeile der Tabelle {@code firma} aus {@code V2__firma_und_ansprechpartner.sql}. */
@Entity
@Table(name = "firma")
class FirmaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  /*
   * Hibernate laesst das eingebettete Objekt weg, wenn jede seiner Spalten NULL ist — eine Firma
   * wird oft mit nichts als ihrem Namen angelegt. Darum liest getAnschrift() es ueber
   * AnschriftSpalten.anschriftAus und nicht direkt.
   */
  @Embedded private @Nullable AnschriftSpalten anschrift;

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

  /**
   * Die Zeile zu einer Firma — jedes Feld kommt aus dem Fachobjekt (Plan #238, A5).
   *
   * <p>Die Zeile holt sich ihren Stand selbst, statt ihn als Liste von elf Parametern zu bekommen:
   * Der Adapter nennt dann am Aufruf nur noch, <i>was</i> abgebildet wird, und ein neues Feld der
   * Firma landet hier und nicht zusaetzlich in jeder Aufrufstelle.
   *
   * @param firma die Firma, deren Stand die Zeile tragen soll
   * @return die Zeile; ihre Kennung ist die der Firma und damit {@code null}, solange die Firma
   *     noch nicht geschrieben wurde
   */
  static FirmaEntity aus(final Firma firma) {
    return new FirmaEntity(firma);
  }

  private FirmaEntity(final Firma firma) {
    this.id = firma.id();
    this.name = firma.name();
    this.anschrift = AnschriftSpalten.aus(firma.anschrift());
    this.steuernummer = firma.steuernummer();
    this.umsatzsteuerId = firma.umsatzsteuerId();
    this.aktiv = firma.aktiv();
    this.createdAt = firma.createdAt();
    this.updatedAt = firma.updatedAt();
  }

  @Nullable Long getId() {
    return id;
  }

  String getName() {
    return name;
  }

  Anschrift getAnschrift() {
    return AnschriftSpalten.anschriftAus(anschrift);
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
