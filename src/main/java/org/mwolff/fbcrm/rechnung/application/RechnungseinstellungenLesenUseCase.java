package org.mwolff.fbcrm.rechnung.application;

import java.time.Clock;
import java.time.LocalDate;
import org.mwolff.fbcrm.common.Geschaeftszone;
import org.mwolff.fbcrm.rechnung.domain.Nummernkreis;
import org.mwolff.fbcrm.rechnung.domain.Rechnungseinstellungen;
import org.mwolff.fbcrm.rechnung.domain.RechnungseinstellungenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Rechnungseinstellungen samt naechster Nummer, wie sie im Bestand stehen (fachliche Quelle
 * #159, Kriterium 5).
 *
 * <p>Ohne Sonderzweig fuer die frische Instanz: Die Migration hat die eine Zeile der Einstellungen
 * mit ihren Vorbelegungen angelegt, und ein Zaehlerjahr ohne Zeile steht vor seiner ersten Nummer,
 * also bei 1.
 *
 * <p>Die Nummer gehoert dem Zaehlerjahr des <b>gespeicherten</b> Musters; das laufende Jahr kommt
 * aus der Geschaeftszone und nicht aus der UTC-Uhr (E12). In der ersten Stunde des 1. Januar zeigte
 * die Maske sonst den Zaehler des alten Jahres.
 */
@Service
@Transactional(readOnly = true)
public class RechnungseinstellungenLesenUseCase {

  private final RechnungseinstellungenRepository bestand;
  private final Nummernkreis nummernkreis;
  private final Clock clock;

  public RechnungseinstellungenLesenUseCase(
      final RechnungseinstellungenRepository bestand,
      final Nummernkreis nummernkreis,
      final Clock clock) {
    this.bestand = bestand;
    this.nummernkreis = nummernkreis;
    this.clock = clock;
  }

  /** Die Einstellungen und die naechste Nummer ihres Zaehlerjahrs. */
  public RechnungseinstellungenMitNummer lese() {
    final Rechnungseinstellungen einstellungen = bestand.lies();
    final int jahr = LocalDate.now(clock.withZone(Geschaeftszone.ZONE)).getYear();
    return new RechnungseinstellungenMitNummer(
        einstellungen, nummernkreis.lies(einstellungen.nummerMuster().zaehlerjahr(jahr)));
  }
}
