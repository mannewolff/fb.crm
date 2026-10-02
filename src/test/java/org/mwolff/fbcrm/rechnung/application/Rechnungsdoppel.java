package org.mwolff.fbcrm.rechnung.application;

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
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.rechnung.domain.Belegabsender;
import org.mwolff.fbcrm.rechnung.domain.Belegempfaenger;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.Rechnungsposition;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;

/**
 * Die Angebote, Rechnungen und Firmen, gegen die die Anwendungsfaelle dieses Pakets laufen.
 *
 * <p>An einer Stelle, weil sechs Testklassen dieselben brauchen: ein bestelltes Angebot mit zwei
 * Positionen — 160 Stunden zu 100,00 € und eine Pauschale zu 1.200,00 € —, dazu Rechnungen darauf.
 * Die Zahlen sind die des Ziels von #160: 160 angeboten, 80 in diesem Monat.
 *
 * <p>Daneben stehen zwei Stuecke, die {@link #angebot()} nicht traegt und die nur die Vorbelegung
 * aus der Arbeitszeit braucht (Issue #199): {@link #WARTUNG} als <b>zweite</b> buchbare Position —
 * an einer einzigen liesse sich nicht zeigen, dass eine buchbare Position ohne Stunden im Entwurf
 * fehlt — und {@link #beratungUeber(String)} fuer die 20 angebotenen Stunden des Beispiels aus
 * Issue #193.
 */
final class Rechnungsdoppel {

  /** Die Firma, an die das Angebot geht. */
  static final long FIRMA = 5L;

  /** Der Name dieser Firma. */
  static final String FIRMENNAME = "Adler AG";

  /** Das Angebot, das abgerechnet wird. */
  static final long ANGEBOT = 11L;

  /** Die Kennung der ersten Angebotsposition: 160 Stunden zu 100,00 €. */
  static final long BERATUNG_ID = 101L;

  /** Die Kennung der zweiten Angebotsposition: eine Pauschale zu 1.200,00 €. */
  static final long PAUSCHALE_ID = 102L;

  /** Die Kennung einer zweiten buchbaren Position — auch sie rechnet nach Aufwand in Stunden. */
  static final long WARTUNG_ID = 103L;

  /** Eine Kennung, die zu keiner Position dieses Angebots gehoert. */
  static final long FREMDE_POSITION = 999L;

  static final LocalDate ANGEBOTSDATUM = LocalDate.of(2026, 9, 20);
  static final Instant ANGELEGT = Instant.parse("2026-09-20T08:00:00Z");
  static final String ZEITRAUM = "September 2026";

  static final BigDecimal STUNDENSATZ = new BigDecimal("100.00");
  static final BigDecimal PAUSCHALPREIS = new BigDecimal("1200.00");

  static final Angebotsposition BERATUNG =
      new Angebotsposition(
          Long.valueOf(BERATUNG_ID),
          "Beratung",
          Abrechnungsmodus.AUFWAND,
          new BigDecimal("160.00"),
          Einheit.STUNDE,
          STUNDENSATZ);

  static final Angebotsposition WARTUNG =
      new Angebotsposition(
          Long.valueOf(WARTUNG_ID),
          "Wartung",
          Abrechnungsmodus.AUFWAND,
          new BigDecimal("10.00"),
          Einheit.STUNDE,
          new BigDecimal("130.00"));

  static final Angebotsposition PAUSCHALE =
      new Angebotsposition(
          Long.valueOf(PAUSCHALE_ID),
          "Schulungstag",
          Abrechnungsmodus.FESTPREIS,
          BigDecimal.ONE,
          Einheit.PAUSCHAL,
          PAUSCHALPREIS);

  private Rechnungsdoppel() {}

  /** Das bestellte Angebot mit seinen beiden Positionen. */
  static Angebot angebot() {
    return angebot(Angebotsstatus.BESTELLT, List.of(BERATUNG, PAUSCHALE));
  }

  /** Dasselbe Angebot in einem frei gewaehlten Status. */
  static Angebot angebot(final Angebotsstatus status) {
    return angebot(status, List.of(BERATUNG, PAUSCHALE));
  }

  /** Dasselbe Angebot mit frei gewaehlten Positionen — etwa nach einer Preisaenderung. */
  static Angebot angebot(final Angebotsstatus status, final List<Angebotsposition> positionen) {
    return angebot(ANGEBOT, status, positionen);
  }

  /** Ein Angebot mit frei gewaehlter Kennung — fuer die Listen ueber mehrere Angebote. */
  static Angebot angebot(
      final long id, final Angebotsstatus status, final List<Angebotsposition> positionen) {
    return new Angebot(
        Long.valueOf(id),
        FIRMA,
        null,
        status.intern(),
        status,
        ANGEBOTSDATUM,
        "Neugestaltung der Website",
        positionen,
        ANGELEGT,
        ANGELEGT);
  }

  /** Ein Rechnungsentwurf auf {@link #ANGEBOT} mit den uebergebenen Positionen. */
  static Rechnung entwurf(final long id, final List<Rechnungsposition> positionen) {
    return entwurf(id, ANGEBOT, positionen);
  }

  /** Ein Rechnungsentwurf auf ein frei gewaehltes Angebot. */
  static Rechnung entwurf(
      final long id, final long angebotId, final List<Rechnungsposition> positionen) {
    return new Rechnung(
        Long.valueOf(id),
        angebotId,
        Rechnungszustand.ENTWURF,
        LocalDate.of(2026, 9, 30),
        ZEITRAUM,
        positionen,
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

  /** Eine gestellte Rechnung auf {@link #ANGEBOT}, festgeschrieben mit ihrem eigenen Steuersatz. */
  static Rechnung gestellt(
      final long id, final String nummer, final List<Rechnungsposition> positionen) {
    return gestellt(id, ANGEBOT, nummer, positionen, LocalDate.of(2026, 9, 30));
  }

  /** Dieselbe gestellte Rechnung mit frei gewaehltem Angebot und Rechnungsdatum. */
  static Rechnung gestellt(
      final long id,
      final long angebotId,
      final String nummer,
      final List<Rechnungsposition> positionen,
      final LocalDate rechnungDatum) {
    return new Rechnung(
        Long.valueOf(id),
        angebotId,
        Rechnungszustand.GESTELLT,
        rechnungDatum,
        ZEITRAUM,
        positionen,
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
  static Rechnung gestelltMitKopien(
      final long id, final String nummer, final List<Rechnungsposition> positionen) {
    return entwurf(id, positionen)
        .gestellt(nummer, new BigDecimal("7.00"), 10, EMPFAENGER_KOPIE, ABSENDER_KOPIE, ANGELEGT);
  }

  /**
   * Dieselbe Beratung mit frei gewaehlter angebotener Menge.
   *
   * <p>Fuer das Beispiel aus Issue #193: dort sind 20 Stunden angeboten und nicht 160.
   *
   * @param menge die angebotene Menge
   */
  static Angebotsposition beratungUeber(final String menge) {
    return new Angebotsposition(
        Long.valueOf(BERATUNG_ID),
        "Beratung",
        Abrechnungsmodus.AUFWAND,
        new BigDecimal(menge),
        Einheit.STUNDE,
        STUNDENSATZ);
  }

  /** Eine Rechnungsposition zur Beratung ueber die angegebene Menge. */
  static Rechnungsposition beratung(final String menge) {
    return beratung(menge, STUNDENSATZ);
  }

  /** Dieselbe Rechnungsposition mit frei gewaehltem Einzelpreis — fuer den Preis des Entwurfs. */
  static Rechnungsposition beratung(final String menge, final BigDecimal einzelpreis) {
    return new Rechnungsposition(
        BERATUNG_ID, "Beratung", new BigDecimal(menge), Einheit.STUNDE, einzelpreis);
  }

  /** Eine Rechnungsposition zur Pauschale ueber die angegebene Menge. */
  static Rechnungsposition pauschale(final String menge) {
    return new Rechnungsposition(
        PAUSCHALE_ID, "Schulungstag", new BigDecimal(menge), Einheit.PAUSCHAL, PAUSCHALPREIS);
  }

  /** Die Firma, an die das Angebot geht — mit vollstaendiger Anschrift. */
  static Firma firma() {
    return firma(FIRMA, FIRMENNAME);
  }

  /** Die Firma zu einer Kennung. */
  static Firma firma(final long id, final String name) {
    return new Firma(
        Long.valueOf(id),
        name,
        new Anschrift("Hauptstrasse 1", "28195", "Bremen", "Deutschland"),
        null,
        null,
        true,
        ANGELEGT,
        ANGELEGT);
  }

  /** Vollstaendige eigene Angaben: alles, was das Stellen verlangt, und die freiwilligen dazu. */
  static EigeneAngaben eigeneAngaben() {
    return new EigeneAngaben(
        "Manfred Wolff",
        "Softwarearchitekt",
        new Anschrift("Am Deich 2", "28199", "Bremen", "Deutschland"),
        "post@example.org",
        "0421 123456",
        "https://example.org",
        "75/123/45678",
        "DE123456789",
        "DE02 1203 0000 0000 2020 51");
  }
}
