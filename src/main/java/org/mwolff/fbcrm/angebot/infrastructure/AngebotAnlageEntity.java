package org.mwolff.fbcrm.angebot.infrastructure;

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
import org.mwolff.fbcrm.angebot.domain.Vorschauart;

/**
 * Die Zeile der Tabelle {@code angebot_anlage} aus {@code V13__angebot_anlage.sql}.
 *
 * <p>Das Angebot steht als blosse Kennung und nicht als Beziehung, aus demselben Grund wie in
 * {@link AngebotKommentarEntity}: Eine Beziehung laedt das Angebot mit jeder Anlage, ohne dass es
 * gebraucht wuerde.
 *
 * <p>{@code vorschau_art} geht als Text in die Spalte ({@link EnumType#STRING}) — dieselben fuenf
 * Werte nennt der CHECK der Migration. {@code null} heisst „keine Vorschau"; eine Tabelle ist eine
 * gueltige Anlage ohne Vorschau.
 *
 * <p>Ein {@code updated_at} gibt es nicht: Eine Anlage aendert sich nie. Die Begruendung steht im
 * Kopf der Migration.
 */
@Entity
@Table(name = "angebot_anlage")
class AngebotAnlageEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "angebot_id", nullable = false)
  private long angebotId;

  @Column(name = "datei_name", nullable = false, length = 255)
  private String dateiName;

  @Column(name = "groesse", nullable = false)
  private long groesse;

  @Enumerated(EnumType.STRING)
  @Column(name = "vorschau_art", length = 4)
  private @Nullable Vorschauart vorschauArt;

  @Column(name = "objekt_schluessel", nullable = false, columnDefinition = "text")
  private String objektSchluessel;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /*
   * JPA verlangt einen parameterlosen Konstruktor und fuellt die Felder danach selbst; NullAway
   * sieht diesen Weg nicht und meldete sonst nicht initialisierte Felder.
   */
  @SuppressWarnings("NullAway.Init")
  protected AngebotAnlageEntity() {
    // Von Hibernate benutzt.
  }

  AngebotAnlageEntity(
      final @Nullable Long id,
      final long angebotId,
      final String dateiName,
      final long groesse,
      final @Nullable Vorschauart vorschauArt,
      final String objektSchluessel,
      final Instant createdAt) {
    this.id = id;
    this.angebotId = angebotId;
    this.dateiName = dateiName;
    this.groesse = groesse;
    this.vorschauArt = vorschauArt;
    this.objektSchluessel = objektSchluessel;
    this.createdAt = createdAt;
  }

  @Nullable Long getId() {
    return id;
  }

  long getAngebotId() {
    return angebotId;
  }

  String getDateiName() {
    return dateiName;
  }

  long getGroesse() {
    return groesse;
  }

  @Nullable Vorschauart getVorschauArt() {
    return vorschauArt;
  }

  String getObjektSchluessel() {
    return objektSchluessel;
  }

  Instant getCreatedAt() {
    return createdAt;
  }
}
