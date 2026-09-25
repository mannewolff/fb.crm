package org.mwolff.fbcrm.vorgang.application;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.vorgang.domain.EintragRepository;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;

/**
 * Macht aus Vorgaengen die Zeilen, die beide Listen zeigen — die Uebersicht (Kriterium 2) und die
 * Liste an der Firma (Kriterium 12).
 *
 * <p>Beide brauchen dieselben zwei Angaben, die am Vorgang selbst nicht stehen: den Namen der Firma
 * und den Tag des juengsten Eintrags. Sie an einer Stelle zu holen haelt die Zahl der Abfragen
 * gleich — <b>eine</b> fuer die Zeitpunkte aller Zeilen und eine je <b>Firma</b>, nicht je Zeile.
 *
 * <p>Kein eigenes Bean: Die beiden Anwendungsfaelle bauen sich ihre Instanz im Konstruktor. Die
 * Klasse haelt keinen Zustand ueber einen Aufruf hinaus.
 */
final class VorgangZeilen {

  private final EintragRepository eintraege;
  private final FirmaRepository firmen;

  VorgangZeilen(final EintragRepository eintraege, final FirmaRepository firmen) {
    this.eintraege = eintraege;
    this.firmen = firmen;
  }

  /**
   * Die Zeilen zu den Vorgaengen, in deren Reihenfolge.
   *
   * <p>Sortiert wird im Bestand (E16); hier wird nicht nachsortiert.
   */
  List<VorgangZeile> zu(final List<Vorgang> vorgaenge) {
    final Map<Long, Instant> juengste =
        eintraege.juengstesGeschehenJeVorgang(vorgaenge.stream().map(Vorgang::requireId).toList());
    final Map<Long, String> namen = new HashMap<>();
    return vorgaenge.stream().map(vorgang -> zeile(vorgang, juengste, namen)).toList();
  }

  private VorgangZeile zeile(
      final Vorgang vorgang, final Map<Long, Instant> juengste, final Map<Long, String> namen) {
    final long id = vorgang.requireId();
    return new VorgangZeile(
        id,
        vorgang.nummer(),
        vorgang.titel(),
        firmaName(vorgang.firmaId(), namen),
        vorgang.phase(),
        vorgang.abgeschlossen(),
        // Kriterium 2: ohne Eintrag zaehlt der Vorgang mit dem Zeitpunkt seines Anlegens.
        juengste.getOrDefault(id, vorgang.createdAt()));
  }

  /*
   * Gemerkt statt je Zeile geholt: Ein Freiberufler hat zu einer Firma oft mehrere Vorgaenge, und
   * die Liste an der Firma besteht sogar ausschliesslich aus einer einzigen.
   */
  private String firmaName(final long firmaId, final Map<Long, String> namen) {
    final String bekannt = namen.get(firmaId);
    if (bekannt != null) {
      return bekannt;
    }
    final String name = firmen.findById(firmaId).map(Firma::name).orElseThrow(() -> fehlt(firmaId));
    namen.put(firmaId, name);
    return name;
  }

  /*
   * Der Fremdschluessel auf vorgang.firma_id schliesst diesen Fall aus. Traete er trotzdem ein,
   * waere der Bestand kaputt — dann ist ein lauter Fehler die richtige Antwort und kein 404, das
   * eine gebrochene Beziehung wie eine erfundene Kennung aussehen liesse.
   */
  private static IllegalStateException fehlt(final long firmaId) {
    return new IllegalStateException(
        "Zum Vorgang gibt es keine Firma mit der Kennung " + firmaId + ".");
  }
}
