package org.mwolff.fbcrm.angebot.infrastructure;

import org.mwolff.fbcrm.angebot.domain.Angebotsnummer;
import org.mwolff.fbcrm.angebot.domain.BelegnummerRepository;
import org.springframework.stereotype.Repository;

/**
 * Setzt den Port {@link BelegnummerRepository} auf JPA um.
 *
 * <p>Ohne eigene Transaktionsgrenze — und zwar absichtlich: Der Zug laeuft in der Transaktion des
 * Aufrufers, damit die Sperre auf der Jahreszeile bis zum Ende <b>dessen</b> Arbeit haelt und ein
 * Ruecklauf die Nummer wieder freigibt (E6). Ein eigenes {@code @Transactional} schloesse die
 * Sperre hier und gaebe die Nummer heraus, bevor das Angebot dazu geschrieben ist.
 */
@Repository
class JpaBelegnummerRepository implements BelegnummerRepository {

  private final SpringDataAngebotNummernkreisRepository jpa;

  JpaBelegnummerRepository(final SpringDataAngebotNummernkreisRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public String zieheNummer(final int jahr) {
    jpa.legeJahrAn(jahr);
    final AngebotNummernkreisEntity zaehler = jpa.sperreUndLies(jahr);
    final long gezogen = zaehler.getNaechste();
    zaehler.setNaechste(gezogen + 1);
    // Das Fortschreiben steht ausdruecklich da, statt sich auf das Schreiben beim Abschluss der
    // Transaktion zu verlassen: Ein zweiter Zug in derselben Transaktion soll die neue Zahl sehen.
    jpa.save(zaehler);
    return Angebotsnummer.formatiere(jahr, gezogen);
  }
}
