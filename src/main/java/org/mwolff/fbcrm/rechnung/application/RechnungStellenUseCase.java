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
import org.mwolff.fbcrm.rechnung.domain.Briefbogen;
import org.mwolff.fbcrm.rechnung.domain.BriefbogenRepository;
import org.mwolff.fbcrm.rechnung.domain.Briefpapierblatt;
import org.mwolff.fbcrm.rechnung.domain.Briefpapierspeicher;
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
 *   <li><b>Zuerst der Stand des Entwurfs</b> (#411, Kriterium 5; Plan #415, E10). Unmittelbar nach
 *       dem Lesen und vor jeder fachlichen Pruefung: Wer auf einem alten Stand stellt, bekommt
 *       {@link RechnungGeaendert} — es wird nie ein Entwurf festgeschrieben, den der Handelnde
 *       nicht gesehen hat. Es zaehlt allein der Entwurf: Einstellungen, Eigene Angaben, Briefbogen
 *       und das Angebot gelten, wie sie im Augenblick des Stellens sind, und weisen nicht ab.
 *   <li><b>Abgewiesen wird vor dem Zug aus dem Nummernkreis.</b> Ein veralteter Stand, eine
 *       unbekannte Rechnung, eine schon gestellte, ein Entwurf ohne Position und fehlende
 *       Pflichtangaben — all das kostet keine Nummer, auch wenn ein Ruecklauf sie ohnehin wieder
 *       freigaebe.
 *   <li><b>Erst die gestellte Rechnung, dann ihr Dokument.</b> Entstuende das Dokument vorher,
 *       muesste die Nummer danach geschrieben werden, und ein fehlgeschlagener Druck risse eine
 *       Luecke in den Nummernkreis. So liegt im schlechten Fall ein Objekt ohne Rechnung im
 *       Speicher — eine Waise, die niemanden stoert (siehe {@code V18__rechnung.sql}). Erst zum
 *       Dokument wird der Briefbogen gelesen: Er wird nicht festgeschrieben, sondern wirkt im
 *       Augenblick des Drucks (Plan #363, E2). Das fertige Dokument erzeugt danach niemand neu —
 *       ein spaeter getauschtes Briefpapier beruehrt es nicht (fachliche Quelle #361, Kriterium
 *       10).
 *   <li><b>Der Sprung des Angebots zum Schluss</b>, gerechnet allein mit den <b>gestellten</b>
 *       Rechnungen: „Abgerechnet" heisst, dass die Rechnungen gestellt sind; ein Entwurf kann noch
 *       geloescht werden (Manne, 2026-09-30; Kriterium 27). Gestellt heisst dabei jeder Zustand
 *       ausser Entwurf — eine bezahlte Rechnung gibt ihre Mengen nicht wieder frei (Issue #253).
 *       Das Angebot wird dafuer <b>sperrend</b> gelesen: Zwischen Lesen und Fortschreiben schreibt
 *       es kein anderer, und sein Zaehler weist das Stellen nicht ab (Plan #415, E10).
 *       Fortgeschrieben traegt es das handelnde Konto als Urheber (E14).
 * </ol>
 *
 * <p>Scheitert ein Schritt, rollt die Transaktion zurueck — der Zaehler gibt seine Nummer wieder
 * frei, und der Entwurf ist unveraendert ein Entwurf.
 */
@Service
@Transactional
/*
 * PMD.CouplingBetweenObjects: Aus demselben Grund wie die Ausnahme am Konstruktor — das Stellen
 * ist der Schritt, an dem alle Angaben eines Belegs zusammenkommen. Mit dem Briefbogen (Plan #363,
 * E3) kennt die Klasse auch dessen Bestand, das hinterlegte Blatt und den Speicher seiner Bytes.
 * Den Satz samt Wahl des Papiers in eine eigene Klasse zu ziehen, verschoebe die Kopplung, ohne
 * sie zu loesen: Die Reihenfolge Festschreibung, Dokument, Angebot bleibt hier.
 */
@SuppressWarnings("PMD.CouplingBetweenObjects")
public class RechnungStellenUseCase {

  private final RechnungRepository rechnungen;
  private final AngebotRepository angebote;
  private final FirmaRepository firmen;
  private final EigeneAngabenRepository eigeneAngaben;
  private final RechnungseinstellungenRepository einstellungen;
  private final Nummernkreis nummernkreis;
  private final Rechnungsnummern rechnungsnummern;
  private final Belegdrucker drucker;
  private final DokumentSpeicher dokumente;
  private final BriefbogenRepository briefboegen;
  private final Briefpapierspeicher briefpapier;
  private final Clock clock;

  /*
   * PMD.ExcessiveParameterList: Zwoelf Mitspieler, und keiner davon ist zu entbehren: der Bestand
   * der Rechnungen, das Angebot und seine Firma fuer die Kopie des Empfaengers, die eigenen Angaben
   * fuer die des Absenders, die Einstellungen, der Nummernkreis und die Rechnungsnummern fuer die
   * Nummer (#254, Kriterium 5), Drucker und Speicher fuer das Dokument, das BriefbogenRepository
   * fuer Schalter, Raender und das hinterlegte Blatt und der Briefpapierspeicher fuer dessen Bytes
   * als Untergrund (Plan #363, E3) und die Uhr fuer den Zeitpunkt. Sie zu Gruppen zusammenzufassen
   * verschoebe die Zahl, ohne etwas zu klaeren: Das Stellen ist der Schritt, an dem alle Angaben
   * eines Belegs zusammenkommen. Die Ausnahme steht am Konstruktor, die Regel bleibt im Regelsatz
   * scharf.
   */
  @SuppressWarnings("PMD.ExcessiveParameterList")
  RechnungStellenUseCase(
      final RechnungRepository rechnungen,
      final AngebotRepository angebote,
      final FirmaRepository firmen,
      final EigeneAngabenRepository eigeneAngaben,
      final RechnungseinstellungenRepository einstellungen,
      final Nummernkreis nummernkreis,
      final Rechnungsnummern rechnungsnummern,
      final Belegdrucker drucker,
      final DokumentSpeicher dokumente,
      final BriefbogenRepository briefboegen,
      final Briefpapierspeicher briefpapier,
      final Clock clock) {
    this.rechnungen = rechnungen;
    this.angebote = angebote;
    this.firmen = firmen;
    this.eigeneAngaben = eigeneAngaben;
    this.einstellungen = einstellungen;
    this.nummernkreis = nummernkreis;
    this.rechnungsnummern = rechnungsnummern;
    this.drucker = drucker;
    this.dokumente = dokumente;
    this.briefboegen = briefboegen;
    this.briefpapier = briefpapier;
    this.clock = clock;
  }

  /**
   * Stellt den Entwurf und liefert die gestellte Rechnung samt dem Schluessel ihres Dokuments.
   *
   * @param rechnungId Kennung der Rechnung
   * @param version der vorgelegte Stand des Entwurfs aus {@code If-Match}
   * @param konto Kennung des handelnden Kontos
   * @throws RechnungNichtGefunden wenn es die Rechnung nicht gibt
   * @throws RechnungGeloescht wenn der Entwurf inzwischen geloescht ist (Plan #415, E16)
   * @throws RechnungGeaendert wenn der Entwurf inzwischen einen anderen Stand hat als den
   *     vorgelegten
   * @throws RechnungszustandPasstNicht wenn die Rechnung schon gestellt ist
   * @throws RechnungOhnePosition wenn der Entwurf keine Position traegt
   * @throws AngebotNichtGefunden wenn es das Angebot der Rechnung nicht gibt
   * @throws FirmaNichtGefunden wenn es die Firma des Angebots nicht gibt
   * @throws PflichtangabenFehlen wenn eine Angabe fehlt, die auf einem Beleg stehen muss
   * @throws RechnungsnummerSchonVergeben wenn die gezogene Nummer schon eine Rechnung traegt, auch
   *     eine nachgetragene
   */
  public Rechnung stelle(final long rechnungId, final long version, final long konto) {
    final Rechnung entwurf =
        rechnungen.findByIdMitSperre(rechnungId).orElseThrow(RechnungNichtGefunden::new);
    Rechnungsvergleich.pruefe(entwurf, version, konto);
    if (entwurf.zustand() != Rechnungszustand.ENTWURF) {
      throw new RechnungszustandPasstNicht();
    }
    if (entwurf.positionen().isEmpty()) {
      throw new RechnungOhnePosition();
    }
    final Angebot angebot =
        angebote.findByIdMitSperre(entwurf.angebotId()).orElseThrow(AngebotNichtGefunden::new);
    final Firma firma = firmen.findById(angebot.firmaId()).orElseThrow(FirmaNichtGefunden::new);
    final EigeneAngaben eigene = eigeneAngaben.lies();
    Belegpflichtangaben.pruefe(eigene, firma);

    final Rechnung festgeschrieben = schreibeFest(entwurf, eigene, firma, konto);
    final Rechnung gestellt = mitDokument(festgeschrieben);
    schliesseAngebotAbWennNichtsOffenIst(angebot, konto);
    return gestellt;
  }

  /*
   * Die Nummer wird gezogen, gegen beide Bestaende geprueft (#254, Kriterium 5) und mit allem
   * Festzuschreibenden in einem Zug geschrieben. Das Schreiben geht sofort in die Datenbank, damit
   * eine Verletzung von UNIQUE hier auffaellt und nicht erst beim Commit — dort waere sie ein
   * Serverfehler und kein 409.
   */
  private Rechnung schreibeFest(
      final Rechnung entwurf, final EigeneAngaben eigene, final Firma firma, final long konto) {
    final Rechnungseinstellungen satz = einstellungen.lies();
    final Nummernmuster muster = satz.nummerMuster();
    final int jahr = entwurf.rechnungDatum().getYear();
    final String nummer =
        muster.rechnungsnummer(nummernkreis.ziehe(muster.zaehlerjahr(jahr)), jahr);
    if (rechnungsnummern.vergeben(nummer)) {
      throw new RechnungsnummerSchonVergeben(nummer);
    }
    final Instant jetzt = clock.instant();
    try {
      return rechnungen.saveAndFlush(
          entwurf
              .gestellt(
                  nummer,
                  satz.steuersatz(),
                  satz.zahlungszielTage(),
                  empfaengerAus(firma),
                  absenderAus(eigene),
                  jetzt)
              .durch(konto));
    } catch (final DataIntegrityViolationException vergeben) {
      throw new RechnungsnummerSchonVergeben(nummer, vergeben);
    }
  }

  /**
   * Das Dokument entsteht aus der festgeschriebenen Rechnung und haengt sich an sie (Plan #363, E3,
   * E7, E18). Mit hinterlegtem Briefpapier setzt {@link Briefbogenlayout}, und das Briefpapier
   * liegt als Untergrund darunter; ohne setzt {@link Rechnungslayout} wie bisher, ohne Untergrund —
   * Schalter und Raender des Briefbogens wirken dann nicht (fachliche Quelle #361, Kriterium 11).
   */
  private Rechnung mitDokument(final Rechnung festgeschrieben) {
    final RechnungDruckdaten daten = Rechnungsbeleg.druckdaten(festgeschrieben);
    final Briefbogen bogen = briefboegen.lies();
    final Briefpapierblatt blatt = bogen.blatt();
    final byte[] pdf =
        blatt == null
            ? drucker.drucke(Rechnungslayout.setze(daten), null)
            : drucker.drucke(
                Briefbogenlayout.setze(daten, bogen), briefpapier.lies(blatt.schluessel()));
    return rechnungen.save(
        festgeschrieben.mitDokument(dokumente.lege(festgeschrieben.requireId(), pdf)));
  }

  /*
   * Gerechnet wird allein mit den gestellten Rechnungen des Angebots: Ein Entwurf, der den Rest
   * schon traegt, kann noch geloescht werden, und das Angebot waere zu frueh abgerechnet.
   *
   * Am stornierten Angebot unterbleibt der Sprung (Plan #332, E2): Solange es storniert ist, steht
   * die Statuskette still, und die Ruecknahme des Stornos fuehrt in den Status, aus dem storniert
   * wurde (#330, Kriterium 9). Eine Mengenpruefung gegen das Angebot gibt es beim Stellen weiterhin
   * nicht (E8) — ein vor dem Storno angelegter Entwurf wird gestellt, wie er ist.
   */
  private void schliesseAngebotAbWennNichtsOffenIst(final Angebot angebot, final long konto) {
    if (angebot.storniert()) {
      return;
    }
    final List<Rechnung> gestellte =
        rechnungen.findByAngebot(angebot.requireId()).stream()
            .filter(rechnung -> rechnung.zustand().istGestellt())
            .toList();
    if (!Abrechnungsstand.fuer(angebot.positionen(), gestellte).etwasOffen()) {
      angebote.save(angebot.abgerechnet(clock.instant()).durch(konto));
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
        // Bis zur Belegkopie mit Bankfeldern (#467) der bisherige Freitext.
        eigene.bisherigeBankverbindung(),
        eigene.webadresse());
  }
}
