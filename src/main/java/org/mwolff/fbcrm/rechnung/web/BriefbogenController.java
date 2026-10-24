package org.mwolff.fbcrm.rechnung.web;

import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Objects;
import org.mwolff.fbcrm.common.web.Anlagekopf;
import org.mwolff.fbcrm.common.web.Bedingungskopf;
import org.mwolff.fbcrm.rechnung.application.BriefbogenLesenUseCase;
import org.mwolff.fbcrm.rechnung.application.BriefbogenPflegenUseCase;
import org.mwolff.fbcrm.rechnung.application.BriefpapierUseCase;
import org.mwolff.fbcrm.rechnung.application.ProbedruckUseCase;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stand und Pflege des Briefbogens und das Briefpapier selbst (fachliche Quelle #361; Plan #363,
 * E1).
 *
 * <p>Der Weg liegt im Modul {@code rechnung}, weil die Randpruefung mit dem Rechnungssatz misst;
 * die Oberflaeche bleibt bei den eigenen Angaben.
 *
 * <p>Der Controller entscheidet nichts (CLAUDE-java.md §6.3). {@code PUT} und beide Wege auf {@code
 * /datei} antworten mit dem neuen Stand. Keine eigene Regel in {@code SecurityConfig}: Der Pfad
 * faellt unter {@code /api/**} fuer angemeldete Benutzer.
 *
 * <p><b>Die drei schreibenden Wege legen den Stand vor</b> (#411, Kriterium 4; Plan #415, E2, E3,
 * E11, E14): {@code PUT} und beide Wege auf {@code /datei} lesen {@link Bedingungskopf} aus {@code
 * If-Match} und das handelnde Konto aus der Sitzung. Fehlt der Kopf, antwortet der Weg mit 428,
 * ohne den Anwendungsfall zu fragen. Der Stand gilt fuer den ganzen Briefbogen (E11). Lesen und
 * Probedruck bleiben frei.
 */
@RestController
@RequestMapping("/api/briefpapier")
public class BriefbogenController {

  /** Der Dateiname, unter dem der Probedruck ankommt. */
  private static final String PROBEDRUCK_DATEINAME = "Probedruck.pdf";

  private final BriefbogenLesenUseCase lesenUseCase;
  private final BriefbogenPflegenUseCase pflegenUseCase;
  private final BriefpapierUseCase briefpapierUseCase;
  private final ProbedruckUseCase probedruckUseCase;

  public BriefbogenController(
      final BriefbogenLesenUseCase lesenUseCase,
      final BriefbogenPflegenUseCase pflegenUseCase,
      final BriefpapierUseCase briefpapierUseCase,
      final ProbedruckUseCase probedruckUseCase) {
    this.lesenUseCase = lesenUseCase;
    this.pflegenUseCase = pflegenUseCase;
    this.briefpapierUseCase = briefpapierUseCase;
    this.probedruckUseCase = probedruckUseCase;
  }

  /** Der Stand; auf einer frischen Instanz die Vorgaben der Migration ohne Briefpapier. */
  @GetMapping
  public BriefbogenResponse lesen() {
    return BriefbogenResponse.of(lesenUseCase.lese());
  }

  /**
   * Schreibt Schalter und Raender fort und antwortet mit dem neuen Stand.
   *
   * <p>Der Kopf steht vor dem Rumpf: Fehlt er, weist der Weg mit 428 ab, bevor der Rumpf gelesen
   * und geprueft wird (Plan #415, E3).
   */
  @PutMapping
  public BriefbogenResponse pflegen(
      final Bedingungskopf stand,
      @AuthenticationPrincipal final Long accountId,
      @Valid @RequestBody final BriefbogenRequest anfrage) {
    return BriefbogenResponse.of(
        pflegenUseCase.pflege(
            anfrage.absenderzeile(),
            anfrage.steuerBankZeile(),
            anfrage.raender(),
            stand.version(),
            accountId));
  }

  /**
   * Hinterlegt das Briefpapier oder ersetzt es (Kriterien 1 und 2) und antwortet mit dem neuen
   * Stand.
   *
   * <p>200 und nicht 201: Das Briefpapier ist keine eigene Ressource mit Kennung, sondern ein Teil
   * des Briefbogens. Ob die Datei ein PDF ist, entscheidet der Anwendungsfall am Inhalt; die
   * gemeldete Art des Teils liest dieser Controller nicht, den Dateinamen reicht er ungesaeubert
   * weiter — ein fehlender Name ist dort ein leerer.
   *
   * @param datei der Teil {@code datei} der Formulardaten
   * @throws IOException wenn sich der Inhalt der hochgeladenen Datei nicht lesen laesst
   */
  @PostMapping("/datei")
  public BriefbogenResponse hochladen(
      final Bedingungskopf stand,
      @AuthenticationPrincipal final Long accountId,
      @RequestPart("datei") final MultipartFile datei)
      throws IOException {
    return BriefbogenResponse.of(
        briefpapierUseCase.ladeHoch(
            Objects.requireNonNullElse(datei.getOriginalFilename(), ""),
            datei.getBytes(),
            stand.version(),
            accountId));
  }

  /** Entfernt das Briefpapier; Schalter und Raender bleiben (Kriterium 16). */
  @DeleteMapping("/datei")
  public BriefbogenResponse entfernen(
      final Bedingungskopf stand, @AuthenticationPrincipal final Long accountId) {
    return BriefbogenResponse.of(briefpapierUseCase.entferne(stand.version(), accountId));
  }

  /**
   * Der Probedruck auf dem hinterlegten Briefpapier (Kriterium 9; E14); ohne Briefpapier 404.
   *
   * <p>Dieselben strengen Kopfzeilen wie beim Dokument einer gestellten Rechnung: {@code
   * attachment} statt Anzeige und die Sandbox-Regel als letzte Schranke — unter dem Satz liegt ein
   * hochgeladenes Dokument.
   */
  @GetMapping("/probedruck")
  public ResponseEntity<byte[]> probedruck() {
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION, Anlagekopf.contentDisposition(PROBEDRUCK_DATEINAME))
        .header(Anlagekopf.INHALTSREGEL, Anlagekopf.SANDKASTEN)
        .contentType(MediaType.APPLICATION_PDF)
        .body(probedruckUseCase.drucke());
  }
}
