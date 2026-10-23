package org.mwolff.fbcrm.arbeitszeit.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.arbeitszeit.domain.Zeiteintrag;

/**
 * Die Zeile der Tabelle {@code arbeitszeit} aus {@code V19__arbeitszeit.sql}, ergaenzt um V32 und
 * um Version und Urheber aus {@code V33__version.sql}.
 *
 * <p>Die Angebotsposition steht als blosse Kennung und nicht als Beziehung, aus demselben Grund wie
 * das Angebot in {@code AngebotKommentarEntity}: Eine Beziehung laedt das Angebot mit jeder
 * erfassten Viertelstunde, ohne dass es gebraucht wuerde. Dasselbe gilt fuer das Konto, dem der
 * Eintrag gehoert (Plan #426, E1).
 *
 * <p><b>Die Zeile ist unveraenderlich</b> — anders als {@code RechnungPositionEntity} und aus
 * demselben Grund wie der Kommentar am Angebot: Ein Zeiteintrag ist eine Zeile fuer sich, und der
 * Adapter schreibt ihn als Ganzes. Eine Dauer traegt die Zeile nicht; sie wird gerechnet (Plan
 * #194, A3).
 */
@Entity
@Table(name = "arbeitszeit")
class ZeiteintragEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "account_id", nullable = false)
  private long accountId;

  @Column(name = "angebot_position_id", nullable = false)
  private long angebotPositionId;

  @Column(name = "tag", nullable = false)
  private LocalDate tag;

  @Column(name = "von", nullable = false)
  private LocalTime von;

  @Column(name = "bis", nullable = false)
  private LocalTime bis;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /*
   * Der Stand der Zeile, gefuehrt von Hibernate (Plan #415, E1) — wie in AngebotKommentarEntity:
   * Jedes Schreiben erhoeht ihn, und ein UPDATE auf einem anderen Stand als dem gelesenen
   * scheitert. Ein long und kein Long, damit weiter die Kennung entscheidet, ob die Zeile neu ist.
   */
  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "geaendert_von")
  private @Nullable Long geaendertVon;

  /*
   * JPA verlangt einen parameterlosen Konstruktor und fuellt die Felder danach selbst; NullAway
   * sieht diesen Weg nicht und meldete sonst nicht initialisierte Felder.
   */
  @SuppressWarnings("NullAway.Init")
  protected ZeiteintragEntity() {
    // Von Hibernate benutzt.
  }

  /**
   * Die Zeile zu einem Zeiteintrag — jedes Feld kommt aus dem Fachobjekt (Plan #426, E2), wie bei
   * {@code AccountEntity.aus}.
   *
   * <p>Mit der Person haette ein Konstruktor mit einzelnen Feldern acht Parameter und verstiesse
   * gegen die Grenze von sieben, die {@code ArchitectureTest} ohne Ausnahme fuer jede Entity zieht.
   * So nennt der Adapter am Aufruf nur, <i>was</i> abgebildet wird, und kein Aufruf kann Konto und
   * Position vertauschen.
   *
   * @param eintrag der Zeiteintrag, dessen Stand die Zeile tragen soll
   * @return die Zeile; ihre Kennung ist die des Eintrags und damit {@code null}, solange er noch
   *     nicht geschrieben wurde
   */
  static ZeiteintragEntity aus(final Zeiteintrag eintrag) {
    return new ZeiteintragEntity(eintrag);
  }

  private ZeiteintragEntity(final Zeiteintrag eintrag) {
    this.id = eintrag.id();
    this.accountId = eintrag.accountId();
    this.angebotPositionId = eintrag.angebotPositionId();
    this.tag = eintrag.tag();
    this.von = eintrag.von();
    this.bis = eintrag.bis();
    this.createdAt = eintrag.createdAt();
    this.updatedAt = eintrag.updatedAt();
    this.version = eintrag.version();
    this.geaendertVon = eintrag.geaendertVon();
  }

  @Nullable Long getId() {
    return id;
  }

  long getAccountId() {
    return accountId;
  }

  long getAngebotPositionId() {
    return angebotPositionId;
  }

  LocalDate getTag() {
    return tag;
  }

  LocalTime getVon() {
    return von;
  }

  LocalTime getBis() {
    return bis;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }

  long getVersion() {
    return version;
  }

  @Nullable Long getGeaendertVon() {
    return geaendertVon;
  }
}
