package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import org.mwolff.fbcrm.angebot.domain.Angebot;

/**
 * Eine Zeile der Wahl „Neue Rechnung": ein Angebot, an dem noch etwas offen ist (#160, Kriterium
 * 2).
 *
 * <p>Das Angebot bringt sein Datum selbst mit; dazu kommen der Name seiner Firma und der offene
 * Betrag, damit der Freiberufler in der Wahl sieht, worum es geht.
 *
 * @param angebot das Angebot, aus dem eine Rechnung entstehen darf
 * @param firmaName Name der Firma, an die das Angebot geht
 *     <p>Am stornierten Angebot ist der Betrag der Wert der nicht abgerechneten Stunden und nicht
 *     der des offenen Rests (#330, Kriterium 7; Plan #332, E8). Der Name des Feldes bleibt, weil
 *     die Oberflaeche ihn liest: Er nennt, was noch in Rechnung gestellt werden kann.
 * @param offenerBetrag Summe der abrechenbaren Mengen mal Einzelpreis, je Position auf den Cent
 *     gerundet — ohne Storno die offenen Mengen
 */
public record AbrechenbaresAngebot(Angebot angebot, String firmaName, BigDecimal offenerBetrag) {}
