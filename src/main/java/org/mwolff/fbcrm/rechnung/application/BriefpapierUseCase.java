package org.mwolff.fbcrm.rechnung.application;

import java.time.Clock;
import java.util.Optional;
import org.mwolff.fbcrm.angebot.domain.Dateiname;
import org.mwolff.fbcrm.angebot.domain.Vorschauart;
import org.mwolff.fbcrm.common.NachDemCommit;
import org.mwolff.fbcrm.rechnung.domain.Briefbogen;
import org.mwolff.fbcrm.rechnung.domain.BriefbogenRepository;
import org.mwolff.fbcrm.rechnung.domain.Briefpapierblatt;
import org.mwolff.fbcrm.rechnung.domain.Briefpapierspeicher;
import org.mwolff.fbcrm.rechnung.domain.DokumentSpeicherAusfall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Briefpapier: hochladen und ersetzen, lesen fuer den Druck, entfernen (fachliche Quelle #361,
 * Kriterien 1, 2 und 16; Plan #363, E4, E5, E6, E15 und E17).
 *
 * <p><b>Drei Riegel vor dem Pruefer</b>, in dieser Reihenfolge, wie bei {@link
 * NachtragDokumentUseCase}: leer, groesser als {@link Briefpapiergrenze}, kein PDF. Ob es ein PDF
 * ist, wird <b>am Inhalt</b> erkannt, allein ueber {@link Vorschauart#erkannt} — nicht am
 * Dateinamen und nicht an der vom Browser gemeldeten Art. Danach entscheidet der {@link
 * Briefpapierpruefer} am geladenen Dokument und reinigt es (E4); <b>abgelegt wird allein, was er
 * zurueckgibt</b>. Abgewiesen wird vor jeder Ablage, und ein bisher hinterlegtes Briefpapier bleibt
 * unberuehrt (Kriterium 2).
 *
 * <p><b>Das alte Objekt geht erst nach dem Commit</b> ({@link NachDemCommit}, E6), beim Ersetzen
 * wie beim Entfernen: Die Zeile zeigt erst auf das neue oder auf kein Briefpapier, dann
 * verschwindet das alte. Vor dem Commit geloescht, liesse ein gescheiterter Commit eine Zeile
 * zurueck, die auf ein fehlendes Objekt zeigt.
 *
 * <p><b>Schalter und Raender bleiben</b>, wie der Betreiber sie eingestellt hat (E15, Kriterium
 * 16): Ersetzen tauscht allein das {@link Briefpapierblatt}, Entfernen setzt es auf {@code null}.
 *
 * <p>Der Dateiname ist eine Eingabe von aussen und wird gesaeubert wie bei den Anlagen am Angebot
 * ({@link Dateiname}). Bleibt davon nichts, traegt die Zeile den {@link #ERSATZNAME}: Ein fehlender
 * Name ist kein Grund, ein sonst gutes Briefpapier abzuweisen. Die Groesse ist die des abgelegten,
 * gereinigten Objekts.
 */
@Service
@Transactional
public class BriefpapierUseCase {

  /** Der Name, den die Zeile traegt, wenn vom hochgeladenen Namen nichts bleibt. */
  static final String ERSATZNAME = "briefpapier.pdf";

  private static final Logger LOG = LoggerFactory.getLogger(BriefpapierUseCase.class);

  private final BriefbogenRepository bestand;
  private final Briefpapierspeicher speicher;
  private final Briefpapierpruefer pruefer;
  private final Clock clock;

  BriefpapierUseCase(
      final BriefbogenRepository bestand,
      final Briefpapierspeicher speicher,
      final Briefpapierpruefer pruefer,
      final Clock clock) {
    this.bestand = bestand;
    this.speicher = speicher;
    this.pruefer = pruefer;
    this.clock = clock;
  }

  /**
   * Hinterlegt das Briefpapier; ein vorhandenes wird ersetzt und nach dem Commit entfernt.
   *
   * @param rohDateiname der Name, wie er mit der Anfrage kam
   * @param inhalt die hochgeladenen Bytes
   * @return der neue Stand
   * @throws BriefpapierNichtAnnehmbar wenn die Datei leer, zu gross oder kein PDF ist, sich nicht
   *     laden laesst oder verschluesselt ist, mehr als zwei Seiten hat oder nicht A4 hochkant ist
   * @throws DokumentSpeicherAusfall wenn der Speicher die Ablage nicht annimmt — dann ist nichts
   *     geschrieben
   */
  public Briefbogenstand ladeHoch(final String rohDateiname, final byte[] inhalt) {
    pruefe(inhalt);
    final Briefpapierpruefer.Gereinigt gereinigt = pruefer.pruefe(inhalt);
    final byte[] abzulegen = gereinigt.inhalt();
    final String schluessel = speicher.lege(abzulegen);
    final Briefbogen bisher = bestand.lies();
    final Briefpapierblatt blatt =
        new Briefpapierblatt(
            schluessel,
            Dateiname.gesaeubert(rohDateiname).orElse(ERSATZNAME),
            abzulegen.length,
            gereinigt.seiten());
    final Briefbogen neu =
        new Briefbogen(blatt, bisher.absenderzeile(), bisher.steuerBankZeile(), bisher.raender());
    bestand.speichere(neu, clock.instant());
    final Briefpapierblatt alt = bisher.blatt();
    if (alt != null) {
      NachDemCommit.fuehreAus(() -> entferneObjekt(alt.schluessel()));
    }
    return Briefbogenstand.von(neu);
  }

  /**
   * Das hinterlegte Briefpapier, Byte fuer Byte, fuer den Druck.
   *
   * @return die gereinigten Bytes, oder leer ohne Briefpapier — dann bleibt die Rechnung, wie sie
   *     heute ist (Kriterium 11)
   * @throws DokumentSpeicherAusfall wenn der Speicher das Objekt nicht liefert
   */
  @Transactional(readOnly = true)
  public Optional<byte[]> lies() {
    return Optional.ofNullable(bestand.lies().blatt())
        .map(blatt -> speicher.lies(blatt.schluessel()));
  }

  /**
   * Entfernt das Briefpapier; Schalter und Raender bleiben, das Objekt geht nach dem Commit.
   *
   * @return der Stand ohne Briefpapier
   * @throws BriefpapierNichtGefunden wenn keines hinterlegt ist
   */
  public Briefbogenstand entferne() {
    final Briefbogen bisher = bestand.lies();
    final Briefpapierblatt alt = bisher.blatt();
    if (alt == null) {
      throw new BriefpapierNichtGefunden();
    }
    final Briefbogen neu =
        new Briefbogen(null, bisher.absenderzeile(), bisher.steuerBankZeile(), bisher.raender());
    bestand.speichere(neu, clock.instant());
    NachDemCommit.fuehreAus(() -> entferneObjekt(alt.schluessel()));
    return Briefbogenstand.von(neu);
  }

  /*
   * Die Groesse steht vor dem Inhalt: Eine zu grosse Datei ist abzuweisen, gleich was sie ist.
   */
  private static void pruefe(final byte[] inhalt) {
    if (inhalt.length == 0) {
      throw BriefpapierNichtAnnehmbar.leer();
    }
    if (inhalt.length > Briefpapiergrenze.MAX_BYTE) {
      throw BriefpapierNichtAnnehmbar.zuGross();
    }
    if (Vorschauart.erkannt(inhalt).filter(Vorschauart.PDF::equals).isEmpty()) {
      throw BriefpapierNichtAnnehmbar.keinPdf();
    }
  }

  /*
   * Ein Fehlschlag wird protokolliert und nicht gemeldet (E6): Die Zeile zeigt zu diesem Zeitpunkt
   * auf ein anderes oder kein Briefpapier, und ein Objekt ohne Zeile ist unerreichbar.
   */
  private void entferneObjekt(final String schluessel) {
    try {
      speicher.loesche(schluessel);
    } catch (final DokumentSpeicherAusfall ausfall) {
      LOG.warn(
          "Das ersetzte oder entfernte Briefpapier {} blieb im Speicher liegen.",
          schluessel,
          ausfall);
    }
  }
}
