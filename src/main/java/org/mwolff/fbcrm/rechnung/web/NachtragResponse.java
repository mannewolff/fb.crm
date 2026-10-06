package org.mwolff.fbcrm.rechnung.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.mwolff.fbcrm.rechnung.application.NachgetrageneRechnungMitFirma;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnung;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;

/**
 * Eine nachgetragene Rechnung, wie die Oberflaeche sie sieht (#254, Kriterien 2, 7, 8 und 11).
 *
 * <p>{@code dokument} sagt nur, <b>ob</b> ein Original hinterlegt ist: Der Schluessel im Speicher
 * ist ein Detail der Ablage und geht nicht nach aussen. Heruntergeladen wird ueber den eigenen Weg
 * {@code …/dokument}.
 *
 * <p>Der {@code firmaName} ist der von heute — die Rechnung verweist auf die Firma (Kriterium 11).
 *
 * @param id Kennung der nachgetragenen Rechnung
 * @param firmaId Kennung der Firma, an die die Rechnung ging
 * @param firmaName der heutige Name dieser Firma
 * @param nummer die frei erfasste Rechnungsnummer
 * @param rechnungDatum Datum der Rechnung
 * @param netto der Nettobetrag, wie erfasst
 * @param brutto der Bruttobetrag, wie erfasst
 * @param zustand gestellt, bezahlt oder abgeschrieben
 * @param dokument ob ein Original hinterlegt ist
 */
public record NachtragResponse(
    long id,
    long firmaId,
    String firmaName,
    String nummer,
    LocalDate rechnungDatum,
    BigDecimal netto,
    BigDecimal brutto,
    Rechnungszustand zustand,
    boolean dokument) {

  /** Die Sicht der Oberflaeche auf eine nachgetragene Rechnung samt Firma. */
  static NachtragResponse of(final NachgetrageneRechnungMitFirma mitFirma) {
    final NachgetrageneRechnung rechnung = mitFirma.rechnung();
    return new NachtragResponse(
        rechnung.requireId(),
        mitFirma.firmaId(),
        mitFirma.firmaName(),
        rechnung.nummer(),
        rechnung.rechnungDatum(),
        rechnung.netto(),
        rechnung.brutto(),
        rechnung.zustand(),
        rechnung.pdfSchluessel() != null);
  }
}
