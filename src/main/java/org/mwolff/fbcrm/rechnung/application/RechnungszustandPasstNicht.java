package org.mwolff.fbcrm.rechnung.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Der verlangte Schritt passt nicht zum Zustand der Rechnung (Plan #169, E3).
 *
 * <p>Drei Grenzen, eine Regel: Ein Entwurf laesst sich aendern, eine gestellte Rechnung nicht; ein
 * Entwurf laesst sich stellen, eine gestellte Rechnung nicht noch einmal — eine zweite Nummer auf
 * derselben Rechnung risse ein Loch in den Nummernkreis; und ein Dokument traegt nur eine gestellte
 * Rechnung, weil ein Entwurf keines hat. Dieselben Grenzen zieht der CHECK in {@code
 * V18__rechnung.sql}; die Domaene faengt sie vorher ab, damit die Abweisung fachlich benannt ist
 * und nicht als Datenbankfehler herauskommt.
 *
 * <p>Geworfen wird sie in der Domaene — in {@code Rechnung} selbst —, damit kein Weg an der Regel
 * vorbeifuehrt. Sie liegt trotzdem hier: Ihr Statuscode ist eine Aussage der Schnittstelle, und
 * {@code @ResponseStatus} ist eine Spring-Annotation, die das Domaenenmodell nicht tragen darf
 * (CLAUDE-java.md §6.1, {@code ArchitectureTest}) — dieselbe Aufteilung wie bei {@code
 * angebot.application.StatusGrenzeErreicht}.
 *
 * <p>409 und nicht 422: Die Anfrage ist wohlgeformt, nur der Zustand der Rechnung passt nicht — und
 * er kann sich hinter dem Ruecken des Anwenders geaendert haben.
 */
@ResponseStatus(code = HttpStatus.CONFLICT, reason = RechnungszustandPasstNicht.MELDUNG)
public final class RechnungszustandPasstNicht extends RuntimeException {

  /** Die Meldung an der Schnittstelle. */
  public static final String MELDUNG = "Der Zustand der Rechnung laesst diesen Schritt nicht zu.";
}
