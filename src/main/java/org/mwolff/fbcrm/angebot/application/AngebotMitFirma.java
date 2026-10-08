package org.mwolff.fbcrm.angebot.application;

import org.mwolff.fbcrm.angebot.domain.Angebot;

/**
 * Eine Zeile der Uebersicht aller Angebote: das Angebot samt dem Namen seiner Firma (Issue #127,
 * Kriterium 8).
 *
 * @param angebot das Angebot
 * @param firmaName Name der Firma, an die das Angebot geht
 */
public record AngebotMitFirma(Angebot angebot, String firmaName) {}
