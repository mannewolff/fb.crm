package org.mwolff.fbcrm.jahresabschluss.web;

import java.time.Year;
import java.util.List;
import org.mwolff.fbcrm.jahresabschluss.application.JahresabschlussUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die beiden Wege des Jahresabschlusses (Issue #294; Plan #288, E2, E3, E23).
 *
 * <p><b>Zwei Wege</b> (E2): die Uebersicht mit den drei Hauptzahlen je Jahr und der vollstaendige
 * Abschluss eines Jahres. Ein Weg mit allen Jahren vollstaendig luede die Zeiterfassung fuer jedes
 * Jahr und lieferte Teile, die die Uebersicht nicht zeigt.
 *
 * <p>Der Controller entscheidet nichts (CLAUDE-java.md §6.3): Welche Jahre erscheinen und ob ein
 * Jahr einen Abschluss hat, entscheidet der Anwendungsfall. Ein Jahr ohne Daten kommt dort als
 * {@code JahrOhneDaten} heraus und ueber den {@code GlobalExceptionHandler} als 404 zurueck (E3).
 *
 * <p><b>Das Jahr kommt als {@link Year} herein</b> (E23): Spring wandelt {@code String} → {@code
 * Year} mit seinen eigenen Formatierern fuer {@code java.time}; einen eigenen Converter braucht es
 * nicht. Ein Pfad, der kein Jahr ist, laesst sich nicht wandeln und kommt ueber den {@code
 * TypeMismatchException}-Zweig als 400 zurueck — und nicht als 404, das ein Jahr ohne Daten
 * behauptete, wo gar kein Jahr stand.
 */
@RestController
@RequestMapping("/api/jahresabschluesse")
public class JahresabschlussController {

  private final JahresabschlussUseCase useCase;

  public JahresabschlussController(final JahresabschlussUseCase useCase) {
    this.useCase = useCase;
  }

  /** Die Uebersicht: je Jahr mit Daten eine Zeile, das juengste zuerst; leer ohne ein solches. */
  @GetMapping
  public List<JahresuebersichtResponse> jahre() {
    return useCase.jahre().stream().map(JahresuebersichtResponse::of).toList();
  }

  /**
   * Der vollstaendige Abschluss eines Jahres.
   *
   * @param jahr das Kalenderjahr aus dem Pfad
   */
  @GetMapping("/{jahr}")
  public JahresabschlussResponse abschluss(@PathVariable final Year jahr) {
    return JahresabschlussResponse.of(useCase.abschluss(jahr));
  }
}
