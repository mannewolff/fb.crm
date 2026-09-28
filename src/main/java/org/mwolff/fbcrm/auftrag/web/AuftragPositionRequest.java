package org.mwolff.fbcrm.auftrag.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.auftrag.application.AuftragPositionwahl;

/**
 * Eine uebernommene Angebotsposition, wie die Auftragsmaske sie einreicht (Kriterium 2, Plan E7).
 *
 * <p><b>Kein Preisfeld, keine Bezeichnung, kein Abrechnungsmodus, keine Einheit.</b> Alle vier
 * liest der Anwendungsfall aus dem Angebot; sie hier zu fuehren hiesse, dem Absender genau die
 * Angaben zu glauben, die Kriterium 2 schuetzt. Uebrig bleiben drei: welche Position gemeint ist,
 * wie viel davon beauftragt wird, und der Umrechnungsfaktor der Zeiterfassung.
 *
 * <p>{@code platz} ist ein {@code int} und kein {@code Integer}: Eine fehlende Angabe bindet damit
 * auf 0 und faellt durch {@link Min} — eine zweite Meldung aus {@code @NotNull} fuer dasselbe leere
 * Feld hilft niemandem.
 *
 * <p>Die Grenzen der Zahlen sind die des Schemas: {@code menge} wie {@code numeric(12,2)}, {@code
 * stundenJePersonentag} wie {@code numeric(4,2)} und dort ueber null (E10). Ohne sie antwortete die
 * Anwendung auf eine dritte Nachkommastelle mit einem stillen Rundungsfehler oder einem
 * Datenbankfehler.
 *
 * @param platz Platz der Position im Angebot — {@code index + 1} der Liste in {@code
 *     AngebotResponse.positionen}
 * @param menge die vereinbarte Menge, nicht negativ
 * @param stundenJePersonentag Stunden je Personentag, positiv — oder {@code null}, wenn die
 *     Position im Angebot nicht nach Aufwand abgerechnet wird
 */
public record AuftragPositionRequest(
    @Min(1) int platz,
    @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal menge,
    @DecimalMin(value = "0", inclusive = false) @Digits(integer = 2, fraction = 2)
        @Nullable BigDecimal stundenJePersonentag) {

  /** Dieselbe Wahl in der Sprache der Fachschicht. */
  AuftragPositionwahl wahl() {
    return new AuftragPositionwahl(platz, menge, stundenJePersonentag);
  }
}
