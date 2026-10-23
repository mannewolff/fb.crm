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
 *
 * <p><b>Danach zuerst der Stand</b> (#411, Kriterium 4; Plan #415, E1, E14): Wer auf einem alten
 * Stand speichert, bekommt {@link AnsprechpartnerGeaendert} mit Urheber und Zeitpunkt der fremden
 * Aenderung. Das handelnde Konto steht danach als Urheber am Ansprechpartner.
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
   * @param version der vorgelegte Stand aus {@code If-Match}
   * @param konto Kennung des handelnden Kontos
   * @return der Ansprechpartner mit dem neuen Stand
   * @throws AnsprechpartnerNichtGefunden wenn es ihn unter dieser Firma nicht gibt
   * @throws AnsprechpartnerGeaendert wenn er inzwischen einen anderen Stand hat als den vorgelegten
   * @throws IllegalArgumentException wenn vom Nachnamen nur Leerraum uebrig bleibt
   */
  public Ansprechpartner aendern(
      final long firmaId,
      final long id,
      final AnsprechpartnerDaten daten,
      final long version,
      final long konto) {
    final AnsprechpartnerDaten sauber = daten.normalisiert();
    final Ansprechpartner vorhanden =
        ansprechpartner
            .findById(id)
            .filter(partner -> partner.firmaId() == firmaId)
            .orElseThrow(AnsprechpartnerNichtGefunden::new);
    AnsprechpartnerGeaendert.pruefe(vorhanden, version, konto);
    return ansprechpartner.save(
        vorhanden
            .geaendert(
                sauber.vorname(),
                sauber.nachname(),
                sauber.rolle(),
                sauber.email(),
                sauber.telefonFestnetz(),
                sauber.telefonMobil(),
                clock.instant())
            .durch(konto));
  }
}
