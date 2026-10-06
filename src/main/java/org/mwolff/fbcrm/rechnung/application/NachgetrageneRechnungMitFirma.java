package org.mwolff.fbcrm.rechnung.application;

import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnung;

/**
 * Eine nachgetragene Rechnung samt ihrer Firma (#254, Kriterium 11).
 *
 * <p>Der Name ist der von heute: Die Rechnung verweist auf die Firma und kopiert sie nicht — eine
 * spaetere Umbenennung zeigt sich auch hier.
 *
 * @param rechnung die nachgetragene Rechnung
 * @param firmaId Kennung der Firma, an die die Rechnung ging
 * @param firmaName der heutige Name dieser Firma
 */
public record NachgetrageneRechnungMitFirma(
    NachgetrageneRechnung rechnung, long firmaId, String firmaName) {}
