package org.mwolff.fbcrm.vorgang.web;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.vorgang.application.VorgangMitHistorie;
import org.mwolff.fbcrm.vorgang.domain.Phase;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;

/**
 * Ein Vorgang mit seiner Zuordnung und seiner vollstaendigen Historie (Kriterien 9, 11, 15, 23).
 *
 * <p>Alles in einem Stueck und ohne Seitenteilung (E25): Die Historie ist die Detailansicht, und
 * zwei Wege bedeuteten zwei Ladezustaende fuer eine Ansicht.
 *
 * <p>Die Zeitstempel des Vorgangs fehlen bewusst — die Ansicht zeigt sie nicht, und die Historie
 * des Vorgangs sind seine Eintraege, nicht sein {@code updated_at}.
 *
 * @param id technische Id
 * @param nummer fortlaufende Vorgangsnummer; als Zahl, das {@code #} setzt die Oberflaeche
 * @param titel Titel des Vorgangs
 * @param phase abgeleitete Phase — in diesem Stand immer {@link Phase#ANBAHNUNG} (Kriterium 11)
 * @param abgeschlossen {@code true}, solange der Vorgang abgeschlossen ist (Kriterium 9)
 * @param firma die zugeordnete Firma, mit Kennung, Namen und Stilllegungsstand
 * @param ansprechpartner der zugeordnete Ansprechpartner, oder {@code null}
 * @param historie alle Eintraege, juengstes Geschehen oben (Kriterium 15)
 */
public record VorgangResponse(
    long id,
    long nummer,
    String titel,
    Phase phase,
    boolean abgeschlossen,
    ZuordnungResponse firma,
    @Nullable ZuordnungResponse ansprechpartner,
    List<EintragResponse> historie) {

  /** Die Sicht der Oberflaeche auf einen Vorgang samt seiner Historie. */
  static VorgangResponse of(final VorgangMitHistorie gelesen) {
    final Vorgang vorgang = gelesen.vorgang();
    final Ansprechpartner partner = gelesen.ansprechpartner();
    return new VorgangResponse(
        vorgang.requireId(),
        vorgang.nummer(),
        vorgang.titel(),
        vorgang.phase(),
        vorgang.abgeschlossen(),
        ZuordnungResponse.of(gelesen.firma()),
        partner == null ? null : ZuordnungResponse.of(partner),
        gelesen.historie().stream().map(EintragResponse::of).toList());
  }
}
