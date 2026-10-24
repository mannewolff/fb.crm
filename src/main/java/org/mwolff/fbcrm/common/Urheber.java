package org.mwolff.fbcrm.common;

/**
 * Wer eine Aenderung vorgenommen hat, so wie die Meldung eines Bearbeitungskonflikts ihn nennt
 * (Plan #415, E6).
 *
 * @param konto die Kennung des Kontos
 * @param anzeigename der Name, den die Meldung zeigt
 */
public record Urheber(long konto, String anzeigename) {}
