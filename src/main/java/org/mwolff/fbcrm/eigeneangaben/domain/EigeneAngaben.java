package org.mwolff.fbcrm.eigeneangaben.domain;

import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Anschrift;

/**
 * Die Selbstauskunft der Instanz: wer hier arbeitet und wie er sich auf einem Beleg nennt
 * (Kriterium 1).
 *
 * <p>Jede Angabe darf fehlen — die Angaben werden nach und nach vervollstaendigt. Fehlt eine, steht
 * dort {@code null} und nicht der Leerstring, sonst gaebe es zwei Schreibweisen fuer „nicht
 * angegeben" (E9).
 *
 * <p>Eine Kennung traegt der Record nicht: Es gibt genau einen Satz Angaben je Instanz, und der
 * Bestand haelt ihn an der festen Zeile. Der Zeitpunkt der letzten Aenderung fehlt hier aus
 * demselben Grund, aus dem die Domaene keine Uhr kennt (CLAUDE-java.md §6.2) — er kommt von aussen
 * und geht am Port mit.
 *
 * @param name Name, unter dem Rechnungen und Angebote hinausgehen
 * @param berufsbezeichnung Berufsbezeichnung fuer den Belegkopf, oder {@code null}
 * @param anschrift Postanschrift; jede ihrer Angaben darf fehlen
 * @param email E-Mail-Adresse, oder {@code null}
 * @param telefon Telefonnummer, oder {@code null}
 * @param webadresse Webadresse als Text fuer den Belegkopf, oder {@code null}
 * @param steuernummer Steuernummer, oder {@code null}
 * @param umsatzsteuerId Umsatzsteuer-Identifikationsnummer, oder {@code null}
 * @param bankverbindung Bankverbindung als Text, oder {@code null}
 */
public record EigeneAngaben(
    @Nullable String name,
    @Nullable String berufsbezeichnung,
    Anschrift anschrift,
    @Nullable String email,
    @Nullable String telefon,
    @Nullable String webadresse,
    @Nullable String steuernummer,
    @Nullable String umsatzsteuerId,
    @Nullable String bankverbindung) {}
