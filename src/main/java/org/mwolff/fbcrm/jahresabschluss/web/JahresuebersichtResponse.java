package org.mwolff.fbcrm.jahresabschluss.web;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.jahresabschluss.application.Jahreszeile;

/**
 * Ein Jahr der Uebersicht „Jahresabschluesse", wie die Ansicht es zeigt (#287, Kriterien 1 und 3;
 * Plan #288, E2).
 *
 * <p>Die drei Hauptzahlen und nichts darueber hinaus: Kundenliste, Steuerzeilen und Arbeitszeit
 * stehen allein im Abschluss des Jahres ({@link JahresabschlussResponse}).
 *
 * <p><b>Das Jahr steht als Text darin</b>, wie die Jahre der Startseite: genau das, was die Ansicht
 * in die Adresse des Abschlusses setzt; ein {@code Year} schriebe Jackson als Zahl.
 *
 * @param jahr das Kalenderjahr als {@code JJJJ}
 * @param laeuftNoch ob das Jahr das laufende ist (#287, Kriterium 1)
 * @param netto die Einnahmen netto, auf den Cent
 * @param anzahl die Zahl der gestellten Rechnungen des Jahres
 * @param annahmequote angenommene durch abgegebene Angebote in Prozent, eine Nachkommastelle;
 *     {@code null} ohne abgegebenes Angebot (Kriterium 11)
 */
public record JahresuebersichtResponse(
    String jahr,
    boolean laeuftNoch,
    BigDecimal netto,
    int anzahl,
    @Nullable BigDecimal annahmequote) {

  /** Dieselbe Zeile in der Sprache der Schnittstelle. */
  static JahresuebersichtResponse of(final Jahreszeile zeile) {
    return new JahresuebersichtResponse(
        String.valueOf(zeile.jahr()),
        zeile.laeuftNoch(),
        zeile.einnahmenNetto(),
        zeile.anzahlRechnungen(),
        zeile.annahmequote());
  }
}
