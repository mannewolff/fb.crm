package org.mwolff.fbcrm.rechnung.application;

import org.mwolff.fbcrm.common.NachDemCommit;
import org.mwolff.fbcrm.rechnung.domain.DokumentSpeicher;
import org.mwolff.fbcrm.rechnung.domain.DokumentSpeicherAusfall;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnung;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnungRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Loeschen einer nachgetragenen Rechnung — in jedem Zustand (#254, Kriterium 10; Plan #259).
 *
 * <p>Anders als bei der Rechnung gibt es keine Grenze: fb.crm hat sie nicht erzeugt, also gibt es
 * nichts festzuschreiben und keine Luecke im Nummernkreis.
 *
 * <p>Die <b>Reihenfolge</b> ist die eigentliche Aussage (E14): erst die Zeile, das hinterlegte
 * Original erst nach dem Commit ({@link NachDemCommit}). Vor dem Commit geloescht, liesse ein
 * gescheiterter Commit eine Zeile ohne Objekt zurueck.
 */
@Service
@Transactional
public class NachtragLoeschenUseCase {

  private static final Logger LOG = LoggerFactory.getLogger(NachtragLoeschenUseCase.class);

  private final NachgetrageneRechnungRepository nachgetragene;
  private final DokumentSpeicher speicher;

  NachtragLoeschenUseCase(
      final NachgetrageneRechnungRepository nachgetragene, final DokumentSpeicher speicher) {
    this.nachgetragene = nachgetragene;
    this.speicher = speicher;
  }

  /**
   * Loescht die Rechnung samt ihrem hinterlegten Original.
   *
   * @param id Kennung der nachgetragenen Rechnung
   * @throws NachtragNichtGefunden wenn es die Rechnung nicht gibt
   */
  public void loesche(final long id) {
    final NachgetrageneRechnung rechnung =
        nachgetragene.findById(id).orElseThrow(NachtragNichtGefunden::new);
    nachgetragene.delete(id);
    final String schluessel = rechnung.pdfSchluessel();
    if (schluessel != null) {
      NachDemCommit.fuehreAus(() -> entferneObjekt(schluessel));
    }
  }

  /*
   * Ein Fehlschlag wird protokolliert und nicht gemeldet (E14): Die Zeile ist zu diesem Zeitpunkt
   * fort, und ein Objekt ohne Zeile ist unerreichbar. Im Log steht allein der Schluessel — er
   * traegt weder Nummer noch Betrag der Rechnung.
   */
  private void entferneObjekt(final String schluessel) {
    try {
      speicher.loesche(schluessel);
    } catch (final DokumentSpeicherAusfall ausfall) {
      LOG.warn(
          "Das Original {} einer geloeschten nachgetragenen Rechnung blieb im Speicher liegen.",
          schluessel,
          ausfall);
    }
  }
}
