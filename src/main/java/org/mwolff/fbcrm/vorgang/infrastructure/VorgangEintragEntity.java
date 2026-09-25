package org.mwolff.fbcrm.vorgang.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.vorgang.domain.Eintragsart;
import org.mwolff.fbcrm.vorgang.domain.Herkunft;

/**
 * Die Zeile der Tabelle {@code vorgang_eintrag} aus {@code V3__vorgang_und_historie.sql}.
 *
 * <p>Beide Arten der Historie liegen in dieser einen Zeile (E6); welche Spalten eine Art verlangt
 * und welche sie verbietet, halten die Fabriken in {@code Eintrag} und die Checks der Migration
 * fest — die Zeile selbst laesst alle Dateispalten leer stehen.
 *
 * <p>{@code art} und {@code herkunft} gehen als Text in die Datenbank ({@link EnumType#STRING}) und
 * nicht als Ordnungszahl: Die Checks der Migration nennen die Werte im Klartext, und eine neue
 * Herkunft darf die Bedeutung der bestehenden Zeilen nicht verschieben.
 */
@Entity
@Table(name = "vorgang_eintrag")
class VorgangEintragEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "vorgang_id", nullable = false)
  private long vorgangId;

  @Enumerated(EnumType.STRING)
  @Column(name = "art", nullable = false, length = 20)
  private Eintragsart art;

  @Column(name = "text")
  private @Nullable String text;

  @Column(name = "geschehen_am", nullable = false)
  private Instant geschehenAm;

  @Enumerated(EnumType.STRING)
  @Column(name = "herkunft", nullable = false, length = 20)
  private Herkunft herkunft;

  @Column(name = "datei_name", length = 255)
  private @Nullable String dateiName;

  @Column(name = "datei_groesse")
  private @Nullable Long dateiGroesse;

  @Column(name = "objekt_schluessel", length = 300)
  private @Nullable String objektSchluessel;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "geaendert_am")
  private @Nullable Instant geaendertAm;

  /*
   * JPA verlangt einen parameterlosen Konstruktor und fuellt die Felder danach selbst; NullAway
   * sieht diesen Weg nicht und meldete sonst nicht initialisierte Felder.
   */
  @SuppressWarnings("NullAway.Init")
  protected VorgangEintragEntity() {
    // Von Hibernate benutzt.
  }

  /*
   * Elf Spalten ergeben elf Parameter — dieselbe Lage wie bei FirmaEntity: Die Zeile ist die Zeile
   * der Tabelle, und genau die uebersetzt JpaEintragRepository in beide Richtungen.
   */
  @SuppressWarnings("PMD.ExcessiveParameterList")
  VorgangEintragEntity(
      final @Nullable Long id,
      final long vorgangId,
      final Eintragsart art,
      final @Nullable String text,
      final Instant geschehenAm,
      final Herkunft herkunft,
      final @Nullable String dateiName,
      final @Nullable Long dateiGroesse,
      final @Nullable String objektSchluessel,
      final Instant createdAt,
      final @Nullable Instant geaendertAm) {
    this.id = id;
    this.vorgangId = vorgangId;
    this.art = art;
    this.text = text;
    this.geschehenAm = geschehenAm;
    this.herkunft = herkunft;
    this.dateiName = dateiName;
    this.dateiGroesse = dateiGroesse;
    this.objektSchluessel = objektSchluessel;
    this.createdAt = createdAt;
    this.geaendertAm = geaendertAm;
  }

  @Nullable Long getId() {
    return id;
  }

  long getVorgangId() {
    return vorgangId;
  }

  Eintragsart getArt() {
    return art;
  }

  @Nullable String getText() {
    return text;
  }

  Instant getGeschehenAm() {
    return geschehenAm;
  }

  Herkunft getHerkunft() {
    return herkunft;
  }

  @Nullable String getDateiName() {
    return dateiName;
  }

  @Nullable Long getDateiGroesse() {
    return dateiGroesse;
  }

  @Nullable String getObjektSchluessel() {
    return objektSchluessel;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  @Nullable Instant getGeaendertAm() {
    return geaendertAm;
  }
}
