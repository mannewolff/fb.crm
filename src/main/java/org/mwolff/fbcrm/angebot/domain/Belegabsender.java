package org.mwolff.fbcrm.angebot.domain;

import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Anschrift;

/**
 * Der Absender eines Belegs, wie er beim Versenden galt (R8).
 *
 * <p>Die Kopie der „Eigenen Angaben" zum Zeitpunkt des Versendens, aus demselben Grund wie bei
 * {@link Belegempfaenger}: Eine neue Bankverbindung darf ein bereits versendetes Angebot nicht
 * nachtraeglich veraendern.
 *
 * @param name Name, unter dem der Beleg hinausgeht; Pflicht, sobald ein Beleg festgeschrieben ist
 * @param anschrift eigene Postanschrift; jede ihrer Angaben darf fehlen
 * @param email E-Mail-Adresse, oder {@code null}
 * @param telefon Telefonnummer, oder {@code null}
 * @param steuernummer Steuernummer, oder {@code null}
 * @param umsatzsteuerId Umsatzsteuer-Identifikationsnummer, oder {@code null}
 * @param bankverbindung Bankverbindung als Text, oder {@code null}
 */
public record Belegabsender(
    String name,
    Anschrift anschrift,
    @Nullable String email,
    @Nullable String telefon,
    @Nullable String steuernummer,
    @Nullable String umsatzsteuerId,
    @Nullable String bankverbindung) {}
