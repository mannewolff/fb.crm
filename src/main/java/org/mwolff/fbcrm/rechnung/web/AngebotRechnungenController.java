package org.mwolff.fbcrm.rechnung.web;

import jakarta.validation.Valid;
import java.time.YearMonth;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.rechnung.application.AbrechnungsstandUseCase;
import org.mwolff.fbcrm.rechnung.application.RechnungAnlegenUseCase;
import org.mwolff.fbcrm.rechnung.application.RechnungLesenUseCase;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die beiden Wege am Angebot: einen Entwurf anlegen und den Abrechnungsstand lesen (Plan #169,
 * E11).
 *
 * <p>Eine eigene Klasse und keine Methode im {@link RechnungController}: Dessen Pfadpraefix steht
 * auf {@code /api/rechnungen}, und diese beiden Wege liegen unter dem Angebot. Gehalten werden sie
 * vom Modul {@code rechnung}, damit {@code angebot} nichts von der Rechnung wissen muss — ein
 * Verweis zurueck waere ein Zyklus zwischen den Modulen.
 *
 * <p><b>Warum das Anlegen mit der ganzen Rechnung antwortet.</b> Kennung, Datum, Leistungszeitraum
 * und die vorbelegten Mengen entstehen erst im Anwendungsfall; die Maske soll sie ohne zweiten
 * Aufruf zeigen. Gelesen wird dafuer derselbe Weg wie beim {@code GET}, damit der Entwurf nach dem
 * Anlegen genauso aussieht wie nach dem Neuladen.
 *
 * <p><b>Der Rumpf des Anlegens ist optional, und sein Monat ist es auch</b> (Issue #193, Antwort 7;
 * Plan #194, A9): Ohne Rumpf und mit {@code null} als Monat fragt der Controller ohne Monat, und
 * der Entwurf entsteht wie bisher aus den offenen Mengen. Beides ist dieselbe Anfrage, weil es
 * dasselbe bedeutet — „ohne Arbeitszeit". Welcher Monat der laufende ist und was ein Monat ohne
 * Stunden bedeutet, entscheidet der Anwendungsfall; der Controller entscheidet nichts
 * (CLAUDE-java.md §6.3).
 *
 * <p>Abgewiesen wird im Anwendungsfall und nicht hier: Ein Angebot vor „bestellt" und ein Angebot
 * ohne Offenes sind 409 ({@code AngebotNichtAbrechenbar}), ein unbekanntes Angebot ist 404. <b>Der
 * Server entscheidet</b>, nicht die Auswahlliste der Oberflaeche.
 */
@RestController
@RequestMapping("/api/angebote/{id}")
public class AngebotRechnungenController {

  private final RechnungAnlegenUseCase anlegenUseCase;
  private final RechnungLesenUseCase lesenUseCase;
  private final AbrechnungsstandUseCase abrechnungsstandUseCase;

  public AngebotRechnungenController(
      final RechnungAnlegenUseCase anlegenUseCase,
      final RechnungLesenUseCase lesenUseCase,
      final AbrechnungsstandUseCase abrechnungsstandUseCase) {
    this.anlegenUseCase = anlegenUseCase;
    this.lesenUseCase = lesenUseCase;
    this.abrechnungsstandUseCase = abrechnungsstandUseCase;
  }

  /**
   * Legt zum Angebot einen Rechnungsentwurf an (Kriterium 3).
   *
   * @param id Kennung des Angebots
   * @param anfrage der Rumpf mit dem optionalen Monat; er darf ganz fehlen
   */
  @PostMapping("/rechnungen")
  @ResponseStatus(HttpStatus.CREATED)
  public RechnungResponse anlegen(
      @PathVariable final long id,
      @Valid @RequestBody(required = false) final @Nullable RechnungAnlegenRequest anfrage) {
    final Rechnung entwurf = anlegenUseCase.anlegen(id, monatAus(anfrage));
    return RechnungResponse.of(lesenUseCase.lese(entwurf.requireId()));
  }

  /** Der Abrechnungsstand des Angebots samt seinen Rechnungen (Kriterium 26). */
  @GetMapping("/abrechnung")
  public AngebotAbrechnungResponse abrechnung(@PathVariable final long id) {
    return AngebotAbrechnungResponse.of(abrechnungsstandUseCase.zu(id));
  }

  private static Optional<YearMonth> monatAus(final @Nullable RechnungAnlegenRequest anfrage) {
    return anfrage == null ? Optional.empty() : Optional.ofNullable(anfrage.monat());
  }
}
