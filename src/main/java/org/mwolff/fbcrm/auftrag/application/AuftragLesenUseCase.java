package org.mwolff.fbcrm.auftrag.application;

import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
import org.mwolff.fbcrm.auftrag.domain.Auftrag;
import org.mwolff.fbcrm.auftrag.domain.AuftragRepository;
import org.mwolff.fbcrm.vorgang.application.VorgangNichtGefunden;
import org.mwolff.fbcrm.vorgang.domain.VorgangRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die beiden Lesewege des Auftrags: der Auftrag selbst und die Auskunft am Angebot.
 *
 * <p>Der erste traegt die Nummer des Quell-Angebots, weil Kriterium 3 verlangt, am Auftrag zu
 * sehen, aus welchem Angebot er entstand — eine Kennung zeigt kein Wort.
 *
 * <p><b>Der zweite liegt hier und nicht im Modul {@code angebot}</b> (Plan E3): Die Angebotsansicht
 * braucht zwei Auskuenfte — ob zu diesem Angebot schon ein Auftrag besteht und ob das Anlegen heute
 * zulaessig ist —, und beide Wege liegen darum im juengeren Modul, dieselbe Richtung, die {@code
 * angebot.web.VorgangAngeboteController} gegenueber {@code vorgang} schon nimmt. Ein Rueckverweis
 * in {@code AngebotResponse} waere die Rueckkante, die {@code
 * ArchitectureTest.modules_thenFreeOfCycles} abweist.
 *
 * <p><b>{@code anlegbar} nennt nicht, ob schon ein Auftrag haengt.</b> Das steht daneben in
 * derselben Antwort; zweimal dasselbe auszudruecken hiesse, beide Werte in Einklang halten zu
 * muessen. Der Abschlussstand des Vorgangs wird nur gelesen, wenn das Angebot ueberhaupt angenommen
 * ist — sonst steht die Antwort schon fest.
 */
@Service
@Transactional(readOnly = true)
public class AuftragLesenUseCase {

  private final AuftragRepository auftraege;
  private final AngebotRepository angebote;
  private final VorgangRepository vorgaenge;

  public AuftragLesenUseCase(
      final AuftragRepository auftraege,
      final AngebotRepository angebote,
      final VorgangRepository vorgaenge) {
    this.auftraege = auftraege;
    this.angebote = angebote;
    this.vorgaenge = vorgaenge;
  }

  /**
   * Der Auftrag zu einer Kennung, samt der Nummer seines Quell-Angebots.
   *
   * @param auftragId Kennung des Auftrags
   * @throws AuftragNichtGefunden wenn es den Auftrag nicht gibt
   */
  public AuftragAnsicht lese(final long auftragId) {
    final Auftrag auftrag = auftraege.findById(auftragId).orElseThrow(AuftragNichtGefunden::new);
    return new AuftragAnsicht(auftrag, quelle(auftrag.angebotId()).nummer());
  }

  /**
   * Der Auftrag zu einem Angebot und die Auskunft, ob sich heute einer anlegen laesst.
   *
   * @param angebotId Kennung des Angebots
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   */
  public AuftragAmAngebot zuAngebot(final long angebotId) {
    final Angebot angebot = quelle(angebotId);
    final AuftragAnsicht ansicht =
        auftraege
            .findByAngebot(angebotId)
            .map(auftrag -> new AuftragAnsicht(auftrag, angebot.nummer()))
            .orElse(null);
    return new AuftragAmAngebot(ansicht, anlegbar(angebot));
  }

  /*
   * Die Quelle eines Auftrags. Dass es sie gibt, haelt der Fremdschluessel zu — die Ausnahme steht
   * hier fuer den Weg, der mit einer Kennung aus dem Pfad kommt.
   */
  private Angebot quelle(final long angebotId) {
    return angebote.findById(angebotId).orElseThrow(AngebotNichtGefunden::new);
  }

  /* Kriterien 1 und 11: angenommenes Angebot an einem offenen Vorgang. */
  private boolean anlegbar(final Angebot angebot) {
    return angebot.zustand() == Angebotszustand.ANGENOMMEN
        && !vorgaenge
            .findById(angebot.vorgangId())
            .orElseThrow(VorgangNichtGefunden::new)
            .abgeschlossen();
  }
}
