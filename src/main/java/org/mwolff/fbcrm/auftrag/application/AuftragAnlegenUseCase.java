package org.mwolff.fbcrm.auftrag.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
import org.mwolff.fbcrm.auftrag.domain.Auftrag;
import org.mwolff.fbcrm.auftrag.domain.AuftragRepository;
import org.mwolff.fbcrm.auftrag.domain.AuftragsnummerRepository;
import org.mwolff.fbcrm.auftrag.domain.Auftragsposition;
import org.mwolff.fbcrm.auftrag.domain.Auftragsstatus;
import org.mwolff.fbcrm.common.Geschaeftszone;
import org.mwolff.fbcrm.vorgang.application.EreignisVermerkenUseCase;
import org.mwolff.fbcrm.vorgang.application.VorgangNichtGefunden;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;
import org.mwolff.fbcrm.vorgang.domain.VorgangRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Anlegen eines Auftrags aus einem angenommenen Angebot (Kriterien 1 bis 6, 8, 11; F2, F9).
 *
 * <p><b>Vier Vorbedingungen, und ihre Reihenfolge ist Teil der Zusage.</b> Geprueft wird von der
 * groebsten Lage zur feinsten: Gibt es das Angebot, ist es angenommen (Kriterium 1), ist der
 * Vorgang offen (Kriterium 11), gibt es noch keinen Auftrag (F9)? Erst danach die Positionen. So
 * erfaehrt der Anwender zuerst, was seine Wahl ueberhaupt gegenstandslos macht, statt Feldmeldungen
 * zu einem Angebot zu lesen, aus dem gar kein Auftrag entsteht.
 *
 * <p><b>Der Preis kommt aus dem Angebot und nie aus der Anfrage</b> (Plan E7, Kriterium 2): Die
 * Wahl traegt je Position nur Platz, Menge und „Stunden je Personentag"; Bezeichnung,
 * Abrechnungsmodus, Einheit und Einzelpreis liest dieser Anwendungsfall aus der Quelle. Einer
 * Anfrage den Preis zu glauben, hiesse, genau die Angabe vom Absender zu nehmen, die Kriterium 2
 * schuetzt — Sicherheit vor Bequemlichkeit.
 *
 * <p><b>Alles in einer Transaktion.</b> Nummer ziehen, Auftrag schreiben und das Ereignis vermerken
 * gehoeren zusammen (Kriterium 8, Plan E9): Der Zug der Nummer sperrt die Jahreszeile bis zum Ende
 * dieser Arbeit, und ein Ruecklauf gibt sie wieder frei. Eine abgewiesene Uebernahme verbraucht
 * darum keine Nummer.
 */
@Service
@Transactional
public class AuftragAnlegenUseCase {

  private final AuftragRepository auftraege;
  private final AuftragsnummerRepository nummern;
  private final AngebotRepository angebote;
  private final VorgangRepository vorgaenge;
  private final EreignisVermerkenUseCase ereignisse;
  private final Clock clock;

  public AuftragAnlegenUseCase(
      final AuftragRepository auftraege,
      final AuftragsnummerRepository nummern,
      final AngebotRepository angebote,
      final VorgangRepository vorgaenge,
      final EreignisVermerkenUseCase ereignisse,
      final Clock clock) {
    this.auftraege = auftraege;
    this.nummern = nummern;
    this.angebote = angebote;
    this.vorgaenge = vorgaenge;
    this.ereignisse = ereignisse;
    this.clock = clock;
  }

  /**
   * Legt aus dem Angebot einen Auftrag an.
   *
   * @param angebotId Kennung des Angebots, aus dem der Auftrag entsteht
   * @param daten die Angaben des Anwenders samt seiner Positionswahl
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   * @throws AngebotNichtWaehlbar wenn das Angebot nicht angenommen ist
   * @throws VorgangAbgeschlossen wenn der Vorgang abgeschlossen ist
   * @throws AuftragBereitsVorhanden wenn zu diesem Angebot schon ein Auftrag gehoert
   * @throws AuftragsuebernahmeUngueltig wenn die Positionswahl nicht zum Angebot passt
   */
  public AuftragAnsicht anlegen(final long angebotId, final AuftragDaten daten) {
    final Angebot angebot = angebote.findById(angebotId).orElseThrow(AngebotNichtGefunden::new);
    if (angebot.zustand() != Angebotszustand.ANGENOMMEN) {
      throw new AngebotNichtWaehlbar();
    }
    final Vorgang vorgang =
        vorgaenge.findById(angebot.vorgangId()).orElseThrow(VorgangNichtGefunden::new);
    if (vorgang.abgeschlossen()) {
      throw new VorgangAbgeschlossen();
    }
    if (auftraege.findByAngebot(angebotId).isPresent()) {
      throw new AuftragBereitsVorhanden();
    }
    final List<Auftragsposition> positionen = Positionsuebernahme.aus(angebot, daten.positionen());
    final LocalDate heute = LocalDate.now(clock.withZone(Geschaeftszone.ZONE));
    final String nummer = nummern.zieheNummer(heute.getYear());
    final Instant jetzt = clock.instant();
    final Auftrag angelegt =
        auftraege.save(
            new Auftrag(
                null,
                angebot.vorgangId(),
                angebotId,
                nummer,
                Auftragsstatus.OFFEN,
                Objects.requireNonNullElse(daten.auftragDatum(), heute),
                daten.kundenbestellnummer(),
                daten.leistungAb(),
                daten.leistungBis(),
                positionen,
                jetzt,
                jetzt));
    ereignisse.vermerken(angebot.vorgangId(), "Auftrag %s angelegt".formatted(nummer));
    return new AuftragAnsicht(angelegt, angebot.nummer());
  }
}
