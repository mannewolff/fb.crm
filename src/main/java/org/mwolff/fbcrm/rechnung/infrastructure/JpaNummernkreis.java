package org.mwolff.fbcrm.rechnung.infrastructure;

import org.mwolff.fbcrm.rechnung.domain.Nummernkreis;
import org.springframework.stereotype.Repository;

/**
 * Setzt den Port {@link Nummernkreis} auf JPA um.
 *
 * <p>Ohne eigene Transaktionsgrenze — und zwar absichtlich: Der Zug laeuft in der Transaktion des
 * Aufrufers, damit die Sperre auf der Jahreszeile bis zum Ende <b>dessen</b> Arbeit haelt und ein
 * Ruecklauf die Nummer wieder freigibt. Ein eigenes {@code @Transactional} schloesse die Sperre
 * hier und gaebe die Nummer heraus, bevor die Rechnung dazu geschrieben ist.
 */
@Repository
class JpaNummernkreis implements Nummernkreis {

  /** Der Stand eines Zaehlerjahrs, aus dem noch keine Nummer gezogen wurde. */
  private static final int VOR_DER_ERSTEN = 1;

  private final SpringDataRechnungNummernkreisRepository jpa;

  JpaNummernkreis(final SpringDataRechnungNummernkreisRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public int lies(final int zaehlerjahr) {
    return jpa.findById(zaehlerjahr)
        .map(RechnungNummernkreisEntity::getNaechsteNummer)
        .orElse(VOR_DER_ERSTEN);
  }

  @Override
  public void setze(final int zaehlerjahr, final int naechsteNummer) {
    jpa.setzeZaehler(zaehlerjahr, naechsteNummer);
  }

  @Override
  public int ziehe(final int zaehlerjahr) {
    // Erst anlegen, dann sperren: Ein FOR UPDATE auf eine fehlende Zeile sperrt nichts.
    jpa.legeJahrAn(zaehlerjahr);
    final RechnungNummernkreisEntity zaehler = jpa.sperreUndLies(zaehlerjahr);
    final int gezogen = zaehler.getNaechsteNummer();
    zaehler.setNaechsteNummer(gezogen + 1);
    // Das Fortschreiben steht ausdruecklich da, statt sich auf das Schreiben beim Abschluss der
    // Transaktion zu verlassen: Ein zweiter Zug in derselben Transaktion soll die neue Zahl sehen.
    jpa.save(zaehler);
    return gezogen;
  }
}
