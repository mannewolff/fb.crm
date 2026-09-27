package org.mwolff.fbcrm.angebot.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.application.AngebotNichtAenderbar;
import org.mwolff.fbcrm.common.Identifiable;

/**
 * Ein Angebot — ein eigenstaendiges Dokument am Vorgang, mit eigenem Lebenszyklus (Kapitel 03).
 *
 * <p><b>Der Entwurf und das festgeschriebene Dokument.</b> Solange der Zustand {@link
 * Angebotszustand#ENTWURF} ist, traegt das Angebot keine Nummer, kein Dokument und keine Kopien der
 * Anschriften, und es laesst sich beliebig aendern (Kriterium 6). {@link #versendet} macht daraus
 * ein festes Dokument; danach aendert sich nur noch die Reaktion des Kunden (Kriterium 17). Die
 * Datenbank sagt dasselbe mit zwei gegenlaeufigen Checks noch einmal.
 *
 * <p><b>Nichts Gerechnetes wird gespeichert.</b> {@link #summe()} ist die Summe der gerundeten
 * Positionsbetraege (E5), und {@link #stand} leitet „abgelaufen" aus der Gueltigkeit ab, statt es
 * zu fuehren (E4).
 *
 * <p>Unveraenderlich: Jeder Uebergang liefert ein neues Angebot. Der Zeitpunkt kommt von aussen,
 * weil die Domaene keine Uhr kennt (CLAUDE-java.md §6.2). Ein unzulaessiger Uebergang wirft {@link
 * AngebotNichtAenderbar} — hier und nicht erst im Anwendungsfall, damit kein Weg daran
 * vorbeifuehrt.
 *
 * @param id technische Id — {@code null}, solange das Angebot nicht gespeichert ist
 * @param vorgangId Kennung des Vorgangs, zu dem das Angebot gehoert
 * @param nummer Angebotsnummer aus dem Nummernkreis, oder {@code null} im Entwurf (Kriterium 11)
 * @param zustand der gespeicherte Zustand
 * @param angebotDatum Datum des Angebots; im Entwurf der Tag der Anlage (Kriterium 3)
 * @param gueltigBis letzter Tag der Gueltigkeit — im Entwurf frei, danach nicht vor dem
 *     Angebotsdatum (E27)
 * @param leistungsbeschreibung einleitender Text, oder {@code null}
 * @param zahlungsbedingungen Zahlungsbedingungen, oder {@code null}
 * @param versendetAm Zeitpunkt des Versendens, oder {@code null} im Entwurf
 * @param reaktionAm Zeitpunkt der Reaktion des Kunden, oder {@code null}
 * @param pdfSchluessel Schluessel des archivierten Dokuments im Objektspeicher, oder {@code null}
 *     im Entwurf (Kriterium 14)
 * @param empfaenger Kopie der Empfaengeranschrift, oder {@code null} im Entwurf (R8)
 * @param absender Kopie der eigenen Angaben, oder {@code null} im Entwurf (R8)
 * @param positionen die Positionen in ihrer Reihenfolge; die Liste ist die Reihenfolge (E24)
 * @param createdAt Zeitpunkt der Anlage
 * @param updatedAt Zeitpunkt der letzten Aenderung
 */
public record Angebot(
    @Nullable Long id,
    long vorgangId,
    @Nullable String nummer,
    Angebotszustand zustand,
    LocalDate angebotDatum,
    LocalDate gueltigBis,
    @Nullable String leistungsbeschreibung,
    @Nullable String zahlungsbedingungen,
    @Nullable Instant versendetAm,
    @Nullable Instant reaktionAm,
    @Nullable String pdfSchluessel,
    @Nullable Belegempfaenger empfaenger,
    @Nullable Belegabsender absender,
    List<Angebotsposition> positionen,
    Instant createdAt,
    Instant updatedAt)
    implements Identifiable {

  /** Nimmt die Positionen als Kopie: Der Aufrufer darf seine Liste danach weiterverwenden. */
  public Angebot {
    positionen = List.copyOf(positionen);
  }

  /** Die Netto-Summe: die Summe der gerundeten Positionsbetraege, Cent fuer Cent (Kriterium 5). */
  public BigDecimal summe() {
    return positionen.stream()
        .map(Angebotsposition::betrag)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .setScale(2, RoundingMode.HALF_UP);
  }

  /**
   * Der Stand, als der das Angebot heute angezeigt wird (Kriterium 18).
   *
   * <p>Ein versendetes Angebot, dessen Gueltigkeit vor dem heutigen Tag endete, gilt als {@link
   * Angebotsstand#ABGELAUFEN}; der letzte Tag der Gueltigkeit zaehlt noch mit. Jeder andere Zustand
   * nennt seinen Stand selbst — ein Entwurf laeuft nicht ab (E27), und ein endgueltiger Zustand
   * auch nicht.
   *
   * @param heute der heutige Tag in {@code common.Geschaeftszone}
   */
  public Angebotsstand stand(final LocalDate heute) {
    if (zustand == Angebotszustand.VERSENDET && gueltigBis.isBefore(heute)) {
      return Angebotsstand.ABGELAUFEN;
    }
    return zustand.stand();
  }

  /**
   * {@code true}, solange das Angebot auf eine Reaktion des Kunden wartet.
   *
   * <p>Das sind genau die beiden Staende {@link Angebotsstand#VERSENDET} und {@link
   * Angebotsstand#ABGELAUFEN}: Die verstrichene Gueltigkeit schliesst ein Angebot nicht, sie macht
   * es nur alt (Kriterium 18). Ein offenes Angebot ist es, das ein spaeterer Versand abloest
   * (Kriterium 19) und das eine Annahme abloest (Kriterium 17).
   *
   * @param heute der heutige Tag in {@code common.Geschaeftszone}
   */
  public boolean offen(final LocalDate heute) {
    final Angebotsstand stand = stand(heute);
    return stand == Angebotsstand.VERSENDET || stand == Angebotsstand.ABGELAUFEN;
  }

  /**
   * Der Entwurf mit neuen Angaben (Kriterium 6).
   *
   * @param gueltigBis letzter Tag der Gueltigkeit
   * @param leistungsbeschreibung einleitender Text, oder {@code null}
   * @param zahlungsbedingungen Zahlungsbedingungen, oder {@code null}
   * @param positionen die vollstaendige Positionsliste in der gewuenschten Reihenfolge
   * @param zeitpunkt Zeitpunkt der Aenderung
   * @throws AngebotNichtAenderbar wenn das Angebot kein Entwurf mehr ist
   */
  public Angebot entwurfGeaendert(
      final LocalDate gueltigBis,
      final @Nullable String leistungsbeschreibung,
      final @Nullable String zahlungsbedingungen,
      final List<Angebotsposition> positionen,
      final Instant zeitpunkt) {
    nurWenn(zustand == Angebotszustand.ENTWURF);
    return new Angebot(
        id,
        vorgangId,
        nummer,
        zustand,
        angebotDatum,
        gueltigBis,
        leistungsbeschreibung,
        zahlungsbedingungen,
        versendetAm,
        reaktionAm,
        pdfSchluessel,
        empfaenger,
        absender,
        positionen,
        createdAt,
        zeitpunkt);
  }

  /**
   * Das festgeschriebene Angebot: Nummer, Kopien der Anschriften und das archivierte Dokument
   * (Kriterien 10 bis 15).
   *
   * @param nummer die gezogene Angebotsnummer
   * @param empfaenger Kopie der Empfaengeranschrift
   * @param absender Kopie der eigenen Angaben
   * @param pdfSchluessel Schluessel des abgelegten Dokuments
   * @param zeitpunkt Zeitpunkt des Versendens
   * @throws AngebotNichtAenderbar wenn das Angebot kein Entwurf mehr ist
   */
  public Angebot versendet(
      final String nummer,
      final Belegempfaenger empfaenger,
      final Belegabsender absender,
      final String pdfSchluessel,
      final Instant zeitpunkt) {
    nurWenn(zustand == Angebotszustand.ENTWURF);
    return new Angebot(
        id,
        vorgangId,
        nummer,
        Angebotszustand.VERSENDET,
        angebotDatum,
        gueltigBis,
        leistungsbeschreibung,
        zahlungsbedingungen,
        zeitpunkt,
        reaktionAm,
        pdfSchluessel,
        empfaenger,
        absender,
        positionen,
        createdAt,
        zeitpunkt);
  }

  /**
   * Das Angebot als angenommen (Kriterium 17).
   *
   * @param zeitpunkt Zeitpunkt der Reaktion
   * @throws AngebotNichtAenderbar wenn das Angebot nicht versendet oder abgeloest ist
   */
  public Angebot angenommen(final Instant zeitpunkt) {
    return mitReaktion(Angebotszustand.ANGENOMMEN, zeitpunkt);
  }

  /**
   * Das Angebot als abgelehnt (Kriterium 17).
   *
   * @param zeitpunkt Zeitpunkt der Reaktion
   * @throws AngebotNichtAenderbar wenn das Angebot nicht versendet oder abgeloest ist
   */
  public Angebot abgelehnt(final Instant zeitpunkt) {
    return mitReaktion(Angebotszustand.ABGELEHNT, zeitpunkt);
  }

  /**
   * Das Angebot als abgeloest (Kriterium 19).
   *
   * <p>Ohne Reaktionszeitpunkt: Die Abloesung ist ein Zug der Anwendung und keine Antwort des
   * Kunden. Abgeloest wird nur, was offen ist.
   *
   * @param zeitpunkt Zeitpunkt der Abloesung
   * @throws AngebotNichtAenderbar wenn das Angebot nicht versendet ist
   */
  public Angebot abgeloest(final Instant zeitpunkt) {
    nurWenn(zustand == Angebotszustand.VERSENDET);
    return new Angebot(
        id,
        vorgangId,
        nummer,
        Angebotszustand.ABGELOEST,
        angebotDatum,
        gueltigBis,
        leistungsbeschreibung,
        zahlungsbedingungen,
        versendetAm,
        reaktionAm,
        pdfSchluessel,
        empfaenger,
        absender,
        positionen,
        createdAt,
        zeitpunkt);
  }

  /*
   * Annehmen und Ablehnen unterscheiden sich allein im Zielzustand — und beide sind aus VERSENDET
   * *und* aus ABGELOEST erlaubt, jeweils unabhaengig vom Datum (F13, Kriterium 18).
   */
  private Angebot mitReaktion(final Angebotszustand ziel, final Instant zeitpunkt) {
    nurWenn(zustand == Angebotszustand.VERSENDET || zustand == Angebotszustand.ABGELOEST);
    return new Angebot(
        id,
        vorgangId,
        nummer,
        ziel,
        angebotDatum,
        gueltigBis,
        leistungsbeschreibung,
        zahlungsbedingungen,
        versendetAm,
        zeitpunkt,
        pdfSchluessel,
        empfaenger,
        absender,
        positionen,
        createdAt,
        zeitpunkt);
  }

  private static void nurWenn(final boolean derUebergangIstErlaubt) {
    if (!derUebergangIstErlaubt) {
      throw new AngebotNichtAenderbar();
    }
  }
}
