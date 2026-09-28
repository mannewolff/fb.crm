package org.mwolff.fbcrm.auftrag.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
import org.mwolff.fbcrm.angebot.domain.Belegabsender;
import org.mwolff.fbcrm.angebot.domain.Belegempfaenger;
import org.mwolff.fbcrm.auftrag.domain.Auftrag;
import org.mwolff.fbcrm.auftrag.domain.Auftragsposition;
import org.mwolff.fbcrm.auftrag.domain.Auftragsstatus;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;

/**
 * Die Quelle und ihr Ergebnis, gegen die die Anwendungsfaelle dieses Pakets laufen.
 *
 * <p>An einer Stelle, weil zwei Testklassen dasselbe Angebot brauchen: ein festgeschriebenes mit
 * zwei Positionen — die erste nach Aufwand, die zweite zum Festpreis. Genau dieses Paar traegt die
 * Positionspruefung: Die Aufwandsposition braucht „Stunden je Personentag", die Festpreisposition
 * darf sie nicht tragen (E10).
 */
final class Auftragsdoppel {

  /** Der Vorgang, an dem Angebot und Auftrag haengen. */
  static final long VORGANG = 3L;

  /** Das angenommene Angebot, aus dem der Auftrag entsteht. */
  static final long ANGEBOT = 11L;

  /** Die Nummer des Quell-Angebots — sie steht am Auftrag (Kriterium 3). */
  static final String ANGEBOTSNUMMER = "A-2026-011";

  /** Die Nummer, die der Nummernkreis im Test zieht. */
  static final String AUFTRAGSNUMMER = "AU-2026-001";

  static final LocalDate ANGEBOTSDATUM = LocalDate.of(2026, 9, 20);
  static final LocalDate GUELTIG_BIS = LocalDate.of(2026, 10, 20);
  static final Instant ANGELEGT = Instant.parse("2026-09-20T08:00:00Z");
  static final Instant VERSENDET_AM = Instant.parse("2026-09-21T09:00:00Z");

  /** Platz 1 im Angebot: nach Aufwand, und damit mit „Stunden je Personentag". */
  static final Angebotsposition KONZEPTION =
      new Angebotsposition(
          "Konzeption",
          Abrechnungsmodus.AUFWAND,
          new BigDecimal("2.50"),
          Einheit.PERSONENTAG,
          new BigDecimal("1000.01"));

  /** Platz 2 im Angebot: zum Festpreis, und damit ohne „Stunden je Personentag". */
  static final Angebotsposition SCHULUNG =
      new Angebotsposition(
          "Schulungstag",
          Abrechnungsmodus.FESTPREIS,
          new BigDecimal("1.00"),
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

  private Auftragsdoppel() {}

  /** Das Angebot in einem beliebigen Zustand, mit beiden Positionen. */
  static Angebot angebot(final Angebotszustand zustand) {
    final boolean entwurf = zustand == Angebotszustand.ENTWURF;
    return new Angebot(
        Long.valueOf(ANGEBOT),
        VORGANG,
        entwurf ? null : ANGEBOTSNUMMER,
        zustand,
        ANGEBOTSDATUM,
        GUELTIG_BIS,
        "Neugestaltung der Website",
        "Zahlbar innerhalb von 14 Tagen ohne Abzug.",
        entwurf ? null : VERSENDET_AM,
        null,
        entwurf ? null : "angebot/11/beleg.pdf",
        entwurf ? null : EMPFAENGER,
        entwurf ? null : ABSENDER,
        List.of(KONZEPTION, SCHULUNG),
        ANGELEGT,
        ANGELEGT);
  }

  /** Das angenommene Angebot — der einzige Zustand, aus dem ein Auftrag entsteht (Kriterium 1). */
  static Angebot angenommenesAngebot() {
    return angebot(Angebotszustand.ANGENOMMEN);
  }

  /** Der Vorgang, offen oder abgeschlossen (Kriterium 11). */
  static Vorgang vorgang(final boolean abgeschlossen) {
    return new Vorgang(
        Long.valueOf(VORGANG),
        12L,
        "Website-Relaunch",
        7L,
        null,
        null,
        null,
        abgeschlossen,
        ANGELEGT,
        ANGELEGT);
  }

  /** Ein gespeicherter Auftrag mit einer Position. */
  static Auftrag auftrag(final long id) {
    return new Auftrag(
        Long.valueOf(id),
        VORGANG,
        ANGEBOT,
        AUFTRAGSNUMMER,
        Auftragsstatus.OFFEN,
        LocalDate.of(2026, 9, 28),
        "BST-4711",
        LocalDate.of(2026, 10, 1),
        LocalDate.of(2026, 12, 31),
        List.of(
            new Auftragsposition(
                KONZEPTION.bezeichnung(),
                KONZEPTION.abrechnungsmodus(),
                KONZEPTION.menge(),
                KONZEPTION.einheit(),
                KONZEPTION.einzelpreis(),
                new BigDecimal("7.50"))),
        ANGELEGT,
        ANGELEGT);
  }
}
