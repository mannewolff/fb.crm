package org.mwolff.fbcrm.angebot.application;

import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngabenRepository;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.AnsprechpartnerRepository;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.springframework.stereotype.Service;

/**
 * Liest die Nachbarn zusammen, aus denen ein Beleg entsteht (R8, Kriterium 12).
 *
 * <p>Ein eigener Baustein und keine drei weiteren Ports am Anwendungsfall: Das Versenden ist eine
 * Reihenfolge von Schritten — pruefen, Nummer ziehen, drucken, festschreiben, abloesen, vermerken
 * —, und wer daneben noch drei Stammdatenbestaende aufsammelt, erzaehlt zwei Geschichten in einer
 * Methode. Hier steht die eine Frage „was liegt neben diesem Angebot", dort die Reihenfolge.
 *
 * <p>Ohne eigene Transaktionsgrenze: Gelesen wird in der Transaktion des Aufrufers, damit die
 * Angaben, die die Pruefung sieht, dieselben sind, die in die Kopien gehen.
 */
@Service
class Versandunterlagen {

  private final FirmaRepository firmen;
  private final AnsprechpartnerRepository personen;
  private final EigeneAngabenRepository eigeneAngaben;

  Versandunterlagen(
      final FirmaRepository firmen,
      final AnsprechpartnerRepository personen,
      final EigeneAngabenRepository eigeneAngaben) {
    this.firmen = firmen;
    this.personen = personen;
    this.eigeneAngaben = eigeneAngaben;
  }

  /**
   * Die Angaben zu einem Angebot.
   *
   * @param angebot das Angebot, dessen Beleg entsteht
   * @throws FirmaNichtGefunden wenn es die Firma des Angebots nicht gibt
   */
  Belegangaben zu(final Angebot angebot) {
    final Firma firma = firmen.findById(angebot.firmaId()).orElseThrow(FirmaNichtGefunden::new);
    return new Belegangaben(firma, person(angebot.ansprechpartnerId()), eigeneAngaben.lies());
  }

  /*
   * Ein am Angebot vermerkter Ansprechpartner, den es im Bestand nicht mehr gibt, laesst den Versand
   * nicht scheitern: Kriterium 12 verlangt ihn nicht, und der Beleg traegt dann nur die Firma.
   */
  private @Nullable Ansprechpartner person(final @Nullable Long ansprechpartnerId) {
    if (ansprechpartnerId == null) {
      return null;
    }
    return personen.findById(ansprechpartnerId).orElse(null);
  }
}
