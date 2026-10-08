package org.mwolff.fbcrm.angebot.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Die Zeile der Tabelle {@code angebot_kommentar} aus {@code V12__angebot_kommentar.sql}.
 *
 * <p>Das Angebot steht als blosse Kennung und nicht als Beziehung, aus demselben Grund wie Firma
 * und Ansprechpartner in {@link AngebotEntity}: Eine Beziehung laedt das Angebot mit jedem
 * Kommentar, ohne dass es gebraucht wuerde.
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

  @Column(name = "text", nullable = false, columnDefinition = "text")
  private String text;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /*
   * JPA verlangt einen parameterlosen Konstruktor und fuellt die Felder danach selbst; NullAway
   * sieht diesen Weg nicht und meldete sonst nicht initialisierte Felder.
   */
  @SuppressWarnings("NullAway.Init")
  protected AngebotKommentarEntity() {
    // Von Hibernate benutzt.
  }

  AngebotKommentarEntity(
      final @Nullable Long id,
      final long angebotId,
      final String text,
      final Instant createdAt,
      final Instant updatedAt) {
    this.id = id;
    this.angebotId = angebotId;
    this.text = text;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  @Nullable Long getId() {
    return id;
  }

  long getAngebotId() {
    return angebotId;
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
}
