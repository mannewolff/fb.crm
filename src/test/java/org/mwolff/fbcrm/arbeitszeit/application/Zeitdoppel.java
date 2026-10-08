package org.mwolff.fbcrm.arbeitszeit.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.arbeitszeit.domain.Zeiteintrag;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.firma.domain.Firma;

/**
 * Das Angebot, die Firma und die Zeiteintraege, gegen die die Anwendungsfaelle dieses Pakets
 * laufen.
 *
 * <p>An einer Stelle, weil mehrere Testklassen dieselben brauchen: ein Angebot mit drei Positionen
 * — zwei buchbare nach Aufwand in Stunden und eine Pauschale, auf die nicht gebucht werden darf.
 * Die Namen sind die des Beispiels aus Issue #193: „Konzeption" bei „IT Bildungshaus".
 */
final class Zeitdoppel {

  /** Die Firma, an die das Angebot geht. */
  static final long FIRMA = 5L;

  /** Ihr Name — er steht in der Meldung der Ueberschneidung. */
  static final String FIRMENNAME = "IT Bildungshaus";

  /** Das Angebot, auf dessen Positionen gebucht wird. */
  static final long ANGEBOT = 11L;

  /** Die erste buchbare Position: nach Aufwand in Stunden. */
  static final long KONZEPTION_ID = 101L;

  /** Die zweite buchbare Position — fuer die Ueberschneidung ueber zwei Positionen. */
  static final long WARTUNG_ID = 102L;

  /** Die Pauschale: weder nach Aufwand noch in Stunden, also nicht buchbar. */
  static final long SCHULUNG_ID = 103L;

  /** Eine Kennung, die zu keiner Position eines Angebots gehoert. */
  static final long FREMDE_POSITION = 999L;

  /** Der Tag, an dem in diesen Tests gearbeitet wird. */
  static final LocalDate TAG = LocalDate.of(2026, 11, 12);

  /** Die Uhr, gegen die die Zeitstempel geprueft werden. */
  static final Instant JETZT = Instant.parse("2026-11-12T17:30:00Z");

  /** Ein frueherer Zeitpunkt — der Anlagezeitpunkt eines vorhandenen Eintrags. */
  static final Instant FRUEHER = Instant.parse("2026-11-12T08:00:00Z");

  static final Angebotsposition KONZEPTION =
      new Angebotsposition(
          Long.valueOf(KONZEPTION_ID),
          "Konzeption",
          Abrechnungsmodus.AUFWAND,
          new BigDecimal("20.00"),
          Einheit.STUNDE,
          new BigDecimal("120.00"));

  static final Angebotsposition WARTUNG =
      new Angebotsposition(
          Long.valueOf(WARTUNG_ID),
          "Wartung",
          Abrechnungsmodus.AUFWAND,
          new BigDecimal("10.00"),
          Einheit.STUNDE,
          new BigDecimal("130.00"));

  static final Angebotsposition SCHULUNG =
      new Angebotsposition(
          Long.valueOf(SCHULUNG_ID),
          "Schulungstag",
          Abrechnungsmodus.FESTPREIS,
          BigDecimal.ONE,
          Einheit.PAUSCHAL,
          new BigDecimal("1200.00"));

  private Zeitdoppel() {}

  /** Das bestellte Angebot mit seinen drei Positionen. */
  static Angebot angebot() {
    return angebot(Angebotsstatus.BESTELLT);
  }

  /** Dasselbe Angebot in einem frei gewaehlten Status. */
  static Angebot angebot(final Angebotsstatus status) {
    return new Angebot(
        Long.valueOf(ANGEBOT),
        FIRMA,
        null,
        status.intern(),
        status,
        TAG.withDayOfMonth(1),
        "Neugestaltung der Website",
        List.of(KONZEPTION, WARTUNG, SCHULUNG),
        FRUEHER,
        FRUEHER);
  }

  /** Die Firma des Angebots, wie der Bestand sie fuehrt. */
  static Firma firma() {
    return new Firma(
        Long.valueOf(FIRMA),
        FIRMENNAME,
        new Anschrift(null, null, null, null),
        null,
        null,
        true,
        FRUEHER,
        FRUEHER);
  }

  /**
   * Ein gespeicherter Zeiteintrag am {@link #TAG}.
   *
   * @param id Kennung des Eintrags
   * @param angebotPositionId Kennung der Position, auf die gebucht wurde
   * @param von Beginn als {@code H:mm}
   * @param bis Ende als {@code H:mm}
   */
  static Zeiteintrag zeiteintrag(
      final long id, final long angebotPositionId, final String von, final String bis) {
    return new Zeiteintrag(
        Long.valueOf(id),
        angebotPositionId,
        TAG,
        LocalTime.parse(von),
        LocalTime.parse(bis),
        FRUEHER,
        FRUEHER);
  }
}
