package org.mwolff.fbcrm.angebot.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.application.StatusGrenzeErreicht;
import org.mwolff.fbcrm.common.Geldrechnung;
import org.mwolff.fbcrm.common.Identifiable;

/**
 * Ein Angebot — die Mappe zu einem Angebot, das ausserhalb des Tools entsteht (Issue #127).
 *
 * <p>Es haelt fest, fuer welche Firma, mit welchem Ansprechpartner, was genau (Beschreibung und
 * Positionen) und wie weit es gediehen ist ({@link Angebotsstatus}). Es ist kein Beleg: keine
 * Nummer, kein Dokument, keine Gueltigkeit. In jedem Status laesst es sich aendern; nur die Firma
 * bleibt die, bei der es angelegt wurde.
 *
 * <p><b>Nichts Gerechnetes wird gespeichert.</b> {@link #summe()} ist die Summe der gerundeten
 * Positionsbetraege (E5).
 *
 * <p>Unveraenderlich: Jeder Uebergang liefert ein neues Angebot. Der Zeitpunkt kommt von aussen,
 * weil die Domaene keine Uhr kennt (CLAUDE-java.md §6.2). Ein Statuswechsel ueber das Ende der
 * Reihe hinaus wirft {@link StatusGrenzeErreicht} — hier und nicht erst im Anwendungsfall, damit
 * kein Weg daran vorbeifuehrt.
 *
 * @param id technische Id — {@code null}, solange das Angebot nicht gespeichert ist
 * @param firmaId Kennung der Firma, an die das Angebot geht
 * @param ansprechpartnerId Kennung des Ansprechpartners bei dieser Firma, oder {@code null}
 * @param status wie weit das Angebot gediehen ist
 * @param angebotDatum Datum des Angebots; beim Anlegen der Tag der Anlage, danach aenderbar
 * @param beschreibung der Text des Angebots, oder {@code null}
 * @param positionen die Positionen in ihrer Reihenfolge; die Liste ist die Reihenfolge (E24)
 * @param createdAt Zeitpunkt der Anlage
 * @param updatedAt Zeitpunkt der letzten Aenderung
 */
public record Angebot(
    @Nullable Long id,
    long firmaId,
    @Nullable Long ansprechpartnerId,
    Angebotsstatus status,
    LocalDate angebotDatum,
    @Nullable String beschreibung,
    List<Angebotsposition> positionen,
    Instant createdAt,
    Instant updatedAt)
    implements Identifiable {

  /** Nimmt die Positionen als Kopie: Der Aufrufer darf seine Liste danach weiterverwenden. */
  public Angebot {
    positionen = List.copyOf(positionen);
  }

  /** Die Netto-Summe: die Summe der gerundeten Positionsbetraege, Cent fuer Cent (Kriterium 1). */
  public BigDecimal summe() {
    return Geldrechnung.summe(positionen.stream().map(Angebotsposition::betrag));
  }

  /**
   * Das Angebot mit neuen Angaben — in jedem Status (Kriterium 5).
   *
   * @param neuesDatum Datum des Angebots
   * @param neuerAnsprechpartnerId Kennung des Ansprechpartners, oder {@code null}
   * @param neueBeschreibung Text des Angebots, oder {@code null}
   * @param neuePositionen die vollstaendige Positionsliste in der gewuenschten Reihenfolge
   * @param zeitpunkt Zeitpunkt der Aenderung
   */
  public Angebot geaendert(
      final LocalDate neuesDatum,
      final @Nullable Long neuerAnsprechpartnerId,
      final @Nullable String neueBeschreibung,
      final List<Angebotsposition> neuePositionen,
      final Instant zeitpunkt) {
    return new Angebot(
        id,
        firmaId,
        neuerAnsprechpartnerId,
        status,
        neuesDatum,
        neueBeschreibung,
        neuePositionen,
        createdAt,
        zeitpunkt);
  }

  /**
   * Das Angebot eine Stufe weiter (Kriterium 4).
   *
   * @param zeitpunkt Zeitpunkt des Wechsels
   * @throws StatusGrenzeErreicht wenn das Angebot schon abgerechnet ist
   */
  public Angebot statusWeiter(final Instant zeitpunkt) {
    return mitStatus(status.weiter().orElseThrow(StatusGrenzeErreicht::new), zeitpunkt);
  }

  /**
   * Das Angebot eine Stufe zurueck (Kriterium 4).
   *
   * @param zeitpunkt Zeitpunkt des Wechsels
   * @throws StatusGrenzeErreicht wenn das Angebot erst angelegt ist
   */
  public Angebot statusZurueck(final Instant zeitpunkt) {
    return mitStatus(status.zurueck().orElseThrow(StatusGrenzeErreicht::new), zeitpunkt);
  }

  private Angebot mitStatus(final Angebotsstatus ziel, final Instant zeitpunkt) {
    return new Angebot(
        id,
        firmaId,
        ansprechpartnerId,
        ziel,
        angebotDatum,
        beschreibung,
        positionen,
        createdAt,
        zeitpunkt);
  }
}
