package org.mwolff.fbcrm.rechnung.web;

import jakarta.validation.constraints.NotNull;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;

/**
 * Der Rumpf beim Umstellen des Zustands einer gestellten Rechnung (Issue #253).
 *
 * <p>Ein Feld, und es ist Pflicht: Ohne Ziel gibt es keinen Uebergang. Der Zustand steht als {@link
 * Rechnungszustand} und nicht als Text — was kein Zustand ist, laesst sich nicht wandeln und kommt
 * als 400 zurueck ({@code GlobalExceptionHandler}); eine eigene Pruefung dafuer waere eine zweite
 * Abschrift derselben Regel (dieselbe Form wie {@code RechnungAnlegenRequest} mit seinem Monat).
 *
 * <p><b>Welches Ziel zulaessig ist, entscheidet die Domaene</b> und nicht diese Annotation: Der Weg
 * nimmt jeden der vier Werte an, und {@code Rechnung#mitZustand} weist mit 409 ab, was keine der
 * drei Kanten ist. Ein {@code ENTWURF} im Rumpf ist kein Formfehler, sondern ein Uebergang, den der
 * Zustand nicht zulaesst.
 *
 * @param zustand der neue Zustand der Rechnung
 */
public record RechnungZustandRequest(@NotNull Rechnungszustand zustand) {}
