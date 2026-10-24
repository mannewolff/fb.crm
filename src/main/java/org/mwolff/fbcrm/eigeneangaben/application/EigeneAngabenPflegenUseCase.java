package org.mwolff.fbcrm.eigeneangaben.application;

import java.time.Clock;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngabenRepository;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngabenStand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Fortschreiben der eigenen Angaben (Kriterium 1).
 *
 * <p>Geschrieben wird immer der ganze Satz: Die Maske zeigt alle Felder auf einmal, und ein Feld,
 * das leer zurueckkommt, ist geleert worden und nicht ausgelassen.
 *
 * <p>Hier liegt die Normalisierung (E9): Leerraum am Rand faellt weg, und was danach leer ist, wird
 * {@code null} statt Leerstring — sonst gaebe es zwei Schreibweisen fuer „nicht angegeben", die
 * Anzeige und spaeter der Beleg beide kennen muessten.
 *
 * <p><b>Davor steht der Stand</b> (#411, Kriterium 4; Plan #415, E1, E14): Der Anwendungsfall
 * vergleicht den vorgelegten Stand mit dem der Zeile. Wer auf einem alten Stand speichert, bekommt
 * {@link EigeneAngabenGeaendert} mit Urheber und Zeitpunkt der fremden Aenderung, und nichts wird
 * geschrieben. Das handelnde Konto steht danach als Urheber an der Zeile.
 */
@Service
@Transactional
public class EigeneAngabenPflegenUseCase {

  private final EigeneAngabenRepository bestand;
  private final Clock clock;

  public EigeneAngabenPflegenUseCase(final EigeneAngabenRepository bestand, final Clock clock) {
    this.bestand = bestand;
    this.clock = clock;
  }

  /**
   * Schreibt die neuen Angaben fort.
   *
   * @param angaben die eingereichten Angaben; normalisiert werden sie hier (E9)
   * @param version der vorgelegte Stand aus {@code If-Match}
   * @param konto Kennung des handelnden Kontos
   * @return die gespeicherten Angaben samt erhoehter Version
   * @throws EigeneAngabenGeaendert wenn die Angaben inzwischen einen anderen Stand haben als den
   *     vorgelegten
   */
  public GespeicherteEigeneAngaben pflege(
      final EigeneAngaben angaben, final long version, final long konto) {
    EigeneAngabenGeaendert.pruefe(bestand.stand(), version, konto);
    final EigeneAngaben sauber = normalisiert(angaben);
    final EigeneAngabenStand neu = bestand.speichere(sauber, clock.instant(), version, konto);
    return new GespeicherteEigeneAngaben(sauber, neu.version());
  }

  private static EigeneAngaben normalisiert(final EigeneAngaben angaben) {
    final Anschrift anschrift = angaben.anschrift();
    return new EigeneAngaben(
        ohneLeerraum(angaben.name()),
        ohneLeerraum(angaben.berufsbezeichnung()),
        new Anschrift(
            ohneLeerraum(anschrift.strasse()),
            ohneLeerraum(anschrift.plz()),
            ohneLeerraum(anschrift.ort()),
            ohneLeerraum(anschrift.land())),
        ohneLeerraum(angaben.email()),
        ohneLeerraum(angaben.telefon()),
        ohneLeerraum(angaben.webadresse()),
        ohneLeerraum(angaben.steuernummer()),
        ohneLeerraum(angaben.umsatzsteuerId()),
        ohneLeerraum(angaben.bankverbindung()));
  }

  private static @Nullable String ohneLeerraum(final @Nullable String wert) {
    if (wert == null) {
      return null;
    }
    final String sauberer = wert.strip();
    return sauberer.isEmpty() ? null : sauberer;
  }
}
