package org.mwolff.fbcrm.rechnung.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.rechnung.application.Abrechnungszeile;
import org.mwolff.fbcrm.rechnung.application.Rechnungsansicht;
import org.mwolff.fbcrm.rechnung.domain.Belegabsender;
import org.mwolff.fbcrm.rechnung.domain.Belegempfaenger;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.Rechnungsposition;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;

/**
 * Die Angebote, Rechnungen und Ansichten, gegen die die Controller dieses Pakets laufen.
 *
 * <p>An einer Stelle, weil drei Testklassen dieselben brauchen: ein bestelltes Angebot ueber 160
 * Stunden zu 100,00 € und Rechnungen darauf. Dieselben Zahlen wie im Ziel von #160.
 */
final class Webdoppel {

  /** Die Firma, an die das Angebot geht. */
  static final long FIRMA = 5L;

  /** Der Name dieser Firma. */
  static final String FIRMENNAME = "Adler AG";

  /** Das Angebot, das abgerechnet wird. */
  static final long ANGEBOT = 11L;

  /** Die Kennung der Angebotsposition: 160 Stunden zu 100,00 €. */
  static final long BERATUNG_ID = 101L;

  /** Die Kennung der Rechnung, um die es in den Tests geht. */
  static final long RECHNUNG = 7L;

  static final LocalDate ANGEBOTSDATUM = LocalDate.of(2026, 9, 20);
  static final LocalDate RECHNUNGSDATUM = LocalDate.of(2026, 9, 30);
  static final Instant ANGELEGT = Instant.parse("2026-09-20T08:00:00Z");
  static final String ZEITRAUM = "September 2026";
  static final BigDecimal STUNDENSATZ = new BigDecimal("100.00");
  static final BigDecimal STEUERSATZ = new BigDecimal("19.00");

  static final Angebotsposition BERATUNG =
      new Angebotsposition(
          Long.valueOf(BERATUNG_ID),
          "Beratung",
          Abrechnungsmodus.AUFWAND,
          new BigDecimal("160.00"),
          Einheit.STUNDE,
          STUNDENSATZ);

  /** Die Kopie des Empfaengers an einer gestellten Rechnung — die Firma hiess damals anders. */
  static final Belegempfaenger EMPFAENGER_KOPIE =
      new Belegempfaenger(
          "Adler Aktiengesellschaft",
          new Anschrift("Hauptstrasse 1", "28195", "Bremen", "Deutschland"),
          null);

  /** Die Kopie der eigenen Angaben an einer gestellten Rechnung. */
  static final Belegabsender ABSENDER_KOPIE =
      new Belegabsender(
          "Manfred Wolff",
          "Softwarearchitekt",
          new Anschrift("Am Deich 2", "28199", "Bremen", "Deutschland"),
          "post@example.org",
          "0421 123456",
          "75/123/45678",
          "DE123456789",
          "DE02 1203 0000 0000 2020 51",
          "https://example.org");

  private Webdoppel() {}

  /** Das bestellte Angebot mit seiner einen Position. */
  static Angebot angebot() {
    return new Angebot(
        Long.valueOf(ANGEBOT),
        FIRMA,
        null,
        Angebotsstatus.BESTELLT,
        ANGEBOTSDATUM,
        "Neugestaltung der Website",
        List.of(BERATUNG),
        ANGELEGT,
        ANGELEGT);
  }

  /** Ein Rechnungsentwurf ueber die angegebene Menge Beratung. */
  static Rechnung entwurf(final String menge) {
    return new Rechnung(
        Long.valueOf(RECHNUNG),
        ANGEBOT,
        Rechnungszustand.ENTWURF,
        RECHNUNGSDATUM,
        ZEITRAUM,
        List.of(beratung(menge)),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        ANGELEGT,
        ANGELEGT);
  }

  /** Eine gestellte Rechnung mit ihrer Nummer und ihrem festgeschriebenen Satz. */
  static Rechnung gestellt(final long id, final String nummer, final String menge) {
    return new Rechnung(
        Long.valueOf(id),
        ANGEBOT,
        Rechnungszustand.GESTELLT,
        RECHNUNGSDATUM,
        ZEITRAUM,
        List.of(beratung(menge)),
        nummer,
        new BigDecimal("7.00"),
        Integer.valueOf(10),
        ANGELEGT,
        null,
        null,
        null,
        ANGELEGT,
        ANGELEGT);
  }

  /** Dieselbe gestellte Rechnung, festgeschrieben mit den Kopien von Empfaenger und Absender. */
  static Rechnung gestelltMitKopien(final String nummer, final String menge) {
    return entwurf(menge)
        .gestellt(nummer, new BigDecimal("7.00"), 14, EMPFAENGER_KOPIE, ABSENDER_KOPIE, ANGELEGT);
  }

  /** Eine Rechnungsposition zur Beratung ueber die angegebene Menge. */
  static Rechnungsposition beratung(final String menge) {
    return new Rechnungsposition(
        BERATUNG_ID, "Beratung", new BigDecimal(menge), Einheit.STUNDE, STUNDENSATZ);
  }

  /**
   * Die Ansicht zu einer Rechnung: eine Zeile zur Beratung, abgerechnet ohne diese Rechnung.
   *
   * @param rechnung die Rechnung
   * @param abgerechnet die Menge aus allen anderen Rechnungen dieses Angebots
   */
  static Rechnungsansicht ansicht(final Rechnung rechnung, final String abgerechnet) {
    final BigDecimal fremd = new BigDecimal(abgerechnet);
    final BigDecimal jetzt = rechnung.positionen().getFirst().menge();
    final BigDecimal offen = BigDecimal.ZERO.max(BERATUNG.menge().subtract(fremd));
    return new Rechnungsansicht(
        rechnung,
        FIRMA,
        rechnung.empfaenger() == null ? FIRMENNAME : rechnung.empfaenger().firma(),
        rechnung.steuersatz() == null ? STEUERSATZ : rechnung.steuersatz(),
        List.of(
            new Abrechnungszeile(
                BERATUNG,
                "Beratung",
                STUNDENSATZ,
                fremd,
                offen,
                BigDecimal.ZERO.max(fremd.add(jetzt).subtract(BERATUNG.menge())),
                jetzt)));
  }
}
