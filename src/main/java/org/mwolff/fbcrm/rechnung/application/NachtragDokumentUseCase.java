package org.mwolff.fbcrm.rechnung.application;

import java.time.Clock;
import org.mwolff.fbcrm.angebot.domain.Vorschauart;
import org.mwolff.fbcrm.common.NachDemCommit;
import org.mwolff.fbcrm.common.Uploadgrenze;
import org.mwolff.fbcrm.rechnung.domain.DokumentSpeicher;
import org.mwolff.fbcrm.rechnung.domain.DokumentSpeicherAusfall;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnung;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnungRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Original einer nachgetragenen Rechnung: hinterlegen, ersetzen, herunterladen, entfernen
 * (#254, Kriterien 2, 7 und 10; Plan #259).
 *
 * <p><b>Drei Riegel vor dem Speicher</b>, in dieser Reihenfolge: leer, zu gross ({@link
 * Uploadgrenze}), kein PDF. Ob es ein PDF ist, wird <b>am Inhalt</b> erkannt, allein ueber {@link
 * Vorschauart#erkannt} (E12) — der Anwendungsfall bekommt weder den Dateinamen noch die vom Browser
 * gemeldete Art, beides ist eine Eingabe von aussen. Eine HTML-Datei, die {@code rechnung.pdf}
 * heisst, ist kein PDF. Abgewiesen wird, bevor abgelegt wird; eine abgewiesene Datei hinterlaesst
 * keine Waise im Speicher.
 *
 * <p><b>Das alte Objekt geht erst nach dem Commit</b> ({@link NachDemCommit}, E14): Beim Ersetzen
 * und Entfernen zeigt die Zeile erst auf das neue oder auf kein Original, dann verschwindet das
 * alte. Vor dem Commit geloescht, liesse ein gescheiterter Commit eine Zeile zurueck, die auf ein
 * fehlendes Objekt zeigt.
 *
 * <p>Ohne hinterlegtes Original antworten Lesen und Entfernen mit {@link NachtragNichtGefunden}
 * (404, E17) — anders als beim Entwurf einer fb.crm-Rechnung ist nicht der Zustand das Hindernis,
 * sondern die Sache fehlt.
 */
@Service
@Transactional
public class NachtragDokumentUseCase {

  private static final Logger LOG = LoggerFactory.getLogger(NachtragDokumentUseCase.class);

  private final NachgetrageneRechnungRepository nachgetragene;
  private final DokumentSpeicher speicher;
  private final Clock clock;

  NachtragDokumentUseCase(
      final NachgetrageneRechnungRepository nachgetragene,
      final DokumentSpeicher speicher,
      final Clock clock) {
    this.nachgetragene = nachgetragene;
    this.speicher = speicher;
    this.clock = clock;
  }

  /**
   * Hinterlegt das Original; ein vorhandenes wird ersetzt und nach dem Commit entfernt.
   *
   * @param id Kennung der nachgetragenen Rechnung
   * @param inhalt das hochgeladene Dokument
   * @return die Rechnung mit dem Schluessel des neuen Originals
   * @throws NachtragNichtGefunden wenn es die Rechnung nicht gibt
   * @throws DokumentNichtAnnehmbar wenn die Datei leer, zu gross oder kein PDF ist
   * @throws DokumentSpeicherAusfall wenn der Speicher die Ablage nicht annimmt — dann ist nichts
   *     gespeichert
   */
  public NachgetrageneRechnung lege(final long id, final byte[] inhalt) {
    final NachgetrageneRechnung rechnung = gesucht(id);
    pruefe(inhalt);
    final String neu = speicher.legeHochgeladenes(id, inhalt);
    final NachgetrageneRechnung gesichert =
        nachgetragene.save(rechnung.mitDokument(neu, clock.instant()));
    final String alt = rechnung.pdfSchluessel();
    if (alt != null) {
      NachDemCommit.fuehreAus(() -> entferneObjekt(alt));
    }
    return gesichert;
  }

  /**
   * Das hinterlegte Original, Byte fuer Byte.
   *
   * @param id Kennung der nachgetragenen Rechnung
   * @throws NachtragNichtGefunden wenn es die Rechnung nicht gibt oder sie kein Original traegt
   */
  @Transactional(readOnly = true)
  public byte[] lies(final long id) {
    return speicher.lies(schluesselVon(gesucht(id)));
  }

  /**
   * Entfernt das Original; das Objekt geht nach dem Commit.
   *
   * @param id Kennung der nachgetragenen Rechnung
   * @return die Rechnung ohne Original
   * @throws NachtragNichtGefunden wenn es die Rechnung nicht gibt oder sie kein Original traegt
   */
  public NachgetrageneRechnung entferne(final long id) {
    final NachgetrageneRechnung rechnung = gesucht(id);
    final String alt = schluesselVon(rechnung);
    final NachgetrageneRechnung gesichert =
        nachgetragene.save(rechnung.ohneDokument(clock.instant()));
    NachDemCommit.fuehreAus(() -> entferneObjekt(alt));
    return gesichert;
  }

  private NachgetrageneRechnung gesucht(final long id) {
    return nachgetragene.findById(id).orElseThrow(NachtragNichtGefunden::new);
  }

  private static String schluesselVon(final NachgetrageneRechnung rechnung) {
    final String schluessel = rechnung.pdfSchluessel();
    if (schluessel == null) {
      throw new NachtragNichtGefunden();
    }
    return schluessel;
  }

  /*
   * Die Groesse steht vor dem Inhalt: Eine zu grosse Datei ist abzuweisen, gleich was sie ist.
   */
  private static void pruefe(final byte[] inhalt) {
    if (inhalt.length == 0) {
      throw DokumentNichtAnnehmbar.leer();
    }
    if (inhalt.length > Uploadgrenze.MAX_BYTE) {
      throw DokumentNichtAnnehmbar.zuGross();
    }
    if (Vorschauart.erkannt(inhalt).filter(Vorschauart.PDF::equals).isEmpty()) {
      throw DokumentNichtAnnehmbar.keinPdf();
    }
  }

  /*
   * Ein Fehlschlag wird protokolliert und nicht gemeldet (E14): Die Zeile zeigt zu diesem Zeitpunkt
   * auf ein anderes oder kein Original, und ein Objekt ohne Zeile ist unerreichbar. Im Log steht
   * allein der Schluessel — er traegt weder Nummer noch Betrag der Rechnung.
   */
  private void entferneObjekt(final String schluessel) {
    try {
      speicher.loesche(schluessel);
    } catch (final DokumentSpeicherAusfall ausfall) {
      LOG.warn(
          "Das ersetzte oder entfernte Original {} einer nachgetragenen Rechnung blieb im Speicher"
              + " liegen.",
          schluessel,
          ausfall);
    }
  }
}
