package org.mwolff.fbcrm.common;

import java.text.Collator;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Die Laender nach ISO 3166-1, mit Kuerzel und Namen (Plan #461, E11).
 *
 * <p>Die einzige Quelle zur Laufzeit: Die Firma speichert das Kuerzel, die Oberflaeche liest die
 * Liste ueber {@code GET /api/laender}. Kuerzel und Namen kommen aus dem JDK — ein Kuerzel aendert
 * sich nicht, wenn die Schreibweise eines Namens wechselt, darum ist es der Schluessel und nicht
 * der Name.
 *
 * <p>Die Zuordnung eines Freitexts ({@link #kuerzel(String, Collection)}) nimmt die Namen als
 * Parameter: Die Migration des Bestands ordnet gegen eine eingefrorene Liste zu, damit ihr Ergebnis
 * nicht vom JDK des Abbilds abhaengt (E12). Die Regel ist dieselbe.
 */
public final class Laenderliste {

  /** Das Land, das jeder Freitext ohne eindeutige Zuordnung wird (Kriterium 10). */
  public static final String VORGABE = "DE";

  private static final Locale DEUTSCH = Locale.GERMAN;

  private static final List<Land> LAENDER =
      Arrays.stream(Locale.getISOCountries())
          .map(Laenderliste::ausDemJdk)
          .sorted(Comparator.comparing(Land::name, Collator.getInstance(DEUTSCH)))
          .toList();

  private static final Map<String, Land> NACH_KUERZEL =
      LAENDER.stream().collect(Collectors.toUnmodifiableMap(Land::kuerzel, Function.identity()));

  /**
   * Ein Land.
   *
   * @param kuerzel das Kuerzel nach ISO 3166-1 alpha-2, in Grossbuchstaben
   * @param name der deutsche Name
   * @param englisch der englische Name; nur fuer die Zuordnung eines Freitexts
   */
  public record Land(String kuerzel, String name, String englisch) {}

  private Laenderliste() {
    // Nur statische Zugaenge.
  }

  private static Land ausDemJdk(final String kuerzel) {
    final Locale land = Locale.of("", kuerzel);
    return new Land(
        kuerzel, land.getDisplayCountry(DEUTSCH), land.getDisplayCountry(Locale.ENGLISH));
  }

  /**
   * Alle Laender.
   *
   * @return jedes Land nach ISO 3166-1 genau einmal, sortiert nach dem deutschen Namen
   */
  public static List<Land> alle() {
    return List.copyOf(LAENDER);
  }

  /**
   * Der deutsche Name zu einem Kuerzel.
   *
   * @param kuerzel das Kuerzel, oder {@code null}
   * @return der Name, oder {@code null}, wenn das Kuerzel fehlt oder unbekannt ist
   */
  public static @Nullable String name(final @Nullable String kuerzel) {
    final Land land = kuerzel == null ? null : NACH_KUERZEL.get(kuerzel);
    return land == null ? null : land.name();
  }

  /**
   * Ob ein Kuerzel in der Liste steht.
   *
   * <p>Genau so, wie die Spalte es fuehrt: zwei Grossbuchstaben. {@code de} ist kein Kuerzel der
   * Liste.
   *
   * @param kuerzel das Kuerzel, oder {@code null}
   * @return {@code true}, wenn es ein Land mit diesem Kuerzel gibt
   */
  public static boolean bekannt(final @Nullable String kuerzel) {
    return name(kuerzel) != null;
  }

  /**
   * Das Kuerzel zu einem Freitext (fachliche Quelle #460, Kriterium 10).
   *
   * <p>Ein Freitext, der genau dem deutschen oder englischen Namen eines Landes oder seinem Kuerzel
   * entspricht, wird diesem Land zugeordnet; Gross- und Kleinschreibung und Leerraum am Rand
   * zaehlen dabei nicht. Jeder andere Freitext, auch ein leerer, wird {@link #VORGABE}.
   *
   * @param freitext das bisherige Land, oder {@code null}
   * @param namen die Laender, gegen die zugeordnet wird
   * @return das Kuerzel
   */
  public static String kuerzel(final @Nullable String freitext, final Collection<Land> namen) {
    final String gesucht = freitext == null ? "" : freitext.strip();
    return namen.stream()
        .filter(
            land ->
                land.kuerzel().equalsIgnoreCase(gesucht)
                    || land.name().equalsIgnoreCase(gesucht)
                    || land.englisch().equalsIgnoreCase(gesucht))
        .map(Land::kuerzel)
        .findFirst()
        .orElse(VORGABE);
  }
}
