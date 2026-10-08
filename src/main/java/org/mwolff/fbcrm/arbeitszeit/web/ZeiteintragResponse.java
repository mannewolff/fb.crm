package org.mwolff.fbcrm.arbeitszeit.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import org.mwolff.fbcrm.arbeitszeit.domain.Zeiteintrag;

/**
 * Ein Zeiteintrag, wie {@code POST} und {@code PUT} ihn zurueckgeben.
 *
 * <p>Die Kennung entsteht erst beim Speichern, und die Dauer wird gerechnet (Plan #194, A3, E5) —
 * beides kann der Absender nicht wissen, und darum antworten beide Wege mit einem Rumpf.
 *
 * <p>Ohne die Zeitstempel: Die Ansicht zeigt den Tag der Arbeit, nicht den der Erfassung.
 *
 * @param id technische Kennung des Eintrags
 * @param angebotPositionId Kennung der Position, auf die gebucht wurde
 * @param tag der Tag, an dem gearbeitet wurde
 * @param von Beginn
 * @param bis Ende
 * @param stunden die gerechnete Dauer in Stunden mit zwei Nachkommastellen
 */
public record ZeiteintragResponse(
    long id,
    long angebotPositionId,
    LocalDate tag,
    LocalTime von,
    LocalTime bis,
    BigDecimal stunden) {

  /** Derselbe Eintrag in der Sprache der Schnittstelle. */
  static ZeiteintragResponse of(final Zeiteintrag eintrag) {
    return new ZeiteintragResponse(
        eintrag.requireId(),
        eintrag.angebotPositionId(),
        eintrag.tag(),
        eintrag.von(),
        eintrag.bis(),
        eintrag.stunden());
  }
}
