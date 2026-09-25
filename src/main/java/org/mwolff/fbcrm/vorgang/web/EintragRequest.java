package org.mwolff.fbcrm.vorgang.web;

import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.vorgang.application.EintragDaten;
import org.mwolff.fbcrm.vorgang.domain.Dateiname;
import org.mwolff.fbcrm.vorgang.domain.Eintragsart;
import org.springframework.web.multipart.MultipartFile;

/**
 * Die Eingaben der Eintragsmaske — als Formulardaten, weil eine Datei dabei sein kann (E21).
 *
 * <p><b>Kein Feld fuer die Herkunft.</b> In diesem Stand entsteht jeder Eintrag von Hand (Kriterium
 * 16); ein Feld dafuer waere eine Angabe, die der Aufrufer faelschen koennte.
 *
 * <p>Was je nach Art Pflicht ist, steht in {@link EintragConstraint} und nicht an den einzelnen
 * Komponenten: Ob der Text fehlen darf, haengt an {@code art}, und eine Feldannotation kann kein
 * zweites Feld lesen.
 *
 * @param art Kommentar oder Anhang
 * @param text der Text; beim Kommentar Pflicht, beim Anhang die Beschreibung
 * @param geschehenAm Zeitpunkt des Geschehens — nicht der der Erfassung
 * @param datei die hochgeladene Datei; nur beim Anhang
 */
@EintragConstraint
public record EintragRequest(
    @NotNull Eintragsart art,
    @Nullable String text,
    @NotNull Instant geschehenAm,
    @Nullable MultipartFile datei) {

  /**
   * Dieselben Angaben in der Sprache der Anwendungsschicht.
   *
   * <p>Der Dateiname wird hier gesaeubert (E13) — am Rand, an dem die Eingabe ankommt. Die Datei
   * zaehlt nur, wenn die Art sie vorsieht: Eine Datei an einem Kommentar waere eine Angabe, die
   * niemand angefordert hat, und wuerde stillschweigend zum Anhang.
   *
   * @throws IOException wenn sich der Datenstrom der hochgeladenen Datei nicht oeffnen laesst
   */
  public EintragDaten daten() throws IOException {
    final MultipartFile hochgeladen = art == Eintragsart.ANHANG ? datei : null;
    if (hochgeladen == null) {
      return new EintragDaten(art, text, geschehenAm, null, 0L, null);
    }
    return new EintragDaten(
        art,
        text,
        geschehenAm,
        gesaeuberterName(hochgeladen),
        hochgeladen.getSize(),
        hochgeladen.getInputStream());
  }

  private static @Nullable String gesaeuberterName(final MultipartFile datei) {
    return Dateiname.gesaeubert(Objects.requireNonNullElse(datei.getOriginalFilename(), ""))
        .orElse(null);
  }
}
