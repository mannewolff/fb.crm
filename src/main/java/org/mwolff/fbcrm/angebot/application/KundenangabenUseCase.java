package org.mwolff.fbcrm.angebot.application;

import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.AnsprechpartnerRepository;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Namen von Firma und Ansprechpartner eines Angebots, fuer Kopf und Angaben der Ansicht (Issue
 * #126).
 *
 * <p>Ein eigener Lesebaustein und kein Feld des Angebots: Jeder Anwendungsfall am Angebot liefert
 * das Angebot, und keiner von ihnen braucht die Namen fuer seine eigene Arbeit. Der Controller
 * fragt sie einmal je Antwort hinzu.
 *
 * <p>Ein Ansprechpartner, den es im Bestand nicht mehr gibt, ist kein Fehler: Die Ansicht nennt
 * dann nur die Firma. Eine Firma wird nie geloescht; fehlt sie trotzdem, ist das ein Widerspruch im
 * Bestand und wird gemeldet.
 */
@Service
@Transactional(readOnly = true)
public class KundenangabenUseCase {

  private final FirmaRepository firmen;
  private final AnsprechpartnerRepository personen;

  public KundenangabenUseCase(
      final FirmaRepository firmen, final AnsprechpartnerRepository personen) {
    this.firmen = firmen;
    this.personen = personen;
  }

  /**
   * Die Namen zu einem Angebot.
   *
   * @param angebot das Angebot
   * @throws FirmaNichtGefunden wenn es die Firma des Angebots nicht gibt
   */
  public Kundenangaben zu(final Angebot angebot) {
    final String firmaName =
        firmen.findById(angebot.firmaId()).orElseThrow(FirmaNichtGefunden::new).name();
    return new Kundenangaben(firmaName, person(angebot.ansprechpartnerId()));
  }

  private @Nullable String person(final @Nullable Long ansprechpartnerId) {
    if (ansprechpartnerId == null) {
      return null;
    }
    return personen.findById(ansprechpartnerId).map(Personenname::von).orElse(null);
  }
}
