package org.mwolff.fbcrm.angebot.application;

import java.time.Clock;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.common.Bearbeitungskonflikt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Weiter- und Zurueckschalten des Status (Issue #127, Kriterium 4).
 *
 * <p>Beide Richtungen gehen eine Stufe und ohne weitere Pruefung (Kriterium 9). Ob es in die
 * verlangte Richtung eine Stufe gibt, entscheidet die Domaene ({@link Angebot#statusWeiter}, {@link
 * Angebot#statusZurueck}); die Ausnahme fliegt, bevor gespeichert wird.
 *
 * <p><b>Zuerst der Stand</b> (#411, Kriterien 4 und 5; Plan #415, E9, E14). Der Statuswechsel ist
 * ein Zustandswechsel am Angebot als Ganzem und darum geschuetzt wie das Speichern: Wer auf einem
 * alten Stand schaltet, bekommt {@link AngebotGeaendert} — vor dem Storno und vor dem Ende der
 * Reihe, denn beides kann der andere eben erst herbeigefuehrt haben. Das handelnde Konto steht
 * danach als Urheber am Angebot.
 */
@Service
@Transactional
public class AngebotStatusUseCase {

  private final AngebotRepository angebote;
  private final Clock clock;

  AngebotStatusUseCase(final AngebotRepository angebote, final Clock clock) {
    this.angebote = angebote;
    this.clock = clock;
  }

  /**
   * Schaltet das Angebot eine Stufe weiter.
   *
   * @param angebotId Kennung des Angebots
   * @param version der vorgelegte Stand aus {@code If-Match}
   * @param konto Kennung des handelnden Kontos
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   * @throws AngebotGeaendert wenn das Angebot inzwischen einen anderen Stand hat als den
   *     vorgelegten
   * @throws AngebotStorniert wenn das Angebot storniert ist
   * @throws StatusGrenzeErreicht wenn das Angebot schon abgerechnet ist
   */
  public Angebot weiter(final long angebotId, final long version, final long konto) {
    return angebote.save(
        lies(angebotId, version, konto).statusWeiter(clock.instant()).durch(konto));
  }

  /**
   * Schaltet das Angebot eine Stufe zurueck.
   *
   * @param angebotId Kennung des Angebots
   * @param version der vorgelegte Stand aus {@code If-Match}
   * @param konto Kennung des handelnden Kontos
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   * @throws AngebotGeaendert wenn das Angebot inzwischen einen anderen Stand hat als den
   *     vorgelegten
   * @throws AngebotStorniert wenn das Angebot storniert ist
   * @throws StatusGrenzeErreicht wenn das Angebot erst angelegt ist
   */
  public Angebot zurueck(final long angebotId, final long version, final long konto) {
    return angebote.save(
        lies(angebotId, version, konto).statusZurueck(clock.instant()).durch(konto));
  }

  /* Liest das Angebot und weist ab, wenn der vorgelegte Stand nicht der aktuelle ist. */
  private Angebot lies(final long angebotId, final long version, final long konto) {
    final Angebot angebot = angebote.findById(angebotId).orElseThrow(AngebotNichtGefunden::new);
    Bearbeitungskonflikt.pruefe(
        version,
        angebot.version(),
        () -> new AngebotGeaendert(angebot.geaendertVon(), konto, angebot.updatedAt()));
    return angebot;
  }
}
