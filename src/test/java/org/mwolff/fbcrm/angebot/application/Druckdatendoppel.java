package org.mwolff.fbcrm.angebot.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.rechnung.domain.Belegabsender;
import org.mwolff.fbcrm.rechnung.domain.Belegempfaenger;

/**
 * Die Druckdaten, gegen die Satz und Druck geprueft werden.
 *
 * <p>An einer Stelle und oeffentlich, weil zwei Testklassen in zwei Paketen dasselbe Angebot
 * brauchen: {@code BeleglayoutTest} prueft daran den Satz, {@code PdfBoxDruckerTest} liest
 * dieselben Angaben aus dem fertigen PDF zurueck. Zwei Vorlagen liefen auseinander, und der
 * Nachweis fuer Kriterium 15 haengt daran, dass beide Tests dieselben Pflichtinhalte meinen.
 */
public final class Druckdatendoppel {

  /** Die Angebotsnummer des festgeschriebenen Belegs (Kriterium 11). */
  public static final String NUMMER = "A-2026-001";

  public static final LocalDate ANGEBOTSDATUM = LocalDate.of(2026, 9, 20);
  public static final LocalDate GUELTIG_BIS = LocalDate.of(2026, 10, 20);

  /** 40 Woerter zu vier Zeichen — bei 96 Zeichen Textbreite genau 19 Woerter je Zeile. */
  public static final String LANGER_TEXT = "wort ".repeat(40).trim();

  public static final String BEDINGUNGEN = "Zahlbar in 14 Tagen.";

  /** Der Satz, den das Nicht-Ziel „Umsatzsteuer im Angebot" gerade verlangt (E10). */
  public static final String NETTO_HINWEIS = "Alle Beträge netto, zzgl. gesetzlicher Umsatzsteuer";

  public static final Anschrift EIGENE_ANSCHRIFT =
      new Anschrift("Am Deich 2", "28199", "Hansestadt", "Deutschland");

  /** „Eigene Angaben" mit jeder Angabe gefuellt (Kriterium 1). */
  public static final Belegabsender ABSENDER =
      new Belegabsender(
          "Manfred Wolff",
          null,
          EIGENE_ANSCHRIFT,
          "manne@example.org",
          "0421 123456",
          "12/345/67890",
          "DE123456789",
          "Sparkasse, IBAN DE02 1203 0000 0000 2020 51",
          null);

  /** Firma mit Ansprechpartner — die vollstaendige Empfaengeranschrift (Kriterium 12). */
  public static final Belegempfaenger EMPFAENGER =
      new Belegempfaenger(
          "Adler AG",
          new Anschrift("Hauptstrasse 1", "28195", "Bremen", "Deutschland"),
          "Frau Dr. Adler");

  /** Die Summe der beiden Positionen aus {@link Angebotsdoppel}: 2.500,03 € und 1.200,00 €. */
  public static final BigDecimal SUMME = new BigDecimal("3700.03");

  private Druckdatendoppel() {}

  /** Ein vollstaendiger Beleg: beide Anschriften, beide Texte, zwei Positionen. */
  public static AngebotDruckdaten standard() {
    return mit(
        ABSENDER,
        EMPFAENGER,
        LANGER_TEXT,
        BEDINGUNGEN,
        List.of(Angebotsdoppel.KONZEPTION, Angebotsdoppel.SCHULUNG),
        SUMME);
  }

  /** Derselbe Beleg mit mehr Positionen, als eine Seite traegt. */
  public static AngebotDruckdaten mehrseitig() {
    return mit(ABSENDER, EMPFAENGER, LANGER_TEXT, null, vielePositionen(), SUMME);
  }

  /**
   * 40 unterscheidbare Positionen.
   *
   * <p>Numeriert, damit ein Test sagen kann, <em>welche</em> Zeile auf welcher Seite steht — bei
   * gleichlautenden Bezeichnungen liesse sich die Grenze zwischen den Seiten nicht benennen.
   */
  public static List<Angebotsposition> vielePositionen() {
    return IntStream.rangeClosed(1, 40)
        .mapToObj(
            nummer ->
                new Angebotsposition(
                    null,
                    "Posten %02d".formatted(nummer),
                    Abrechnungsmodus.FESTPREIS,
                    BigDecimal.ONE,
                    Einheit.PAUSCHAL,
                    BigDecimal.TEN))
        .toList();
  }

  /** Ein Beleg mit frei gewaehlten Angaben — fuer die Faelle, in denen etwas fehlt. */
  public static AngebotDruckdaten mit(
      final Belegabsender absender,
      final Belegempfaenger empfaenger,
      final @Nullable String beschreibung,
      final @Nullable String bedingungen,
      final List<Angebotsposition> positionen,
      final BigDecimal summe) {
    return new AngebotDruckdaten(
        absender,
        empfaenger,
        NUMMER,
        ANGEBOTSDATUM,
        GUELTIG_BIS,
        beschreibung,
        positionen,
        summe,
        bedingungen);
  }
}
