package org.mwolff.fbcrm.angebot.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.common.Geschaeftszone;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Anlegen eines Angebots an eine Firma (Issue #127).
 *
 * <p><b>Die Vorbelegung.</b> Das Angebotsdatum ist der heutige Tag in der Geschaeftszone und nicht
 * in UTC (E12) — zwischen 22:00 UTC und Mitternacht deutscher Zeit unterscheiden sich beide
 * Antworten um einen Tag. Der Status ist {@link Angebotsstatus#ANGELEGT}, bei interner Arbeit
 * {@link Angebotsstatus#LAEUFT} (Issue #226); Beschreibung und Positionen bekommt das Angebot
 * danach ueber das Aendern.
 *
 * <p><b>Die Wahl des Kunden.</b> Die Firma muss es geben und sie darf nicht stillgelegt sein — an
 * eine stillgelegte Firma geht kein neues Angebot. Der Ansprechpartner ist optional und geht durch
 * {@link Ansprechpartnerwahl}. Geprueft wird vor allem anderen: Eine abgewiesene Anlage schreibt
 * nichts.
 */
@Service
@Transactional
public class AngebotAnlegenUseCase {

  private final AngebotRepository angebote;
  private final FirmaRepository firmen;
  private final Ansprechpartnerwahl wahl;
  private final Clock clock;

  AngebotAnlegenUseCase(
      final AngebotRepository angebote,
      final FirmaRepository firmen,
      final Ansprechpartnerwahl wahl,
      final Clock clock) {
    this.angebote = angebote;
    this.firmen = firmen;
    this.wahl = wahl;
    this.clock = clock;
  }

  /**
   * Legt an eine Firma ein Angebot an.
   *
   * @param firmaId Kennung der Firma, an die das Angebot geht
   * @param ansprechpartnerId Kennung des Ansprechpartners bei dieser Firma, oder {@code null}
   * @param intern ob das Angebot die eigene interne Arbeit festhaelt
   * @throws FirmaNichtGefunden wenn es die Firma nicht gibt
   * @throws FirmaStillgelegt wenn die Firma stillgelegt ist
   * @throws AnsprechpartnerNichtWaehlbar wenn der Ansprechpartner nicht zur Wahl steht
   */
  public Angebot anlegen(
      final long firmaId, final @Nullable Long ansprechpartnerId, final boolean intern) {
    final Firma firma = firmen.findById(firmaId).orElseThrow(FirmaNichtGefunden::new);
    if (!firma.aktiv()) {
      throw new FirmaStillgelegt();
    }
    wahl.pruefe(firmaId, ansprechpartnerId, null);
    final Instant jetzt = clock.instant();
    final LocalDate heute = LocalDate.now(clock.withZone(Geschaeftszone.ZONE));
    return angebote.save(
        new Angebot(
            null,
            firmaId,
            ansprechpartnerId,
            intern,
            intern ? Angebotsstatus.LAEUFT : Angebotsstatus.ANGELEGT,
            heute,
            null,
            List.of(),
            jetzt,
            jetzt));
  }
}
