package org.mwolff.fbcrm.firma.application;

import java.time.Clock;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.AnsprechpartnerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Aendern der Angaben eines Ansprechpartners (Kriterium 11).
 *
 * <p>Die Firma bleibt, wie sie war — es gibt kein Feld dafuer und keinen Uebergang, der sie
 * anfasste. Passt die Kennung im Pfad nicht zu der des gespeicherten Ansprechpartners, ist er unter
 * dieser Firma nicht da (Kriterium 12, E7).
 *
 * <p>Der Stilllegungsstand bleibt ebenfalls: Wer einen stillgelegten Ansprechpartner korrigiert,
 * hat ihn damit nicht reaktiviert. Dafuer gibt es einen eigenen Weg.
 *
 * <p>Die Normalisierung steht <b>vor</b> dem Zugriff auf den Bestand — eine Eingabe, die ohnehin
 * abgewiesen wird, soll keine Abfrage kosten.
 */
@Service
@Transactional
public class AnsprechpartnerAendernUseCase {

  private final AnsprechpartnerRepository ansprechpartner;
  private final Clock clock;

  public AnsprechpartnerAendernUseCase(
      final AnsprechpartnerRepository ansprechpartner, final Clock clock) {
    this.ansprechpartner = ansprechpartner;
    this.clock = clock;
  }

  /**
   * Schreibt die neuen Angaben fort.
   *
   * @param firmaId Kennung der Firma aus dem Pfad
   * @param id technische Id des Ansprechpartners
   * @param daten die eingereichten Angaben; normalisiert werden sie hier (E9)
   * @throws AnsprechpartnerNichtGefunden wenn es ihn unter dieser Firma nicht gibt
   * @throws IllegalArgumentException wenn vom Nachnamen nur Leerraum uebrig bleibt
   */
  public void aendern(final long firmaId, final long id, final AnsprechpartnerDaten daten) {
    final AnsprechpartnerDaten sauber = daten.normalisiert();
    final Ansprechpartner vorhanden =
        ansprechpartner
            .findById(id)
            .filter(partner -> partner.firmaId() == firmaId)
            .orElseThrow(AnsprechpartnerNichtGefunden::new);
    ansprechpartner.save(
        vorhanden.geaendert(
            sauber.vorname(),
            sauber.nachname(),
            sauber.rolle(),
            sauber.email(),
            sauber.telefonFestnetz(),
            sauber.telefonMobil(),
            clock.instant()));
  }
}
