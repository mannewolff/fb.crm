package org.mwolff.fbcrm.angebot.application;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.Firma;

/**
 * Die Umgebung, aus der ein Versand seine Anschriftskopien nimmt (R8, Kriterium 12).
 *
 * <p>Getrennt von {@link Angebotsdoppel}, weil hier nichts vom Angebot steht: Firma,
 * Ansprechpartner und „Eigene Angaben" sind die Nachbarn, die das Festschreiben liest. Zwei
 * Testklassen brauchen sie — die Versandpruefung und der Anwendungsfall —, und beide muessen
 * dieselben vollstaendigen Angaben meinen, damit „vollstaendig" in beiden dasselbe heisst.
 *
 * <p>Neben den vollstaendigen Angaben stehen die {@code …Mit}-Fabriken: Sie stellen genau eine
 * Angabe auf {@code null}, damit ein Test sagen kann, <em>welche</em> fehlt.
 */
final class Versanddoppel {

  /** Die Firma aus {@link Angebotsdoppel#FIRMA}. */
  static final long FIRMA = Angebotsdoppel.FIRMA;

  static final long ANSPRECHPARTNER = Angebotsdoppel.ANSPRECHPARTNER;

  static final String FIRMENNAME = "Adler AG";
  static final String EIGENER_NAME = "Manfred Wolff";

  /** Anrede und Name, wie sie als Kopie in den Beleg gehen (R8). */
  static final String ANSPRECHPARTNER_TEXT = "Eva Adler";

  static final Anschrift FIRMENANSCHRIFT =
      new Anschrift("Hauptstrasse 1", "28195", "Bremen", "Deutschland");

  static final Anschrift EIGENE_ANSCHRIFT =
      new Anschrift("Am Deich 2", "28199", "Hansestadt", "Deutschland");

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");

  private Versanddoppel() {}

  /** Eine Firma mit vollstaendiger Anschrift (Kriterium 12). */
  static Firma firma() {
    return firmaMit(FIRMENANSCHRIFT);
  }

  /** Dieselbe Firma mit frei gewaehlter Anschrift. */
  static Firma firmaMit(final Anschrift anschrift) {
    return new Firma(
        Long.valueOf(FIRMA), FIRMENNAME, anschrift, null, null, true, ANGELEGT, ANGELEGT);
  }

  /** Der Ansprechpartner des Angebots. */
  static Ansprechpartner ansprechpartner() {
    return new Ansprechpartner(
        Long.valueOf(ANSPRECHPARTNER),
        FIRMA,
        "Eva",
        "Adler",
        null,
        null,
        null,
        null,
        true,
        ANGELEGT,
        ANGELEGT);
  }

  /** Die vollstaendigen Nachbarn eines Angebots, wie {@code Versandunterlagen} sie liefert. */
  static Belegangaben belegangaben() {
    return new Belegangaben(firma(), ansprechpartner(), eigeneAngaben());
  }

  /**
   * Dieselben Nachbarn mit frei gewaehlten eigenen Angaben — fuer die Faelle, in denen etwas fehlt.
   */
  static Belegangaben belegangabenMit(final EigeneAngaben angaben) {
    return new Belegangaben(firma(), ansprechpartner(), angaben);
  }

  /** „Eigene Angaben" mit jeder Angabe gefuellt (Kriterium 1). */
  static EigeneAngaben eigeneAngaben() {
    return eigeneAngabenMit(EIGENER_NAME, EIGENE_ANSCHRIFT);
  }

  /** Dieselben Angaben mit frei gewaehltem Namen und frei gewaehlter Anschrift. */
  static EigeneAngaben eigeneAngabenMit(final @Nullable String name, final Anschrift anschrift) {
    return new EigeneAngaben(
        name,
        anschrift,
        "manne@example.org",
        "0421 123456",
        "12/345/67890",
        "DE123456789",
        "Sparkasse, IBAN DE02 1203 0000 0000 2020 51",
        Angebotsdoppel.BEDINGUNGEN);
  }
}
