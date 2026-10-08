package org.mwolff.fbcrm.eigeneangaben.application;

import java.time.Clock;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngabenRepository;
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
   */
  public void pflege(final EigeneAngaben angaben) {
    bestand.speichere(normalisiert(angaben), clock.instant());
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
