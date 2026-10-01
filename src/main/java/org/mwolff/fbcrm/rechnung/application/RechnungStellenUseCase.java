package org.mwolff.fbcrm.rechnung.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngabenRepository;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.rechnung.domain.Abrechnungsstand;
import org.mwolff.fbcrm.rechnung.domain.Belegabsender;
import org.mwolff.fbcrm.rechnung.domain.Belegempfaenger;
import org.mwolff.fbcrm.rechnung.domain.DokumentSpeicher;
import org.mwolff.fbcrm.rechnung.domain.Nummernkreis;
import org.mwolff.fbcrm.rechnung.domain.Nummernmuster;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungseinstellungen;
import org.mwolff.fbcrm.rechnung.domain.RechnungseinstellungenRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Stellen einer Rechnung: Nummer, Festschreibung, Dokument (#160, Kriterien 13 bis 16, 18 und
 * 27; Plan #169, E7).
 *
 * <p>Der eine nicht umkehrbare Schritt dieses Moduls. Er laeuft <b>in einer Transaktion</b>, und
 * die Reihenfolge seiner Schritte ist nicht beliebig:
 *
 * <ol>
 *   <li><b>Die Rechnung wird mit Sperre auf ihrer Zeile gelesen.</b> Ohne sie laesen zwei
 *       gleichzeitige Aufrufe fuer denselben Entwurf beide {@code ENTWURF}, warteten nacheinander
 *       am Zaehler und schrieben beide — der zweite mit der naechsten Nummer ueber die Zeile des
 *       ersten. Eine Nummer waere verbraucht und verschwunden, ihr Dokument eine Waise.
 *   <li><b>Abgewiesen wird vor dem Zug aus dem Nummernkreis.</b> Eine unbekannte Rechnung, eine
 *       schon gestellte, ein Entwurf ohne Position und fehlende Pflichtangaben — all das kostet
 *       keine Nummer, auch wenn ein Ruecklauf sie ohnehin wieder freigaebe.
 *   <li><b>Erst die gestellte Rechnung, dann ihr Dokument.</b> Entstuende das Dokument vorher,
 *       muesste die Nummer danach geschrieben werden, und ein fehlgeschlagener Druck risse eine
 *       Luecke in den Nummernkreis. So liegt im schlechten Fall ein Objekt ohne Rechnung im
 *       Speicher — eine Waise, die niemanden stoert (siehe {@code V18__rechnung.sql}).
 *   <li><b>Der Sprung des Angebots zum Schluss</b>, gerechnet allein mit den <b>gestellten</b>
 *       Rechnungen: „Abgerechnet" heisst, dass die Rechnungen gestellt sind; ein Entwurf kann noch
 *       geloescht werden (Manne, 2026-09-30; Kriterium 27).
 * </ol>
 *
 * <p>Scheitert ein Schritt, rollt die Transaktion zurueck — der Zaehler gibt seine Nummer wieder
 * frei, und der Entwurf ist unveraendert ein Entwurf.
 */
@Service
@Transactional
public class RechnungStellenUseCase {

  private final RechnungRepository rechnungen;
  private final AngebotRepository angebote;
  private final FirmaRepository firmen;
  private final EigeneAngabenRepository eigeneAngaben;
  private final RechnungseinstellungenRepository einstellungen;
  private final Nummernkreis nummernkreis;
  private final Belegdrucker drucker;
  private final DokumentSpeicher dokumente;
  private final Clock clock;

  /*
   * Neun Mitspieler, und keiner davon ist zu entbehren: der Bestand der Rechnungen, das Angebot und
   * seine Firma fuer die Kopie des Empfaengers, die eigenen Angaben fuer die des Absenders, die
   * Einstellungen und der Nummernkreis fuer die Nummer, Drucker und Speicher fuer das Dokument und
   * die Uhr fuer den Zeitpunkt. Sie zu Gruppen zusammenzufassen verschoebe die Zahl, ohne etwas zu
   * klaeren: Das Stellen ist der Schritt, an dem alle Angaben eines Belegs zusammenkommen.
   */
  RechnungStellenUseCase(
      final RechnungRepository rechnungen,
      final AngebotRepository angebote,
      final FirmaRepository firmen,
      final EigeneAngabenRepository eigeneAngaben,
      final RechnungseinstellungenRepository einstellungen,
      final Nummernkreis nummernkreis,
      final Belegdrucker drucker,
      final DokumentSpeicher dokumente,
      final Clock clock) {
    this.rechnungen = rechnungen;
    this.angebote = angebote;
    this.firmen = firmen;
    this.eigeneAngaben = eigeneAngaben;
    this.einstellungen = einstellungen;
    this.nummernkreis = nummernkreis;
    this.drucker = drucker;
    this.dokumente = dokumente;
    this.clock = clock;
  }

  /**
   * Stellt den Entwurf und liefert die gestellte Rechnung samt dem Schluessel ihres Dokuments.
   *
   * @param rechnungId Kennung der Rechnung
   * @throws RechnungNichtGefunden wenn es die Rechnung nicht gibt
   * @throws RechnungszustandPasstNicht wenn die Rechnung schon gestellt ist
   * @throws RechnungOhnePosition wenn der Entwurf keine Position traegt
   * @throws AngebotNichtGefunden wenn es das Angebot der Rechnung nicht gibt
   * @throws FirmaNichtGefunden wenn es die Firma des Angebots nicht gibt
   * @throws PflichtangabenFehlen wenn eine Angabe fehlt, die auf einem Beleg stehen muss
   * @throws RechnungsnummerSchonVergeben wenn die gezogene Nummer schon eine Rechnung traegt
   */
  public Rechnung stelle(final long rechnungId) {
    final Rechnung entwurf =
        rechnungen.findByIdMitSperre(rechnungId).orElseThrow(RechnungNichtGefunden::new);
    if (entwurf.zustand() != Rechnungszustand.ENTWURF) {
      throw new RechnungszustandPasstNicht();
    }
    if (entwurf.positionen().isEmpty()) {
      throw new RechnungOhnePosition();
    }
    final Angebot angebot =
        angebote.findById(entwurf.angebotId()).orElseThrow(AngebotNichtGefunden::new);
    final Firma firma = firmen.findById(angebot.firmaId()).orElseThrow(FirmaNichtGefunden::new);
    final EigeneAngaben eigene = eigeneAngaben.lies();
    Belegpflichtangaben.pruefe(eigene, firma);

    final Rechnung festgeschrieben = schreibeFest(entwurf, eigene, firma);
    final Rechnung gestellt = mitDokument(festgeschrieben);
    schliesseAngebotAbWennNichtsOffenIst(angebot);
    return gestellt;
  }

  /*
   * Die Nummer wird gezogen, gegen den Bestand geprueft und mit allem Festzuschreibenden in einem
   * Zug geschrieben. Das Schreiben geht sofort in die Datenbank, damit eine Verletzung von UNIQUE
   * hier auffaellt und nicht erst beim Commit — dort waere sie ein Serverfehler und kein 409.
   */
  private Rechnung schreibeFest(
      final Rechnung entwurf, final EigeneAngaben eigene, final Firma firma) {
    final Rechnungseinstellungen satz = einstellungen.lies();
    final Nummernmuster muster = satz.nummerMuster();
    final int jahr = entwurf.rechnungDatum().getYear();
    final String nummer =
        muster.rechnungsnummer(nummernkreis.ziehe(muster.zaehlerjahr(jahr)), jahr);
    if (rechnungen.existiertNummer(nummer)) {
      throw new RechnungsnummerSchonVergeben(nummer);
    }
    final Instant jetzt = clock.instant();
    try {
      return rechnungen.saveAndFlush(
          entwurf.gestellt(
              nummer,
              satz.steuersatz(),
              satz.zahlungszielTage(),
              empfaengerAus(firma),
              absenderAus(eigene),
              jetzt));
    } catch (final DataIntegrityViolationException vergeben) {
      throw new RechnungsnummerSchonVergeben(nummer, vergeben);
    }
  }

  /** Das Dokument entsteht aus der festgeschriebenen Rechnung und haengt sich an sie. */
  private Rechnung mitDokument(final Rechnung festgeschrieben) {
    final byte[] pdf =
        drucker.drucke(Rechnungslayout.setze(Rechnungsbeleg.druckdaten(festgeschrieben)));
    return rechnungen.save(
        festgeschrieben.mitDokument(dokumente.lege(festgeschrieben.requireId(), pdf)));
  }

  /*
   * Gerechnet wird allein mit den gestellten Rechnungen des Angebots: Ein Entwurf, der den Rest
   * schon traegt, kann noch geloescht werden, und das Angebot waere zu frueh abgerechnet.
   */
  private void schliesseAngebotAbWennNichtsOffenIst(final Angebot angebot) {
    final List<Rechnung> gestellte =
        rechnungen.findByAngebot(angebot.requireId()).stream()
            .filter(rechnung -> rechnung.zustand() == Rechnungszustand.GESTELLT)
            .toList();
    if (!Abrechnungsstand.fuer(angebot.positionen(), gestellte).etwasOffen()) {
      angebote.save(angebot.abgerechnet(clock.instant()));
    }
  }

  private static Belegempfaenger empfaengerAus(final Firma firma) {
    return new Belegempfaenger(firma.name(), firma.anschrift(), null);
  }

  /*
   * Der Name ist an den eigenen Angaben @Nullable, weil sie nach und nach entstehen; auf einem
   * Beleg steht er fest. Dass er da ist, hat Belegpflichtangaben unmittelbar davor geprueft.
   */
  private static Belegabsender absenderAus(final EigeneAngaben eigene) {
    return new Belegabsender(
        Objects.requireNonNull(eigene.name()),
        eigene.berufsbezeichnung(),
        eigene.anschrift(),
        eigene.email(),
        eigene.telefon(),
        eigene.steuernummer(),
        eigene.umsatzsteuerId(),
        eigene.bankverbindung(),
        eigene.webadresse());
  }
}
