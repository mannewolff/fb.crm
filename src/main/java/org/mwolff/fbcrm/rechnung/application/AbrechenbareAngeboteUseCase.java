package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.arbeitszeit.application.Arbeitszeitauskunft;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.rechnung.domain.Abrechnungsstand;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Angebote, aus denen eine Rechnung entstehen darf (#160, Kriterium 2).
 *
 * <p>Zwei Bedingungen: der Status aus {@link Abrechenbarkeit} und mindestens eine Position, an der
 * noch etwas abrechenbar ist. Entwuerfe zaehlen dabei als abgerechnet (Kriterium 6) — ein Angebot,
 * dessen Rest schon in einem Entwurf steht, gehoert nicht noch einmal zur Wahl. Am stornierten
 * Angebot ist das nicht die offene Menge, sondern die nicht abgerechneten Stunden (#330, Kriterium
 * 7; Plan #332, E8), und der gezeigte Betrag ist ihr Wert.
 *
 * <p>Gefragt wird in vier Zuegen und nicht je Zeile: alle Angebote, alle Rechnungen, die
 * angefallenen Stunden aller Positionen, danach die Namen der beteiligten Firmen auf einmal. Je
 * Angebot nach seinen Rechnungen oder Stunden zu fragen waere die bekannte Abfrage-Lawine. Ohne
 * Angebot im passenden Status wird gar nicht weitergefragt.
 *
 * <p>Ohne zugesagte Reihenfolge: Die Wahl zeigt, was zur Wahl steht; welche Ordnung sie waehlt,
 * sagt #160 nicht, und eine erfundene Ordnung waere eine Aussage, die niemand verlangt hat.
 */
@Service
@Transactional(readOnly = true)
public class AbrechenbareAngeboteUseCase {

  private final AngebotRepository angebote;
  private final RechnungRepository rechnungen;
  private final FirmaRepository firmen;
  private final Arbeitszeitauskunft arbeitszeit;

  AbrechenbareAngeboteUseCase(
      final AngebotRepository angebote,
      final RechnungRepository rechnungen,
      final FirmaRepository firmen,
      final Arbeitszeitauskunft arbeitszeit) {
    this.angebote = angebote;
    this.rechnungen = rechnungen;
    this.firmen = firmen;
    this.arbeitszeit = arbeitszeit;
  }

  /**
   * Die abrechenbaren Angebote, jedes mit dem Namen seiner Firma und seinem abrechenbaren Betrag.
   *
   * @throws FirmaNichtGefunden wenn es die Firma eines Angebots nicht gibt — Firmen werden nie
   *     geloescht, das waere ein Widerspruch im Bestand
   */
  public List<AbrechenbaresAngebot> abrechenbare() {
    final List<Angebot> kandidaten =
        angebote.findAlle(Optional.empty()).stream()
            .filter(angebot -> Abrechenbarkeit.STATUS.contains(angebot.status()))
            .toList();
    if (kandidaten.isEmpty()) {
      return List.of();
    }
    final Map<Long, List<Rechnung>> jeAngebot =
        rechnungen.findAlle().stream().collect(Collectors.groupingBy(Rechnung::angebotId));
    final Map<Long, BigDecimal> angefallen = arbeitszeit.alleAngefallen();
    final List<Offenes> offene = new ArrayList<>();
    for (final Angebot angebot : kandidaten) {
      final Abrechnungsstand stand =
          Abrechnungsstand.fuer(
              angebot.positionen(), jeAngebot.getOrDefault(angebot.requireId(), List.of()));
      if (stand.etwasAbrechenbar(angebot, angefallen)) {
        offene.add(new Offenes(angebot, stand.abrechenbarerBetrag(angebot, angefallen)));
      }
    }
    if (offene.isEmpty()) {
      return List.of();
    }
    final Set<Long> firmaIds =
        offene.stream().map(eintrag -> eintrag.angebot().firmaId()).collect(Collectors.toSet());
    final Map<Long, String> namen =
        firmen.findAllById(firmaIds).stream()
            .collect(Collectors.toMap(Firma::requireId, Firma::name));
    return offene.stream()
        .map(
            eintrag ->
                new AbrechenbaresAngebot(
                    eintrag.angebot(), nameVon(namen, eintrag.angebot()), eintrag.betrag()))
        .toList();
  }

  private static String nameVon(final Map<Long, String> namen, final Angebot angebot) {
    final String name = namen.get(angebot.firmaId());
    if (name == null) {
      throw new FirmaNichtGefunden();
    }
    return name;
  }

  /** Ein Angebot mit abrechenbarem Rest, bevor der Name seiner Firma dazukommt. */
  private record Offenes(Angebot angebot, BigDecimal betrag) {}
}
