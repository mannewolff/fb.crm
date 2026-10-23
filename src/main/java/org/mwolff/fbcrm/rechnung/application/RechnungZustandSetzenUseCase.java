package org.mwolff.fbcrm.rechnung.application;

import java.time.Clock;
import org.mwolff.fbcrm.common.Bearbeitungskonflikt;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Der Ausgang einer gestellten Rechnung: bezahlt, abgeschrieben, oder zurueck auf gestellt (Issue
 * #253).
 *
 * <p>Wenige Zeilen Ablauf und keine fachliche Entscheidung: Gelesen, Stand verglichen, umgestellt,
 * geschrieben. <b>Welcher Uebergang zulaessig ist, entscheidet die Domaene</b> ({@link
 * Rechnung#mitZustand(Rechnungszustand, java.time.Instant)}) — hier stuende die Regel ein zweites
 * Mal, und ein Weg an ihr vorbei waere der Weg ueber einen anderen Anwendungsfall.
 *
 * <p><b>Zuerst der Stand</b> (#411, Kriterien 4 und 5; Plan #415, E1, E14). Auch „bezahlt" und
 * „abgeschrieben" sind Zustandswechsel und darum geschuetzt: Wer auf einem alten Stand umstellt,
 * bekommt {@link RechnungGeaendert} — vor der Kantenregel der Domaene, denn der andere kann den
 * Zustand eben erst gewechselt haben. Das handelnde Konto steht danach als Urheber an der Rechnung.
 *
 * <p><b>Ohne Sperre auf der Zeile</b>, anders als beim Stellen: Hier wird keine Nummer gezogen.
 * Zwei Aufrufe auf demselben Stand faengt der Zaehler der Zeile ab ({@code @Version}); der zweite
 * scheitert beim Schreiben.
 */
@Service
@Transactional
public class RechnungZustandSetzenUseCase {

  private final RechnungRepository rechnungen;
  private final Clock clock;

  RechnungZustandSetzenUseCase(final RechnungRepository rechnungen, final Clock clock) {
    this.rechnungen = rechnungen;
    this.clock = clock;
  }

  /**
   * Stellt die Rechnung auf den neuen Zustand um und liefert sie zurueck.
   *
   * @param rechnungId Kennung der Rechnung
   * @param ziel der neue Zustand
   * @param version der vorgelegte Stand aus {@code If-Match}
   * @param konto Kennung des handelnden Kontos
   * @throws RechnungNichtGefunden wenn es die Rechnung nicht gibt
   * @throws RechnungGeaendert wenn die Rechnung inzwischen einen anderen Stand hat als den
   *     vorgelegten
   * @throws RechnungszustandPasstNicht wenn der Uebergang nicht zulaessig ist
   */
  public Rechnung setze(
      final long rechnungId, final Rechnungszustand ziel, final long version, final long konto) {
    final Rechnung rechnung =
        rechnungen.findById(rechnungId).orElseThrow(RechnungNichtGefunden::new);
    Bearbeitungskonflikt.pruefe(
        version,
        rechnung.version(),
        () -> new RechnungGeaendert(rechnung.geaendertVon(), konto, rechnung.updatedAt()));
    return rechnungen.save(rechnung.mitZustand(ziel, clock.instant()).durch(konto));
  }
}
