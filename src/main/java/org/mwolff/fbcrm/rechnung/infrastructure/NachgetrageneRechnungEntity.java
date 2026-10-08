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
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnung;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;

/**
 * Die Zeile der Tabelle {@code rechnung_nachgetragen} nach {@code V22__rechnung_nachgetragen.sql}.
 *
 * <p>Die Firma steht als blosse Kennung und nicht als Beziehung, aus demselben Grund wie das
 * Angebot an {@link RechnungEntity}: Firma und Rechnung sind eigene Wurzeln, und eine Beziehung
 * luede das eine mit dem anderen, ohne dass es gebraucht wuerde.
 *
 * <p>{@code zustand} geht als Text in die Datenbank ({@link EnumType#STRING}): Der CHECK der
 * Migration nennt die Werte im Klartext.
 */
@Entity
@Table(name = "rechnung_nachgetragen")
class NachgetrageneRechnungEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "firma_id", nullable = false)
  private long firmaId;

  @Column(name = "nummer", nullable = false, length = 50)
  private String nummer;

  @Column(name = "rechnung_datum", nullable = false)
  private LocalDate rechnungDatum;

  @Column(name = "netto", nullable = false, precision = 12, scale = 2)
  private BigDecimal netto;

  @Column(name = "brutto", nullable = false, precision = 12, scale = 2)
  private BigDecimal brutto;

  @Enumerated(EnumType.STRING)
  @Column(name = "zustand", nullable = false, length = 20)
  private Rechnungszustand zustand;

  @Column(name = "pdf_schluessel", length = 300)
  private @Nullable String pdfSchluessel;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /*
   * JPA verlangt einen parameterlosen Konstruktor und fuellt die Felder danach selbst; NullAway
   * sieht diesen Weg nicht und meldete sonst nicht initialisierte Felder.
   */
  @SuppressWarnings("NullAway.Init")
  protected NachgetrageneRechnungEntity() {
    // Von Hibernate benutzt.
  }

  /**
   * Die Zeile zu einer nachgetragenen Rechnung — jedes Feld kommt aus dem Fachobjekt.
   *
   * @param rechnung die Rechnung, deren Stand die Zeile tragen soll
   * @return die Zeile; ihre Kennung ist die der Rechnung und damit {@code null}, solange die
   *     Rechnung noch nicht geschrieben wurde
   */
  static NachgetrageneRechnungEntity aus(final NachgetrageneRechnung rechnung) {
    return new NachgetrageneRechnungEntity(rechnung);
  }

  private NachgetrageneRechnungEntity(final NachgetrageneRechnung rechnung) {
    this.id = rechnung.id();
    this.firmaId = rechnung.firmaId();
    this.nummer = rechnung.nummer();
    this.rechnungDatum = rechnung.rechnungDatum();
    this.netto = rechnung.netto();
    this.brutto = rechnung.brutto();
    this.zustand = rechnung.zustand();
    this.pdfSchluessel = rechnung.pdfSchluessel();
    this.createdAt = rechnung.createdAt();
    this.updatedAt = rechnung.updatedAt();
  }

  @Nullable Long getId() {
    return id;
  }

  long getFirmaId() {
    return firmaId;
  }

  String getNummer() {
    return nummer;
  }

  LocalDate getRechnungDatum() {
    return rechnungDatum;
  }

  BigDecimal getNetto() {
    return netto;
  }

  BigDecimal getBrutto() {
    return brutto;
  }

  Rechnungszustand getZustand() {
    return zustand;
  }

  @Nullable String getPdfSchluessel() {
    return pdfSchluessel;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }
}
