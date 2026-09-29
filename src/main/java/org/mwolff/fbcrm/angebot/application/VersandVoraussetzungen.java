package org.mwolff.fbcrm.angebot.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;
import org.mwolff.fbcrm.firma.domain.Firma;

/**
 * Was vorliegen muss, damit ein Entwurf versendet werden kann (Kriterium 12, E22).
 *
 * <p><b>Reine Pruefung</b> — keine Uhr, kein Bestand, kein Nummernkreis. Genau deshalb steht sie im
 * Anwendungsfall <b>vor</b> jedem Zug am Nummernkreis: Ein abgewiesener Versuch verbraucht damit
 * keine Angebotsnummer, und die Reihenfolge ist die Zusage, nicht der Rollback als ihr Ersatz.
 *
 * <p><b>Alles auf einmal.</b> Kriterium 12 sagt „nennt mir die fehlende Angabe"; wer drei Angaben
 * vergessen hat, soll das in einem Durchgang erfahren. Die Karte traegt darum je betroffenes Feld
 * einen Eintrag, und an einem Feld duerfen mehrere Meldungen stehen.
 *
 * <p><b>Die Datenbank traegt diese Regeln nicht mit</b> (E27). Ein unbedingter CHECK liesse schon
 * das Speichern eines halbfertigen Entwurfs scheitern — mit einer generischen 500 statt der
 * Auskunft, die Kriterium 12 verlangt.
 *
 * <p>Ein Ansprechpartner kommt hier nicht vor: Verlangt ist die Firmenanschrift, nicht eine Person.
 */
final class VersandVoraussetzungen {

  /** Schluessel der Feldliste: die Positionen des Angebots. */
  static final String POSITIONEN = "positionen";

  /** Schluessel der Feldliste: die Gueltigkeit des Angebots. */
  static final String GUELTIG_BIS = "gueltigBis";

  /** Schluessel der Feldliste: die Anschrift der Firma des Angebots. */
  static final String FIRMA = "firma";

  /** Schluessel der Feldliste: die eigenen Angaben. */
  static final String EIGENE_ANGABEN = "eigeneAngaben";

  private static final String OHNE_POSITION = "Das Angebot braucht mindestens eine Position.";
  private static final String OHNE_BEZEICHNUNG = "Jede Position braucht eine Bezeichnung.";
  private static final String GUELTIGKEIT_ZU_FRUEH =
      "Die Gueltigkeit darf nicht vor dem Angebotsdatum liegen.";
  private static final String FIRMA_OHNE_ANSCHRIFT =
      "Die Firma des Angebots braucht Strasse, PLZ und Ort.";
  private static final String OHNE_EIGENEN_NAMEN =
      "Unter „Eigene Angaben\" fehlt der Name, unter dem der Beleg hinausgeht.";
  private static final String OHNE_EIGENE_ANSCHRIFT =
      "Unter „Eigene Angaben\" fehlen Strasse, PLZ oder Ort.";

  private VersandVoraussetzungen() {}

  /**
   * Prueft die Voraussetzungen und wirft, wenn etwas fehlt.
   *
   * @param angebot der Entwurf, der versendet werden soll
   * @param firma die Firma des Angebots
   * @param eigeneAngaben die Selbstauskunft der Instanz
   * @throws VersandUnvollstaendig wenn mindestens eine Angabe fehlt; die Ausnahme traegt alle
   */
  static void pruefe(final Angebot angebot, final Firma firma, final EigeneAngaben eigeneAngaben) {
    final Map<String, List<String>> fehlend = fehlend(angebot, firma, eigeneAngaben);
    if (!fehlend.isEmpty()) {
      throw new VersandUnvollstaendig(fehlend);
    }
  }

  /**
   * Alle fehlenden Angaben, je Feld gesammelt — leer, wenn nichts fehlt.
   *
   * <p>Die Reihenfolge der Schluessel ist die Leserichtung der Maske: erst das Angebot selbst, dann
   * seine Nachbarn.
   */
  static Map<String, List<String>> fehlend(
      final Angebot angebot, final Firma firma, final EigeneAngaben eigeneAngaben) {
    final Map<String, List<String>> fehlend = new LinkedHashMap<>();
    trageEin(fehlend, POSITIONEN, positionsmaengel(angebot.positionen()));
    if (angebot.gueltigBis().isBefore(angebot.angebotDatum())) {
      trageEin(fehlend, GUELTIG_BIS, List.of(GUELTIGKEIT_ZU_FRUEH));
    }
    if (!anschriftVollstaendig(firma.anschrift())) {
      trageEin(fehlend, FIRMA, List.of(FIRMA_OHNE_ANSCHRIFT));
    }
    trageEin(fehlend, EIGENE_ANGABEN, eigenmaengel(eigeneAngaben));
    return fehlend;
  }

  private static void trageEin(
      final Map<String, List<String>> fehlend, final String feld, final List<String> meldungen) {
    if (!meldungen.isEmpty()) {
      fehlend.put(feld, List.copyOf(meldungen));
    }
  }

  /* „Mindestens eine Position" und „jede Bezeichnung nicht leer" sind zwei Maengel an einem Feld. */
  private static List<String> positionsmaengel(final List<Angebotsposition> positionen) {
    if (positionen.isEmpty()) {
      return List.of(OHNE_POSITION);
    }
    if (positionen.stream().anyMatch(position -> !gefuellt(position.bezeichnung()))) {
      return List.of(OHNE_BEZEICHNUNG);
    }
    return List.of();
  }

  /* Name und Anschrift sind zwei getrennte Maengel (F12): Der Anwender soll wissen, was fehlt. */
  private static List<String> eigenmaengel(final EigeneAngaben eigeneAngaben) {
    final List<String> maengel = new ArrayList<>(2);
    if (!gefuellt(eigeneAngaben.name())) {
      maengel.add(OHNE_EIGENEN_NAMEN);
    }
    if (!anschriftVollstaendig(eigeneAngaben.anschrift())) {
      maengel.add(OHNE_EIGENE_ANSCHRIFT);
    }
    return maengel;
  }

  /* Strasse, PLZ und Ort — das Land nicht: Ein Inlandsbeleg nennt es oft gar nicht. */
  private static boolean anschriftVollstaendig(final Anschrift anschrift) {
    return gefuellt(anschrift.strasse()) && gefuellt(anschrift.plz()) && gefuellt(anschrift.ort());
  }

  /* Leerzeichen sind keine Angabe: „   " ist so leer wie null. */
  private static boolean gefuellt(final @Nullable String text) {
    return text != null && !text.isBlank();
  }
}
