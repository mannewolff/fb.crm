package org.mwolff.fbcrm.eigeneangaben.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Der Stand der einen Zeile der eigenen Angaben, wie ihn {@code If-Match} vorlegt (#411, Kriterium
 * 4; Plan #415, E1, E14).
 *
 * <p>Getrennt von {@link EigeneAngaben}: Die Angaben werden beim Stellen einer Rechnung und beim
 * Drucken gelesen, wie sie im Augenblick sind, und loesen dort keine Abweisung aus (E10). Der Stand
 * gehoert allein zum Weg, der sie fortschreibt.
 *
 * @param version der Stand der Zeile; 0 vor der ersten Aenderung, danach fuehrt ihn der Bestand
 * @param geaendertVon Kennung des Kontos, das die Zeile zuletzt geaendert hat, oder {@code null},
 *     solange sie das nicht weiss
 * @param geaendertAm Zeitpunkt der letzten Aenderung
 */
public record EigeneAngabenStand(long version, @Nullable Long geaendertVon, Instant geaendertAm) {}
