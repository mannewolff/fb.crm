package org.mwolff.fbcrm.auth.application;

import java.time.Instant;

/**
 * Der ausgestellte Einmalcode im Klartext, fuer genau einen Ausgabeweg: Mailtext oder
 * Protokollzeile (E10).
 *
 * <p>Er verlaesst {@link RequestLoginCodeUseCase} nie — gespeichert wird nur sein Hash, und kein
 * Aufrufer bekommt ihn zurueck. Darum ist er paketsichtbar.
 *
 * @param code der sechsstellige Code, wie ihn der Mensch abtippt
 * @param expiresAt Zeitpunkt, ab dem er nicht mehr gilt
 */
record LoginCodeAusgabe(String code, Instant expiresAt) {}
