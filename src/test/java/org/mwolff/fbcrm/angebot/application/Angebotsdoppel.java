package org.mwolff.fbcrm.angebot.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
import org.mwolff.fbcrm.angebot.domain.Belegabsender;
import org.mwolff.fbcrm.angebot.domain.Belegempfaenger;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Einheit;

/**
 * Die Angebote, gegen die die Anwendungsfaelle dieses Pakets laufen.
 *
 * <p>An einer Stelle, weil vier Testklassen dieselben brauchen: einen Entwurf und je ein Angebot in
 * jedem der vier festgeschriebenen Zustaende. Die festgeschriebenen tragen Nummer, Versandzeitpunkt
 * und beide Anschriftskopien — ohne sie waeren sie Gebilde, die es im Bestand nicht gibt, und die
 * Pruefung liefe gegen eine Erfindung.
 */
final class Angebotsdoppel {

  /** Der Vorgang, an dem die Angebote dieses Pakets haengen. */
  static final long VORGANG = 3L;

  /** Ein anderer Vorgang — die Quelle, die als Vorlage nicht zur Wahl steht (E23). */
  static final long FREMDER_VORGANG = 4L;

  static final LocalDate ANGEBOTSDATUM = LocalDate.of(2026, 9, 20);
  static final LocalDate GUELTIG_BIS = LocalDate.of(2026, 10, 20);
  static final Instant ANGELEGT = Instant.parse("2026-09-20T08:00:00Z");
  static final Instant VERSENDET_AM = Instant.parse("2026-09-21T09:00:00Z");

  static final String BESCHREIBUNG = "Neugestaltung der Website";
  static final String BEDINGUNGEN = "Zahlbar innerhalb von 14 Tagen ohne Abzug.";

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

  private static final Belegempfaenger EMPFAENGER =
      new Belegempfaenger(
          "Adler AG", new Anschrift("Hauptstrasse 1", "28195", "Bremen", "Deutschland"), null);

  private static final Belegabsender ABSENDER =
      new Belegabsender(
          "Manfred Wolff",
          new Anschrift("Am Deich 2", "28199", "Hansestadt", "Bundesrepublik"),
          null,
          null,
          null,
          null,
          null);

  private Angebotsdoppel() {}

  /** Ein Entwurf mit Kennung, zwei Positionen und beiden Texten. */
  static Angebot entwurf(final long id) {
    return entwurf(id, VORGANG, List.of(KONZEPTION, SCHULUNG));
  }

  /** Ein Entwurf an einem beliebigen Vorgang mit beliebigen Positionen. */
  static Angebot entwurf(final long id, final long vorgangId, final List<Angebotsposition> zeilen) {
    return angebot(id, vorgangId, Angebotszustand.ENTWURF, zeilen, ANGELEGT);
  }

  /** Ein Angebot in einem der vier festgeschriebenen Zustaende. */
  static Angebot festgeschrieben(final long id, final Angebotszustand zustand) {
    return angebot(id, VORGANG, zustand, List.of(KONZEPTION), ANGELEGT);
  }

  /** Ein Angebot mit frei gewaehltem Anlagezeitpunkt — fuer die Reihenfolge aus Kriterium 20. */
  static Angebot angebot(
      final long id,
      final long vorgangId,
      final Angebotszustand zustand,
      final List<Angebotsposition> zeilen,
      final Instant angelegt) {
    return angebot(id, vorgangId, zustand, zeilen, angelegt, GUELTIG_BIS);
  }

  /**
   * Ein Angebot mit frei gewaehlter Gueltigkeit — fuer die Auswahl der Pipeline (Kriterium 23).
   *
   * <p>Die Gueltigkeit ist dort der Unterschied zwischen „zaehlt" und „abgelaufen" (E4), und sie
   * liegt gegen den heutigen Tag der jeweiligen Pruefung, nicht gegen ein festes Datum.
   */
  static Angebot angebot(
      final long id,
      final long vorgangId,
      final Angebotszustand zustand,
      final List<Angebotsposition> zeilen,
      final Instant angelegt,
      final LocalDate gueltigBis) {
    final boolean entwurf = zustand == Angebotszustand.ENTWURF;
    return new Angebot(
        Long.valueOf(id),
        vorgangId,
        entwurf ? null : "A-2026-%03d".formatted(id),
        zustand,
        ANGEBOTSDATUM,
        gueltigBis,
        BESCHREIBUNG,
        BEDINGUNGEN,
        entwurf ? null : VERSENDET_AM,
        null,
        entwurf ? null : "angebot/%d/beleg.pdf".formatted(id),
        entwurf ? null : EMPFAENGER,
        entwurf ? null : ABSENDER,
        zeilen,
        angelegt,
        angelegt);
  }
}
