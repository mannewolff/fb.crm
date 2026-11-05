package org.mwolff.fbcrm.arbeitszeit.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

/**
 * Die fachlichen Angaben eines Zeiteintrags, wie sie beim Anlegen und beim Aendern ankommen (Issue
 * #477).
 *
 * <p>Beide Schreibwege von {@link ZeiteintragUseCase} nehmen dieselben Angaben, darum ein
 * gemeinsamer Typ: Er haelt beide gleich. Geprueft wird hier nichts — Raster, {@code bis > von} und
 * die Buchbarkeit prueft der Anwendungsfall in der dort beschriebenen Reihenfolge.
 *
 * @param person Kennung der Person, der der Eintrag gehoeren soll, oder leer — beim Anlegen fuer
 *     das anfragende Konto, beim Aendern fuer die bisherige Person (Plan #426, E12)
 * @param angebotPositionId Kennung der Position, auf die gebucht wird
 * @param tag der Tag, an dem gearbeitet wurde
 * @param von Beginn, auf einer Viertelstunde
 * @param bis Ende, auf einer Viertelstunde und nach {@code von}
 */
public record ZeiteintragAngaben(
    Optional<Long> person, long angebotPositionId, LocalDate tag, LocalTime von, LocalTime bis) {}
