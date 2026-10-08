package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnung;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;

/**
 * Eine Zeile der Liste „Rechnungen": eine von fb.crm geschriebene oder eine nachgetragene Rechnung
 * (#160, Kriterium 1; #254, Kriterien 6 und 7).
 *
 * <p>Beide Arten stehen in derselben Liste, ihre Kennungen aber in zwei Raeumen — darum traegt die
 * Zeile ihre Art: Erst Art und Kennung zusammen bezeichnen eine Rechnung, und die Oberflaeche
 * waehlt daran Kennzeichnung und Weg (Plan #259, E18).
 *
 * <p>Ob ein Dokument herunterzuladen ist, steht daneben und nicht am Zustand: Eine nachgetragene
 * Rechnung ohne Original ist gestellt und hat trotzdem keines (Kriterium 7). Fuer beide Arten gilt
 * dieselbe Regel — ein Dokument gibt es genau dann, wenn ein {@code pdfSchluessel} da ist.
 *
 * <p>Die Firma steht daneben, weil sie bei der geschriebenen Rechnung nicht an ihr haengt, sondern
 * an ihrem Angebot. Der Bruttobetrag ist bei der geschriebenen gerechnet — mit dem Steuersatz, der
 * fuer sie gilt (Kriterium 14) —, bei der nachgetragenen der erfasste (#254, Kriterium 3).
 *
 * @param nachgetragen ob die Rechnung nachgetragen und nicht von fb.crm geschrieben ist
 * @param id Kennung der Rechnung im Raum ihrer Art
 * @param nummer die Rechnungsnummer, oder {@code null} im Entwurf
 * @param firmaId Kennung der Firma, an die die Rechnung geht
 * @param firmaName Name dieser Firma
 * @param rechnungDatum Datum der Rechnung
 * @param brutto der Bruttobetrag
 * @param zustand der Zustand der Rechnung
 * @param hatDokument ob ein Dokument hinterlegt ist
 */
public record Rechnungslistenzeile(
    boolean nachgetragen,
    long id,
    @Nullable String nummer,
    long firmaId,
    String firmaName,
    LocalDate rechnungDatum,
    BigDecimal brutto,
    Rechnungszustand zustand,
    boolean hatDokument) {

  /**
   * Die Zeile einer von fb.crm geschriebenen Rechnung.
   *
   * @param rechnung die Rechnung
   * @param firmaId Kennung der Firma ihres Angebots
   * @param firmaName Name dieser Firma
   * @param brutto der Bruttobetrag mit dem Steuersatz, der fuer diese Rechnung gilt
   */
  static Rechnungslistenzeile vonRechnung(
      final Rechnung rechnung,
      final long firmaId,
      final String firmaName,
      final BigDecimal brutto) {
    return new Rechnungslistenzeile(
        false,
        rechnung.requireId(),
        rechnung.nummer(),
        firmaId,
        firmaName,
        rechnung.rechnungDatum(),
        brutto,
        rechnung.zustand(),
        rechnung.pdfSchluessel() != null);
  }

  /**
   * Die Zeile einer nachgetragenen Rechnung.
   *
   * @param rechnung die nachgetragene Rechnung
   * @param firmaName der heutige Name ihrer Firma
   */
  static Rechnungslistenzeile vonNachtrag(
      final NachgetrageneRechnung rechnung, final String firmaName) {
    return new Rechnungslistenzeile(
        true,
        rechnung.requireId(),
        rechnung.nummer(),
        rechnung.firmaId(),
        firmaName,
        rechnung.rechnungDatum(),
        rechnung.brutto(),
        rechnung.zustand(),
        rechnung.pdfSchluessel() != null);
  }
}
