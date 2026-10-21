package org.mwolff.fbcrm.rechnung.application;

import java.util.ArrayList;
import java.util.List;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngabenRepository;
import org.mwolff.fbcrm.rechnung.domain.Briefbogen;
import org.mwolff.fbcrm.rechnung.domain.BriefbogenRepository;
import org.mwolff.fbcrm.rechnung.domain.Briefpapierblatt;
import org.mwolff.fbcrm.rechnung.domain.Briefpapierspeicher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Der Probedruck auf dem hinterlegten Briefpapier (fachliche Quelle #361, Kriterium 9; Plan #363,
 * E13 und E14).
 *
 * <p>Gesetzt werden die erfundenen {@link Probedruckdaten} mit den echten eigenen Angaben, wie sie
 * gerade stehen, und mit den Schaltern und Raendern des {@link Briefbogen}s — derselbe Satz wie
 * spaeter auf dem Beleg, ueber {@link Briefbogenlayout}. Unter den Satz legt der Drucker das
 * Briefpapier, Byte fuer Byte aus dem Speicher.
 *
 * <p><b>Lesend und ohne Spuren</b>: Der Anwendungsfall legt keinen Beleg an und zieht keine Nummer.
 * Er kennt weder den Bestand der Belege noch den Zaehler ihrer Nummern, und ein Entwurf als Umweg
 * ist ausdruecklich verworfen (E14).
 *
 * <p><b>Jede Seite traegt ein hellgraues, schraeges {@value #MUSTER}</b> (E13), damit niemand einen
 * Probedruck versehentlich verschickt. Es laeuft von unten links nach oben rechts und steht mit der
 * Mitte seiner Grundlinie ungefaehr in der Mitte des Blattes.
 *
 * <p>Ohne hinterlegtes Briefpapier gibt es keinen Probedruck (Kriterium 9, zweiter Satz): {@link
 * BriefpapierNichtGefunden}, an der Schnittstelle 404.
 */
@Service
@Transactional(readOnly = true)
public class ProbedruckUseCase {

  /** Das Wort quer ueber jeder Seite. */
  static final String MUSTER = "Muster";

  /** Hell genug, um nicht zu stoeren, dunkel genug, um auf weissem Papier aufzufallen. */
  static final Farbe HELLGRAU = new Farbe(210, 210, 210);

  /** Die Drehung gegen die Waagerechte: ungefaehr die Diagonale von A4. */
  private static final int WINKEL = 55;

  /** Schriftgroesse des Musters in Punkten. */
  private static final int GROESSE = 120;

  /**
   * Der Ankerpunkt: Bei 120 Punkt Helvetica fett ist das Wort rund 393 Punkte breit; um 55 Grad
   * gedreht, steht die Mitte seiner Grundlinie damit nahe der Blattmitte (297, 421).
   */
  private static final int ANKER_X = 184;

  /** Die Hoehe des Ankerpunkts, siehe {@link #ANKER_X}. */
  private static final int ANKER_Y = 260;

  private final BriefbogenRepository bestand;
  private final Briefpapierspeicher speicher;
  private final EigeneAngabenRepository eigeneAngaben;
  private final Belegdrucker drucker;

  public ProbedruckUseCase(
      final BriefbogenRepository bestand,
      final Briefpapierspeicher speicher,
      final EigeneAngabenRepository eigeneAngaben,
      final Belegdrucker drucker) {
    this.bestand = bestand;
    this.speicher = speicher;
    this.eigeneAngaben = eigeneAngaben;
    this.drucker = drucker;
  }

  /**
   * Druckt die Beispieldaten auf das hinterlegte Briefpapier.
   *
   * @return das PDF des Probedrucks
   * @throws BriefpapierNichtGefunden wenn kein Briefpapier hinterlegt ist
   * @throws org.mwolff.fbcrm.rechnung.domain.DokumentSpeicherAusfall wenn der Speicher das
   *     Briefpapier nicht liefert
   */
  public byte[] drucke() {
    final Briefbogen bogen = bestand.lies();
    final Briefpapierblatt blatt = bogen.blatt();
    if (blatt == null) {
      throw new BriefpapierNichtGefunden();
    }
    final byte[] briefpapier = speicher.lies(blatt.schluessel());
    final List<Druckelement> elemente =
        new ArrayList<>(Briefbogenlayout.setze(Probedruckdaten.von(eigeneAngaben.lies()), bogen));
    final int seiten = elemente.stream().mapToInt(Druckelement::seite).max().orElseThrow();
    for (int seite = 1; seite <= seiten; seite++) {
      elemente.add(
          new Druckelement.Schraegtext(
              seite, ANKER_X, ANKER_Y, WINKEL, Schrift.FETT, GROESSE, HELLGRAU, MUSTER));
    }
    return drucker.drucke(elemente, briefpapier);
  }
}
