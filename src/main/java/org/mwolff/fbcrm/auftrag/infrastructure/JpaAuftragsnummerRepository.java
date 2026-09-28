package org.mwolff.fbcrm.auftrag.infrastructure;

import org.mwolff.fbcrm.auftrag.domain.Auftragsnummer;
import org.mwolff.fbcrm.auftrag.domain.AuftragsnummerRepository;
import org.springframework.stereotype.Repository;

/**
 * Setzt den Port {@link AuftragsnummerRepository} auf JPA um.
 *
 * <p>Ohne eigene Transaktionsgrenze — und zwar absichtlich, wie bei {@code
 * JpaBelegnummerRepository} des Angebots: Der Zug laeuft in der Transaktion des Aufrufers, damit
 * die Sperre auf der Jahreszeile bis zum Ende <b>dessen</b> Arbeit haelt und ein Ruecklauf die
 * Nummer wieder freigibt (E6). Ein eigenes {@code @Transactional} schloesse die Sperre hier und
 * gaebe die Nummer heraus, bevor der Auftrag dazu geschrieben ist.
 */
@Repository
class JpaAuftragsnummerRepository implements AuftragsnummerRepository {

  private final SpringDataAuftragNummernkreisRepository jpa;

  JpaAuftragsnummerRepository(final SpringDataAuftragNummernkreisRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public String zieheNummer(final int jahr) {
    jpa.legeJahrAn(jahr);
    final AuftragNummernkreisEntity zaehler = jpa.sperreUndLies(jahr);
    final long gezogen = zaehler.getNaechste();
    zaehler.setNaechste(gezogen + 1);
    // Das Fortschreiben steht ausdruecklich da, statt sich auf das Schreiben beim Abschluss der
    // Transaktion zu verlassen: Ein zweiter Zug in derselben Transaktion soll die neue Zahl sehen.
    jpa.save(zaehler);
    return Auftragsnummer.formatiere(jahr, gezogen);
  }
}
