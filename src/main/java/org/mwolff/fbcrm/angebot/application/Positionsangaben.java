package org.mwolff.fbcrm.angebot.application;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Feldfehler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Einer Position fehlen Angaben, die ihr Angebot braucht (Issue #227, Kriterium 8 von #207).
 *
 * <p>Ein Angebot an einen Kunden braucht je Position Menge, Einheit, Preis und Abrechnungsart; die
 * interne Arbeit braucht keine davon. Weil die Pflicht am Nachbarfeld {@code intern} haengt, prueft
 * sie {@link AngebotAendernUseCase} nach der Zielart und nicht die Bean-Validation (E7). <b>Folge
 * fuer den Antwortcode:</b> Eine fehlende Menge an einem Angebot an einen Kunden ist damit 422 und
 * nicht mehr 400 — die Anfrage ist wohlgeformt, aber fachlich nicht verarbeitbar. Die Maske liest
 * Feldfehler unabhaengig vom Status und bleibt davon unberuehrt.
 *
 * <p><b>Alle fehlenden Felder einer Position zusammen</b>, nicht das erste: Wer vier Angaben
 * nachtragen muss, soll es in einem Gang tun koennen — dieselbe Ueberlegung wie bei {@code
 * rechnung.application.PflichtangabenFehlen}. Die Feldnamen tragen den Platz der Position in der
 * eingereichten Liste ({@code positionen[0].menge}), genau wie die Feldnamen der Bean-Validation
 * zur selben Liste; so findet die Maske die Meldung ohne eine zweite Form zu lesen.
 *
 * <p>Abgewiesen wird <b>vor</b> dem Schreiben: Ein abgewiesenes Aendern hinterlaesst nichts.
 */
@ResponseStatus(code = HttpStatus.UNPROCESSABLE_ENTITY, reason = Positionsangaben.MELDUNG)
public final class Positionsangaben extends Feldfehler {

  /** Was der Anwender liest — derselbe Satz, den die Antwort als {@code detail} traegt. */
  public static final String MELDUNG =
      "Jede Position eines Angebots an einen Kunden braucht Menge, Einheit, Preis und"
          + " Abrechnungsart.";

  /** Die Meldung an jedem Feld, dessen Angabe fehlt. */
  public static final String ANGABE_FEHLT =
      "Diese Angabe gehört zu einem Angebot an einen Kunden dazu.";

  private final Map<String, List<String>> fehlende;

  /*
   * Der Zugang laeuft ueber die Fabrik: Welche Felder fehlen, liest die Ausnahme selbst aus der
   * Angabe ab. Ein Konstruktor mit einer Feldkarte liesse jedem Aufrufer die Wahl der Feldnamen,
   * und die muessen zu denen der Bean-Validation passen.
   */
  private Positionsangaben(final Map<String, List<String>> felder) {
    super();
    this.fehlende = Collections.unmodifiableMap(new LinkedHashMap<>(felder));
  }

  /**
   * Die Ausnahme zu einer Position, der eine oder mehrere der vier Angaben fehlen.
   *
   * @param platz der Platz der Position in der eingereichten Liste, von 0 an
   * @param angabe die eingereichte Angabe; mindestens eines ihrer vier Felder ist {@code null}
   */
  public static Positionsangaben fehlendeAngaben(final int platz, final Positionsangabe angabe) {
    final Map<String, List<String>> felder = new LinkedHashMap<>();
    vermerke(felder, platz, "menge", angabe.menge());
    vermerke(felder, platz, "einheit", angabe.einheit());
    vermerke(felder, platz, "einzelpreis", angabe.einzelpreis());
    vermerke(felder, platz, "abrechnungsmodus", angabe.abrechnungsmodus());
    return new Positionsangaben(felder);
  }

  @Override
  public Map<String, List<String>> felder() {
    return fehlende;
  }

  private static void vermerke(
      final Map<String, List<String>> felder,
      final int platz,
      final String feld,
      final @Nullable Object wert) {
    if (wert == null) {
      felder.put("positionen[" + platz + "]." + feld, List.of(ANGABE_FEHLT));
    }
  }
}
