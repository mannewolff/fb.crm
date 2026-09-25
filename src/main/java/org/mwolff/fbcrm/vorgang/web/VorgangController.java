package org.mwolff.fbcrm.vorgang.web;

import jakarta.validation.Valid;
import org.mwolff.fbcrm.vorgang.application.VorgaengeUebersichtUseCase;
import org.mwolff.fbcrm.vorgang.application.VorgangAbschliessenUseCase;
import org.mwolff.fbcrm.vorgang.application.VorgangAendernUseCase;
import org.mwolff.fbcrm.vorgang.application.VorgangAnlegenUseCase;
import org.mwolff.fbcrm.vorgang.application.VorgangLesenUseCase;
import org.springframework.http.HttpStatus;
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
 * Die Wege des Vorgangs: Uebersicht, Detailansicht, Anlegen, Aendern, Abschliessen,
 * Wiedereroeffnen.
 *
 * <p>Der Controller entscheidet nichts: Er nimmt die Eingaben, reicht sie in der Sprache der
 * Anwendungsschicht weiter und uebersetzt das Ergebnis in Statuscode und Antwortrumpf
 * (CLAUDE-java.md §6.3). Auch die Nummernerkennung des Suchtexts ist Sache der Anwendungsschicht
 * (E17) — hier kommt der Text unveraendert an, und auch die Wahlregel aus E19 steht dort.
 *
 * <p>Lese- und Schreibwege in derselben Klasse: Ein Pfad, eine Klasse — das Modul {@code firma}
 * haelt es genauso.
 *
 * <p><b>Warum drei der vier Schreibwege ohne Rumpf antworten.</b> Die Detailantwort eines Vorgangs
 * traegt seine vollstaendige Historie; die Anwendungsfaelle laden die nicht, weil sie sie nicht
 * brauchen. Eine Antwort mit leerer Historie waere gelogen — deshalb 204, und die Oberflaeche liest
 * den neuen Stand ueber {@code GET /api/vorgaenge/{id}}. Nur das Anlegen antwortet mit einem Rumpf:
 * Kennung und Nummer entstehen erst im Bestand (E25).
 */
@RestController
@RequestMapping("/api/vorgaenge")
public class VorgangController {

  private final VorgaengeUebersichtUseCase uebersichtUseCase;
  private final VorgangLesenUseCase lesenUseCase;
  private final VorgangAnlegenUseCase anlegenUseCase;
  private final VorgangAendernUseCase aendernUseCase;
  private final VorgangAbschliessenUseCase abschliessenUseCase;

  public VorgangController(
      final VorgaengeUebersichtUseCase uebersichtUseCase,
      final VorgangLesenUseCase lesenUseCase,
      final VorgangAnlegenUseCase anlegenUseCase,
      final VorgangAendernUseCase aendernUseCase,
      final VorgangAbschliessenUseCase abschliessenUseCase) {
    this.uebersichtUseCase = uebersichtUseCase;
    this.lesenUseCase = lesenUseCase;
    this.anlegenUseCase = anlegenUseCase;
    this.aendernUseCase = aendernUseCase;
    this.abschliessenUseCase = abschliessenUseCase;
  }

  /**
   * Die Uebersicht (Kriterien 2, 3, 20).
   *
   * @param suche Teil des Titels, des Firmennamens oder die Nummer mit oder ohne {@code #}; ohne
   *     Angabe trifft die Suche jeden Vorgang
   * @param auchAbgeschlossene ohne Angabe bleiben abgeschlossene Vorgaenge aussen vor
   */
  @GetMapping
  public VorgaengeUebersichtResponse uebersicht(
      @RequestParam(defaultValue = "") final String suche,
      @RequestParam(defaultValue = "false") final boolean auchAbgeschlossene) {
    return VorgaengeUebersichtResponse.of(uebersichtUseCase.uebersicht(suche, auchAbgeschlossene));
  }

  /** Legt einen Vorgang an (Kriterien 5, 6, 8). */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public VorgangAngelegtResponse anlegen(@Valid @RequestBody final VorgangRequest anfrage) {
    return VorgangAngelegtResponse.of(anlegenUseCase.anlegen(anfrage.daten()));
  }

  /** Die Detailansicht samt Historie (Kriterien 9, 15, 23). */
  @GetMapping("/{id}")
  public VorgangResponse lesen(@PathVariable final long id) {
    return VorgangResponse.of(lesenUseCase.lese(id));
  }

  /** Schreibt Titel, Firma und Ansprechpartner fort (Kriterien 10, 23). */
  @PutMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void aendern(
      @PathVariable final long id, @Valid @RequestBody final VorgangRequest anfrage) {
    aendernUseCase.aendern(id, anfrage.daten());
  }

  /** Schliesst den Vorgang ab (Kriterium 20). */
  @PostMapping("/{id}/abschliessen")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void abschliessen(@PathVariable final long id) {
    abschliessenUseCase.abschliessen(id);
  }

  /** Oeffnet den Vorgang wieder (Kriterium 20). */
  @PostMapping("/{id}/wiedereroeffnen")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void wiederEroeffnen(@PathVariable final long id) {
    abschliessenUseCase.wiederEroeffnen(id);
  }
}
