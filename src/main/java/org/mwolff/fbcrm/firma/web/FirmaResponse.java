package org.mwolff.fbcrm.firma.web;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.firma.application.FirmaMitAnsprechpartnern;
import org.mwolff.fbcrm.firma.domain.Firma;

/**
 * Eine Firma mit allen ihren Angaben und ihren Ansprechpartnern.
 *
 * <p>Die Anschrift steht flach und nicht geschachtelt: Die Maske hat fuer jede ihrer Angaben ein
 * eigenes Feld, und eine Schachtelung braechte der Oberflaeche nur eine Ebene mehr.
 *
 * <p>Die Zeitstempel fehlen bewusst — die Detailansicht zeigt sie nicht, und eine Historie ist
 * ausdruecklich Nicht-Ziel (E3).
 *
 * @param id technische Id
 * @param name Name der Firma
 * @param strasse Strasse und Hausnummer, oder {@code null}
 * @param plz Postleitzahl, oder {@code null}
 * @param ort Ort, oder {@code null}
 * @param land Land, oder {@code null}
 * @param steuernummer Steuernummer, oder {@code null}
 * @param umsatzsteuerId Umsatzsteuer-Identifikationsnummer, oder {@code null}
 * @param aktiv {@code false}, solange die Firma stillgelegt ist
 * @param ansprechpartner aktive und stillgelegte Ansprechpartner gemeinsam, nach Nachnamen sortiert
 */
public record FirmaResponse(
    long id,
    String name,
    @Nullable String strasse,
    @Nullable String plz,
    @Nullable String ort,
    @Nullable String land,
    @Nullable String steuernummer,
    @Nullable String umsatzsteuerId,
    boolean aktiv,
    List<AnsprechpartnerResponse> ansprechpartner) {

  /** Die Sicht der Oberflaeche auf eine Firma samt ihrer Ansprechpartner. */
  static FirmaResponse of(final FirmaMitAnsprechpartnern gelesen) {
    return of(
        gelesen.firma(),
        gelesen.ansprechpartner().stream().map(AnsprechpartnerResponse::of).toList());
  }

  /**
   * Die Sicht der Oberflaeche auf eine Firma ohne Ansprechpartner.
   *
   * <p>Fuer die frisch angelegte Firma: Sie hat noch keine, und eine leere Liste ist hier die
   * Wahrheit und kein Platzhalter.
   */
  static FirmaResponse ohneAnsprechpartner(final Firma firma) {
    return of(firma, List.of());
  }

  private static FirmaResponse of(
      final Firma firma, final List<AnsprechpartnerResponse> ansprechpartner) {
    return new FirmaResponse(
        firma.requireId(),
        firma.name(),
        firma.anschrift().strasse(),
        firma.anschrift().plz(),
        firma.anschrift().ort(),
        firma.anschrift().land(),
        firma.steuernummer(),
        firma.umsatzsteuerId(),
        firma.aktiv(),
        ansprechpartner);
  }
}
