package org.mwolff.fbcrm.vorgang.web;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.vorgang.application.EintragAnsicht;
import org.mwolff.fbcrm.vorgang.domain.Eintragsart;
import org.mwolff.fbcrm.vorgang.domain.Herkunft;

/**
 * Ein Eintrag der Historie, so wie die Detailansicht ihn zeigt (Kriterien 15, 16, 19).
 *
 * <p>Der Objektschluessel der Datei steht hier nicht — er ist die interne Adresse im Objektspeicher
 * (E9) und kommt schon aus der Anwendungsschicht nicht heraus.
 *
 * @param id technische Id des Eintrags
 * @param art Kommentar oder Anhang
 * @param text der Text, oder {@code null}
 * @param geschehenAm Zeitpunkt des Geschehens
 * @param herkunft woher der Eintrag stammt (Kriterium 16)
 * @param dateiName Dateiname — nur beim Anhang gesetzt
 * @param dateiGroesse Groesse in Byte — nur beim Anhang gesetzt
 * @param geaendertAm Zeitpunkt der letzten Aenderung, oder {@code null} (Kriterium 19)
 */
public record EintragResponse(
    long id,
    Eintragsart art,
    @Nullable String text,
    Instant geschehenAm,
    Herkunft herkunft,
    @Nullable String dateiName,
    @Nullable Long dateiGroesse,
    @Nullable Instant geaendertAm) {

  /** Die Sicht der Oberflaeche auf einen Eintrag der Historie. */
  static EintragResponse of(final EintragAnsicht eintrag) {
    return new EintragResponse(
        eintrag.id(),
        eintrag.art(),
        eintrag.text(),
        eintrag.geschehenAm(),
        eintrag.herkunft(),
        eintrag.dateiName(),
        eintrag.dateiGroesse(),
        eintrag.geaendertAm());
  }
}
