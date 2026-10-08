package org.mwolff.fbcrm.rechnung.domain;

import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Anschrift;

/**
 * Der Absender eines Belegs, wie er beim Stellen der Rechnung galt (R8).
 *
 * <p>Die Kopie der „Eigenen Angaben" zum Zeitpunkt des Stellens, aus demselben Grund wie bei {@link
 * Belegempfaenger}: Eine neue Bankverbindung darf eine bereits gestellte Rechnung nicht
 * nachtraeglich veraendern.
 *
 * @param name Name, unter dem der Beleg hinausgeht; Pflicht, sobald ein Beleg festgeschrieben ist
 * @param berufsbezeichnung Berufsbezeichnung unter dem Namen, oder {@code null}
 * @param anschrift eigene Postanschrift; jede ihrer Angaben darf fehlen
 * @param email E-Mail-Adresse, oder {@code null}
 * @param telefon Telefonnummer, oder {@code null}
 * @param steuernummer Steuernummer, oder {@code null}
 * @param umsatzsteuerId Umsatzsteuer-Identifikationsnummer, oder {@code null}
 * @param bankverbindung Bankverbindung als Text, oder {@code null}
 * @param webadresse Adresse des eigenen Webauftritts, oder {@code null}
 */
public record Belegabsender(
    String name,
    @Nullable String berufsbezeichnung,
    Anschrift anschrift,
    @Nullable String email,
    @Nullable String telefon,
    @Nullable String steuernummer,
    @Nullable String umsatzsteuerId,
    @Nullable String bankverbindung,
    @Nullable String webadresse) {}
