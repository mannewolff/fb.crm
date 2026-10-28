package org.mwolff.fbcrm.common;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

/**
 * Liest IBAN und BIC aus der bisherigen Bankverbindung, die ein Freitext war (Plan #461, E14;
 * fachliche Quelle #460, Kriterium 11).
 *
 * <p><b>IBAN.</b> Ein Satzzeichen beendet eine Folge, Leerraum nicht: Innerhalb eines Abschnitts
 * zwischen zwei Satzzeichen werden aufeinanderfolgende Woerter ohne Leerzeichen zusammengesetzt.
 * Gueltig ist die erste Folge, die {@link Iban#gueltig(String)} besteht; beginnen an derselben
 * Stelle mehrere, gilt die laengste, damit eine IBAN in Vierergruppen nicht an einer Gruppe endet,
 * die zufaellig auch die Pruefung besteht. Gross- und Kleinschreibung zaehlt dabei nicht.
 *
 * <p><b>BIC.</b> Ein Wort aus 8 oder 11 Grossbuchstaben oder Ziffern, dessen Zeichen 5 und 6 ein
 * Kuerzel der {@link Laenderliste} sind ({@link #istBic(String)}). Das allein schliesst
 * „COMMERZBANK" nicht aus — ER ist das Kuerzel Eritreas. Darum gilt beim Zerlegen nur eine BIC aus
 * dem Land der IBAN, und ohne IBAN gibt es keine BIC.
 *
 * <p>Statisch und ohne Spring, weil die Java-Migration {@code BankverbindungInFeldernMigration} sie
 * ohne Anwendungskontext aufruft.
 */
public final class Bankverbindungszerlegung {

  private static final Pattern SATZZEICHEN = Pattern.compile("[^A-Za-z0-9\\s]+");

  private static final Pattern LEERRAUM = Pattern.compile("\\s+");

  private static final Pattern KEIN_WORTZEICHEN = Pattern.compile("[^A-Za-z0-9]+");

  private static final Pattern BIC_FORMAT = Pattern.compile("[A-Z0-9]{8}|[A-Z0-9]{11}");

  /** Wo in der BIC das Laenderkuerzel steht: die Zeichen 5 und 6. */
  private static final int LAND_VON = 4;

  private static final int LAND_BIS = 6;

  /**
   * Was der Freitext hergibt.
   *
   * @param iban die erste gueltige IBAN, oder {@code null}
   * @param bic die BIC aus dem Land der IBAN, oder {@code null}; ohne IBAN immer {@code null}
   */
  public record Zerlegt(@Nullable Iban iban, @Nullable String bic) {}

  private Bankverbindungszerlegung() {
    // Nur statische Zugaenge.
  }

  /**
   * Zerlegt die bisherige Bankverbindung.
   *
   * @param freitext die bisherige Bankverbindung, oder {@code null}
   * @return IBAN und BIC, soweit der Text sie hergibt
   */
  public static Zerlegt zerlege(final @Nullable String freitext) {
    if (freitext == null) {
      return new Zerlegt(null, null);
    }
    final Iban iban = ersteIban(freitext);
    if (iban == null) {
      return new Zerlegt(null, null);
    }
    return new Zerlegt(iban, bicAus(freitext, iban.elektronisch().substring(0, 2)));
  }

  /**
   * Ob ein Wort die Form einer BIC hat.
   *
   * @param wort das Wort, so wie es dasteht
   * @return {@code true} bei 8 oder 11 Grossbuchstaben oder Ziffern, deren Zeichen 5 und 6 ein
   *     Kuerzel der Laenderliste sind
   */
  public static boolean istBic(final String wort) {
    return BIC_FORMAT.matcher(wort).matches() && Laenderliste.bekannt(land(wort));
  }

  private static @Nullable Iban ersteIban(final String freitext) {
    for (final String abschnitt : SATZZEICHEN.split(freitext, -1)) {
      final List<String> woerter =
          List.of(LEERRAUM.split(abschnitt.strip().toUpperCase(Locale.ROOT), -1));
      final Optional<Iban> iban =
          IntStream.range(0, woerter.size())
              .mapToObj(anfang -> laengsteAb(woerter, anfang))
              .filter(Objects::nonNull)
              .findFirst();
      if (iban.isPresent()) {
        return iban.get();
      }
    }
    return null;
  }

  /*
   * Ohne Abbruch nach 34 Zeichen: Eine laengere Folge besteht das Format ohnehin nicht, und der
   * Freitext hat hoechstens 200 Zeichen.
   */
  private static @Nullable Iban laengsteAb(final List<String> woerter, final int anfang) {
    final StringBuilder folge = new StringBuilder();
    Iban laengste = null;
    for (int ende = anfang; ende < woerter.size(); ende++) {
      folge.append(woerter.get(ende));
      if (Iban.gueltig(folge.toString())) {
        laengste = new Iban(folge.toString());
      }
    }
    return laengste;
  }

  private static @Nullable String bicAus(final String freitext, final String land) {
    return Arrays.stream(KEIN_WORTZEICHEN.split(freitext, -1))
        .filter(wort -> istBic(wort) && land(wort).equals(land))
        .findFirst()
        .orElse(null);
  }

  /** Das Laenderkuerzel einer BIC; das Wort hat schon die Form einer BIC. */
  private static String land(final String bic) {
    return bic.substring(LAND_VON, LAND_BIS);
  }
}
