package org.mwolff.fbcrm.arbeitszeit.application;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.arbeitszeit.domain.ZeiteintragRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Was die Zeiterfassung anderen Modulen ueber ein Angebot sagt (Plan #194, A1).
 *
 * <p><b>Die eine Tuer nach draussen.</b> {@code rechnung} braucht die Stunden eines Monats, um
 * einen Entwurf vorzubelegen (Issue #193, Kriterium 9), und spaeter die insgesamt angefallenen fuer
 * die Spalte „Angefallen" am Angebot. Beides geht ueber diese Klasse und nicht ueber {@link
 * ZeiteintragRepository}: Der Port gehoert diesem Modul, und ein fremdes Modul, das ihn selbst
 * aufruft, muesste wissen, welche Positionen ein Angebot hat und wie der Bestand antwortet. Die
 * Richtung der Abhaengigkeit ist damit {@code rechnung} → {@code arbeitszeit}; umgekehrt zeigt
 * nichts.
 *
 * <p><b>Gefragt wird mit allen Positionen des Angebots</b>, auch mit den nicht buchbaren. Welche
 * Position Stunden tragen darf, entscheidet der Leser an {@link Buchbarkeit} — eine zweite
 * Abschrift dieser Regel liefe beim ersten Nachziehen auseinander. Und eine Position, auf die nie
 * gebucht werden konnte, steht ohnehin mit {@code 0.00} in der Antwort: Der Bestand liefert jede
 * angefragte Kennung.
 *
 * <p><b>Ein Zug in die Angebote und einer in die Zeiten</b>, nie einer je Position: Der Bestand
 * nimmt alle Kennungen auf einmal entgegen. Die Kennung des Angebots und nicht die Liste seiner
 * Positionen ist der Parameter, damit der Aufrufer die Positionen nicht erst zusammentragen muss.
 */
@Service
@Transactional(readOnly = true)
public class Arbeitszeitauskunft {

  private final ZeiteintragRepository zeiten;
  private final AngebotRepository angebote;

  Arbeitszeitauskunft(final ZeiteintragRepository zeiten, final AngebotRepository angebote) {
    this.zeiten = zeiten;
    this.angebote = angebote;
  }

  /**
   * Die Stunden eines Monats je Position des Angebots (Issue #193, Kriterium 9).
   *
   * @param angebotId Kennung des Angebots
   * @param monat der Monat, dessen Eintraege zaehlen
   * @return je Position des Angebots ihre Stunden in diesem Monat; eine Position ohne Eintrag steht
   *     mit {@code 0.00} darin, keine fehlt
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt — ohne seine Positionen ist nicht
   *     bekannt, nach welchen Stunden zu fragen waere
   */
  public Map<Long, BigDecimal> imMonat(final long angebotId, final YearMonth monat) {
    return zeiten.stundenJePositionImMonat(positionen(angebotId), monat);
  }

  /**
   * Die insgesamt erfassten Stunden je Position des Angebots (Issue #193, Kriterium 7).
   *
   * <p>Ueber alle Monate: Das ist die Spalte „Angefallen" am Angebot, die auch nach dem Sprung auf
   * „abgerechnet" stehen bleibt (Kriterium 8). Welche Position Stunden tragen darf, entscheidet der
   * Leser an {@link Buchbarkeit} — eine nicht buchbare Position steht mit {@code 0.00} darin.
   *
   * @param angebotId Kennung des Angebots
   * @return je Position des Angebots ihre insgesamt erfassten Stunden; eine Position ohne Eintrag
   *     steht mit {@code 0.00} darin, keine fehlt
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt — ohne seine Positionen ist nicht
   *     bekannt, nach welchen Stunden zu fragen waere
   */
  public Map<Long, BigDecimal> angefallen(final long angebotId) {
    return zeiten.angefallenJePosition(positionen(angebotId));
  }

  private Set<Long> positionen(final long angebotId) {
    final Angebot angebot = angebote.findById(angebotId).orElseThrow(AngebotNichtGefunden::new);
    return angebot.positionen().stream()
        .map(Angebotsposition::requireId)
        .collect(Collectors.toSet());
  }
}
