package org.mwolff.fbcrm.angebot.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.application.Positionsangabe;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;

/**
 * Eine Position, wie die Maske des Angebots sie einreicht (Issue #127, Kriterium 1).
 *
 * <p><b>Kein Platz und kein Betrag.</b> Die Reihenfolge ist die der Liste (E24), und der Betrag
 * wird gerechnet (E5); beide als Feld hiesse, dem Absender eine Aussage zu glauben, die die
 * Anwendung selbst kennt.
 *
 * <p><b>Die Kennung ist freiwillig</b> (Plan #169, E2). Wer sie mitschickt, sagt „dieselbe Position
 * wie vorher"; wer sie weglaesst, legt eine neue an. Sie darf nur eine Position des Angebots
 * benennen, das geaendert wird — das prueft {@code AngebotAendernUseCase} vor dem Schreiben und
 * antwortet sonst 422. Die Maske schickt sie noch nicht mit (Issue #172).
 *
 * <p>Jede Position braucht eine Bezeichnung (Kriterium 9); die Regel steht hier an der
 * Schnittstelle und nicht in der Datenbank, damit die Meldung am Feld der Position erscheint. Menge
 * und Einzelpreis duerfen nicht negativ sein und tragen hoechstens zwei Nachkommastellen —
 * dieselben Grenzen wie {@code numeric(12,2)} in {@code V6__angebot.sql}; ohne sie antwortete die
 * Anwendung auf eine dritte Nachkommastelle mit einem stillen Rundungsfehler oder einem
 * Datenbankfehler.
 *
 * <p><b>Menge, Einheit, Preis und Abrechnungsart sind hier nicht mehr pflichtig</b> (Issue #227,
 * E7). Ob sie es sind, haengt an der Art des Angebots — ein Angebot an einen Kunden braucht alle
 * vier, die interne Arbeit keine davon —, und das ist eine Aussage ueber das Nachbarfeld {@code
 * intern}, das die Bean-Validation nicht sieht. Die Pflicht entscheidet darum {@code
 * AngebotAendernUseCase} nach der Zielart und antwortet 422 mit demselben Feldnamen, den die
 * Bean-Validation verwendet. {@code @DecimalMin} und {@code @Digits} bleiben: Sie gelten fuer einen
 * <em>vorhandenen</em> Wert und gehen an {@code null} vorbei.
 *
 * @param id Kennung der Position, die fortgeschrieben werden soll, oder {@code null} fuer eine neue
 * @param bezeichnung die Leistung; nicht leer
 * @param abrechnungsmodus nach Aufwand oder zum Festpreis, oder {@code null}
 * @param menge Menge in der angegebenen Einheit, nicht negativ, oder {@code null}
 * @param einheit Einheit der Menge, oder {@code null}
 * @param einzelpreis Netto-Preis je Einheit, nicht negativ, oder {@code null}
 */
public record AngebotPositionRequest(
    @Nullable Long id,
    @NotBlank(message = BEZEICHNUNG_FEHLT) @Size(max = 300) String bezeichnung,
    @Nullable Abrechnungsmodus abrechnungsmodus,
    @Nullable @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal menge,
    @Nullable Einheit einheit,
    @Nullable @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal einzelpreis) {

  /** Die Meldung am Feld, wenn die Bezeichnung fehlt oder nur aus Leerzeichen besteht. */
  static final String BEZEICHNUNG_FEHLT = "Jede Position braucht eine Bezeichnung.";

  /** Dieselbe Position als Angabe in der Sprache der Anwendungsschicht. */
  Positionsangabe angabe() {
    return new Positionsangabe(id, bezeichnung, abrechnungsmodus, menge, einheit, einzelpreis);
  }
}
