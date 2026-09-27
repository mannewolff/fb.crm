package org.mwolff.fbcrm.angebot.application;

import java.util.Objects;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.DokumentSpeicher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Wiederlesen des archivierten Belegs (Kriterium 14, E17).
 *
 * <p>Ausgeliefert wird das beim Versenden erzeugte Dokument und keine Neuberechnung aus heutigen
 * Daten — deshalb kennt dieser Anwendungsfall weder Layout noch Drucker, sondern nur den
 * Schluessel, den das festgeschriebene Angebot selbst traegt. Zieht die Firma spaeter um, bleibt
 * die Anschrift im alten Angebot die alte.
 *
 * <p>Ein Entwurf hat keinen Schluessel; das ist kein Widerspruch im Bestand, sondern ein Angebot,
 * das den Kunden noch nicht erreicht hat — {@link AngebotOhneBeleg} statt eines 404. Ein
 * unbekannter Schluessel <em>an einem festgeschriebenen Angebot</em> waere dagegen wirklich ein
 * Widerspruch; den gibt der Speicher als Fehler weiter, statt leere Bytes zu liefern.
 */
@Service
@Transactional(readOnly = true)
public class AngebotPdfLesenUseCase {

  private final AngebotRepository angebote;
  private final DokumentSpeicher speicher;

  public AngebotPdfLesenUseCase(final AngebotRepository angebote, final DokumentSpeicher speicher) {
    this.angebote = angebote;
    this.speicher = speicher;
  }

  /**
   * Der Beleg eines festgeschriebenen Angebots.
   *
   * @param angebotId Kennung des Angebots
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   * @throws AngebotOhneBeleg wenn das Angebot ein Entwurf ist
   */
  public Belegdokument pdf(final long angebotId) {
    final Angebot angebot = angebote.findById(angebotId).orElseThrow(AngebotNichtGefunden::new);
    final String schluessel = angebot.pdfSchluessel();
    if (schluessel == null) {
      throw new AngebotOhneBeleg();
    }
    // Die Nummer steht neben dem Schluessel: Der Check der Migration laesst in keinem
    // festgeschriebenen Zustand eines von beiden fehlen.
    return new Belegdokument(
        "%s.pdf".formatted(Objects.requireNonNull(angebot.nummer())), speicher.lies(schluessel));
  }
}
