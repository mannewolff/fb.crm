package org.mwolff.fbcrm.angebot.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.domain.Angebotskommentar;

/**
 * Die Zeile der Tabelle {@code angebot_kommentar} aus {@code V12__angebot_kommentar.sql}, mit
 * Version und Urheber aus {@code V33__version.sql} und dem Loeschkennzeichen aus {@code
 * V34__geloescht.sql}.
 *
 * <p>Das Angebot steht als blosse Kennung und nicht als Beziehung, aus demselben Grund wie Firma
 * und Ansprechpartner in {@link AngebotEntity}: Eine Beziehung laedt das Angebot mit jedem
 * Kommentar, ohne dass es gebraucht wuerde. Ebenso der Verfasser aus {@code
 * V31__verfasser_am_kommentar.sql}: eine Kennung des Kontos, keine Beziehung in das Modul {@code
 * auth} (Plan #414, A7).
 *
 * <p>{@code updated_at} hat hier eine Spalte und einen Getter, aber keinen Leser ausserhalb der
 * Uebersetzung: Keine Antwort traegt den Aenderungszeitpunkt (Kriterium 9). Die Spalte folgt der
 * Konvention der Fachtabellen — die Begruendung steht im Kopf der Migration.
 */
@Entity
@Table(name = "angebot_kommentar")
class AngebotKommentarEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "angebot_id", nullable = false)
  private long angebotId;

  @Column(name = "verfasser_id", nullable = false)
  private long verfasserId;

  @Column(name = "text", nullable = false, columnDefinition = "text")
  private String text;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /*
   * Der Stand der Zeile, gefuehrt von Hibernate (Plan #415, E1, E8) — wie in AngebotEntity: Jedes
   * Schreiben erhoeht ihn, und ein UPDATE auf einem anderen Stand als dem gelesenen scheitert. Ein
   * long und kein Long, damit weiter die Kennung entscheidet, ob die Zeile neu ist.
   */
  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "geaendert_von")
  private @Nullable Long geaendertVon;

  @Column(name = "geloescht_am")
  private @Nullable Instant geloeschtAm;

  @Column(name = "geloescht_von")
  private @Nullable Long geloeschtVon;

  /*
   * JPA verlangt einen parameterlosen Konstruktor und fuellt die Felder danach selbst; NullAway
   * sieht diesen Weg nicht und meldete sonst nicht initialisierte Felder.
   */
  @SuppressWarnings("NullAway.Init")
  protected AngebotKommentarEntity() {
    // Von Hibernate benutzt.
  }

  /** Die Zeile zu einem Kommentar, mit allen Feldern. */
  AngebotKommentarEntity(final Angebotskommentar kommentar) {
    this.id = kommentar.id();
    this.angebotId = kommentar.angebotId();
    this.verfasserId = kommentar.verfasserId();
    this.text = kommentar.text();
    this.createdAt = kommentar.createdAt();
    this.updatedAt = kommentar.updatedAt();
    this.version = kommentar.version();
    this.geaendertVon = kommentar.geaendertVon();
    this.geloeschtAm = kommentar.geloeschtAm();
    this.geloeschtVon = kommentar.geloeschtVon();
  }

  @Nullable Long getId() {
    return id;
  }

  long getAngebotId() {
    return angebotId;
  }

  long getVerfasserId() {
    return verfasserId;
  }

  String getText() {
    return text;
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

  @Nullable Instant getGeloeschtAm() {
    return geloeschtAm;
  }

  @Nullable Long getGeloeschtVon() {
    return geloeschtVon;
  }
}
