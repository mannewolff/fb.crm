/**
 * Firma und Ansprechpartner als Modul — der erste fachliche Stammsatz von fb.crm (Plan #37).
 *
 * <p>Beide liegen in einem Modul, weil ein Ansprechpartner nie ohne seine Firma existiert. Eigene
 * Wurzel bleibt er trotzdem: eigene Tabelle, eigener Port, eigener Lebenszyklus — das Stilllegen
 * der Firma laesst ihre Ansprechpartner unberuehrt (E2).
 */
@NullMarked
package org.mwolff.fbcrm.firma;

import org.jspecify.annotations.NullMarked;
