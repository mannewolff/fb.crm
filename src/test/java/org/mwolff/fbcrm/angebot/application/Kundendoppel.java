package org.mwolff.fbcrm.angebot.application;

import java.time.Instant;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.Firma;

/**
 * Der Kunde, an den die Angebote dieses Pakets gehen: Firma und Ansprechpartner.
 *
 * <p>Getrennt von {@link Angebotsdoppel}, weil hier nichts vom Angebot steht — nur die Nachbarn,
 * deren Namen die Ansicht eines Angebots beschriften.
 */
final class Kundendoppel {

  /** Die Firma aus {@link Angebotsdoppel#FIRMA}. */
  static final long FIRMA = Angebotsdoppel.FIRMA;

  static final long ANSPRECHPARTNER = Angebotsdoppel.ANSPRECHPARTNER;

  static final String FIRMENNAME = "Adler AG";

  /** Vor- und Nachname des Ansprechpartners, wie die Ansicht sie zeigt. */
  static final String ANSPRECHPARTNER_TEXT = "Eva Adler";

  static final Anschrift FIRMENANSCHRIFT =
      new Anschrift("Hauptstrasse 1", "28195", "Bremen", "Deutschland");

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");

  private Kundendoppel() {}

  /** Die Firma des Angebots. */
  static Firma firma() {
    return new Firma(
        Long.valueOf(FIRMA), FIRMENNAME, FIRMENANSCHRIFT, null, null, true, ANGELEGT, ANGELEGT);
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
}
