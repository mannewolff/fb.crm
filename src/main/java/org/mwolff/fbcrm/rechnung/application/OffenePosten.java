package org.mwolff.fbcrm.rechnung.application;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnung;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;

/**
 * Die offenen Rechnungen beider Arten als Zeilen mit dem Namen ihrer Firma (Issue #285).
 *
 * <p>Sie steht neben {@link Rechnungsauskunft} und nicht darin, aus demselben Grund wie {@code
 * startseite.application.Abrechnungsblick} neben seinem Anwendungsfall: Der Zuschnitt soll nicht an
 * einem Grenzwert von PMD haengen. Paket-privat und ohne eigene Testklasse — die Zerlegung ist
 * keine eigene Zusage, geprueft wird sie ueber {@code RechnungsauskunftTest}.
 *
 * <p><b>Der Firmenname kommt in zwei Zuegen, nicht je Zeile</b>, ueber {@link Firmenblick}: Bei der
 * von fb.crm geschriebenen Rechnung haengt die Firma nicht an ihr, sondern an ihrem Angebot, bei
 * der nachgetragenen an ihr selbst. Ist nichts offen, wird gar nicht gefragt — das ist der
 * Regelfall eines bezahlten Bestands.
 *
 * <p>Die Ports kommen als Parameter und nicht als Feld: Die Klasse haelt keinen Zustand, und wer
 * sie ruft, hat sie schon.
 */
final class OffenePosten {

  private OffenePosten() {}

  /**
   * Die Zeilen zu den offenen Rechnungen, aelteste zuerst.
   *
   * @param eigene die offenen, von fb.crm geschriebenen Rechnungen
   * @param nachgetragene die offenen nachgetragenen Rechnungen
   * @param angebote der Weg zum Angebot einer geschriebenen Rechnung
   * @param firmen der Weg zum Namen einer Firma
   * @return je offene Rechnung eine Zeile, aelteste zuerst; leer, wo nichts offen ist
   * @throws AngebotNichtGefunden wenn es das Angebot einer offenen Rechnung nicht gibt
   * @throws FirmaNichtGefunden wenn es die Firma eines solchen Angebots oder einer offenen
   *     nachgetragenen Rechnung nicht gibt
   */
  static List<OffeneRechnung> zeilen(
      final List<Rechnung> eigene,
      final List<NachgetrageneRechnung> nachgetragene,
      final AngebotRepository angebote,
      final FirmaRepository firmen) {
    final Firmenblick blick = Firmenblick.fuer(eigene, nachgetragene, angebote, firmen);
    return Stream.concat(
            eigene.stream().map(rechnung -> zeile(rechnung, blick)),
            nachgetragene.stream().map(rechnung -> zeile(rechnung, blick)))
        .sorted(Rechnungsreihenfolge.OFFENE_AELTESTE_ZUERST)
        .toList();
  }

  /*
   * Die Zeile einer geschriebenen Rechnung. Die Nummer steht ohne Pruefung da: Eine Rechnung ohne
   * Nummer ist ein Entwurf, und ein Entwurf ist nicht offen (wie in Rechnungsbeleg).
   */
  private static OffeneRechnung zeile(final Rechnung rechnung, final Firmenblick blick) {
    return new OffeneRechnung(
        false,
        rechnung.requireId(),
        Objects.requireNonNull(rechnung.nummer()),
        blick.nameVon(blick.firmaVon(rechnung)),
        rechnung.rechnungDatum(),
        rechnung.netto());
  }

  /* Die Zeile einer nachgetragenen Rechnung — ihre Firma haengt an ihr selbst. */
  private static OffeneRechnung zeile(
      final NachgetrageneRechnung rechnung, final Firmenblick blick) {
    return new OffeneRechnung(
        true,
        rechnung.requireId(),
        rechnung.nummer(),
        blick.nameVon(rechnung.firmaId()),
        rechnung.rechnungDatum(),
        rechnung.netto());
  }
}
