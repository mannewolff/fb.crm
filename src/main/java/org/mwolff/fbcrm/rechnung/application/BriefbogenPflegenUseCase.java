package org.mwolff.fbcrm.rechnung.application;

import java.time.Clock;
import java.util.Arrays;
import org.mwolff.fbcrm.rechnung.application.Randgrenzen.Rand;
import org.mwolff.fbcrm.rechnung.domain.Briefbogen;
import org.mwolff.fbcrm.rechnung.domain.BriefbogenRepository;
import org.mwolff.fbcrm.rechnung.domain.Randmasse;
import org.mwolff.fbcrm.rechnung.domain.Zeilenstand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Fortschreiben von Schaltern und Raendern des Briefbogens (fachliche Quelle #361, Kriterien 13
 * und 16; Plan #363, E9 und E15).
 *
 * <p>Geschrieben wird immer der ganze Satz. Das Briefpapier gehoert nicht dazu: Es bleibt, wie es
 * hinterlegt ist, und Ersetzen oder Entfernen laufen ueber eigene Wege.
 *
 * <p><b>Vor dem Schreiben steht die Randpruefung</b> mit den eingereichten Schaltern. Passt der
 * Satz nicht, faellt der ganze {@code PUT}, und die bisherige Zeile bleibt. Die Meldung haengt am
 * geaenderten Rand: Welcher das ist, entsteht aus dem Vergleich mit der gespeicherten Zeile — bei
 * mehreren abweichenden am ersten in der Reihenfolge links, rechts, Beginn des Inhalts, unten,
 * Folgeseite oben, Folgeseite unten (E9). Weicht keiner ab, hat allein ein Schalter den Platz
 * genommen; dann haengt sie am ersten Rand, der durch Verkleinern wieder passt, und gibt es keinen,
 * am linken.
 *
 * <p><b>Davor steht nur der Stand</b> (#411, Kriterium 4; Plan #415, E1, E11, E14): Bevor die
 * Raender geprueft werden, vergleicht der Anwendungsfall den vorgelegten Stand mit dem der Zeile.
 * Der Stand gilt fuer den ganzen Briefbogen: Wer Raender auf dem Stand vor einem Hochladen
 * speichert, bekommt {@link BriefbogenGeaendert}. Das handelnde Konto steht danach als Urheber an
 * der Zeile.
 *
 * <p>Der Zeitpunkt der Aenderung kommt aus der injizierten {@link Clock} (CLAUDE-java.md §6.2).
 */
@Service
@Transactional
public class BriefbogenPflegenUseCase {

  private final BriefbogenRepository bestand;
  private final Clock clock;

  public BriefbogenPflegenUseCase(final BriefbogenRepository bestand, final Clock clock) {
    this.bestand = bestand;
    this.clock = clock;
  }

  /**
   * Schreibt Schalter und Raender fort.
   *
   * @param absenderzeile ob die Absenderzeile eingedruckt wird
   * @param steuerBankZeile ob die Steuer- und Bankzeile eingedruckt wird
   * @param raender die sechs Raender, an der Schnittstelle bereits auf nicht negativ geprueft
   * @param version der vorgelegte Stand aus {@code If-Match}
   * @param konto Kennung des handelnden Kontos
   * @return der neue Stand mit erhoehter Version
   * @throws BriefbogenGeaendert wenn der Briefbogen inzwischen einen anderen Stand hat als den
   *     vorgelegten
   * @throws RandNichtMoeglich wenn die Raender dem Inhalt keinen Platz mehr lassen
   */
  public Briefbogenstand pflege(
      final boolean absenderzeile,
      final boolean steuerBankZeile,
      final Randmasse raender,
      final long version,
      final long konto) {
    BriefbogenGeaendert.pruefe(bestand.stand(), version, konto);
    final Briefbogen bisher = bestand.lies();
    if (!Randgrenzen.passt(raender, absenderzeile, steuerBankZeile)) {
      final Rand rand =
          Randgrenzen.abweichenderRand(bisher.raender(), raender)
              .orElseGet(() -> ersterVerkleinerbarer(raender, absenderzeile, steuerBankZeile));
      throw new RandNichtMoeglich(
          rand, Randgrenzen.groesstZulaessig(rand, raender, absenderzeile, steuerBankZeile));
    }
    final Briefbogen neu = new Briefbogen(bisher.blatt(), absenderzeile, steuerBankZeile, raender);
    final Zeilenstand gespeichert = bestand.speichere(neu, clock.instant(), version, konto);
    return Briefbogenstand.von(neu, gespeichert.version());
  }

  /** Der erste Rand, mit dem der Satz durch Verkleinern wieder passt; sonst der linke. */
  private static Rand ersterVerkleinerbarer(
      final Randmasse raender, final boolean absenderzeile, final boolean steuerBankZeile) {
    return Arrays.stream(Rand.values())
        .filter(
            rand ->
                Randgrenzen.groesstZulaessig(rand, raender, absenderzeile, steuerBankZeile)
                    .isPresent())
        .findFirst()
        .orElse(Rand.LINKS);
  }
}
