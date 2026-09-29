package org.mwolff.fbcrm.angebot.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;

/**
 * Die Angebote, gegen die die Anwendungsfaelle dieses Pakets laufen.
 *
 * <p>An einer Stelle, weil mehrere Testklassen dieselben brauchen: ein Angebot mit zwei Positionen,
 * wahlweise in einem beliebigen Status oder mit frei gewaehltem Datum.
 */
final class Angebotsdoppel {

  /** Die Firma, an die die Angebote dieses Pakets gehen. */
  static final long FIRMA = 5L;

  /** Eine andere Firma — deren Ansprechpartner fuer {@link #FIRMA} nicht zur Wahl steht. */
  static final long FREMDE_FIRMA = 6L;

  /** Der Ansprechpartner bei {@link #FIRMA}, den die Angebote dieses Pakets tragen. */
  static final long ANSPRECHPARTNER = 8L;

  static final LocalDate ANGEBOTSDATUM = LocalDate.of(2026, 9, 20);
  static final Instant ANGELEGT = Instant.parse("2026-09-20T08:00:00Z");

  static final String BESCHREIBUNG = "Neugestaltung der Website";

  static final Angebotsposition KONZEPTION =
      new Angebotsposition(
          "Konzeption",
          Abrechnungsmodus.AUFWAND,
          new BigDecimal("2.50"),
          Einheit.PERSONENTAG,
          new BigDecimal("1000.01"));

  static final Angebotsposition SCHULUNG =
      new Angebotsposition(
          "Schulungstag",
          Abrechnungsmodus.FESTPREIS,
          BigDecimal.ONE,
          Einheit.PAUSCHAL,
          new BigDecimal("1200.00"));

  private Angebotsdoppel() {}

  /** Ein angelegtes Angebot mit Kennung, zwei Positionen und Beschreibung. */
  static Angebot angebot(final long id) {
    return angebot(id, Angebotsstatus.ANGELEGT);
  }

  /** Dasselbe Angebot in einem frei gewaehlten Status. */
  static Angebot angebot(final long id, final Angebotsstatus status) {
    return angebot(id, FIRMA, status, ANGEBOTSDATUM);
  }

  /** Ein Angebot mit frei gewaehlter Firma, Status und Datum — fuer die Reihenfolge der Liste. */
  static Angebot angebot(
      final long id, final long firmaId, final Angebotsstatus status, final LocalDate datum) {
    return new Angebot(
        Long.valueOf(id),
        firmaId,
        Long.valueOf(ANSPRECHPARTNER),
        status,
        datum,
        BESCHREIBUNG,
        List.of(KONZEPTION, SCHULUNG),
        ANGELEGT,
        ANGELEGT);
  }

  /** Dasselbe Angebot ohne Ansprechpartner — der Ansprechpartner ist optional (Issue #126). */
  static Angebot ohneAnsprechpartner(final Angebot angebot) {
    return new Angebot(
        angebot.id(),
        angebot.firmaId(),
        null,
        angebot.status(),
        angebot.angebotDatum(),
        angebot.beschreibung(),
        angebot.positionen(),
        angebot.createdAt(),
        angebot.updatedAt());
  }
}
