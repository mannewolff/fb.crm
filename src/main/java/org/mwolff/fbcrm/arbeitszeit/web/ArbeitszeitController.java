package org.mwolff.fbcrm.arbeitszeit.web;

import jakarta.validation.Valid;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.mwolff.fbcrm.arbeitszeit.application.ArbeitszeitMonatUseCase;
import org.mwolff.fbcrm.arbeitszeit.application.BuchbarePositionenUseCase;
import org.mwolff.fbcrm.arbeitszeit.application.Personenauskunft;
import org.mwolff.fbcrm.arbeitszeit.application.ZeiteintragUseCase;
import org.mwolff.fbcrm.common.web.Bedingungskopf;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Wege der Arbeitszeit (Issue #193, Kriterien 1, 5 und 6).
 *
 * <p>Ein eigener Bestand mit eigener Kennung und darum eigene Wege unter {@code /api/arbeitszeit} —
 * nicht unter dem Angebot: Die Position steht im Rumpf, weil sie sich beim Aendern wechseln laesst
 * (Plan #194, A7), und die Ansicht liest einen Monat quer ueber alle Angebote.
 *
 * <p>Der Controller entscheidet nichts (CLAUDE-java.md §6.3). Warum {@code POST} und {@code PUT}
 * mit einem Rumpf antworten, steht in {@link ZeiteintragResponse}. Er liest allein die Kennung des
 * angemeldeten Kontos und reicht sie weiter; Rechte und Vorauswahl je Rolle liegen im
 * Anwendungsfall, der Rolle und Zustand selbst aus dem Konto holt (Plan #426, E6) — welche Personen
 * waehlbar sind, entscheidet {@link Personenauskunft}.
 *
 * <p><b>Der Monat ist optional.</b> Fehlt er, fragt der Controller ohne Monat, und welcher der
 * laufende ist, entscheidet der Anwendungsfall an seiner Uhr in der Geschaeftszone (E4) — der
 * Controller entscheidet nichts. Ein Wert, der kein Monat ist, laesst sich nicht wandeln und kommt
 * als 400 zurueck ({@code GlobalExceptionHandler}); eine eigene Pruefung dafuer waere eine zweite
 * Abschrift derselben Regel.
 *
 * <p><b>Aendern und Loeschen legen den Stand vor</b> (#411, Kriterien 1, 4 und 6; Plan #415, E2,
 * E3, E14, E17): {@code PUT} und {@code DELETE} lesen {@link Bedingungskopf} und reichen ihn mit
 * der Kennung des angemeldeten Kontos weiter; ohne {@code If-Match} antworten sie 428, ohne den
 * Anwendungsfall zu fragen. {@code POST} bleibt ohne Kopf — ein neuer Zeiteintrag ist nie
 * geschuetzt (Kriterium 7).
 *
 * <p>Keine Aenderung an {@code SecurityConfig}: Die Wege fallen unter das bestehende {@code
 * /api/**} fuer angemeldete Benutzer.
 */
@RestController
@RequestMapping("/api/arbeitszeit")
public class ArbeitszeitController {

  private final ZeiteintragUseCase useCase;
  private final ArbeitszeitMonatUseCase monate;
  private final BuchbarePositionenUseCase buchbare;
  private final Personenauskunft personenauskunft;

  public ArbeitszeitController(
      final ZeiteintragUseCase useCase,
      final ArbeitszeitMonatUseCase monate,
      final BuchbarePositionenUseCase buchbare,
      final Personenauskunft personenauskunft) {
    this.useCase = useCase;
    this.monate = monate;
    this.buchbare = buchbare;
    this.personenauskunft = personenauskunft;
  }

  /**
   * Die Eintraege eines Monats nach Tagen, mit den Summen (Kriterium 5), fuer die Person, deren
   * Zeiten das angemeldete Konto sehen darf (Plan #426, E10).
   *
   * <p>Der Controller entscheidet nichts: Welche Vorauswahl ohne {@code person} gilt und dass ein
   * Employee immer nur seine eigenen Zeiten bekommt, entscheidet {@link ArbeitszeitMonatUseCase}
   * aus der Rolle des Kontos. Beide Parameter gehen unveraendert weiter.
   *
   * @param accountId Kennung des angemeldeten Kontos
   * @param monat der gesuchte Monat als {@code JJJJ-MM}, oder weggelassen fuer den laufenden
   * @param person die Kennung eines Kontos oder {@code alle}, oder weggelassen fuer die Vorauswahl
   *     der Rolle
   */
  @GetMapping
  public ArbeitszeitMonatResponse monat(
      @AuthenticationPrincipal final Long accountId,
      @RequestParam(required = false) final Optional<YearMonth> monat,
      @RequestParam(required = false) final Optional<String> person) {
    return ArbeitszeitMonatResponse.of(monate.monat(accountId, monat, person));
  }

  /** Die Positionen, auf die gebucht werden darf — die Auswahlliste des Dialogs (A15). */
  @GetMapping("/buchbare-positionen")
  public List<BuchungspositionResponse> buchbarePositionen() {
    return buchbare.positionen().stream().map(BuchungspositionResponse::of).toList();
  }

  /**
   * Die Personen, unter denen das angemeldete Konto waehlen darf — fuer Umschalter und
   * Erfassungsdialog (Plan #426, E11). Ein eigener Weg wie {@code buchbare-positionen}: Die Liste
   * aendert sich nicht je Monat.
   */
  @GetMapping("/personen")
  public List<PersonResponse> personen(@AuthenticationPrincipal final Long accountId) {
    return personenauskunft.personen(accountId).stream().map(PersonResponse::of).toList();
  }

  /**
   * Erfasst eine Arbeitszeit (Kriterium 1) fuer das angemeldete Konto oder die genannte Person.
   *
   * <p>Die Kennung des Kontos kommt aus der Sitzung, die Person optional aus dem Rumpf (Plan #426,
   * E12). Beide gehen unveraendert weiter; ob das Konto fuer die Person erfassen darf, entscheidet
   * der Anwendungsfall — kein {@code @PreAuthorize}, weil die Rechte am einzelnen Datensatz haengen
   * (E6).
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ZeiteintragResponse anlegen(
      @AuthenticationPrincipal final Long accountId,
      @Valid @RequestBody final ZeiteintragRequest anfrage) {
    return ZeiteintragResponse.of(
        useCase.anlegen(
            accountId,
            Optional.ofNullable(anfrage.accountId()),
            anfrage.angebotPositionId(),
            anfrage.tag(),
            anfrage.von(),
            anfrage.bis()));
  }

  /**
   * Aendert einen Zeiteintrag (Kriterium 6) auf dem vorgelegten Stand; mit genannter Person haengt
   * ein C-Level ihn um (Plan #426, E13). Ob das angemeldete Konto den Eintrag schreiben darf,
   * entscheidet der Anwendungsfall (E6, E7).
   *
   * <p>Der Kopf steht vor dem Rumpf: Fehlt er, weist der Weg mit 428 ab, bevor der Rumpf gelesen
   * und geprueft wird (Plan #415, E3).
   */
  @PutMapping("/{id}")
  public ZeiteintragResponse aendern(
      @AuthenticationPrincipal final Long accountId,
      @PathVariable final long id,
      final Bedingungskopf stand,
      @Valid @RequestBody final ZeiteintragRequest anfrage) {
    return ZeiteintragResponse.of(
        useCase.aendern(
            accountId,
            id,
            stand.version(),
            Optional.ofNullable(anfrage.accountId()),
            anfrage.angebotPositionId(),
            anfrage.tag(),
            anfrage.von(),
            anfrage.bis()));
  }

  /**
   * Loescht einen Zeiteintrag mit Kennzeichen auf dem vorgelegten Stand (Kriterium 6, Antwort 4;
   * Plan #415, E17), wenn das angemeldete Konto ihn schreiben darf (Plan #426, E6, E7).
   */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void loeschen(
      @AuthenticationPrincipal final Long accountId,
      @PathVariable final long id,
      final Bedingungskopf stand) {
    useCase.loeschen(accountId, id, stand.version());
  }
}
