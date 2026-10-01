package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.rechnung.domain.Abrechnungsstand;
import org.mwolff.fbcrm.rechnung.domain.Belegempfaenger;
import org.mwolff.fbcrm.rechnung.domain.Positionsstand;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.RechnungseinstellungenRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungsposition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die einzelne Rechnung samt den Zeilen ihrer Maske (Plan #169, E5, E6).
 *
 * <p>Gelesen wird gegen das Angebot und nicht gegen die Rechnung: Die Maske zeigt jede Position des
 * Angebots, damit der Freiberufler beim Wiederoeffnen auch die sieht, die er beim ersten Mal
 * weggelassen hat. Der Abrechnungsstand nimmt diese Rechnung aus — ihre eigene Menge steht in der
 * Spalte „jetzt abrechnen" und nicht in „bereits abgerechnet" (#160, Kriterien 4 und 6).
 *
 * <p><b>Text und Einzelpreis je Zeile kommen aus der Rechnung, wo sie eine tragen</b>, und sonst
 * aus dem Angebot (Kriterium 9, Frage 15) — dieselbe Regel, nach der {@code RechnungAendernUseCase}
 * schreibt. Die Maske muss denselben Preis zeigen, mit dem gerechnet wird.
 *
 * <p>Die Firma und der geltende Steuersatz gehoeren zur Antwort, weil die Maske beide zeigt. Der
 * Satz wird immer gelesen und nicht nur beim Entwurf: Ein Zweig sparte eine Abfrage an der einen
 * festen Zeile der Einstellungen und brachte dafuer einen zweiten Weg durch denselben Code.
 *
 * <p><b>Der Name der Firma kommt bei einer gestellten Rechnung aus ihrer Kopie</b> und nicht von
 * der Firma von heute (#160, Kriterium 14): Zieht die Firma um oder benennt sie sich um, zeigt der
 * Beleg weiterhin, was der Kunde darauf gelesen hat. Die Kennung bleibt die der Firma — sie ist der
 * Griff, mit dem die Oberflaeche zu ihr springt, und eine Kopie hat keinen. Die Firma wird deshalb
 * auch bei einer gestellten Rechnung gelesen, aus demselben Grund wie der Steuersatz.
 */
@Service
@Transactional(readOnly = true)
public class RechnungLesenUseCase {

  private final RechnungRepository rechnungen;
  private final AngebotRepository angebote;
  private final FirmaRepository firmen;
  private final RechnungseinstellungenRepository einstellungen;

  RechnungLesenUseCase(
      final RechnungRepository rechnungen,
      final AngebotRepository angebote,
      final FirmaRepository firmen,
      final RechnungseinstellungenRepository einstellungen) {
    this.rechnungen = rechnungen;
    this.angebote = angebote;
    this.firmen = firmen;
    this.einstellungen = einstellungen;
  }

  /**
   * Die Rechnung zu einer Kennung mit dem Stand jeder Position ihres Angebots.
   *
   * @param rechnungId Kennung der Rechnung
   * @throws RechnungNichtGefunden wenn es die Rechnung nicht gibt
   * @throws AngebotNichtGefunden wenn es das Angebot der Rechnung nicht gibt
   * @throws FirmaNichtGefunden wenn es die Firma des Angebots nicht gibt — weder Angebote noch
   *     Firmen werden geloescht, beides waere ein Widerspruch im Bestand
   */
  public Rechnungsansicht lese(final long rechnungId) {
    final Rechnung rechnung =
        rechnungen.findById(rechnungId).orElseThrow(RechnungNichtGefunden::new);
    final Angebot angebot =
        angebote.findById(rechnung.angebotId()).orElseThrow(AngebotNichtGefunden::new);
    final Firma firma = firmen.findById(angebot.firmaId()).orElseThrow(FirmaNichtGefunden::new);
    final Abrechnungsstand stand =
        Abrechnungsstand.ohne(
            angebot.positionen(), rechnungen.findByAngebot(rechnung.angebotId()), rechnungId);
    final Map<Long, Rechnungsposition> eigene =
        rechnung.positionen().stream()
            .collect(Collectors.toMap(Rechnungsposition::angebotPositionId, Function.identity()));
    final List<Abrechnungszeile> zeilen =
        stand.positionen().stream().map(positionsstand -> zeile(positionsstand, eigene)).toList();
    final @Nullable Belegempfaenger kopie = rechnung.empfaenger();
    return new Rechnungsansicht(
        rechnung,
        angebot.firmaId(),
        kopie == null ? firma.name() : kopie.firma(),
        GeltenderSteuersatz.fuer(rechnung, einstellungen.lies().steuersatz()),
        zeilen);
  }

  private static Abrechnungszeile zeile(
      final Positionsstand positionsstand, final Map<Long, Rechnungsposition> eigene) {
    final Angebotsposition position = positionsstand.position();
    final @Nullable Rechnungsposition vorhanden = eigene.get(position.requireId());
    final BigDecimal menge = vorhanden == null ? BigDecimal.ZERO : vorhanden.menge();
    return new Abrechnungszeile(
        position,
        vorhanden == null ? position.bezeichnung() : vorhanden.bezeichnung(),
        vorhanden == null ? position.einzelpreis() : vorhanden.einzelpreis(),
        positionsstand.abgerechnet(),
        positionsstand.offen(),
        positionsstand.ueberschreitungMit(menge),
        menge);
  }
}
