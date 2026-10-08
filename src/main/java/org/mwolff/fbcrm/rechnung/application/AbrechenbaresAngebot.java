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
 * @param offenerBetrag Summe der offenen Mengen mal Einzelpreis, auf den Cent gerundet
 */
public record AbrechenbaresAngebot(Angebot angebot, String firmaName, BigDecimal offenerBetrag) {}
