package org.mwolff.fbcrm.auftrag.application;

import org.mwolff.fbcrm.auftrag.domain.Auftrag;
import org.mwolff.fbcrm.auftrag.domain.AuftragRepository;
import org.mwolff.fbcrm.vorgang.application.EreignisVermerkenUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Loeschen eines Auftrags (Kriterium 15, Plan E15).
 *
 * <p><b>Geprueft wird nichts ausser der Existenz.</b> Fuer die Bedingung „solange weder Zeiten noch
 * Rechnungen daran haengen" entsteht hier kein Code: Kriterium 15 sagt ausdruecklich, dass sie bis
 * zu den Ideen #6 (Zeiterfassung) und #7 (Rechnung) immer erfuellt ist, und ein Riegel vor einer
 * Tuer, hinter der nichts ist, wird beim Bauen von Idee #6 leichter uebersehen als eine fehlende
 * Pruefung.
 *
 * <p><b>Alles in einer Transaktion:</b> Positionen, Zeile und Ereignis. Der Bestand loescht erst
 * die Positionen, dann die Zeile, die sie traegt; das Ereignis gehoert dazu, weil eine Historie
 * ohne die Loeschung nicht erklaert, warum der Auftrag fehlt (R2).
 *
 * <p>Drei Folgen entstehen ohne eigenes Zutun und sind darum hier auch nicht angefasst: Die Nummer
 * bleibt verbraucht (Plan E6), das Angebot bleibt {@code ANGENOMMEN} und bekommt {@code anlegbar}
 * zurueck (folgt aus Plan E3), und die Phase des Vorgangs faellt auf „Angebot" zurueck (folgt aus
 * Plan E2). Belegt sind sie in {@code AuftragLoeschenIT}.
 *
 * <p>Wie beim Pflegen gibt es keine Sperre am abgeschlossenen Vorgang (Plan E14) — dieser
 * Anwendungsfall kennt den Vorgang nur als Adresse seiner Historie.
 */
@Service
@Transactional
public class AuftragLoeschenUseCase {

  private final AuftragRepository auftraege;
  private final EreignisVermerkenUseCase ereignisse;

  public AuftragLoeschenUseCase(
      final AuftragRepository auftraege, final EreignisVermerkenUseCase ereignisse) {
    this.auftraege = auftraege;
    this.ereignisse = ereignisse;
  }

  /**
   * Loescht den Auftrag samt seinen Positionen und vermerkt es in der Historie.
   *
   * @param auftragId Kennung des Auftrags
   * @throws AuftragNichtGefunden wenn es den Auftrag nicht gibt
   */
  public void loesche(final long auftragId) {
    final Auftrag auftrag = auftraege.findById(auftragId).orElseThrow(AuftragNichtGefunden::new);
    auftraege.loesche(auftragId);
    ereignisse.vermerken(auftrag.vorgangId(), Auftragsereignis.geloescht(auftrag.nummer()));
  }
}
