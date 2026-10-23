package org.mwolff.fbcrm.rechnung.web;

import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.rechnung.application.Briefbogenstand;

/**
 * Der Stand des Briefbogens, wie die Maske ihn sieht (fachliche Quelle #361, Kriterien 1 und 15).
 *
 * <p>{@code briefpapier} sagt nur, <b>ob</b> eines hinterlegt ist; der Schluessel im Objektspeicher
 * geht nicht nach aussen. Die Raender stehen flach neben den Schaltern und in Zehntel-Millimetern,
 * so wie {@link BriefbogenRequest} sie nimmt.
 *
 * @param briefpapier ob ein Briefpapier hinterlegt ist
 * @param dateiname Name der hochgeladenen Datei, ohne Briefpapier leer
 * @param groesse Groesse in Byte, ohne Briefpapier leer
 * @param seiten Seitenzahl, ohne Briefpapier leer
 * @param absenderzeile ob die Absenderzeile eingedruckt wird
 * @param steuerBankZeile ob die Steuer- und Bankzeile eingedruckt wird
 * @param links linker Rand
 * @param rechts rechter Rand
 * @param inhaltOben Beginn des Inhalts auf Seite 1
 * @param unten unterer Rand auf Seite 1
 * @param folgeOben oberer Rand auf Folgeseiten
 * @param folgeUnten unterer Rand auf Folgeseiten
 * @param version der Stand der ganzen Zeile, den die Maske bei jedem der drei schreibenden Wege als
 *     {@code If-Match} vorlegt (Plan #415, E2, E11)
 */
public record BriefbogenResponse(
    boolean briefpapier,
    @Nullable String dateiname,
    @Nullable Long groesse,
    @Nullable Integer seiten,
    boolean absenderzeile,
    boolean steuerBankZeile,
    int links,
    int rechts,
    int inhaltOben,
    int unten,
    int folgeOben,
    int folgeUnten,
    long version) {

  /** Die Sicht der Oberflaeche auf den Stand. */
  static BriefbogenResponse of(final Briefbogenstand stand) {
    return new BriefbogenResponse(
        stand.briefpapier(),
        stand.dateiname(),
        stand.groesse(),
        stand.seiten(),
        stand.absenderzeile(),
        stand.steuerBankZeile(),
        stand.raender().links(),
        stand.raender().rechts(),
        stand.raender().inhaltOben(),
        stand.raender().unten(),
        stand.raender().folgeOben(),
        stand.raender().folgeUnten(),
        stand.version());
  }
}
