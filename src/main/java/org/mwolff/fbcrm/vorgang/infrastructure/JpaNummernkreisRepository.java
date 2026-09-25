package org.mwolff.fbcrm.vorgang.infrastructure;

import org.mwolff.fbcrm.vorgang.domain.NummernkreisRepository;
import org.springframework.stereotype.Repository;

/**
 * Setzt den Port {@link NummernkreisRepository} auf JPA um.
 *
 * <p>Ohne eigene Transaktionsgrenze — und zwar absichtlich: Der Zug laeuft in der Transaktion des
 * Aufrufers, damit die Sperre auf der Zaehlerzeile bis zum Ende <b>dessen</b> Arbeit haelt und ein
 * Ruecklauf die Nummer wieder freigibt (E3). Ein eigenes {@code @Transactional} schloesse die
 * Sperre hier und gaebe die Nummer heraus, bevor der Vorgang dazu geschrieben ist.
 */
@Repository
class JpaNummernkreisRepository implements NummernkreisRepository {

  private final SpringDataNummernkreisRepository jpa;

  JpaNummernkreisRepository(final SpringDataNummernkreisRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public long naechsteNummer() {
    final NummernkreisEntity zaehler = jpa.sperreUndLies();
    final long gezogen = zaehler.getNaechste();
    zaehler.setNaechste(gezogen + 1);
    // Das Fortschreiben steht ausdruecklich da, statt sich auf das Schreiben beim Abschluss der
    // Transaktion zu verlassen: Ein zweiter Zug in derselben Transaktion soll die neue Zahl sehen.
    jpa.save(zaehler);
    return gezogen;
  }
}
