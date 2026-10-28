package org.mwolff.fbcrm.eigeneangaben.application;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Bankverbindungszerlegung;
import org.mwolff.fbcrm.common.Iban;
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
 *
 * <p><b>Die Bankverbindung</b> (Plan #461, E13, E15): Eine IBAN wird geprueft und ohne Leerzeichen
 * in Grossbuchstaben gespeichert, eine BIC nur auf ihre Form geprueft und in Grossbuchstaben
 * gespeichert. Ist eine davon ungueltig, wird mit {@link BankverbindungUngueltig} abgewiesen, und
 * nichts wird geschrieben. Die bisherige Bankverbindung kommt nicht aus der Eingabe, sondern aus
 * dem Bestand: Sie bleibt stehen, solange IBAN oder Kontoinhaber leer sind, und wird erst geleert,
 * wenn beide gefuellt sind.
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
   * @throws BankverbindungUngueltig wenn IBAN oder BIC ungueltig sind
   * @throws EigeneAngabenGeaendert wenn die Angaben inzwischen einen anderen Stand haben als den
   *     vorgelegten
   */
  public GespeicherteEigeneAngaben pflege(
      final EigeneAngaben angaben, final long version, final long konto) {
    final Bankfelder bank = bankfelder(angaben);
    EigeneAngabenGeaendert.pruefe(bestand.stand(), version, konto);
    final EigeneAngaben sauber = normalisiert(angaben, bank);
    final EigeneAngabenStand neu = bestand.speichere(sauber, clock.instant(), version, konto);
    return new GespeicherteEigeneAngaben(sauber, neu.version());
  }

  /** IBAN und BIC in der Form, in der sie gespeichert werden. */
  private record Bankfelder(@Nullable String iban, @Nullable String bic) {}

  private static Bankfelder bankfelder(final EigeneAngaben angaben) {
    final Map<String, List<String>> ungueltig = new LinkedHashMap<>();
    final String eingegeben = ohneLeerraum(angaben.iban());
    String iban = null;
    if (eingegeben != null) {
      try {
        iban = Iban.aus(eingegeben).elektronisch();
      } catch (final IllegalArgumentException keineIban) {
        ungueltig.put("iban", List.of(BankverbindungUngueltig.IBAN_UNGUELTIG));
      }
    }
    final String bic = grossgeschrieben(ohneLeerraum(angaben.bic()));
    if (bic != null && !Bankverbindungszerlegung.istBic(bic)) {
      ungueltig.put("bic", List.of(BankverbindungUngueltig.BIC_UNGUELTIG));
    }
    if (!ungueltig.isEmpty()) {
      throw new BankverbindungUngueltig(ungueltig);
    }
    return new Bankfelder(iban, bic);
  }

  private static @Nullable String grossgeschrieben(final @Nullable String wert) {
    return wert == null ? null : wert.toUpperCase(Locale.ROOT);
  }

  private EigeneAngaben normalisiert(final EigeneAngaben angaben, final Bankfelder bank) {
    final Anschrift anschrift = angaben.anschrift();
    final String kontoinhaber = ohneLeerraum(angaben.kontoinhaber());
    final String bisherige =
        bank.iban() != null && kontoinhaber != null
            ? null
            : bestand.lies().bisherigeBankverbindung();
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
        bank.iban(),
        bank.bic(),
        kontoinhaber,
        bisherige);
  }

  private static @Nullable String ohneLeerraum(final @Nullable String wert) {
    if (wert == null) {
      return null;
    }
    final String sauberer = wert.strip();
    return sauberer.isEmpty() ? null : sauberer;
  }
}
