package org.mwolff.fbcrm.angebot.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsanlage;
import org.mwolff.fbcrm.angebot.domain.AngebotsanlageRepository;
import org.mwolff.fbcrm.angebot.domain.AnlageSpeicher;
import org.mwolff.fbcrm.angebot.domain.AnlageSpeicherAusfall;
import org.mwolff.fbcrm.angebot.domain.Dateiname;
import org.mwolff.fbcrm.angebot.domain.Vorschauart;
import org.mwolff.fbcrm.common.NachDemCommit;
import org.mwolff.fbcrm.common.Uploadgrenze;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Anwendungsfaelle der Anlagen am Angebot: auflisten, hochladen, Inhalt lesen, loeschen (Issue
 * #148, Kriterien 1 bis 14).
 *
 * <p>Hier laufen die beiden Bestaende zusammen, die eine Anlage ausmachen: die Zeile in {@code
 * angebot_anlage} und das Objekt im {@link AnlageSpeicher}. Ihre <b>Reihenfolge</b> ist die
 * eigentliche Aussage dieser Klasse (Plan #150, E9), denn sie entscheidet, was ein Abbruch
 * hinterlaesst:
 *
 * <ul>
 *   <li><b>Hochladen:</b> erst das Objekt, dann die Zeile. Ein Abbruch dazwischen laesst hoechstens
 *       ein verwaistes Objekt liegen — unerreichbar, aber harmlos. Umgekehrt stuende eine Anlage in
 *       der Liste, die sich nicht oeffnen liesse (Kriterium 14).
 *   <li><b>Loeschen:</b> die Zeile in der Transaktion, das Objekt <b>erst nach dem Commit</b>. Vor
 *       dem Commit geloescht, liesse ein gescheiterter Commit genau die Zeile ohne Objekt zurueck,
 *       die der Punkt davor vermeidet.
 * </ul>
 *
 * <p>Jeder der vier Wege prueft zuerst, dass es das Angebot gibt — auch die, die ohne diese Abfrage
 * bereits 404 liefern wuerden. Das ist die Aussage der Pfade: Sie liegen unter {@code
 * /api/angebote/{id}}, und eine unbekannte Kennung dort ist 404 und nicht eine leere Liste oder
 * eine Meldung ueber die eingereichte Datei.
 *
 * <p><b>Was hier entschieden wird</b> und nicht in der Domaene: Der Dateiname wird ueber {@link
 * Dateiname} gesaeubert und ein danach leerer Name abgewiesen; leere und zu grosse Dateien weist
 * der Anwendungsfall ab, bevor ein Objekt entsteht (E8); und die {@link Vorschauart} wird <b>am
 * Inhalt</b> erkannt, nicht am Namen und nicht an der vom Browser gemeldeten Art (E4, Kriterium
 * 15). Der Zeitpunkt kommt aus der injizierten {@link Clock} (CLAUDE-java.md §6.2).
 */
@Service
@Transactional
public class AngebotsanlageUseCase {

  private static final Logger LOG = LoggerFactory.getLogger(AngebotsanlageUseCase.class);

  private final AngebotRepository angebote;
  private final AngebotsanlageRepository anlagen;
  private final AnlageSpeicher speicher;
  private final Clock clock;

  AngebotsanlageUseCase(
      final AngebotRepository angebote,
      final AngebotsanlageRepository anlagen,
      final AnlageSpeicher speicher,
      final Clock clock) {
    this.angebote = angebote;
    this.anlagen = anlagen;
    this.speicher = speicher;
    this.clock = clock;
  }

  /**
   * Die Anlagen des Angebots, neueste zuerst (Kriterium 4).
   *
   * @param angebotId Kennung des Angebots
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   */
  @Transactional(readOnly = true)
  public List<Angebotsanlage> liste(final long angebotId) {
    pruefeAngebot(angebotId);
    return anlagen.findByAngebot(angebotId).stream()
        .sorted(Anlagenreihenfolge.NEUESTE_ZUERST)
        .toList();
  }

  /**
   * Nimmt eine Datei als Anlage des Angebots an (Kriterien 2, 5, 6) — in jedem Status.
   *
   * <p>Die ersten Bytes des Stroms werden fuer die Erkennung der {@link Vorschauart} gelesen und
   * ueber einen {@link PushbackInputStream} <b>zurueckgelegt</b>: Abgelegt wird der eingereichte
   * Inhalt vollstaendig, nicht der Rest hinter der Signatur.
   *
   * @param angebotId Kennung des Angebots
   * @param rohDateiName der Name, wie er mit der Anfrage kam; er wird gesaeubert
   * @param inhalt der Datenstrom der Bytes; dieser Anwendungsfall schliesst ihn
   * @param groesse Zahl der Byte, die abzulegen sind
   * @return die gespeicherte Anlage mit vergebener Kennung
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   * @throws AnlageOhneNamen wenn vom Dateinamen nach der Saeuberung nichts uebrig bleibt
   * @throws AnlageOhneInhalt wenn die Datei keine Byte traegt
   * @throws AnlageZuGross wenn sie die Grenze aus {@link Uploadgrenze} ueberschreitet
   */
  public Angebotsanlage ladeHoch(
      final long angebotId,
      final String rohDateiName,
      final InputStream inhalt,
      final long groesse) {
    pruefeAngebot(angebotId);
    final String dateiName = Dateiname.gesaeubert(rohDateiName).orElseThrow(AnlageOhneNamen::new);
    if (groesse <= 0) {
      throw new AnlageOhneInhalt();
    }
    if (groesse > Uploadgrenze.MAX_BYTE) {
      throw new AnlageZuGross();
    }
    /*
     * Der Port sagt zu, dass der Aufrufer den Datenstrom schliesst — und der Aufrufer ist diese
     * Klasse. Ein Fehler beim Lesen oder Schliessen wird nicht verschluckt: Er hinterliesse eine
     * halb gelesene Datei ohne jede Spur (CLAUDE-java.md §6.5).
     */
    try (PushbackInputStream strom = new PushbackInputStream(inhalt, Vorschauart.SIGNATUR_BYTES)) {
      final byte[] anfang = strom.readNBytes(Vorschauart.SIGNATUR_BYTES);
      strom.unread(anfang);
      final @Nullable Vorschauart art = Vorschauart.erkannt(anfang).orElse(null);
      final String objektSchluessel = speicher.ablegen(angebotId, strom, groesse);
      return anlagen.save(
          new Angebotsanlage(
              null, angebotId, dateiName, groesse, art, objektSchluessel, clock.instant()));
    } catch (final IOException fehler) {
      throw new UncheckedIOException("Der Datenstrom der Anlage liess sich nicht lesen.", fehler);
    }
  }

  /**
   * Die Anlage, wie sie hinausgeht — Name, Groesse, Vorschauart und der offene Datenstrom
   * (Kriterium 9).
   *
   * @param angebotId Kennung des Angebots
   * @param anlageId Kennung der Anlage
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   * @throws AngebotsanlageNichtGefunden wenn es die Anlage an diesem Angebot nicht gibt oder der
   *     Objektspeicher ihren Schluessel nicht kennt
   */
  @Transactional(readOnly = true)
  public Anlageninhalt liesInhalt(final long angebotId, final long anlageId) {
    final Angebotsanlage anlage = lies(angebotId, anlageId);
    final InputStream inhalt =
        speicher.lesen(anlage.objektSchluessel()).orElseThrow(AngebotsanlageNichtGefunden::new);
    return new Anlageninhalt(anlage.dateiName(), anlage.groesse(), anlage.vorschauArt(), inhalt);
  }

  /**
   * Loescht die Anlage — die Zeile ist danach fort, das Objekt nach dem Commit (Kriterium 10).
   *
   * @param angebotId Kennung des Angebots
   * @param anlageId Kennung der Anlage
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   * @throws AngebotsanlageNichtGefunden wenn es die Anlage an diesem Angebot nicht gibt
   */
  public void loesche(final long angebotId, final long anlageId) {
    final Angebotsanlage anlage = lies(angebotId, anlageId);
    anlagen.deleteById(anlage.requireId());
    NachDemCommit.fuehreAus(() -> entferneObjekt(anlage.objektSchluessel()));
  }

  private void pruefeAngebot(final long angebotId) {
    if (angebote.findById(angebotId).isEmpty()) {
      throw new AngebotNichtGefunden();
    }
  }

  private Angebotsanlage lies(final long angebotId, final long anlageId) {
    pruefeAngebot(angebotId);
    final Angebotsanlage vorhandene =
        anlagen.findById(anlageId).orElseThrow(AngebotsanlageNichtGefunden::new);
    if (vorhandene.angebotId() != angebotId) {
      throw new AngebotsanlageNichtGefunden();
    }
    return vorhandene;
  }

  /*
   * Ein Fehlschlag wird protokolliert und nicht gemeldet (E9): Die Zeile ist zu diesem Zeitpunkt
   * fort, und ein Objekt ohne Zeile ist unerreichbar. Im Log steht allein der Objektschluessel —
   * er traegt keinen Teil des Dateinamens und verraet damit nichts ueber den Inhalt.
   */
  private void entferneObjekt(final String objektSchluessel) {
    try {
      speicher.loeschen(objektSchluessel);
    } catch (final AnlageSpeicherAusfall ausfall) {
      LOG.warn(
          "Das Objekt {} einer geloeschten Anlage blieb im Speicher liegen.",
          objektSchluessel,
          ausfall);
    }
  }
}
