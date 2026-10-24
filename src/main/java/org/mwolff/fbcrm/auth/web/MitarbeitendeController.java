package org.mwolff.fbcrm.auth.web;

import jakarta.validation.Valid;
import org.mwolff.fbcrm.auth.application.MitarbeitendeVerwaltung;
import org.mwolff.fbcrm.common.web.Bedingungskopf;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Wege der Kontenverwaltung: Liste, Anlegen, Aendern, Stilllegen, Wiederaktivieren (Plan #414,
 * A12; fachliche Quelle #409, Kriterien 3 bis 8).
 *
 * <p>Nur fuer C-Level, mit jeder Methode, auch lesend — das sagt die Zugangsregel in {@link
 * SecurityConfig}, nicht dieser Controller.
 *
 * <p>Der Controller entscheidet nichts: Er nimmt die Eingaben, delegiert an {@link
 * MitarbeitendeVerwaltung} und uebersetzt das Ergebnis in Statuscode und Antwortrumpf
 * (CLAUDE-java.md §6.3).
 *
 * <p><b>Aendern, Stilllegen und Wiederaktivieren legen den Stand vor</b> (#411, Kriterium 4; Plan
 * #415, E3, E14, E19): Sie lesen {@link Bedingungskopf} aus {@code If-Match} und das handelnde
 * Konto aus der Sitzung. Fehlt der Kopf, antwortet der Weg mit 428, ohne die Verwaltung zu fragen.
 * Alle drei antworten mit dem neuen Stand samt erhoehter Version, die die Oberflaeche beim
 * naechsten Schritt vorlegt. Das Anlegen bleibt frei (#411, Kriterium 7).
 */
@RestController
@RequestMapping("/api/mitarbeitende")
public class MitarbeitendeController {

  private final MitarbeitendeVerwaltung verwaltung;

  public MitarbeitendeController(final MitarbeitendeVerwaltung verwaltung) {
    this.verwaltung = verwaltung;
  }

  /** Jedes Konto, aktiv oder stillgelegt (Kriterium 3). */
  @GetMapping
  public MitarbeitendeResponse liste() {
    return MitarbeitendeResponse.of(verwaltung.liste());
  }

  /** Legt ein Konto an und stellt die Begruessung ein (Kriterium 4). */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public MitarbeiterResponse anlegen(@Valid @RequestBody final MitarbeiterRequest anfrage) {
    return MitarbeiterResponse.angelegt(
        verwaltung.anlegen(anfrage.email(), anfrage.displayName(), anfrage.role()));
  }

  /**
   * Schreibt Adresse, Name und Rolle fort (Kriterium 6) und liefert den neuen Stand.
   *
   * <p>Der Kopf steht vor dem Rumpf: Fehlt er, weist der Weg mit 428 ab, bevor der Rumpf gelesen
   * und geprueft wird (Plan #415, E3).
   */
  @PutMapping("/{id}")
  public MitarbeiterResponse aendern(
      @PathVariable final long id,
      final Bedingungskopf stand,
      @AuthenticationPrincipal final Long accountId,
      @Valid @RequestBody final MitarbeiterRequest anfrage) {
    return MitarbeiterResponse.of(
        verwaltung.aendern(
            id,
            anfrage.email(),
            anfrage.displayName(),
            anfrage.role(),
            stand.version(),
            accountId));
  }

  /** Legt das Konto still (Kriterium 7) und liefert den neuen Stand. */
  @PostMapping("/{id}/stilllegen")
  public MitarbeiterResponse stilllegen(
      @PathVariable final long id,
      final Bedingungskopf stand,
      @AuthenticationPrincipal final Long accountId) {
    return MitarbeiterResponse.of(verwaltung.stilllegen(id, stand.version(), accountId));
  }

  /** Nimmt das Konto wieder in Betrieb (Kriterium 7) und liefert den neuen Stand. */
  @PostMapping("/{id}/aktivieren")
  public MitarbeiterResponse aktivieren(
      @PathVariable final long id,
      final Bedingungskopf stand,
      @AuthenticationPrincipal final Long accountId) {
    return MitarbeiterResponse.of(verwaltung.aktivieren(id, stand.version(), accountId));
  }
}
