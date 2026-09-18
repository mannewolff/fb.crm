/**
 * Die Anmeldung als Modul: Schalter der Konfiguration, darunter die vier Schichten.
 *
 * <p>{@code AuthProperties} liegt hier und nicht in einer der Schichten, weil alle drei sie lesen —
 * die Anwendungsschicht die Laufzeit des Tokens, die Weboberflaeche Name und Eigenschaften des
 * Cookies, die Infrastruktur das Signaturgeheimnis. Laege sie in {@code infrastructure}, griffe
 * {@code web} in die Adapter (ArchitectureTest).
 */
@NullMarked
package org.mwolff.fbcrm.auth;

import org.jspecify.annotations.NullMarked;
