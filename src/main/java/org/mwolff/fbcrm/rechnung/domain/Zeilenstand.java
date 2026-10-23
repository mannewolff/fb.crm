package org.mwolff.fbcrm.rechnung.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Der Stand einer der beiden Einzelzeilen — Rechnungseinstellungen und Briefbogen —, wie ihn {@code
 * If-Match} vorlegt (#411, Kriterium 4; Plan #415, E1, E11, E14).
 *
 * <p>Getrennt von {@link Rechnungseinstellungen} und {@link Briefbogen}: Beide werden beim Stellen
 * und Drucken gelesen, wie sie im Augenblick sind, und loesen dort keine Abweisung aus (E10). Der
 * Stand gehoert allein zu den Wegen, die sie fortschreiben; die Leser sollen ihn nicht mittragen.
 *
 * <p>Der Briefbogen traegt <b>einen</b> Stand fuer die ganze Zeile, obwohl Schalter und Raender
 * einerseits und das Briefpapier andererseits in getrennte Spalten schreiben (E11).
 *
 * @param version der Stand der Zeile; 0 vor der ersten Aenderung, danach fuehrt ihn der Bestand
 * @param geaendertVon Kennung des Kontos, das die Zeile zuletzt geaendert hat, oder {@code null},
 *     solange sie das nicht weiss
 * @param geaendertAm Zeitpunkt der letzten Aenderung
 */
public record Zeilenstand(long version, @Nullable Long geaendertVon, Instant geaendertAm) {}
