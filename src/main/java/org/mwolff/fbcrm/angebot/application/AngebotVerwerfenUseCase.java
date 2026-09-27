package org.mwolff.fbcrm.angebot.application;

import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Verwerfen eines Entwurfs (Kriterien 7, 13).
 *
 * <p>Der Entwurf verschwindet vollstaendig — samt seinen Positionen und ohne Spur in der Historie;
 * weil er nie eine Nummer getragen hat, reisst er keine Luecke (E19). Es ist der einzige Weg, auf
 * dem im Modul etwas geloescht wird, und er gilt allein dem Entwurf: Jeder festgeschriebene Beleg
 * bleibt stehen.
 *
 * <p>Die Zustandsprobe steht hier und nicht in {@code Angebot}, anders als bei den Uebergaengen:
 * Verwerfen erzeugt kein neues Angebot, sondern beendet das vorhandene — es gibt nichts, was die
 * Domaene zurueckgeben koennte. Geworfen wird trotzdem dieselbe Ausnahme, damit der Anwender auf
 * beiden Wegen dieselbe Auskunft bekommt.
 *
 * <p>Ohne Uhr: Geloeschtes traegt keinen Zeitstempel.
 */
@Service
@Transactional
public class AngebotVerwerfenUseCase {

  private final AngebotRepository angebote;

  public AngebotVerwerfenUseCase(final AngebotRepository angebote) {
    this.angebote = angebote;
  }

  /**
   * Loescht den Entwurf samt seinen Positionen.
   *
   * @param angebotId Kennung des Angebots
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   * @throws AngebotNichtAenderbar wenn das Angebot kein Entwurf mehr ist
   */
  public void verwirf(final long angebotId) {
    final Angebot angebot = angebote.findById(angebotId).orElseThrow(AngebotNichtGefunden::new);
    if (angebot.zustand() != Angebotszustand.ENTWURF) {
      throw new AngebotNichtAenderbar();
    }
    angebote.loesche(angebotId);
  }
}
