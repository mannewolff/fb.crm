package org.mwolff.fbcrm.angebot.application;

import java.time.Clock;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Storno und Ruecknahme am Angebot (Issue #334, #330 Kriterien 1, 5 und 9; Plan #332 E5, E17).
 *
 * <p>Beide Richtungen lesen das Angebot, rufen die Domaene und speichern — wie {@link
 * AngebotStatusUseCase}. Ein zweites Storno ist kein Uebergang (E17): {@link #storniere} weist am
 * schon stornierten Angebot ab, damit der vorhandene Grund nicht stillschweigend ueberschrieben
 * wird; {@link #nimmStornoZurueck} weist am nicht stornierten ab. Beide Pruefungen laufen vor dem
 * Schreiben, eine Abweisung hinterlaesst nichts.
 *
 * <p>Ob aus dem Status storniert werden darf (E3), fragt der Anwendungsfall die Domaene ueber
 * {@link Angebot#laesstSichStornieren()}. Der Konstruktor des Angebots haelt dieselbe Regel, wiese
 * aber mit einer {@link IllegalArgumentException} ab — die Schnittstelle antwortete dann mit 500
 * statt mit dem Satz aus E16.
 */
@Service
@Transactional
public class AngebotStornoUseCase {

  private final AngebotRepository angebote;
  private final Clock clock;

  AngebotStornoUseCase(final AngebotRepository angebote, final Clock clock) {
    this.angebote = angebote;
    this.clock = clock;
  }

  /**
   * Storniert das Angebot; der Status bleibt, wie er ist (E1).
   *
   * <p>Ein Grund aus lauter Leerraum gilt als kein Grund: Der Grund ist freiwillig (E4), und ein
   * leer gelassenes Feld soll das Storno nicht scheitern lassen.
   *
   * @param angebotId Kennung des Angebots
   * @param grund warum storniert wird, oder {@code null} fuer ein Storno ohne Grund
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   * @throws StornoNichtMoeglich wenn das Angebot schon storniert ist oder aus seinem Status nicht
   *     storniert wird
   */
  public Angebot storniere(final long angebotId, final @Nullable String grund) {
    final Angebot angebot = lies(angebotId);
    if (!angebot.laesstSichStornieren()) {
      throw new StornoNichtMoeglich();
    }
    final @Nullable String mitInhalt = grund == null || grund.isBlank() ? null : grund;
    return angebote.save(angebot.storniert(mitInhalt, clock.instant()));
  }

  /**
   * Nimmt das Storno zurueck; das Angebot steht danach im Status, aus dem storniert wurde, und ohne
   * Grund (#330, Kriterium 9).
   *
   * @param angebotId Kennung des Angebots
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   * @throws KeinStornoVorhanden wenn das Angebot nicht storniert ist
   */
  public Angebot nimmStornoZurueck(final long angebotId) {
    final Angebot angebot = lies(angebotId);
    if (!angebot.storniert()) {
      throw new KeinStornoVorhanden();
    }
    return angebote.save(angebot.stornoZurueckgenommen(clock.instant()));
  }

  private Angebot lies(final long angebotId) {
    return angebote.findById(angebotId).orElseThrow(AngebotNichtGefunden::new);
  }
}
