package org.mwolff.fbcrm.angebot.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Uebersicht aller Angebote, wahlweise nach Status gefiltert (Issue #127, Kriterium 8) oder auf
 * die stornierten beschraenkt (Issue #335, Plan #332 E6 und E7).
 *
 * <p>Die Reihenfolge ist die jeder Angebotsliste ({@link Angebotsreihenfolge}). Die Namen der
 * Firmen kommen in <b>einem</b> Aufruf fuer alle beteiligten Firmen; je Zeile nachzufragen waere
 * die bekannte Abfrage-Lawine. Ohne Angebot wird gar nicht gefragt.
 *
 * <p><b>Der Status geht in den Bestand, das Storno filtert die Anwendungsschicht</b> ({@link
 * Angebotsauswahl#passt}): Die Regel, welches Angebot zur Auswahl gehoert, steht an einer Stelle,
 * und {@code findAlle} kennt nur einen Status je Aufruf — wie beim {@code
 * BuchbarePositionenUseCase}.
 */
@Service
@Transactional(readOnly = true)
public class AngeboteUebersichtUseCase {

  private final AngebotRepository bestand;
  private final FirmaRepository firmen;

  AngeboteUebersichtUseCase(final AngebotRepository angebote, final FirmaRepository firmen) {
    this.bestand = angebote;
    this.firmen = firmen;
  }

  /**
   * Die Angebote, neueste zuerst, jedes mit dem Namen seiner Firma.
   *
   * @param auswahl welche Angebote: {@link Angebotsauswahl#alle()} fuer alle, {@link
   *     Angebotsauswahl#imStatus} fuer die nicht stornierten in einem Status, {@link
   *     Angebotsauswahl#stornierte()} fuer jedes stornierte
   * @throws FirmaNichtGefunden wenn es die Firma eines Angebots nicht gibt — Firmen werden nie
   *     geloescht, das waere ein Widerspruch im Bestand
   */
  public List<AngebotMitFirma> angebote(final Angebotsauswahl auswahl) {
    final List<Angebot> gefunden =
        bestand.findAlle(Optional.ofNullable(auswahl.status())).stream()
            .filter(auswahl::passt)
            .toList();
    if (gefunden.isEmpty()) {
      return List.of();
    }
    final Set<Long> firmaIds = gefunden.stream().map(Angebot::firmaId).collect(Collectors.toSet());
    final Map<Long, String> namen =
        firmen.findAllById(firmaIds).stream()
            .collect(Collectors.toMap(Firma::requireId, Firma::name));
    return gefunden.stream()
        .sorted(Angebotsreihenfolge.NEUESTE_ZUERST)
        .map(angebot -> new AngebotMitFirma(angebot, nameVon(namen, angebot)))
        .toList();
  }

  private static String nameVon(final Map<Long, String> namen, final Angebot angebot) {
    final String name = namen.get(angebot.firmaId());
    if (name == null) {
      throw new FirmaNichtGefunden();
    }
    return name;
  }
}
