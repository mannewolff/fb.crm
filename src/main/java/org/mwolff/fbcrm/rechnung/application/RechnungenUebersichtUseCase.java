package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.RechnungseinstellungenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Liste aller Rechnungen, neueste zuerst (#160, Kriterium 1).
 *
 * <p>Die Reihenfolge ist die jeder Rechnungsliste ({@link Rechnungsreihenfolge}). Die Firma steht
 * nicht an der Rechnung, sondern an ihrem Angebot — beide Zuordnungen kommen darum in <b>je
 * einem</b> Aufruf fuer alle Zeilen; je Zeile nachzufragen waere die bekannte Abfrage-Lawine. Ohne
 * Rechnung wird gar nicht gefragt.
 *
 * <p><b>Der Steuersatz kommt aus zwei Quellen</b> ({@link GeltenderSteuersatz}): Ein Entwurf hat
 * noch keinen und rechnet mit dem der aktuellen Einstellungen; eine gestellte Rechnung traegt ihren
 * eigenen und behaelt ihn, auch wenn die Einstellungen sich danach aendern (Kriterium 14).
 */
@Service
@Transactional(readOnly = true)
public class RechnungenUebersichtUseCase {

  private final RechnungRepository bestand;
  private final AngebotRepository angebote;
  private final FirmaRepository firmen;
  private final RechnungseinstellungenRepository einstellungen;

  RechnungenUebersichtUseCase(
      final RechnungRepository rechnungen,
      final AngebotRepository angebote,
      final FirmaRepository firmen,
      final RechnungseinstellungenRepository einstellungen) {
    this.bestand = rechnungen;
    this.angebote = angebote;
    this.firmen = firmen;
    this.einstellungen = einstellungen;
  }

  /**
   * Die Rechnungen, neueste zuerst, jede mit dem Namen ihrer Firma und ihrem Bruttobetrag.
   *
   * @throws AngebotNichtGefunden wenn es das Angebot einer Rechnung nicht gibt
   * @throws FirmaNichtGefunden wenn es die Firma eines Angebots nicht gibt — beides waere ein
   *     Widerspruch im Bestand, denn weder Angebote noch Firmen werden geloescht
   */
  public List<RechnungMitFirma> rechnungen() {
    final List<Rechnung> alle = bestand.findAlle();
    if (alle.isEmpty()) {
      return List.of();
    }
    final Map<Long, Angebot> jeAngebot =
        angebote.findAlle(Optional.empty()).stream()
            .collect(Collectors.toMap(Angebot::requireId, Function.identity()));
    final Set<Long> firmaIds =
        alle.stream()
            .map(rechnung -> angebotVon(jeAngebot, rechnung).firmaId())
            .collect(Collectors.toSet());
    final Map<Long, String> namen =
        firmen.findAllById(firmaIds).stream()
            .collect(Collectors.toMap(Firma::requireId, Firma::name));
    final BigDecimal aktuellerSatz = einstellungen.lies().steuersatz();
    return alle.stream()
        .sorted(Rechnungsreihenfolge.NEUESTE_ZUERST)
        .map(rechnung -> zeile(rechnung, angebotVon(jeAngebot, rechnung), namen, aktuellerSatz))
        .toList();
  }

  private static RechnungMitFirma zeile(
      final Rechnung rechnung,
      final Angebot angebot,
      final Map<Long, String> namen,
      final BigDecimal aktuellerSatz) {
    return new RechnungMitFirma(
        rechnung,
        angebot.firmaId(),
        nameVon(namen, angebot),
        rechnung.brutto(GeltenderSteuersatz.fuer(rechnung, aktuellerSatz)));
  }

  private static Angebot angebotVon(final Map<Long, Angebot> jeAngebot, final Rechnung rechnung) {
    final Angebot angebot = jeAngebot.get(rechnung.angebotId());
    if (angebot == null) {
      throw new AngebotNichtGefunden();
    }
    return angebot;
  }

  private static String nameVon(final Map<Long, String> namen, final Angebot angebot) {
    final String name = namen.get(angebot.firmaId());
    if (name == null) {
      throw new FirmaNichtGefunden();
    }
    return name;
  }
}
