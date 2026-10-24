package org.mwolff.fbcrm.rechnung.application;

import java.time.Clock;
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
 * <p><b>Loeschen heisst kennzeichnen</b> (#411, Kriterium 6; Plan #415, E16, E17). Die Zeile bleibt
 * mit Zeitpunkt und Konto der Loeschung stehen, damit ein spaeterer Zugriff beides nennen kann.
 * Davor steht der Vergleich wie an jedem anderen Weg ({@link Nachtragsvergleich}): Eine inzwischen
 * geaenderte Rechnung wird nicht geloescht ({@link NachtragGeaendert}), eine schon geloeschte nicht
 * ein zweites Mal ({@link NachtragGeloescht}).
 *
 * <p>Die <b>Reihenfolge</b> ist die eigentliche Aussage (E14): erst die Zeile, das hinterlegte
 * Original erst nach dem Commit ({@link NachDemCommit}). Vor dem Commit geloescht, liesse ein
 * gescheiterter Commit eine Zeile ohne Objekt zurueck. Das Original braucht nach der Loeschung
 * keiner mehr — Urheber und Zeitpunkt stehen an der Zeile.
 */
@Service
@Transactional
public class NachtragLoeschenUseCase {

  private static final Logger LOG = LoggerFactory.getLogger(NachtragLoeschenUseCase.class);

  private final NachgetrageneRechnungRepository nachgetragene;
  private final DokumentSpeicher speicher;
  private final Clock clock;

  NachtragLoeschenUseCase(
      final NachgetrageneRechnungRepository nachgetragene,
      final DokumentSpeicher speicher,
      final Clock clock) {
    this.nachgetragene = nachgetragene;
    this.speicher = speicher;
    this.clock = clock;
  }

  /**
   * Kennzeichnet die Rechnung als geloescht und entfernt ihr hinterlegtes Original nach dem Commit.
   *
   * @param id Kennung der nachgetragenen Rechnung
   * @param version der vorgelegte Stand aus {@code If-Match}
   * @param konto Kennung des handelnden Kontos
   * @throws NachtragNichtGefunden wenn es die Rechnung nicht gibt
   * @throws NachtragGeloescht wenn die Rechnung inzwischen geloescht ist
   * @throws NachtragGeaendert wenn die Rechnung inzwischen einen anderen Stand hat als den
   *     vorgelegten
   */
  public void loesche(final long id, final long version, final long konto) {
    final NachgetrageneRechnung rechnung =
        nachgetragene.findById(id).orElseThrow(NachtragNichtGefunden::new);
    Nachtragsvergleich.pruefe(rechnung, version, konto);
    nachgetragene.save(rechnung.geloescht(konto, clock.instant()));
    final String schluessel = rechnung.pdfSchluessel();
    if (schluessel != null) {
      NachDemCommit.fuehreAus(() -> entferneObjekt(schluessel));
    }
  }

  /*
   * Ein Fehlschlag wird protokolliert und nicht gemeldet (E14): Die Zeile ist zu diesem Zeitpunkt
   * geloescht, und ihr Original erreicht kein Leseweg mehr. Im Log steht allein der Schluessel — er
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
