package org.mwolff.fbcrm.angebot.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotskommentar;
import org.mwolff.fbcrm.angebot.domain.AngebotskommentarRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Anwendungsfaelle der Kommentare am Angebot: lesen, schreiben, aendern, loeschen (Issue #140,
 * Kriterien 2 bis 10).
 *
 * <p>Jeder der vier Wege prueft zuerst, dass es das Angebot gibt. Das ist keine doppelte Arbeit,
 * sondern die Aussage der Pfade: Sie liegen unter {@code /api/angebote/{id}}, und eine unbekannte
 * Kennung dort ist 404 und nicht eine leere Liste (Plan #141, E5).
 *
 * <p><b>Was hier entschieden wird</b> und nicht in der Domaene: Leerraum am Rand des Textes faellt
 * weg, und was danach leer ist, wird abgewiesen — nach dem Muster von {@code FirmaDaten} und {@code
 * AnsprechpartnerDaten}; kein {@code domain}-Paket des Bestands bereinigt Text (E7). Die Grenze von
 * 2.000 Zeichen prueft dagegen die Schnittstelle am eingereichten Text ({@code
 * AngebotKommentarRequest}) und die Tabelle am gespeicherten.
 *
 * <p>Der Zeitpunkt kommt aus der injizierten {@link Clock} (CLAUDE-java.md §6.2). Beim Aendern
 * bleibt {@code createdAt} stehen: Die Ansicht zeigt den Zeitpunkt der Anlage, und einen Hinweis
 * „bearbeitet" gibt es nicht (Kriterium 9, E8).
 */
@Service
@Transactional
public class AngebotskommentarUseCase {

  private final AngebotRepository angebote;
  private final AngebotskommentarRepository kommentare;
  private final Clock clock;

  AngebotskommentarUseCase(
      final AngebotRepository angebote,
      final AngebotskommentarRepository kommentare,
      final Clock clock) {
    this.angebote = angebote;
    this.kommentare = kommentare;
    this.clock = clock;
  }

  /**
   * Die Kommentare des Angebots, neuester zuerst (Kriterium 4).
   *
   * @param angebotId Kennung des Angebots
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   */
  @Transactional(readOnly = true)
  public List<Angebotskommentar> liste(final long angebotId) {
    pruefeAngebot(angebotId);
    return kommentare.findByAngebot(angebotId).stream()
        .sorted(Kommentarreihenfolge.NEUESTE_ZUERST)
        .toList();
  }

  /**
   * Schreibt einen Kommentar an das Angebot (Kriterium 2) — in jedem Status (Kriterium 8).
   *
   * @param angebotId Kennung des Angebots
   * @param text der eingereichte Text; Leerraum am Rand faellt weg
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   * @throws IllegalArgumentException wenn vom Text nur Leerraum uebrig bleibt
   */
  public Angebotskommentar schreibe(final long angebotId, final String text) {
    pruefeAngebot(angebotId);
    final String sauberer = bereinigt(text);
    final Instant jetzt = clock.instant();
    return kommentare.save(new Angebotskommentar(null, angebotId, sauberer, jetzt, jetzt));
  }

  /**
   * Aendert den Text eines Kommentars (Kriterium 9).
   *
   * @param angebotId Kennung des Angebots
   * @param kommentarId Kennung des Kommentars
   * @param text der neue Text; Leerraum am Rand faellt weg
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   * @throws AngebotskommentarNichtGefunden wenn es den Kommentar an diesem Angebot nicht gibt
   * @throws IllegalArgumentException wenn vom Text nur Leerraum uebrig bleibt
   */
  public Angebotskommentar aendere(
      final long angebotId, final long kommentarId, final String text) {
    final Angebotskommentar vorhandener = lies(angebotId, kommentarId);
    return kommentare.save(vorhandener.geaendert(bereinigt(text), clock.instant()));
  }

  /**
   * Loescht den Kommentar — die Zeile ist danach fort (Kriterium 10, E6).
   *
   * @param angebotId Kennung des Angebots
   * @param kommentarId Kennung des Kommentars
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   * @throws AngebotskommentarNichtGefunden wenn es den Kommentar an diesem Angebot nicht gibt
   */
  public void loesche(final long angebotId, final long kommentarId) {
    kommentare.deleteById(lies(angebotId, kommentarId).requireId());
  }

  private void pruefeAngebot(final long angebotId) {
    if (angebote.findById(angebotId).isEmpty()) {
      throw new AngebotNichtGefunden();
    }
  }

  private Angebotskommentar lies(final long angebotId, final long kommentarId) {
    pruefeAngebot(angebotId);
    final Angebotskommentar vorhandener =
        kommentare.findById(kommentarId).orElseThrow(AngebotskommentarNichtGefunden::new);
    if (vorhandener.angebotId() != angebotId) {
      throw new AngebotskommentarNichtGefunden();
    }
    return vorhandener;
  }

  private static String bereinigt(final String text) {
    final String sauberer = text.strip();
    if (sauberer.isEmpty()) {
      throw new IllegalArgumentException("Ein Kommentar ohne Text gibt es nicht.");
    }
    return sauberer;
  }
}
