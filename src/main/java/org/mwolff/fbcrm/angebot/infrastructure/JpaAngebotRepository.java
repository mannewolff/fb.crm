package org.mwolff.fbcrm.angebot.infrastructure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Belegabsender;
import org.mwolff.fbcrm.angebot.domain.Belegempfaenger;
import org.mwolff.fbcrm.common.Anschrift;
import org.springframework.stereotype.Repository;

/**
 * Setzt den Port {@link AngebotRepository} auf JPA um und uebersetzt in beide Richtungen.
 *
 * <p>Das Angebot ist mit seinen Positionen eine Einheit. Geschrieben wird es darum als Ganzes: Die
 * alten Positionszeilen fallen weg, und die neuen entstehen mit den Plaetzen 1 bis n in der
 * Reihenfolge der Liste (E24). Gelesen wird in derselben Ordnung zurueck — damit ist die
 * Reihenfolge eine Zusage des Bestands und nicht der Zufall der Einfuegereihenfolge. Geloescht wird
 * nach derselben Regel: erst die Positionen, dann die Zeile, die sie traegt.
 *
 * <p>Jede Liste holt ihre Positionen in <b>einer</b> zweiten Abfrage und ordnet sie danach den
 * Angeboten zu; je Zeile einzeln nachzuladen waere die bekannte Abfrage-Lawine. Die Angebotsliste
 * eines Vorgangs geht diesen Weg.
 *
 * <p>PMD.TooManyMethods: Fuenf Wege des Ports und die Uebersetzungsschritte dazu. Die privaten
 * Methoden sind die Abbildung einer Zeile in ihre Teile — Angebot, Position, Empfaengerkopie,
 * Absenderkopie —, und sie aufzuteilen zerschnitte die Uebersetzung <b>eines</b> Aggregats auf zwei
 * Klassen, die nur zusammen richtig sind.
 */
@SuppressWarnings("PMD.TooManyMethods")
@Repository
class JpaAngebotRepository implements AngebotRepository {

  private final SpringDataAngebotRepository angebote;
  private final SpringDataAngebotPositionRepository positionen;

  JpaAngebotRepository(
      final SpringDataAngebotRepository angebote,
      final SpringDataAngebotPositionRepository positionen) {
    this.angebote = angebote;
    this.positionen = positionen;
  }

  @Override
  public Optional<Angebot> findById(final long id) {
    return angebote.findById(id).map(zeile -> toDomain(zeile, positionen.findByAngebot(id)));
  }

  @Override
  public List<Angebot> findByVorgang(final long vorgangId) {
    return mitPositionen(angebote.findByVorgang(vorgangId));
  }

  /*
   * Die Positionen mehrerer Angebote kommen in EINER zweiten Abfrage und werden danach zugeordnet;
   * je Zeile nachzuladen waere die bekannte Abfrage-Lawine. Ohne sie liesse sich die Summe nicht
   * rechnen (E5).
   */
  private List<Angebot> mitPositionen(final List<AngebotEntity> zeilen) {
    if (zeilen.isEmpty()) {
      // Ohne diesen Zweig liefe eine Abfrage mit leerer IN-Liste los — kein gueltiges SQL.
      return List.of();
    }
    final Map<Long, List<AngebotPositionEntity>> jeAngebot =
        positionen
            .findByAngebote(
                zeilen.stream().map(zeile -> Objects.requireNonNull(zeile.getId())).toList())
            .stream()
            .collect(Collectors.groupingBy(AngebotPositionEntity::getAngebotId));
    return zeilen.stream()
        .map(
            zeile ->
                toDomain(
                    zeile,
                    jeAngebot.getOrDefault(Objects.requireNonNull(zeile.getId()), List.of())))
        .toList();
  }

  @Override
  public Set<Long> vorgaengeMitFestgeschriebenemAngebot(final Collection<Long> vorgangIds) {
    return Set.copyOf(angebote.vorgaengeMitFestgeschriebenemAngebot(vorgangIds));
  }

  @Override
  public void loesche(final long id) {
    // Erst die Positionen: Der Fremdschluessel angebot_position.angebot_id traegt kein ON DELETE,
    // und das soll er auch nicht — eine Zeile verschwindet nur, wenn jemand es ausdruecklich sagt.
    positionen.loescheZuAngebot(id);
    angebote.deleteById(id);
  }

  @Override
  public Angebot save(final Angebot angebot) {
    final AngebotEntity zeile = angebote.save(toEntity(angebot));
    final long angebotId = Objects.requireNonNull(zeile.getId());
    positionen.loescheZuAngebot(angebotId);
    return toDomain(zeile, positionen.saveAll(positionszeilen(angebotId, angebot.positionen())));
  }

  private static List<AngebotPositionEntity> positionszeilen(
      final long angebotId, final List<Angebotsposition> positionen) {
    final List<AngebotPositionEntity> zeilen = new ArrayList<>(positionen.size());
    short platz = 1;
    for (final Angebotsposition position : positionen) {
      zeilen.add(
          new AngebotPositionEntity(
              null,
              angebotId,
              platz,
              position.bezeichnung(),
              position.abrechnungsmodus(),
              position.menge(),
              position.einheit(),
              position.einzelpreis()));
      platz++;
    }
    return zeilen;
  }

  private static Angebot toDomain(
      final AngebotEntity zeile, final List<AngebotPositionEntity> positionszeilen) {
    return new Angebot(
        zeile.getId(),
        zeile.getVorgangId(),
        zeile.getNummer(),
        zeile.getZustand(),
        zeile.getAngebotDatum(),
        zeile.getGueltigBis(),
        zeile.getLeistungsbeschreibung(),
        zeile.getZahlungsbedingungen(),
        zeile.getVersendetAm(),
        zeile.getReaktionAm(),
        zeile.getPdfSchluessel(),
        empfaenger(zeile),
        absender(zeile),
        positionszeilen.stream().map(JpaAngebotRepository::toDomain).toList(),
        zeile.getCreatedAt(),
        zeile.getUpdatedAt());
  }

  private static Angebotsposition toDomain(final AngebotPositionEntity zeile) {
    return new Angebotsposition(
        zeile.getBezeichnung(),
        zeile.getAbrechnungsmodus(),
        zeile.getMenge(),
        zeile.getEinheit(),
        zeile.getEinzelpreis());
  }

  /*
   * Der Firmenname traegt die Entscheidung: Er ist die einzige Pflichtangabe der Kopie, und der
   * Check der Migration laesst ihn in keinem festgeschriebenen Zustand fehlen. Steht er leer, hat
   * das Angebot keine Kopie — es ist ein Entwurf.
   */
  private static @Nullable Belegempfaenger empfaenger(final AngebotEntity zeile) {
    final String firma = zeile.getEmpfaengerFirma();
    if (firma == null) {
      return null;
    }
    return new Belegempfaenger(
        firma,
        new Anschrift(
            zeile.getEmpfaengerStrasse(),
            zeile.getEmpfaengerPlz(),
            zeile.getEmpfaengerOrt(),
            zeile.getEmpfaengerLand()),
        zeile.getEmpfaengerAnsprechpartner());
  }

  /* Dasselbe fuer die eigene Seite: der eigene Name ist ihre Pflichtangabe. */
  private static @Nullable Belegabsender absender(final AngebotEntity zeile) {
    final String name = zeile.getAbsenderName();
    if (name == null) {
      return null;
    }
    return new Belegabsender(
        name,
        new Anschrift(
            zeile.getAbsenderStrasse(),
            zeile.getAbsenderPlz(),
            zeile.getAbsenderOrt(),
            zeile.getAbsenderLand()),
        zeile.getAbsenderEmail(),
        zeile.getAbsenderTelefon(),
        zeile.getAbsenderSteuernummer(),
        zeile.getAbsenderUmsatzsteuerId(),
        zeile.getAbsenderBankverbindung());
  }

  private static AngebotEntity toEntity(final Angebot angebot) {
    final AngebotEntity zeile =
        new AngebotEntity(
            angebot.id(),
            angebot.vorgangId(),
            angebot.nummer(),
            angebot.zustand(),
            angebot.angebotDatum(),
            angebot.gueltigBis(),
            angebot.leistungsbeschreibung(),
            angebot.zahlungsbedingungen(),
            angebot.versendetAm(),
            angebot.reaktionAm(),
            angebot.pdfSchluessel(),
            angebot.createdAt(),
            angebot.updatedAt());
    final Belegempfaenger empfaenger = angebot.empfaenger();
    if (empfaenger != null) {
      zeile.setzeEmpfaenger(empfaenger);
    }
    final Belegabsender absender = angebot.absender();
    if (absender != null) {
      zeile.setzeAbsender(absender);
    }
    return zeile;
  }
}
