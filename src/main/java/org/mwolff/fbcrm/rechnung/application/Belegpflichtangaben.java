package org.mwolff.fbcrm.rechnung.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;
import org.mwolff.fbcrm.firma.domain.Firma;

/**
 * Was auf einem Beleg stehen muss, bevor er hinausgehen darf (#160, Kriterium 13).
 *
 * <p>Eine reine Pruefung ohne Bestand: Sie bekommt die eigenen Angaben und die Firma und sammelt,
 * was fehlt. Gesammelt und nicht beim ersten Fund abgebrochen — wer fuenf Angaben nachtragen muss,
 * soll es in einem Gang tun koennen ({@link PflichtangabenFehlen}).
 *
 * <p><b>Steuernummer oder Umsatzsteuer-Identifikationsnummer</b>, nicht beide: Welche von beiden
 * ein Freiberufler fuehrt, haengt an seiner Lage. Fehlen beide, steht die Meldung unter {@code
 * steuernummer} — die Maske fuehrt sie dort als erstes der zwei Felder.
 *
 * <p>Leer zaehlt wie fehlend. Die eigenen Angaben halten eine fehlende Angabe als {@code null}
 * (E9), die Firma einen Namen als Pflichtspalte; ein Name aus Leerzeichen ist dort trotzdem
 * moeglich und auf einem Beleg kein Name.
 */
final class Belegpflichtangaben {

  /** Was der Anwender an einem fehlenden Feld liest. */
  static final String ANGABE_FEHLT = "Diese Angabe ist fuer eine Rechnung noetig.";

  /** Was er liest, wenn weder Steuernummer noch Umsatzsteuer-Identifikationsnummer steht. */
  static final String STEUERANGABE_FEHLT =
      "Fuer eine Rechnung ist die Steuernummer oder die Umsatzsteuer-Identifikationsnummer noetig.";

  private Belegpflichtangaben() {}

  /**
   * Prueft die Angaben und wirft, wenn eine Pflichtangabe fehlt.
   *
   * @param eigene die eigenen Angaben, wie sie jetzt gelten
   * @param firma die Firma, an die die Rechnung geht
   * @throws PflichtangabenFehlen wenn mindestens eine Pflichtangabe fehlt; die Ausnahme nennt alle
   */
  static void pruefe(final EigeneAngaben eigene, final Firma firma) {
    final Map<String, List<String>> fehlend = new LinkedHashMap<>();
    final Anschrift meine = eigene.anschrift();
    sammle(fehlend, "name", eigene.name());
    sammle(fehlend, "strasse", meine.strasse());
    sammle(fehlend, "plz", meine.plz());
    sammle(fehlend, "ort", meine.ort());
    sammle(fehlend, "bankverbindung", eigene.bankverbindung());
    if (fehlt(eigene.steuernummer()) && fehlt(eigene.umsatzsteuerId())) {
      fehlend.put("steuernummer", List.of(STEUERANGABE_FEHLT));
    }
    final Anschrift ihre = firma.anschrift();
    sammle(fehlend, "firma.name", firma.name());
    sammle(fehlend, "firma.strasse", ihre.strasse());
    sammle(fehlend, "firma.plz", ihre.plz());
    sammle(fehlend, "firma.ort", ihre.ort());
    if (!fehlend.isEmpty()) {
      throw new PflichtangabenFehlen(fehlend);
    }
  }

  private static void sammle(
      final Map<String, List<String>> fehlend, final String feld, final @Nullable String wert) {
    if (fehlt(wert)) {
      fehlend.put(feld, List.of(ANGABE_FEHLT));
    }
  }

  private static boolean fehlt(final @Nullable String wert) {
    return wert == null || wert.isBlank();
  }
}
