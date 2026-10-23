package org.mwolff.fbcrm.angebot.infrastructure;

import org.mwolff.fbcrm.auth.domain.ErstesKontoFolge;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gibt die Kommentare ohne Verfasser dem ersten Konto der Instanz (Plan #414, E13).
 *
 * <p>Solche Kommentare gibt es nur in einem Bestand, der beim Update auf {@code V31} Kommentare und
 * kein Konto hatte — etwa nach dem frueheren Rueckweg „verlorenes Postfach", der das Konto
 * loeschte. Wer danach einrichtet, bekommt sie: So geht nichts verloren, und einen Kommentar ohne
 * Verfasser sieht nie jemand.
 *
 * <p>Die Anwendung schreibt nur Daten. Der {@code CHECK} auf {@code verfasser_id} bleibt {@code NOT
 * VALID}, wie {@code V31} ihn anlegt; er bindet ohnehin jede Zeile, die geschrieben wird.
 *
 * <p>Die Richtung der Abhaengigkeit ist {@code angebot} → {@code auth.domain} (A7): {@code auth}
 * ruft den Port und kennt dieses Modul nicht.
 */
@Component
class KommentarUebernahme implements ErstesKontoFolge {

  private final SpringDataAngebotKommentarRepository zeilen;

  KommentarUebernahme(final SpringDataAngebotKommentarRepository zeilen) {
    this.zeilen = zeilen;
  }

  /**
   * {@inheritDoc}
   *
   * <p>{@link Propagation#MANDATORY}: Die Uebernahme gehoert in die Transaktion, in der das Konto
   * entsteht. Ohne sie scheitert der Aufruf, statt Kommentare einem Konto zu geben, das mit einem
   * spaeteren Fehler wieder verschwaende.
   */
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void erstesKontoAngelegt(final long kontoId) {
    zeilen.gibVerfasserloseAn(kontoId);
  }
}
