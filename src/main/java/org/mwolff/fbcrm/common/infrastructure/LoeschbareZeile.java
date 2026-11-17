package org.mwolff.fbcrm.common.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Eine {@link GefuehrteZeile}, die zusaetzlich das Kennzeichen der Loeschung aus {@code
 * V34__geloescht.sql} traegt (Issue #489).
 */
@MappedSuperclass
public abstract class LoeschbareZeile extends GefuehrteZeile {

  /* Das Kennzeichen der Loeschung (Plan #415, E17): NULL heisst „nicht geloescht" (V34). */
  @Column(name = "geloescht_am")
  private @Nullable Instant geloeschtAm;

  @Column(name = "geloescht_von")
  private @Nullable Long geloeschtVon;

  /** Von Hibernate benutzt; JPA fuellt die Felder danach selbst. */
  protected LoeschbareZeile() {
    super();
  }

  /**
   * Die Verwaltungswerte einer loeschbaren Zeile, wie sie das Fachobjekt traegt.
   *
   * @param createdAt Zeitpunkt der Anlage
   * @param updatedAt Zeitpunkt der letzten Aenderung
   * @param version der Stand, auf dem das Fachobjekt beruht
   * @param geaendertVon das Konto, das zuletzt geaendert hat, oder {@code null}
   * @param geloeschtAm Zeitpunkt der Loeschung, oder {@code null}, solange nicht geloescht
   * @param geloeschtVon das Konto, das geloescht hat, oder {@code null}
   */
  protected LoeschbareZeile(
      final Instant createdAt,
      final Instant updatedAt,
      final long version,
      final @Nullable Long geaendertVon,
      final @Nullable Instant geloeschtAm,
      final @Nullable Long geloeschtVon) {
    super(createdAt, updatedAt, version, geaendertVon);
    this.geloeschtAm = geloeschtAm;
    this.geloeschtVon = geloeschtVon;
  }

  /**
   * Zeitpunkt der Loeschung.
   *
   * @return der Zeitpunkt, oder {@code null}, solange die Zeile nicht geloescht ist
   */
  public @Nullable Instant getGeloeschtAm() {
    return geloeschtAm;
  }

  /**
   * Das Konto, das geloescht hat.
   *
   * @return die Kennung des Kontos, oder {@code null}
   */
  public @Nullable Long getGeloeschtVon() {
    return geloeschtVon;
  }
}
