package org.mwolff.fbcrm.rechnung.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.Firma;
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
 * <p><b>Der Firmenname kommt in zwei Zuegen, nicht je Zeile</b>, und wie in {@link
 * RechnungenUebersichtUseCase}: Bei der von fb.crm geschriebenen Rechnung haengt die Firma nicht an
 * ihr, sondern an ihrem Angebot, bei der nachgetragenen an ihr selbst. Je Zeile nachzufragen waere
 * die bekannte Abfrage-Lawine.
 *
 * <p><b>Ist nichts offen, wird gar nicht gefragt.</b> Das ist der Regelfall eines bezahlten
 * Bestands, und zwei Abfragen fuer eine leere Liste zahlte jeder Aufruf der Startseite mit.
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
    if (eigene.isEmpty() && nachgetragene.isEmpty()) {
      return List.of();
    }
    final Map<Long, Long> firmaJeAngebot =
        angebote.findAlle(Optional.empty()).stream()
            .collect(Collectors.toMap(Angebot::requireId, Angebot::firmaId));
    final Set<Long> firmaIds =
        Stream.concat(
                eigene.stream().map(rechnung -> firmaVon(firmaJeAngebot, rechnung)),
                nachgetragene.stream().map(NachgetrageneRechnung::firmaId))
            .collect(Collectors.toSet());
    final Map<Long, String> namen =
        firmen.findAllById(firmaIds).stream()
            .collect(Collectors.toMap(Firma::requireId, Firma::name));
    return Stream.concat(
            eigene.stream().map(rechnung -> zeile(rechnung, namen, firmaJeAngebot)),
            nachgetragene.stream().map(rechnung -> zeile(rechnung, namen)))
        .sorted(Rechnungsreihenfolge.OFFENE_AELTESTE_ZUERST)
        .toList();
  }

  /*
   * Die Zeile einer geschriebenen Rechnung. Die Nummer steht ohne Pruefung da: Eine Rechnung ohne
   * Nummer ist ein Entwurf, und ein Entwurf ist nicht offen (wie in Rechnungsbeleg).
   */
  private static OffeneRechnung zeile(
      final Rechnung rechnung,
      final Map<Long, String> namen,
      final Map<Long, Long> firmaJeAngebot) {
    return new OffeneRechnung(
        false,
        rechnung.requireId(),
        Objects.requireNonNull(rechnung.nummer()),
        nameVon(namen, firmaVon(firmaJeAngebot, rechnung)),
        rechnung.rechnungDatum(),
        rechnung.netto());
  }

  /* Die Zeile einer nachgetragenen Rechnung — ihre Firma haengt an ihr selbst. */
  private static OffeneRechnung zeile(
      final NachgetrageneRechnung rechnung, final Map<Long, String> namen) {
    return new OffeneRechnung(
        true,
        rechnung.requireId(),
        rechnung.nummer(),
        nameVon(namen, rechnung.firmaId()),
        rechnung.rechnungDatum(),
        rechnung.netto());
  }

  private static long firmaVon(final Map<Long, Long> firmaJeAngebot, final Rechnung rechnung) {
    final Long firmaId = firmaJeAngebot.get(rechnung.angebotId());
    if (firmaId == null) {
      throw new AngebotNichtGefunden();
    }
    return firmaId;
  }

  private static String nameVon(final Map<Long, String> namen, final long firmaId) {
    final String name = namen.get(firmaId);
    if (name == null) {
      throw new FirmaNichtGefunden();
    }
    return name;
  }
}
