package org.mwolff.fbcrm.rechnung.application;

import java.time.Clock;
import java.time.LocalDate;
import org.mwolff.fbcrm.common.Geschaeftszone;
import org.mwolff.fbcrm.rechnung.domain.Nummernkreis;
import org.mwolff.fbcrm.rechnung.domain.Rechnungseinstellungen;
import org.mwolff.fbcrm.rechnung.domain.RechnungseinstellungenRepository;
import org.mwolff.fbcrm.rechnung.domain.Zeilenstand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Fortschreiben der Rechnungseinstellungen (fachliche Quelle #159, Kriterium 7).
 *
 * <p>Geschrieben wird immer der ganze Satz: Die Maske zeigt alle vier Werte auf einmal, und ein
 * Wert, der unveraendert zurueckkommt, ist nicht ausgelassen. Die Nummer kommt getrennt herein und
 * geht getrennt weiter — sie gehoert dem {@link Nummernkreis} und nicht den Einstellungen (#175).
 *
 * <p>Gesetzt wird der Zaehler des Zaehlerjahrs zum <b>neu eingereichten</b> Muster: Wer in
 * derselben Eingabe das Muster auf eines ohne Jahr umstellt und die Nummer auf 7 setzt, meint die 7
 * des neuen, jahreslosen Kreises (#160, Kriterium 15).
 *
 * <p>Der Zeitpunkt der Aenderung und das laufende Jahr kommen aus der injizierten {@link Clock} und
 * nicht aus {@code Instant.now()} — sonst waeren sie im Test nicht festzuhalten (CLAUDE-java.md
 * §6.2). Das Jahr rechnet gegen die Geschaeftszone (E12).
 *
 * <p><b>Vor dem Schreiben steht eine Pruefung</b> (#160, Kriterium 19; Plan #169, E16): Ergaebe die
 * eingereichte Nummer mit dem eingereichten Muster eine Rechnungsnummer, die eine Rechnung schon
 * traegt — auch eine nachgetragene (#254, Kriterium 5) —, wird abgewiesen. Ohne sie koennte der
 * Anwender den Zaehler widerspruchsfrei auf eine Zahl setzen, an der das naechste Stellen
 * unvermeidlich scheitern muesste — und er erfuehre es an einer Maske, an der die Ursache nicht
 * steht.
 *
 * <p><b>Davor steht nur der Stand</b> (#411, Kriterium 4; Plan #415, E1, E14): Bevor die Nummer
 * gefragt wird, vergleicht der Anwendungsfall den vorgelegten Stand mit dem der Zeile. Wer auf
 * einem alten Stand speichert, bekommt {@link RechnungseinstellungenGeaendert} mit Urheber und
 * Zeitpunkt der fremden Aenderung, und weder Einstellungen noch Zaehler werden geschrieben. Das
 * handelnde Konto steht danach als Urheber an der Zeile.
 */
@Service
@Transactional
public class RechnungseinstellungenPflegenUseCase {

  private final RechnungseinstellungenRepository bestand;
  private final Nummernkreis nummernkreis;
  private final Rechnungsnummern rechnungsnummern;
  private final Clock clock;

  public RechnungseinstellungenPflegenUseCase(
      final RechnungseinstellungenRepository bestand,
      final Nummernkreis nummernkreis,
      final Rechnungsnummern rechnungsnummern,
      final Clock clock) {
    this.bestand = bestand;
    this.nummernkreis = nummernkreis;
    this.rechnungsnummern = rechnungsnummern;
    this.clock = clock;
  }

  /**
   * Schreibt die neuen Einstellungen fort und setzt den Zaehler ihres Zaehlerjahrs.
   *
   * @param einstellungen die eingereichten Einstellungen, an der Schnittstelle bereits geprueft
   * @param naechsteNummer die eingereichte naechste laufende Nummer, ab 1
   * @param version der vorgelegte Stand aus {@code If-Match}
   * @param konto Kennung des handelnden Kontos
   * @return der neue Stand samt Nummer und erhoehter Version
   * @throws RechnungseinstellungenGeaendert wenn die Einstellungen inzwischen einen anderen Stand
   *     haben als den vorgelegten
   * @throws NaechsteNummerSchonVergeben wenn die Nummer eine vergebene Rechnungsnummer ergaebe
   */
  public RechnungseinstellungenMitNummer pflege(
      final Rechnungseinstellungen einstellungen,
      final int naechsteNummer,
      final long version,
      final long konto) {
    RechnungseinstellungenGeaendert.pruefe(bestand.stand(), version, konto);
    final int jahr = LocalDate.now(clock.withZone(Geschaeftszone.ZONE)).getYear();
    final int zaehlerjahr = einstellungen.nummerMuster().zaehlerjahr(jahr);
    // Gefragt wird mit dem Zaehlerjahr und nicht mit dem Kalenderjahr: Traegt das Muster ein Jahr,
    // sind beide gleich; traegt es keines, steht in der Nummer ohnehin keines (Nummernmuster).
    final String wuerdeEntstehen =
        einstellungen.nummerMuster().rechnungsnummer(naechsteNummer, zaehlerjahr);
    if (rechnungsnummern.vergeben(wuerdeEntstehen)) {
      throw new NaechsteNummerSchonVergeben(wuerdeEntstehen);
    }
    final Zeilenstand gespeichert =
        bestand.speichere(einstellungen, clock.instant(), version, konto);
    nummernkreis.setze(zaehlerjahr, naechsteNummer);
    return new RechnungseinstellungenMitNummer(
        einstellungen, naechsteNummer, gespeichert.version());
  }
}
