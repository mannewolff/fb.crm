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
 * <p><b>Zwei Arten, eine Mappe</b> (Issue #226, Kriterium 1 von #207): Dasselbe Objekt haelt auch
 * die eigene interne Arbeit fest. Das Kennzeichen {@link #intern} sagt, welche von beiden, und es
 * geht mit dem Status Hand in Hand — siehe den Konstruktor und {@link #umgestellt(boolean,
 * Instant)}.
 *
 * <p><b>Titel und Beschreibung</b> (Issue #316): Der Titel ist die kurze, einzeilige Benennung des
 * Angebots, die Beschreibung der mehrzeilige Text daneben. Ein Angebot von vor dieser Aenderung
 * traegt keinen Titel und bleibt trotzdem gueltig (#314, Kriterium 4).
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
 * @param intern ob das Angebot die eigene interne Arbeit festhaelt und nicht an einen Kunden geht
 * @param status wie weit das Angebot gediehen ist
 * @param angebotDatum Datum des Angebots; beim Anlegen der Tag der Anlage, danach aenderbar
 * @param titel die einzeilige Benennung des Angebots, hoechstens 200 Zeichen und ohne Leerraum am
 *     Rand, oder {@code null} fuer ein Angebot von vor Issue #316
 * @param beschreibung der Text des Angebots, oder {@code null}
 * @param positionen die Positionen in ihrer Reihenfolge; die Liste ist die Reihenfolge (E24)
 * @param createdAt Zeitpunkt der Anlage
 * @param updatedAt Zeitpunkt der letzten Aenderung
 */
public record Angebot(
    @Nullable Long id,
    long firmaId,
    @Nullable Long ansprechpartnerId,
    boolean intern,
    Angebotsstatus status,
    LocalDate angebotDatum,
    @Nullable String titel,
    @Nullable String beschreibung,
    List<Angebotsposition> positionen,
    Instant createdAt,
    Instant updatedAt)
    implements Identifiable {

  /**
   * Die hoechste Laenge des Titels in Zeichen (#314, Kriterium 1); so breit ist auch die Spalte.
   */
  private static final int TITEL_LAENGE = 200;

  /**
   * Nimmt die Positionen als Kopie: Der Aufrufer darf seine Liste danach weiterverwenden.
   *
   * <p><b>Kennzeichen und Status sagen dasselbe</b> (Issue #226, E3): {@code intern} ist genau dann
   * gesetzt, wenn der Status zur internen Arbeit gehoert. Geprueft wird hier und nicht erst im
   * Anwendungsfall, damit kein Weg an der Regel vorbeifuehrt; die Datenbank haelt dieselbe Zusage
   * als CHECK {@code angebot_art_status}.
   *
   * <p><b>Der Titel ist nicht leer und hoechstens 200 Zeichen lang</b> (Issue #316, Plan #315 E1).
   * Leerraum am Rand wird vorher abgeschnitten; {@code null} bleibt erlaubt — ein Angebot von vor
   * dem Titel. Die Datenbank haelt die erste Haelfte als CHECK {@code angebot_titel}.
   *
   * @throws IllegalArgumentException wenn Kennzeichen und Status nicht zueinander passen oder der
   *     Titel leer oder laenger als 200 Zeichen ist
   */
  public Angebot {
    if (intern != status.intern()) {
      throw new IllegalArgumentException(
          "Kennzeichen intern=" + intern + " passt nicht zum Status " + status);
    }
    if (titel != null) {
      titel = titel.strip();
      if (titel.isEmpty() || titel.length() > TITEL_LAENGE) {
        throw new IllegalArgumentException(
            "Titel muss 1 bis " + TITEL_LAENGE + " Zeichen haben, hat " + titel.length());
      }
    }
    positionen = List.copyOf(positionen);
  }

  /** Die Netto-Summe: die Summe der gerundeten Positionsbetraege, Cent fuer Cent (Kriterium 1). */
  public BigDecimal summe() {
    return Geldrechnung.summe(positionen.stream().map(Angebotsposition::betrag));
  }

  /**
   * Das Angebot mit neuen Angaben — in jedem Status (Kriterium 5).
   *
   * <p>Der Titel kommt auf diesem Weg immer mit (Issue #317); getrimmt und geprueft wird er im
   * Konstruktor (E1).
   *
   * @param neuesDatum Datum des Angebots
   * @param neuerAnsprechpartnerId Kennung des Ansprechpartners, oder {@code null}
   * @param neuerTitel die einzeilige Benennung des Angebots
   * @param neueBeschreibung Text des Angebots, oder {@code null}
   * @param neuePositionen die vollstaendige Positionsliste in der gewuenschten Reihenfolge
   * @param zeitpunkt Zeitpunkt der Aenderung
   */
  public Angebot geaendert(
      final LocalDate neuesDatum,
      final @Nullable Long neuerAnsprechpartnerId,
      final String neuerTitel,
      final @Nullable String neueBeschreibung,
      final List<Angebotsposition> neuePositionen,
      final Instant zeitpunkt) {
    return new Angebot(
        id,
        firmaId,
        neuerAnsprechpartnerId,
        intern,
        status,
        neuesDatum,
        neuerTitel,
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

  /**
   * Das Angebot als abgerechnet — aus jedem Status und ohne Zwischenstufen (#160, Kriterium 27).
   *
   * <p>Der eine Sprung neben der Reihe: Ist alles berechnet, ist das Angebot abgerechnet, auch wenn
   * es noch in „bestellt" steht. {@link #statusWeiter} und {@link #statusZurueck} bleiben davon
   * unberuehrt — sie sind der Weg von Hand, dieser hier der Schluss aus den Rechnungen.
   *
   * <p>Ist das Angebot schon abgerechnet, bleibt es unveraendert und traegt auch keinen neuen
   * Zeitpunkt: Nichts ist geschehen.
   *
   * @param zeitpunkt Zeitpunkt des Wechsels
   */
  public Angebot abgerechnet(final Instant zeitpunkt) {
    return status == Angebotsstatus.ABGERECHNET
        ? this
        : mitStatus(Angebotsstatus.ABGERECHNET, zeitpunkt);
  }

  /**
   * Das Angebot auf die andere Art gestellt — Kennzeichen und Status zusammen (Issue #226).
   *
   * <p>Der eine Weg, auf dem {@code intern} sich aendert. Den Status waehlt nicht der Aufrufer: Er
   * kommt aus {@link Angebotsstatus#fuerArt(boolean)} und ist damit immer der, der zum neuen
   * Kennzeichen passt — die Invariante des Konstruktors laesst gar nichts anderes zu.
   *
   * @param neuIntern ob das Angebot danach die interne Arbeit festhaelt
   * @param zeitpunkt Zeitpunkt der Umstellung
   */
  public Angebot umgestellt(final boolean neuIntern, final Instant zeitpunkt) {
    return new Angebot(
        id,
        firmaId,
        ansprechpartnerId,
        neuIntern,
        status.fuerArt(neuIntern),
        angebotDatum,
        titel,
        beschreibung,
        positionen,
        createdAt,
        zeitpunkt);
  }

  private Angebot mitStatus(final Angebotsstatus ziel, final Instant zeitpunkt) {
    return new Angebot(
        id,
        firmaId,
        ansprechpartnerId,
        intern,
        ziel,
        angebotDatum,
        titel,
        beschreibung,
        positionen,
        createdAt,
        zeitpunkt);
  }
}
