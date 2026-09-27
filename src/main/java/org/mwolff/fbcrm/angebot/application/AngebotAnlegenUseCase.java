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
import org.mwolff.fbcrm.vorgang.application.VorgangNichtGefunden;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;
import org.mwolff.fbcrm.vorgang.domain.VorgangRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Anlegen eines Angebots am Vorgang (Kriterien 2, 3, 8, 9).
 *
 * <p><b>Die Vorbelegung.</b> Das Angebotsdatum ist der heutige Tag in der Geschaeftszone und nicht
 * in UTC (E12) — zwischen 22:00 UTC und Mitternacht deutscher Zeit unterscheiden sich beide
 * Antworten um einen Tag. Die Gueltigkeit laeuft dreissig Tage, und die Zahlungsbedingungen kommen
 * aus „Eigene Angaben" (Kriterium 3, F8). Alle drei sind Vorschlaege: Der Entwurf ist danach frei
 * aenderbar.
 *
 * <p><b>Die Vorlage.</b> Wird eine Quelle genannt, kommen Leistungsbeschreibung,
 * Zahlungsbedingungen und Positionen von dort; Angebotsdatum und Gueltigkeit werden <b>nicht</b>
 * kopiert, sondern neu vorbelegt (E23) — ein nachverhandeltes Angebot von heute traegt sonst das
 * Datum von vorgestern. Eine Quelle an einem anderen Vorgang steht nicht zur Wahl.
 *
 * <p><b>Die Schreibsperre.</b> Am abgeschlossenen Vorgang entsteht kein Angebot (Kriterium 9, E13).
 * Geprueft wird vor allem anderen: Eine abgewiesene Anlage soll nicht einmal die Vorlage gelesen
 * haben.
 */
@Service
@Transactional
public class AngebotAnlegenUseCase {

  /** Die Frist der Vorbelegung: dreissig Tage ab dem Angebotsdatum (Kriterium 3). */
  private static final int GUELTIGKEIT_IN_TAGEN = 30;

  private final AngebotRepository angebote;
  private final VorgangRepository vorgaenge;
  private final EigeneAngabenRepository eigeneAngaben;
  private final Clock clock;

  public AngebotAnlegenUseCase(
      final AngebotRepository angebote,
      final VorgangRepository vorgaenge,
      final EigeneAngabenRepository eigeneAngaben,
      final Clock clock) {
    this.angebote = angebote;
    this.vorgaenge = vorgaenge;
    this.eigeneAngaben = eigeneAngaben;
    this.clock = clock;
  }

  /**
   * Legt am Vorgang einen Angebotsentwurf an.
   *
   * @param vorgangId Kennung des Vorgangs, an dem das Angebot entsteht
   * @param vorlageAngebotId Kennung des Angebots, dessen Texte und Positionen uebernommen werden,
   *     oder {@code null}
   * @throws VorgangNichtGefunden wenn es den Vorgang nicht gibt
   * @throws VorgangAbgeschlossen wenn der Vorgang abgeschlossen ist
   * @throws VorlageNichtWaehlbar wenn die Vorlage unbekannt ist oder an einem anderen Vorgang
   *     haengt
   */
  public AngebotAnsicht anlegen(final long vorgangId, final @Nullable Long vorlageAngebotId) {
    final Vorgang vorgang = vorgaenge.findById(vorgangId).orElseThrow(VorgangNichtGefunden::new);
    if (vorgang.abgeschlossen()) {
      throw new VorgangAbgeschlossen();
    }
    final Angebot vorlage = vorlage(vorgangId, vorlageAngebotId);
    final LocalDate heute = AngebotAnsicht.heute(clock);
    final Instant jetzt = clock.instant();
    return AngebotAnsicht.of(angebote.save(entwurf(vorgangId, vorlage, heute, jetzt)), clock);
  }

  private @Nullable Angebot vorlage(final long vorgangId, final @Nullable Long vorlageAngebotId) {
    if (vorlageAngebotId == null) {
      return null;
    }
    final Angebot quelle =
        angebote.findById(vorlageAngebotId).orElseThrow(VorlageNichtWaehlbar::new);
    if (quelle.vorgangId() != vorgangId) {
      throw new VorlageNichtWaehlbar();
    }
    return quelle;
  }

  /*
   * Der frische Entwurf: ohne Nummer, ohne Dokument, ohne Anschriftskopien — die entstehen erst
   * beim Versenden (Kriterium 6). Ohne Vorlage bleibt die Leistungsbeschreibung leer, und die
   * Zahlungsbedingungen kommen aus der Selbstauskunft; mit Vorlage kommen beide von dort.
   */
  private Angebot entwurf(
      final long vorgangId,
      final @Nullable Angebot vorlage,
      final LocalDate heute,
      final Instant jetzt) {
    return new Angebot(
        null,
        vorgangId,
        null,
        Angebotszustand.ENTWURF,
        heute,
        heute.plusDays(GUELTIGKEIT_IN_TAGEN),
        vorlage == null ? null : vorlage.leistungsbeschreibung(),
        vorlage == null
            ? eigeneAngaben.lies().zahlungsbedingungen()
            : vorlage.zahlungsbedingungen(),
        null,
        null,
        null,
        null,
        null,
        vorlage == null ? List.of() : vorlage.positionen(),
        jetzt,
        jetzt);
  }
}
