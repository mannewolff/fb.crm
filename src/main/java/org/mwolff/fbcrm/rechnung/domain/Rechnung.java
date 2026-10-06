package org.mwolff.fbcrm.rechnung.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Geldrechnung;
import org.mwolff.fbcrm.common.Identifiable;
import org.mwolff.fbcrm.rechnung.application.RechnungszustandPasstNicht;

/**
 * Eine Rechnung zu einem Angebot (Plan #169, E3; fachliche Quelle #160).
 *
 * <p>Sie entsteht als Entwurf mit den Positionen, an denen am Angebot noch etwas offen ist, und
 * wird mit dem Stellen festgeschrieben: Von da an traegt sie ihre Nummer, den Steuersatz, das
 * Zahlungsziel, den Zeitpunkt und die Kopien von Empfaenger und Absender — Kopien und keine
 * Verweise, damit ein spaeterer Umzug des Kunden oder eine neue Bankverbindung eine gestellte
 * Rechnung nicht nachtraeglich veraendert (#160, Kriterium 14).
 *
 * <p><b>Nichts Gerechnetes wird gespeichert</b> (E5 am Angebot): {@link #netto()} ist die Summe der
 * gerundeten Positionsbetraege, {@link #steuer(BigDecimal)} entsteht aus dieser Summe und nicht je
 * Position, und {@link #brutto(BigDecimal)} ist beides zusammen. Der Steuersatz geht als Parameter
 * hinein statt aus dem Feld zu kommen: Ein Entwurf hat noch keinen, und Liste und Maske rechnen ihn
 * dort mit dem Satz der aktuellen Einstellungen.
 *
 * <p>Nach dem Stellen kommt der <b>Ausgang der Forderung</b> dazu (Issue #253): {@link
 * #mitZustand(Rechnungszustand, Instant)} fuehrt von {@code GESTELLT} nach {@code BEZAHLT} oder
 * {@code ABGESCHRIEBEN} und von beiden zurueck. Festgeschrieben bleibt dabei alles — der Zustand
 * sagt nur, wie es ausgegangen ist, und {@code BEZAHLT} wie {@code ABGESCHRIEBEN} sind weiterhin
 * gestellte Rechnungen ({@link Rechnungszustand#istGestellt()}).
 *
 * <p>Unveraenderlich: Jeder Uebergang liefert eine neue Rechnung. Der Zeitpunkt kommt von aussen,
 * weil die Domaene keine Uhr kennt (CLAUDE-java.md §6.2). Ein Schritt, der nicht zum Zustand passt,
 * wirft {@link RechnungszustandPasstNicht} — hier und nicht erst im Anwendungsfall, damit kein Weg
 * daran vorbeifuehrt.
 *
 * @param id technische Kennung — {@code null}, solange die Rechnung nicht gespeichert ist
 * @param angebotId Kennung des Angebots, das abgerechnet wird
 * @param zustand Entwurf, gestellt, bezahlt oder abgeschrieben
 * @param rechnungDatum Datum der Rechnung; im Entwurf frei aenderbar
 * @param leistungszeitraum Zeitraum der Leistung als Text, oder {@code null}
 * @param positionen die Positionen in ihrer Reihenfolge; die Liste ist die Reihenfolge (E24)
 * @param nummer die Rechnungsnummer, oder {@code null} im Entwurf
 * @param steuersatz der beim Stellen geltende Steuersatz in Prozent, oder {@code null} im Entwurf
 * @param zahlungszielTage das beim Stellen geltende Zahlungsziel in Tagen, oder {@code null}
 * @param gestelltAm Zeitpunkt des Stellens, oder {@code null} im Entwurf
 * @param pdfSchluessel Schluessel des archivierten Dokuments; es entsteht erst nach dem Stellen
 * @param empfaenger Kopie des Empfaengers, wie er beim Stellen galt, oder {@code null} im Entwurf
 * @param absender Kopie der eigenen Angaben, wie sie beim Stellen galten, oder {@code null}
 * @param createdAt Zeitpunkt der Anlage
 * @param updatedAt Zeitpunkt der letzten Aenderung
 */
public record Rechnung(
    @Nullable Long id,
    long angebotId,
    Rechnungszustand zustand,
    LocalDate rechnungDatum,
    @Nullable String leistungszeitraum,
    List<Rechnungsposition> positionen,
    @Nullable String nummer,
    @Nullable BigDecimal steuersatz,
    @Nullable Integer zahlungszielTage,
    @Nullable Instant gestelltAm,
    @Nullable String pdfSchluessel,
    @Nullable Belegempfaenger empfaenger,
    @Nullable Belegabsender absender,
    Instant createdAt,
    Instant updatedAt)
    implements Identifiable {

  /** Der Teiler, der aus einem Satz in Prozent einen Faktor macht: zwei Stellen nach links. */
  private static final int PROZENT = 2;

  /** Nimmt die Positionen als Kopie: Der Aufrufer darf seine Liste danach weiterverwenden. */
  public Rechnung {
    positionen = List.copyOf(positionen);
  }

  /** Die Netto-Summe: die Summe der gerundeten Positionsbetraege, Cent fuer Cent. */
  public BigDecimal netto() {
    return Geldrechnung.summe(positionen.stream().map(Rechnungsposition::betrag));
  }

  /**
   * Die Steuer auf die Netto-Summe, kaufmaennisch auf den Cent gerundet.
   *
   * <p>Aus der Summe und nicht je Position: Zweimal 249,98 ergeben zu 19 Prozent je Position 95,00,
   * aus der Summe 499,96 gerechnet aber 94,99. Massgeblich ist der Betrag, der auf dem Beleg steht,
   * und das ist der aus der Summe.
   *
   * @param satz der Steuersatz in Prozent
   */
  public BigDecimal steuer(final BigDecimal satz) {
    return Geldrechnung.betrag(netto(), satz.movePointLeft(PROZENT));
  }

  /**
   * Die Brutto-Summe: Netto plus Steuer.
   *
   * @param satz der Steuersatz in Prozent
   */
  public BigDecimal brutto(final BigDecimal satz) {
    return netto().add(steuer(satz));
  }

  /**
   * Die Rechnung mit neuen Angaben — nur im Entwurf (#160, Kriterium 12).
   *
   * @param neuesDatum Datum der Rechnung
   * @param neuerLeistungszeitraum Zeitraum der Leistung als Text, oder {@code null}
   * @param neuePositionen die vollstaendige Positionsliste in der gewuenschten Reihenfolge
   * @param zeitpunkt Zeitpunkt der Aenderung
   * @throws RechnungszustandPasstNicht wenn die Rechnung schon gestellt ist
   */
  public Rechnung geaendert(
      final LocalDate neuesDatum,
      final @Nullable String neuerLeistungszeitraum,
      final List<Rechnungsposition> neuePositionen,
      final Instant zeitpunkt) {
    nurEntwurf();
    return new Rechnung(
        id,
        angebotId,
        zustand,
        neuesDatum,
        neuerLeistungszeitraum,
        neuePositionen,
        nummer,
        steuersatz,
        zahlungszielTage,
        gestelltAm,
        pdfSchluessel,
        empfaenger,
        absender,
        createdAt,
        zeitpunkt);
  }

  /**
   * Die gestellte Rechnung: festgeschrieben mit Nummer, Satz, Ziel und den beiden Kopien.
   *
   * <p>Positionen, Datum und Leistungszeitraum bleiben, wie sie im Entwurf standen — gestellt wird,
   * was dasteht, nicht etwas anderes. Das Dokument fehlt noch; es entsteht unmittelbar danach und
   * kommt ueber {@link #mitDokument(String)} dazu (Plan #169, E7).
   *
   * @param neueNummer die gezogene Rechnungsnummer
   * @param neuerSteuersatz der geltende Steuersatz in Prozent
   * @param neuesZahlungsziel das geltende Zahlungsziel in Tagen
   * @param neuerEmpfaenger Kopie des Empfaengers, wie er jetzt gilt
   * @param neuerAbsender Kopie der eigenen Angaben, wie sie jetzt gelten
   * @param zeitpunkt Zeitpunkt des Stellens
   * @throws RechnungszustandPasstNicht wenn die Rechnung schon gestellt ist
   */
  public Rechnung gestellt(
      final String neueNummer,
      final BigDecimal neuerSteuersatz,
      final int neuesZahlungsziel,
      final Belegempfaenger neuerEmpfaenger,
      final Belegabsender neuerAbsender,
      final Instant zeitpunkt) {
    nurEntwurf();
    return new Rechnung(
        id,
        angebotId,
        Rechnungszustand.GESTELLT,
        rechnungDatum,
        leistungszeitraum,
        positionen,
        neueNummer,
        neuerSteuersatz,
        neuesZahlungsziel,
        zeitpunkt,
        pdfSchluessel,
        neuerEmpfaenger,
        neuerAbsender,
        createdAt,
        zeitpunkt);
  }

  /**
   * Die gestellte Rechnung mit dem Schluessel ihres archivierten Dokuments (Plan #169, E7).
   *
   * <p>Ohne Zeitpunkt: Das Dokument entsteht im selben Schritt wie das Stellen, und {@link
   * #updatedAt()} nennt bereits diesen Zeitpunkt.
   *
   * @param schluessel Schluessel des Dokuments im Objektspeicher
   * @throws RechnungszustandPasstNicht wenn die Rechnung noch Entwurf ist
   */
  public Rechnung mitDokument(final String schluessel) {
    if (!zustand.istGestellt()) {
      throw new RechnungszustandPasstNicht();
    }
    return new Rechnung(
        id,
        angebotId,
        zustand,
        rechnungDatum,
        leistungszeitraum,
        positionen,
        nummer,
        steuersatz,
        zahlungszielTage,
        gestelltAm,
        schluessel,
        empfaenger,
        absender,
        createdAt,
        updatedAt);
  }

  /**
   * Die gestellte Rechnung mit dem Ausgang ihrer Forderung (Issue #253).
   *
   * <p><b>Nichts Festgeschriebenes aendert sich dabei</b>: Nummer, Steuersatz, Zahlungsziel, der
   * Zeitpunkt des Stellens, das Dokument, die beiden Kopien und die Positionen bleiben, wie sie
   * sind — der neue Zustand sagt allein, wie die Forderung ausgegangen ist. Nur {@link
   * #updatedAt()} rueckt vor.
   *
   * @param ziel der neue Zustand
   * @param jetzt Zeitpunkt der Umstellung
   * @throws RechnungszustandPasstNicht wenn der Uebergang keine der drei Kanten ist
   */
  public Rechnung mitZustand(final Rechnungszustand ziel, final Instant jetzt) {
    if (!erlaubt(ziel)) {
      throw new RechnungszustandPasstNicht();
    }
    return new Rechnung(
        id,
        angebotId,
        ziel,
        rechnungDatum,
        leistungszeitraum,
        positionen,
        nummer,
        steuersatz,
        zahlungszielTage,
        gestelltAm,
        pdfSchluessel,
        empfaenger,
        absender,
        createdAt,
        jetzt);
  }

  /*
   * Drei Kanten und keine vierte: von GESTELLT zu einem der beiden Ausgaenge und von jedem Ausgang
   * zurueck auf GESTELLT. Beide Seiten muessen gestellt sein — der Entwurf liegt davor, und ihn
   * stellt `gestellt` mit Nummer und Kopien —, und genau eine der beiden muss GESTELLT sein. Diese
   * zweite Bedingung schliesst zugleich den Stillstand aus und den direkten Weg zwischen den
   * Ausgaengen: Ein Wechsel von bezahlt auf abgeschrieben fuehrt ueber „gestellt".
   */
  private boolean erlaubt(final Rechnungszustand ziel) {
    return zustand.istGestellt()
        && ziel.istGestellt()
        && (zustand == Rechnungszustand.GESTELLT) != (ziel == Rechnungszustand.GESTELLT);
  }

  private void nurEntwurf() {
    if (zustand != Rechnungszustand.ENTWURF) {
      throw new RechnungszustandPasstNicht();
    }
  }
}
