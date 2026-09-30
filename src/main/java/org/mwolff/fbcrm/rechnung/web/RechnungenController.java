package org.mwolff.fbcrm.rechnung.web;

import org.mwolff.fbcrm.rechnung.application.AbrechenbareAngeboteUseCase;
import org.mwolff.fbcrm.rechnung.application.RechnungenUebersichtUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die beiden Wege ueber allen Rechnungen: die Liste und die Wahl „Neue Rechnung" (Plan #169, E11).
 *
 * <p>Der Controller entscheidet nichts: Er reicht weiter und uebersetzt das Ergebnis in den
 * Antwortrumpf (CLAUDE-java.md §6.3).
 *
 * <p>Eine eigene Klasse neben {@link RechnungController}: Dessen Pfad traegt die Kennung, dieser
 * nicht. Der Weg zu den abrechenbaren Angeboten verdeckt den Weg zur einzelnen Rechnung nicht —
 * Spring waehlt bei zwei passenden Mustern das ohne Platzhalter.
 *
 * <p>Keine eigene Regel in {@code SecurityConfig}: Die Pfade fallen unter das bestehende {@code
 * /api/**} fuer angemeldete Benutzer (Plan #169, E17).
 */
@RestController
@RequestMapping("/api/rechnungen")
public class RechnungenController {

  private final RechnungenUebersichtUseCase uebersicht;
  private final AbrechenbareAngeboteUseCase abrechenbare;

  public RechnungenController(
      final RechnungenUebersichtUseCase uebersicht,
      final AbrechenbareAngeboteUseCase abrechenbare) {
    this.uebersicht = uebersicht;
    this.abrechenbare = abrechenbare;
  }

  /** Alle Rechnungen, neueste zuerst (Kriterium 1). */
  @GetMapping
  public RechnungenUebersichtResponse rechnungen() {
    return RechnungenUebersichtResponse.of(uebersicht.rechnungen());
  }

  /** Die Angebote, aus denen eine Rechnung entstehen darf (Kriterium 2). */
  @GetMapping("/abrechenbare-angebote")
  public AbrechenbareAngeboteResponse abrechenbareAngebote() {
    return AbrechenbareAngeboteResponse.of(abrechenbare.abrechenbare());
  }
}
