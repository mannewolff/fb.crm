package org.mwolff.fbcrm.angebot.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngabenRepository;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.AnsprechpartnerRepository;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Anlegen eines Angebots an eine Firma (Kriterien 2, 3; Issue #126).
 *
 * <p><b>Die Vorbelegung.</b> Das Angebotsdatum ist der heutige Tag in der Geschaeftszone und nicht
 * in UTC (E12) — zwischen 22:00 UTC und Mitternacht deutscher Zeit unterscheiden sich beide
 * Antworten um einen Tag. Die Gueltigkeit laeuft dreissig Tage, und die Zahlungsbedingungen kommen
 * aus „Eigene Angaben" (Kriterium 3, F8). Alle drei sind Vorschlaege: Der Entwurf ist danach frei
 * aenderbar.
 *
 * <p><b>Die Wahl des Kunden.</b> Die Firma muss es geben und sie darf nicht stillgelegt sein — an
 * eine stillgelegte Firma geht kein neues Angebot. Der Ansprechpartner ist optional; wird einer
 * genannt, muss er zu dieser Firma gehoeren und aktiv sein. Geprueft wird vor allem anderen: Eine
 * abgewiesene Anlage soll nichts gelesen haben, was sie nicht braucht.
 */
@Service
@Transactional
public class AngebotAnlegenUseCase {

  /** Die Frist der Vorbelegung: dreissig Tage ab dem Angebotsdatum (Kriterium 3). */
  private static final int GUELTIGKEIT_IN_TAGEN = 30;

  private final AngebotRepository angebote;
  private final FirmaRepository firmen;
  private final AnsprechpartnerRepository personen;
  private final EigeneAngabenRepository eigeneAngaben;
  private final Clock clock;

  public AngebotAnlegenUseCase(
      final AngebotRepository angebote,
      final FirmaRepository firmen,
      final AnsprechpartnerRepository personen,
      final EigeneAngabenRepository eigeneAngaben,
      final Clock clock) {
    this.angebote = angebote;
    this.firmen = firmen;
    this.personen = personen;
    this.eigeneAngaben = eigeneAngaben;
    this.clock = clock;
  }

  /**
   * Legt an eine Firma einen Angebotsentwurf an.
   *
   * @param firmaId Kennung der Firma, an die das Angebot geht
   * @param ansprechpartnerId Kennung des Ansprechpartners bei dieser Firma, oder {@code null}
   * @throws FirmaNichtGefunden wenn es die Firma nicht gibt
   * @throws FirmaStillgelegt wenn die Firma stillgelegt ist
   * @throws AnsprechpartnerNichtWaehlbar wenn der Ansprechpartner unbekannt ist, zu einer anderen
   *     Firma gehoert oder stillgelegt ist
   */
  public AngebotAnsicht anlegen(final long firmaId, final @Nullable Long ansprechpartnerId) {
    final Firma firma = firmen.findById(firmaId).orElseThrow(FirmaNichtGefunden::new);
    if (!firma.aktiv()) {
      throw new FirmaStillgelegt();
    }
    pruefe(firmaId, ansprechpartnerId);
    final LocalDate heute = AngebotAnsicht.heute(clock);
    final Instant jetzt = clock.instant();
    return AngebotAnsicht.of(
        angebote.save(entwurf(firmaId, ansprechpartnerId, heute, jetzt)), clock);
  }

  private void pruefe(final long firmaId, final @Nullable Long ansprechpartnerId) {
    if (ansprechpartnerId == null) {
      return;
    }
    final Ansprechpartner person =
        personen.findById(ansprechpartnerId).orElseThrow(AnsprechpartnerNichtWaehlbar::new);
    if (person.firmaId() != firmaId || !person.aktiv()) {
      throw new AnsprechpartnerNichtWaehlbar();
    }
  }

  /*
   * Der frische Entwurf: ohne Nummer, ohne Dokument, ohne Anschriftskopien — die entstehen erst
   * beim Versenden (Kriterium 6). Die Leistungsbeschreibung bleibt leer, die Zahlungsbedingungen
   * kommen aus der Selbstauskunft.
   */
  private Angebot entwurf(
      final long firmaId,
      final @Nullable Long ansprechpartnerId,
      final LocalDate heute,
      final Instant jetzt) {
    return new Angebot(
        null,
        firmaId,
        ansprechpartnerId,
        null,
        Angebotszustand.ENTWURF,
        heute,
        heute.plusDays(GUELTIGKEIT_IN_TAGEN),
        null,
        eigeneAngaben.lies().zahlungsbedingungen(),
        null,
        null,
        null,
        null,
        null,
        List.of(),
        jetzt,
        jetzt);
  }
}
