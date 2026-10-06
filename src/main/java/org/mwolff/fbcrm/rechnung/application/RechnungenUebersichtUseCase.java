package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnung;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.RechnungseinstellungenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Liste aller Rechnungen, neueste zuerst (#160, Kriterium 1) — die von fb.crm geschriebenen und
 * die nachgetragenen in einer Liste (#254, Kriterium 6).
 *
 * <p>Die Reihenfolge ist die Gesamtordnung der gemischten Liste ({@link
 * Rechnungsreihenfolge#GEMISCHT_NEUESTE_ZUERST}, Plan #259, E19). Die Firma steht bei der
 * geschriebenen Rechnung nicht an ihr, sondern an ihrem Angebot, bei der nachgetragenen an ihr
 * selbst — die Angebote kommen darum in <b>einem</b> Aufruf, die Namen fuer die Firmen beider Arten
 * in <b>einem</b> weiteren; je Zeile nachzufragen waere die bekannte Abfrage-Lawine. Ohne Rechnung
 * wird gar nicht gefragt.
 *
 * <p><b>Der Steuersatz kommt aus zwei Quellen</b> ({@link GeltenderSteuersatz}): Ein Entwurf hat
 * noch keinen und rechnet mit dem der aktuellen Einstellungen; eine gestellte Rechnung traegt ihren
 * eigenen und behaelt ihn, auch wenn die Einstellungen sich danach aendern (Kriterium 14).
 */
@Service
@Transactional(readOnly = true)
public class RechnungenUebersichtUseCase {

  private final RechnungRepository bestand;
  private final NachgetrageneRechnungRepository nachtraege;
  private final AngebotRepository angebote;
  private final FirmaRepository firmen;
  private final RechnungseinstellungenRepository einstellungen;

  RechnungenUebersichtUseCase(
      final RechnungRepository rechnungen,
      final NachgetrageneRechnungRepository nachtraege,
      final AngebotRepository angebote,
      final FirmaRepository firmen,
      final RechnungseinstellungenRepository einstellungen) {
    this.bestand = rechnungen;
    this.nachtraege = nachtraege;
    this.angebote = angebote;
    this.firmen = firmen;
    this.einstellungen = einstellungen;
  }

  /**
   * Die Rechnungen beider Arten, neueste zuerst, jede mit dem Namen ihrer Firma und ihrem
   * Bruttobetrag.
   *
   * @throws AngebotNichtGefunden wenn es das Angebot einer Rechnung nicht gibt
   * @throws FirmaNichtGefunden wenn es die Firma eines Angebots oder einer nachgetragenen Rechnung
   *     nicht gibt — beides waere ein Widerspruch im Bestand, denn weder Angebote noch Firmen
   *     werden geloescht
   */
  public List<Rechnungslistenzeile> rechnungen() {
    final List<Rechnung> alle = bestand.findAlle();
    final List<NachgetrageneRechnung> nachgetragene = nachtraege.findAlle();
    if (alle.isEmpty() && nachgetragene.isEmpty()) {
      return List.of();
    }
    final Map<Long, Angebot> jeAngebot =
        angebote.findAlle(Optional.empty()).stream()
            .collect(Collectors.toMap(Angebot::requireId, Function.identity()));
    final Set<Long> firmaIds =
        Stream.concat(
                alle.stream().map(rechnung -> angebotVon(jeAngebot, rechnung).firmaId()),
                nachgetragene.stream().map(NachgetrageneRechnung::firmaId))
            .collect(Collectors.toSet());
    final Map<Long, String> namen =
        firmen.findAllById(firmaIds).stream()
            .collect(Collectors.toMap(Firma::requireId, Firma::name));
    final BigDecimal aktuellerSatz = einstellungen.lies().steuersatz();
    return Stream.concat(
            alle.stream()
                .map(
                    rechnung ->
                        zeile(rechnung, angebotVon(jeAngebot, rechnung), namen, aktuellerSatz)),
            nachgetragene.stream()
                .map(
                    rechnung ->
                        Rechnungslistenzeile.vonNachtrag(
                            rechnung, nameVon(namen, rechnung.firmaId()))))
        .sorted(Rechnungsreihenfolge.GEMISCHT_NEUESTE_ZUERST)
        .toList();
  }

  private static Rechnungslistenzeile zeile(
      final Rechnung rechnung,
      final Angebot angebot,
      final Map<Long, String> namen,
      final BigDecimal aktuellerSatz) {
    return Rechnungslistenzeile.vonRechnung(
        rechnung,
        angebot.firmaId(),
        nameVon(namen, angebot.firmaId()),
        rechnung.brutto(GeltenderSteuersatz.fuer(rechnung, aktuellerSatz)));
  }

  private static Angebot angebotVon(final Map<Long, Angebot> jeAngebot, final Rechnung rechnung) {
    final Angebot angebot = jeAngebot.get(rechnung.angebotId());
    if (angebot == null) {
      throw new AngebotNichtGefunden();
    }
    return angebot;
  }

  private static String nameVon(final Map<Long, String> namen, final long firmaId) {
    final String name = namen.get(firmaId);
    if (name == null) {
      throw new FirmaNichtGefunden();
    }
    return name;
  }
}
