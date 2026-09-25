package org.mwolff.fbcrm.firma.application;

import java.time.Clock;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Aendern der Angaben einer Firma (Kriterium 8).
 *
 * <p>Der Stilllegungsstand bleibt, wie er war: Wer eine stillgelegte Firma korrigiert, hat sie
 * damit nicht wieder in Betrieb genommen (Kriterium 14). Dafuer gibt es einen eigenen Weg.
 *
 * <p>Die Normalisierung steht <b>vor</b> dem Zugriff auf den Bestand — eine Eingabe, die ohnehin
 * abgewiesen wird, soll keine Abfrage kosten.
 */
@Service
@Transactional
public class FirmaAendernUseCase {

  private final FirmaRepository firmen;
  private final Clock clock;

  public FirmaAendernUseCase(final FirmaRepository firmen, final Clock clock) {
    this.firmen = firmen;
    this.clock = clock;
  }

  /**
   * Schreibt die neuen Angaben fort.
   *
   * @param id technische Id der Firma
   * @param daten die eingereichten Angaben; normalisiert werden sie hier (E9)
   * @throws FirmaNichtGefunden wenn es die Firma nicht gibt
   * @throws IllegalArgumentException wenn vom Namen nur Leerraum uebrig bleibt
   */
  public void aendern(final long id, final FirmaDaten daten) {
    final FirmaDaten sauber = daten.normalisiert();
    final Firma vorhanden = firmen.findById(id).orElseThrow(FirmaNichtGefunden::new);
    firmen.save(
        vorhanden.geaendert(
            sauber.name(),
            sauber.anschrift(),
            sauber.steuernummer(),
            sauber.umsatzsteuerId(),
            clock.instant()));
  }
}
