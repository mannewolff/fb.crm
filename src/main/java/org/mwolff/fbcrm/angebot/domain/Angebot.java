package org.mwolff.fbcrm.angebot.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.application.AngebotStorniert;
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
 * <p><b>Das Storno</b> (Issue #333, Plan #332 E1): Ein abgelehntes Angebot oder ein
 * zurueckgezogener Auftrag traegt das Kennzeichen {@link #storniert} neben dem Status, und der
 * Status behaelt den Wert, aus dem storniert wurde — die Ruecknahme fuehrt genau dorthin zurueck
 * (#330, Kriterium 9). Solange es storniert ist, steht die Statuskette still (E2): {@link
 * #statusWeiter}, {@link #statusZurueck} und {@link #abgerechnet} werfen {@link AngebotStorniert}.
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
 * @param status wie weit das Angebot gediehen ist; am stornierten Angebot der Status, aus dem
 *     storniert wurde
 * @param storniert ob das Angebot storniert ist — nur aus {@code ANGELEGT}, {@code ABGEGEBEN} oder
 *     {@code BESTELLT} (E3)
 * @param stornoGrund warum storniert wurde, hoechstens 2000 Zeichen und ohne Leerraum am Rand, oder
 *     {@code null} fuer ein Storno ohne Grund und fuer jedes nicht stornierte Angebot (E4)
 * @param angebotDatum Datum des Angebots; beim Anlegen der Tag der Anlage, danach aenderbar
 * @param titel die einzeilige Benennung des Angebots, hoechstens 200 Zeichen und ohne Leerraum am
 *     Rand, oder {@code null} fuer ein Angebot von vor Issue #316
 * @param beschreibung der Text des Angebots, oder {@code null}
 * @param positionen die Positionen in ihrer Reihenfolge; die Liste ist die Reihenfolge (E24)
 * @param createdAt Zeitpunkt der Anlage
 * @param updatedAt Zeitpunkt der letzten Aenderung
 * @param version der Stand des Angebots, wie ihn {@code If-Match} vorlegt (Plan #415, E1, E9); 0
 *     vor der ersten Aenderung, danach fuehrt ihn der Bestand
 * @param geaendertVon Kennung des Kontos, das das Angebot zuletzt geaendert hat, oder {@code null},
 *     solange es das nicht weiss (Plan #415, E14)
 */
public record Angebot(
    @Nullable Long id,
    long firmaId,
    @Nullable Long ansprechpartnerId,
    boolean intern,
    Angebotsstatus status,
    boolean storniert,
    @Nullable String stornoGrund,
    LocalDate angebotDatum,
    @Nullable String titel,
    @Nullable String beschreibung,
    List<Angebotsposition> positionen,
    Instant createdAt,
    Instant updatedAt,
    long version,
    @Nullable Long geaendertVon)
    implements Identifiable {

  /**
   * Die hoechste Laenge des Titels in Zeichen (#314, Kriterium 1); so breit ist auch die Spalte.
   */
  private static final int TITEL_LAENGE = 200;

  /**
   * Die hoechste Laenge des Storno-Grundes in Zeichen (E4) — dieselbe wie die des Kommentars am
   * Angebot; die Datenbank haelt sie als CHECK {@code angebot_storno_grund_laenge}.
   */
  private static final int STORNO_GRUND_LAENGE = 2000;

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
   * <p><b>Das Storno passt zum Status, der Grund zum Storno</b> (Issue #333, E3 und E4): Ein
   * storniertes Angebot steht in {@code ANGELEGT}, {@code ABGEGEBEN} oder {@code BESTELLT}; dass es
   * damit nicht intern ist, folgt aus der Bindung von {@code intern} an den Status. Ein Grund steht
   * nur an einem stornierten Angebot, wird wie der Titel am Rand getrimmt und ist danach nicht leer
   * und hoechstens 2000 Zeichen lang. Die Datenbank haelt dieselben Zusagen als die vier CHECKs aus
   * {@code V25__angebot_storno.sql}.
   *
   * @throws IllegalArgumentException wenn Kennzeichen und Status nicht zueinander passen, der Titel
   *     leer oder laenger als 200 Zeichen ist, das Storno nicht zum Status oder der Grund nicht zum
   *     Storno passt oder der Grund leer oder laenger als 2000 Zeichen ist
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
    if (storniert && !stornierbar(status)) {
      throw new IllegalArgumentException("Aus dem Status " + status + " wird nicht storniert");
    }
    stornoGrund = gepruefterStornoGrund(storniert, stornoGrund);
    positionen = List.copyOf(positionen);
  }

  /*
   * Die Statusmenge, aus der storniert werden darf, als switch und nicht als Set: Der Compiler
   * verlangt dann fuer einen neuen Status eine Entscheidung, wie Angebotsstatus es begruendet.
   */
  private static boolean stornierbar(final Angebotsstatus status) {
    return switch (status) {
      case ANGELEGT, ABGEGEBEN, BESTELLT -> true;
      case ERLEDIGT, ABGERECHNET, LAEUFT, ABGESCHLOSSEN -> false;
    };
  }

  private static @Nullable String gepruefterStornoGrund(
      final boolean storniert, final @Nullable String grund) {
    if (grund == null) {
      return null;
    }
    if (!storniert) {
      throw new IllegalArgumentException("Ein Storno-Grund steht nur an einem stornierten Angebot");
    }
    final String getrimmt = grund.strip();
    if (getrimmt.isEmpty() || getrimmt.length() > STORNO_GRUND_LAENGE) {
      throw new IllegalArgumentException(
          "Storno-Grund muss 1 bis "
              + STORNO_GRUND_LAENGE
              + " Zeichen haben, hat "
              + getrimmt.length());
    }
    return getrimmt;
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
        storniert,
        stornoGrund,
        neuesDatum,
        neuerTitel,
        neueBeschreibung,
        neuePositionen,
        createdAt,
        zeitpunkt,
        version,
        geaendertVon);
  }

  /**
   * Das Angebot mit dem Konto, das es gerade geaendert hat (Plan #415, E14).
   *
   * <p>Ein eigener Schritt neben den Uebergaengen und kein weiterer Parameter an jedem davon: Wer
   * aendert, ist eine Angabe des Anwendungsfalls, nicht der fachlichen Regel. Die Version bleibt,
   * wie sie ist — sie zaehlt der Bestand beim Schreiben weiter.
   *
   * @param konto Kennung des handelnden Kontos
   */
  public Angebot durch(final long konto) {
    return new Angebot(
        id,
        firmaId,
        ansprechpartnerId,
        intern,
        status,
        storniert,
        stornoGrund,
        angebotDatum,
        titel,
        beschreibung,
        positionen,
        createdAt,
        updatedAt,
        version,
        konto);
  }

  /**
   * Das Angebot eine Stufe weiter (Kriterium 4).
   *
   * @param zeitpunkt Zeitpunkt des Wechsels
   * @throws AngebotStorniert wenn das Angebot storniert ist (E2)
   * @throws StatusGrenzeErreicht wenn das Angebot schon abgerechnet ist
   */
  public Angebot statusWeiter(final Instant zeitpunkt) {
    pruefeNichtStorniert();
    return mitStatus(status.weiter().orElseThrow(StatusGrenzeErreicht::new), zeitpunkt);
  }

  /**
   * Das Angebot eine Stufe zurueck (Kriterium 4).
   *
   * @param zeitpunkt Zeitpunkt des Wechsels
   * @throws AngebotStorniert wenn das Angebot storniert ist (E2)
   * @throws StatusGrenzeErreicht wenn das Angebot erst angelegt ist
   */
  public Angebot statusZurueck(final Instant zeitpunkt) {
    pruefeNichtStorniert();
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
   * @throws AngebotStorniert wenn das Angebot storniert ist — der Sprung ueberschriebe den Status,
   *     in den die Ruecknahme zurueckfuehren muss (E2)
   */
  public Angebot abgerechnet(final Instant zeitpunkt) {
    pruefeNichtStorniert();
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
        storniert,
        stornoGrund,
        angebotDatum,
        titel,
        beschreibung,
        positionen,
        createdAt,
        zeitpunkt,
        version,
        geaendertVon);
  }

  /**
   * Das Angebot als storniert — der Status bleibt, wie er ist (Issue #333, E1).
   *
   * <p>Ob aus diesem Status storniert werden darf, entscheidet der Konstruktor (E3); getrimmt und
   * geprueft wird auch der Grund dort (E4).
   *
   * @param grund warum storniert wird, oder {@code null} fuer ein Storno ohne Grund
   * @param zeitpunkt Zeitpunkt des Stornos
   * @throws IllegalArgumentException wenn aus dem Status nicht storniert werden darf oder der Grund
   *     leer oder laenger als 2000 Zeichen ist
   */
  public Angebot storniert(final @Nullable String grund, final Instant zeitpunkt) {
    return mitStorno(true, grund, zeitpunkt);
  }

  /**
   * Das Angebot ohne Storno und ohne dessen Grund — im Status, aus dem storniert wurde (#330,
   * Kriterium 9).
   *
   * @param zeitpunkt Zeitpunkt der Ruecknahme
   */
  public Angebot stornoZurueckgenommen(final Instant zeitpunkt) {
    return mitStorno(false, null, zeitpunkt);
  }

  /**
   * Ob sich das Angebot jetzt stornieren laesst (Issue #334): Es ist noch nicht storniert (Plan
   * #332, E17), und sein Status gehoert zu denen, aus denen storniert wird (E3).
   *
   * <p>Der Anwendungsfall fragt damit vor {@link #storniert}, damit ein unzulaessiges Storno als
   * {@code StornoNichtMoeglich} mit lesbarem Satz abweist und nicht als {@link
   * IllegalArgumentException} des Konstruktors.
   */
  public boolean laesstSichStornieren() {
    return !storniert && stornierbar(status);
  }

  private void pruefeNichtStorniert() {
    if (storniert) {
      throw new AngebotStorniert();
    }
  }

  private Angebot mitStorno(
      final boolean neuStorniert, final @Nullable String grund, final Instant zeitpunkt) {
    return new Angebot(
        id,
        firmaId,
        ansprechpartnerId,
        intern,
        status,
        neuStorniert,
        grund,
        angebotDatum,
        titel,
        beschreibung,
        positionen,
        createdAt,
        zeitpunkt,
        version,
        geaendertVon);
  }

  private Angebot mitStatus(final Angebotsstatus ziel, final Instant zeitpunkt) {
    return new Angebot(
        id,
        firmaId,
        ansprechpartnerId,
        intern,
        ziel,
        storniert,
        stornoGrund,
        angebotDatum,
        titel,
        beschreibung,
        positionen,
        createdAt,
        zeitpunkt,
        version,
        geaendertVon);
  }
}
