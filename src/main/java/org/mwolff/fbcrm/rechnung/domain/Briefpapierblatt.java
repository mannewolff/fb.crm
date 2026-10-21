package org.mwolff.fbcrm.rechnung.domain;

/**
 * Das hinterlegte Briefpapier: das PDF im Objektspeicher und was die Maske davon zeigt (Plan #363,
 * E1).
 *
 * <p><b>Briefpapier</b> heisst allein dieses PDF; die Einstellung als Ganzes ist der {@link
 * Briefbogen}.
 *
 * @param schluessel Schluessel des PDFs im Objektspeicher
 * @param dateiname Name der hochgeladenen Datei
 * @param groesse Groesse in Byte
 * @param seiten Seitenzahl, 1 oder 2 — Seite 2 liegt unter den Folgeseiten
 */
public record Briefpapierblatt(String schluessel, String dateiname, long groesse, int seiten) {}
