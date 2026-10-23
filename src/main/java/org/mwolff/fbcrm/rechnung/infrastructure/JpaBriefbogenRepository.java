package org.mwolff.fbcrm.rechnung.infrastructure;

import java.time.Instant;
import java.util.NoSuchElementException;
import org.mwolff.fbcrm.rechnung.domain.Briefbogen;
import org.mwolff.fbcrm.rechnung.domain.BriefbogenRepository;
import org.mwolff.fbcrm.rechnung.domain.Zeilenstand;
import org.springframework.stereotype.Repository;

/**
 * Setzt den Port {@link BriefbogenRepository} auf JPA um; die Abbildung in beide Richtungen traegt
 * {@link BriefbogenEntity}.
 *
 * <p>Die Zeile ist da — {@code V26__briefbogen.sql} legt sie an. Fehlt sie doch, ist das kein
 * Fachfall, sondern ein kaputtes Schema; deshalb fliegt hier eine {@link NoSuchElementException}
 * und keine fachliche Ausnahme.
 *
 * <p>Geschrieben wird mit {@code saveAndFlush}: Erst das Schreiben erhoeht die Version, und der
 * neue Stand geht mit der Antwort hinaus (Plan #415, E2).
 */
@Repository
class JpaBriefbogenRepository implements BriefbogenRepository {

  private final SpringDataBriefbogenRepository jpa;

  JpaBriefbogenRepository(final SpringDataBriefbogenRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Briefbogen lies() {
    return jpa.findById(BriefbogenEntity.ZEILE).orElseThrow().alsBriefbogen();
  }

  @Override
  public Zeilenstand stand() {
    return standVon(jpa.findById(BriefbogenEntity.ZEILE).orElseThrow());
  }

  @Override
  public Zeilenstand speichere(
      final Briefbogen briefbogen,
      final Instant geaendertAm,
      final long version,
      final long konto) {
    return standVon(
        jpa.saveAndFlush(BriefbogenEntity.aus(briefbogen, geaendertAm, version, konto)));
  }

  private static Zeilenstand standVon(final BriefbogenEntity zeile) {
    return new Zeilenstand(zeile.getVersion(), zeile.getGeaendertVon(), zeile.getUpdatedAt());
  }
}
