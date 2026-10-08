package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;

/**
 * Eine gestellte Rechnung mit den Angaben, die eine Auswertung ueber sie braucht (Plan #288, E4).
 *
 * <p>„Gestellt" heisst {@link Rechnungszustand#istGestellt()}: gestellt, bezahlt oder
 * abgeschrieben, die von fb.crm geschriebene Rechnung wie die nachgetragene. Ein Entwurf steht hier
 * nicht — er ist noch nicht draussen.
 *
 * <p><b>Die Betraege sind die, mit denen {@link Rechnungsauskunft#gestellte()} rechnet</b> (E5):
 * bei der geschriebenen Rechnung Brutto ueber ihren geltenden Satz ({@link GeltenderSteuersatz}),
 * beim Nachtrag Netto und Brutto wie erfasst. Beide sind je Rechnung gerundet, und ihre Summe
 * trifft in jeder Gruppierung denselben Cent wie die Abrechnung je Monat.
 *
 * <p><b>Der Steuersatz fehlt beim Nachtrag</b>: Er wird dort nicht erfasst, und aus Netto und
 * Brutto abgeleitet waere er eine Zahl, die niemand angegeben hat (#287, Kriterium 6).
 *
 * <p>Die Zeile traegt weder Art noch Kennung noch Nummer: Wer sie liest, zaehlt und summiert und
 * oeffnet keine einzelne Rechnung.
 *
 * @param rechnungDatum Datum der Rechnung
 * @param netto der Nettobetrag
 * @param brutto der Bruttobetrag
 * @param steuersatz der Satz, mit dem die Rechnung rechnet, in Prozent; {@code null} beim Nachtrag
 * @param zustand gestellt, bezahlt oder abgeschrieben
 * @param firmaId Kennung der Firma, an die die Rechnung geht
 * @param firmaName der heutige Name dieser Firma
 */
public record GestellteRechnung(
    LocalDate rechnungDatum,
    BigDecimal netto,
    BigDecimal brutto,
    @Nullable BigDecimal steuersatz,
    Rechnungszustand zustand,
    long firmaId,
    String firmaName) {}
