package org.mwolff.fbcrm.rechnung.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Identifiable;
import org.mwolff.fbcrm.rechnung.application.RechnungszustandPasstNicht;

/**
 * Eine Rechnung, die ausserhalb von fb.crm geschrieben und hier nur mit ihren Eckdaten erfasst
 * wurde (Plan #259, E1; fachliche Quelle #254).
 *
 * <p>Ein eigenes Aggregat neben {@link Rechnung}: fb.crm kennt weder ihre Positionen noch ihr
 * Angebot, also gibt es nichts zu rechnen und nichts festzuschreiben. Netto und Brutto sind
 * Eingaben und gelten, wie sie erfasst wurden — fb.crm rechnet sie nicht nach (#254, Kriterium 3).
 *
 * <p><b>Zwei Invarianten</b> haelt schon der Konstruktor, damit kein Weg an ihnen vorbeifuehrt:
 * Brutto liegt nie unter Netto (E9), und einen Entwurf gibt es bei ihr nicht (E6, Kriterium 8) —
 * sie steht von Anfang an auf {@code GESTELLT}. Beide Verletzungen sind ein Programmierfehler und
 * werfen {@link IllegalArgumentException}; die Meldung am Feld gibt der Anwendungsfall, bevor er
 * das Aggregat baut. Dieselben Grenzen ziehen die CHECKs in {@code V22__rechnung_nachgetragen.sql}.
 *
 * <p>Am Ausgang der Forderung nimmt sie teil wie jede gestellte Rechnung: {@link
 * #mitZustand(Rechnungszustand, Instant)} fragt dieselbe Kantenregel wie die Rechnung ({@link
 * Rechnungszustand#ausgangswechselErlaubt}, E4). Aendern laesst sie sich in jedem Zustand (#254,
 * Kriterium 10).
 *
 * <p>Unveraenderlich: Jeder Uebergang liefert eine neue Instanz. Der Zeitpunkt kommt von aussen,
 * weil die Domaene keine Uhr kennt (CLAUDE-java.md §6.2).
 *
 * @param id technische Kennung — {@code null}, solange die Rechnung nicht gespeichert ist
 * @param firmaId Kennung der Firma, an die die Rechnung ging; ein Verweis, keine Kopie (Kriterium
 *     11)
 * @param nummer die frei erfasste Rechnungsnummer
 * @param rechnungDatum Datum der Rechnung
 * @param netto der Nettobetrag, wie erfasst
 * @param brutto der Bruttobetrag, wie erfasst; nie kleiner als {@code netto}
 * @param zustand gestellt, bezahlt oder abgeschrieben — nie Entwurf
 * @param pdfSchluessel Schluessel des hinterlegten Originals, oder {@code null} ohne Original
 * @param createdAt Zeitpunkt der Erfassung
 * @param updatedAt Zeitpunkt der letzten Aenderung
 */
public record NachgetrageneRechnung(
    @Nullable Long id,
    long firmaId,
    String nummer,
    LocalDate rechnungDatum,
    BigDecimal netto,
    BigDecimal brutto,
    Rechnungszustand zustand,
    @Nullable String pdfSchluessel,
    Instant createdAt,
    Instant updatedAt)
    implements Identifiable {

  /** Prueft die beiden Invarianten; die Meldung am Feld kommt aus der Anwendungsschicht. */
  public NachgetrageneRechnung {
    if (brutto.compareTo(netto) < 0) {
      throw new IllegalArgumentException("Brutto liegt unter Netto: " + brutto + " unter " + netto);
    }
    if (!zustand.istGestellt()) {
      throw new IllegalArgumentException("Eine nachgetragene Rechnung ist nie ein Entwurf.");
    }
  }

  /**
   * Die Rechnung mit neuen Eckdaten — in jedem Zustand (#254, Kriterium 10).
   *
   * <p>Zustand und Original bleiben, wie sie sind; fuer beide gibt es eigene Uebergaenge.
   *
   * @param neueFirmaId Kennung der Firma
   * @param neueNummer die Rechnungsnummer
   * @param neuesDatum Datum der Rechnung
   * @param neuesNetto der Nettobetrag
   * @param neuesBrutto der Bruttobetrag
   * @param zeitpunkt Zeitpunkt der Aenderung
   * @throws IllegalArgumentException wenn das neue Brutto unter dem neuen Netto liegt
   */
  public NachgetrageneRechnung geaendert(
      final long neueFirmaId,
      final String neueNummer,
      final LocalDate neuesDatum,
      final BigDecimal neuesNetto,
      final BigDecimal neuesBrutto,
      final Instant zeitpunkt) {
    return new NachgetrageneRechnung(
        id,
        neueFirmaId,
        neueNummer,
        neuesDatum,
        neuesNetto,
        neuesBrutto,
        zustand,
        pdfSchluessel,
        createdAt,
        zeitpunkt);
  }

  /**
   * Die Rechnung mit dem Ausgang ihrer Forderung (Issue #253, #254 Kriterium 8).
   *
   * @param ziel der neue Zustand
   * @param jetzt Zeitpunkt der Umstellung
   * @throws RechnungszustandPasstNicht wenn der Uebergang keine der drei Kanten ist
   */
  public NachgetrageneRechnung mitZustand(final Rechnungszustand ziel, final Instant jetzt) {
    if (!Rechnungszustand.ausgangswechselErlaubt(zustand, ziel)) {
      throw new RechnungszustandPasstNicht();
    }
    return new NachgetrageneRechnung(
        id, firmaId, nummer, rechnungDatum, netto, brutto, ziel, pdfSchluessel, createdAt, jetzt);
  }

  /**
   * Die Rechnung mit hinterlegtem Original; ein vorhandenes wird ersetzt (#254, Kriterium 10).
   *
   * @param schluessel Schluessel des Originals im Objektspeicher
   * @param jetzt Zeitpunkt der Aenderung
   */
  public NachgetrageneRechnung mitDokument(final String schluessel, final Instant jetzt) {
    return new NachgetrageneRechnung(
        id, firmaId, nummer, rechnungDatum, netto, brutto, zustand, schluessel, createdAt, jetzt);
  }

  /**
   * Die Rechnung ohne Original (#254, Kriterium 10).
   *
   * @param jetzt Zeitpunkt der Aenderung
   */
  public NachgetrageneRechnung ohneDokument(final Instant jetzt) {
    return new NachgetrageneRechnung(
        id, firmaId, nummer, rechnungDatum, netto, brutto, zustand, null, createdAt, jetzt);
  }
}
