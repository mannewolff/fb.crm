package org.mwolff.fbcrm.vorgang.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mwolff.fbcrm.vorgang.domain.Eintrag;
import org.mwolff.fbcrm.vorgang.domain.EintragRepository;
import org.springframework.stereotype.Repository;

/** Setzt den Port {@link EintragRepository} auf JPA um und uebersetzt in beide Richtungen. */
@Repository
class JpaEintragRepository implements EintragRepository {

  private final SpringDataEintragRepository jpa;

  JpaEintragRepository(final SpringDataEintragRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public List<Eintrag> findByVorgang(final long vorgangId) {
    return jpa.findByVorgang(vorgangId).stream().map(JpaEintragRepository::toDomain).toList();
  }

  @Override
  public Optional<Eintrag> findById(final long id) {
    return jpa.findById(id).map(JpaEintragRepository::toDomain);
  }

  @Override
  public Eintrag save(final Eintrag eintrag) {
    return toDomain(jpa.save(toEntity(eintrag)));
  }

  @Override
  public Map<Long, Instant> juengstesGeschehenJeVorgang(final Collection<Long> vorgangIds) {
    // Nur Vorgaenge mit Eintrag stehen am Ende in der Abbildung — der Port sagt genau das zu. Ein
    // Waechter fuer die leere Anfrage steht hier bewusst nicht, wie bei der Zaehlung der
    // Ansprechpartner: Er waere ein Zweig, den kein Test von seinem Gegenteil unterscheiden kann.
    final Map<Long, Instant> juengste = new LinkedHashMap<>();
    jpa.juengstesGeschehenJeVorgang(vorgangIds)
        .forEach(zeile -> juengste.put(zeile.getVorgangId(), zeile.getGeschehenAm()));
    return juengste;
  }

  private static Eintrag toDomain(final VorgangEintragEntity zeile) {
    return new Eintrag(
        zeile.getId(),
        zeile.getVorgangId(),
        zeile.getArt(),
        zeile.getText(),
        zeile.getGeschehenAm(),
        zeile.getHerkunft(),
        zeile.getDateiName(),
        zeile.getDateiGroesse(),
        zeile.getObjektSchluessel(),
        zeile.getCreatedAt(),
        zeile.getGeaendertAm());
  }

  private static VorgangEintragEntity toEntity(final Eintrag eintrag) {
    return new VorgangEintragEntity(
        eintrag.id(),
        eintrag.vorgangId(),
        eintrag.art(),
        eintrag.text(),
        eintrag.geschehenAm(),
        eintrag.herkunft(),
        eintrag.dateiName(),
        eintrag.dateiGroesse(),
        eintrag.objektSchluessel(),
        eintrag.createdAt(),
        eintrag.geaendertAm());
  }
}
