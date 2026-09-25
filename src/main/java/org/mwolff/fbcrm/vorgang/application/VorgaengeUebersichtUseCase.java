package org.mwolff.fbcrm.vorgang.application;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.vorgang.domain.EintragRepository;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;
import org.mwolff.fbcrm.vorgang.domain.VorgangRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Uebersicht der Vorgaenge mit Suchtext und Schalter (Kriterien 2, 3, 20).
 *
 * <p>Gefiltert und sortiert wird im Bestand, nicht hier und nicht im Browser (E16): Die Zahl der
 * Vorgaenge ist nach oben offen, und dieselbe Reihenfolge braucht auch die Liste an der Firma — die
 * Regel gehoert an eine Stelle.
 *
 * <p>Was hier und nicht im Bestand steht, ist die Nummernerkennung (E17): Das fuehrende {@code #}
 * abzuschneiden und zu entscheiden, ob der Rest eine Zahl ist, ist Fachlogik. Ein {@code CAST} in
 * SQL scheiterte am ersten Buchstaben.
 */
@Service
@Transactional(readOnly = true)
public class VorgaengeUebersichtUseCase {

  /** Das Zeichen, das der Freiberufler einer Vorgangsnummer voranstellt, wenn er sie sucht. */
  private static final String NUMMERNZEICHEN = "#";

  private final VorgangRepository vorgaenge;
  private final VorgangZeilen zeilen;

  public VorgaengeUebersichtUseCase(
      final VorgangRepository vorgaenge,
      final EintragRepository eintraege,
      final FirmaRepository firmen) {
    this.vorgaenge = vorgaenge;
    this.zeilen = new VorgangZeilen(eintraege, firmen);
  }

  /**
   * Die gefilterte Liste und die Zahl aller Vorgaenge.
   *
   * @param suche Teil des Titels oder des Firmennamens, oder eine Vorgangsnummer mit oder ohne
   *     {@code #}; der Leerstring trifft jeden Vorgang
   * @param auchAbgeschlossene {@code true}, wenn auch abgeschlossene Vorgaenge erscheinen sollen
   */
  public VorgaengeUebersicht uebersicht(final String suche, final boolean auchAbgeschlossene) {
    final List<Vorgang> gefunden =
        vorgaenge.uebersicht(suche, nummerAus(suche), auchAbgeschlossene);
    return new VorgaengeUebersicht(zeilen.zu(gefunden), vorgaenge.zaehleAlle());
  }

  /*
   * E17: fuehrendes # abschneiden, den Rest als Zahl lesen — gelingt das nicht, ist der Suchtext
   * keine Nummer und der Bestand sucht allein ueber Titel und Firmenname. Die Ausnahme steuert
   * hier keinen Ablauf, sie ist die Antwort von Long.valueOf auf „das ist keine Zahl"; einen
   * pruefenden Weg ohne sie gibt es in der Standardbibliothek nicht.
   */
  private static @Nullable Long nummerAus(final String suche) {
    final String ziffern =
        suche.startsWith(NUMMERNZEICHEN) ? suche.substring(NUMMERNZEICHEN.length()) : suche;
    try {
      return Long.valueOf(ziffern);
    } catch (final NumberFormatException keineNummer) {
      return null;
    }
  }
}
