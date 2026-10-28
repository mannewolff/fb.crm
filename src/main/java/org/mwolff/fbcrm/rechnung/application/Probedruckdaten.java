package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.common.Geldrechnung;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;
import org.mwolff.fbcrm.rechnung.application.RechnungDruckdaten.Druckposition;
import org.mwolff.fbcrm.rechnung.domain.Belegabsender;
import org.mwolff.fbcrm.rechnung.domain.Belegempfaenger;

/**
 * Die Beispieldaten des Probedrucks (Plan #363, E14): erfunden, was die Rechnung ausmacht, echt,
 * was den Absender ausmacht.
 *
 * <p>Empfaenger, Nummer, Datum und Positionen sind erfunden; es wird keine Rechnung angelegt und
 * keine Nummer gezogen. Die {@value #ANZAHL_POSITIONEN} Positionen brechen je auf zwei Zeilen um
 * und fuellen damit mehr als eine Seite, auch wenn ein Briefbogen gar keinen Rand laesst — der
 * Probedruck zeigt so immer auch eine Folgeseite. {@link #mitEinerPosition()} ist dieselbe Probe
 * mit nur der ersten; an ihr misst die Randpruefung (Kriterium 13).
 *
 * <p>Absenderzeile sowie Steuer- und Bankzeile kommen aus den <b>echten</b> eigenen Angaben, auch
 * wenn davon nur ein Teil da ist. Der eigene Name ist dort {@code @Nullable}, an einem {@link
 * Belegabsender} aber Pflicht. Er steht deshalb fuer sich in {@link #name()}, und das {@link
 * Briefbogenlayout} liest ihn nur von dort; der Absender der Druckdaten traegt ihn ebenfalls, wenn
 * es ihn gibt, und sonst den leeren Text.
 *
 * @param name der eigene Name, oder {@code null}
 * @param druckdaten der Beleg, der gesetzt wird
 */
record Probedruckdaten(@Nullable String name, RechnungDruckdaten druckdaten) {

  /** So viele Positionen hat die Probe. */
  private static final int ANZAHL_POSITIONEN = 24;

  /** Der erfundene Empfaenger. */
  private static final Belegempfaenger EMPFAENGER =
      new Belegempfaenger(
          "Beispiel GmbH", new Anschrift("Musterweg 1", "12345", "Musterstadt", null), null);

  /** Die erfundene Rechnungsnummer; sie stammt aus keinem Nummernkreis. */
  private static final String NUMMER = "Muster-001";

  /** Das erfundene Rechnungsdatum. */
  private static final LocalDate DATUM = LocalDate.of(2026, 9, 30);

  /** Der erfundene Leistungszeitraum. */
  private static final String LEISTUNGSZEITRAUM = "September 2026";

  /** Menge je Position. */
  private static final BigDecimal MENGE = new BigDecimal("8");

  /** Einzelpreis je Position. */
  private static final BigDecimal EINZELPREIS = new BigDecimal("95.00");

  /** Der Steuersatz in Prozent. */
  private static final BigDecimal STEUERSATZ = new BigDecimal("19");

  /** Prozent in Hundertsteln: um so viele Stellen wandert das Komma. */
  private static final int PROZENT = 2;

  /** Das Zahlungsziel in Tagen. */
  private static final int ZAHLUNGSZIEL_TAGE = 14;

  /**
   * Die Probe mit den eigenen Angaben, wie sie gerade stehen.
   *
   * @param eigene die eigenen Angaben; jede darf fehlen
   * @return die Probe mit allen Positionen
   */
  static Probedruckdaten von(final EigeneAngaben eigene) {
    final Belegabsender absender =
        new Belegabsender(
            Objects.requireNonNullElse(eigene.name(), ""),
            eigene.berufsbezeichnung(),
            eigene.anschrift(),
            eigene.email(),
            eigene.telefon(),
            eigene.steuernummer(),
            eigene.umsatzsteuerId(),
            // Bis zur Belegkopie mit Bankfeldern (#467) der bisherige Freitext.
            // Bis zur Belegkopie mit Bankfeldern (#467) der bisherige Freitext.
            eigene.bisherigeBankverbindung(),
            eigene.webadresse());
    return new Probedruckdaten(eigene.name(), beleg(absender, ANZAHL_POSITIONEN));
  }

  /**
   * Dieselbe Probe mit nur der ersten Position, fuer die Randpruefung.
   *
   * @return die Probe mit einer Position und den Summen zu ihr
   */
  Probedruckdaten mitEinerPosition() {
    return new Probedruckdaten(name, beleg(druckdaten.absender(), 1));
  }

  private static RechnungDruckdaten beleg(final Belegabsender absender, final int anzahl) {
    final List<Druckposition> positionen =
        IntStream.rangeClosed(1, anzahl).mapToObj(Probedruckdaten::position).toList();
    final BigDecimal netto =
        Geldrechnung.summe(positionen.stream().map(Druckposition::gesamtpreis));
    final BigDecimal steuer = Geldrechnung.betrag(netto, STEUERSATZ.movePointLeft(PROZENT));
    return new RechnungDruckdaten(
        absender,
        EMPFAENGER,
        NUMMER,
        DATUM,
        LEISTUNGSZEITRAUM,
        positionen,
        netto,
        STEUERSATZ,
        steuer,
        netto.add(steuer),
        ZAHLUNGSZIEL_TAGE);
  }

  private static Druckposition position(final int nummer) {
    return new Druckposition(
        MENGE,
        Einheit.STUNDE,
        "Beispielleistung " + nummer + ", Konzeption und Umsetzung",
        EINZELPREIS,
        Geldrechnung.betrag(MENGE, EINZELPREIS));
  }
}
