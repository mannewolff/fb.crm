package org.mwolff.fbcrm.auftrag.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Geldrechnung;
import org.mwolff.fbcrm.common.Identifiable;

/**
 * Ein Auftrag — ein eigenstaendiges Dokument am Vorgang, mit eigenem Lebenszyklus (Kapitel 03).
 *
 * <p><b>Was feststeht und was sich pflegen laesst.</b> Nummer, Angebot, Vorgang und Positionen
 * stehen ab dem Anlegen fest (F3, Kriterium 7); veraenderlich sind genau vier Angaben, und dafuer
 * gibt es genau einen Uebergang: {@link #gepflegt}. Die Nummer faellt beim Anlegen und wird nie
 * nachgerueckt — eine Loeschung reisst eine Luecke, und Kriterium 3 erlaubt sie ausdruecklich, weil
 * ein Auftrag kein steuerlicher Beleg ist (E6).
 *
 * <p><b>Nichts Gerechnetes wird gespeichert.</b> {@link #summe()} ist die Summe der gerundeten
 * Positionsbetraege (E11) und rechnet mit derselben Regel wie das Angebot (E5) — sonst traefe der
 * Auftrag die Summe seiner Quelle um einen Cent nicht.
 *
 * <p>Unveraenderlich: Der Uebergang liefert einen neuen Auftrag. Der Zeitpunkt kommt von aussen,
 * weil die Domaene keine Uhr kennt (CLAUDE-java.md §6.2). Eine Zustandsmaschine gibt es nicht — der
 * Status wechselt in jede Richtung frei (F6), und darum wirft {@link #gepflegt} auch nichts.
 *
 * @param id technische Id — {@code null}, solange der Auftrag nicht gespeichert ist
 * @param vorgangId Kennung des Vorgangs, zu dem der Auftrag gehoert
 * @param angebotId Kennung des Angebots, aus dem er entstand; je Angebot hoechstens ein Auftrag
 *     (F9)
 * @param nummer Auftragsnummer aus dem Nummernkreis, in der Schreibweise von {@link Auftragsnummer}
 * @param status der Status des Auftrags (Kriterium 7)
 * @param auftragDatum Datum des Auftrags; frei setzbar und darum nicht die Quelle des Jahresteils
 *     der Nummer (Kriterium 3)
 * @param kundenbestellnummer Bestellnummer des Kunden, oder {@code null}
 * @param leistungAb erster Tag des Leistungszeitraums — mit {@link #leistungBis} zusammen gesetzt
 *     oder zusammen leer (E21)
 * @param leistungBis letzter Tag des Leistungszeitraums, nicht vor {@link #leistungAb} (E21)
 * @param positionen die Positionen in ihrer Reihenfolge; die Liste ist die Reihenfolge
 * @param createdAt Zeitpunkt der Anlage
 * @param updatedAt Zeitpunkt der letzten Aenderung
 */
public record Auftrag(
    @Nullable Long id,
    long vorgangId,
    long angebotId,
    String nummer,
    Auftragsstatus status,
    LocalDate auftragDatum,
    @Nullable String kundenbestellnummer,
    @Nullable LocalDate leistungAb,
    @Nullable LocalDate leistungBis,
    List<Auftragsposition> positionen,
    Instant createdAt,
    Instant updatedAt)
    implements Identifiable {

  /** Nimmt die Positionen als Kopie: Der Aufrufer darf seine Liste danach weiterverwenden. */
  public Auftrag {
    positionen = List.copyOf(positionen);
  }

  /** Die Netto-Summe: die Summe der gerundeten Positionsbetraege, Cent fuer Cent (Kriterium 5). */
  public BigDecimal summe() {
    return Geldrechnung.summe(positionen.stream().map(Auftragsposition::betrag));
  }

  /**
   * Der Auftrag mit den vier gepflegten Angaben (Kriterium 7).
   *
   * <p>Ein Uebergang fuer alle vier und nicht vier einzelne (E8): Kriterium 7 nennt sie in einem
   * Atemzug, und die Positionen kommen hier gar nicht vor — was sich nicht aendern darf, taucht in
   * der Signatur nicht auf.
   *
   * @param auftragDatum Datum des Auftrags
   * @param kundenbestellnummer Bestellnummer des Kunden, oder {@code null}
   * @param leistungAb erster Tag des Leistungszeitraums, oder {@code null}
   * @param leistungBis letzter Tag des Leistungszeitraums, oder {@code null}
   * @param status der neue Status
   * @param zeitpunkt Zeitpunkt der Aenderung
   */
  public Auftrag gepflegt(
      final LocalDate auftragDatum,
      final @Nullable String kundenbestellnummer,
      final @Nullable LocalDate leistungAb,
      final @Nullable LocalDate leistungBis,
      final Auftragsstatus status,
      final Instant zeitpunkt) {
    return new Auftrag(
        id,
        vorgangId,
        angebotId,
        nummer,
        status,
        auftragDatum,
        kundenbestellnummer,
        leistungAb,
        leistungBis,
        positionen,
        createdAt,
        zeitpunkt);
  }
}
