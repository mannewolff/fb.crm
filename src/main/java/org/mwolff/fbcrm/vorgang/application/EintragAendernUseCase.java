package org.mwolff.fbcrm.vorgang.application;

import java.time.Clock;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.vorgang.domain.Eintrag;
import org.mwolff.fbcrm.vorgang.domain.EintragRepository;
import org.mwolff.fbcrm.vorgang.domain.Zeitpunktgrenze;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Aendern von Text und Zeitpunkt eines Eintrags (Kriterien 18, 19).
 *
 * <p>Die Datei bleibt unberuehrt. Ein Anhang laesst sich nicht austauschen — dafuer gibt es keinen
 * Weg, und es braucht ihn nicht: Wer eine andere Datei meint, haengt sie an und laesst die alte in
 * der Historie stehen, wo sie hingehoert.
 *
 * <p><b>Ein Zugriff auf den Bestand, nicht zwei.</b> Geprueft wird der Eintrag, nicht zusaetzlich
 * der Vorgang: Passt seine Vorgangskennung nicht zum Pfad, ist er unter dieser Adresse nicht
 * vorhanden — und das gilt auch dann, wenn es den Vorgang im Pfad gar nicht gibt. Eine zweite
 * Abfrage brächte dieselbe Antwort und eine Abfrage mehr.
 */
@Service
@Transactional
public class EintragAendernUseCase {

  private final EintragRepository eintraege;
  private final Clock clock;

  public EintragAendernUseCase(final EintragRepository eintraege, final Clock clock) {
    this.eintraege = eintraege;
    this.clock = clock;
  }

  /**
   * Schreibt Text und Zeitpunkt fort und vermerkt die Aenderung (Kriterium 19).
   *
   * @param vorgangId Kennung des Vorgangs aus dem Pfad
   * @param eintragId Kennung des Eintrags
   * @param text der neue Text; beim Kommentar Pflicht
   * @param geschehenAm der neue Zeitpunkt des Geschehens
   * @throws ZeitpunktInDerZukunft wenn der Zeitpunkt ueber der Toleranz aus E15 liegt
   * @throws EintragNichtGefunden wenn es unter dieser Adresse keinen Eintrag gibt
   * @throws IllegalArgumentException wenn der Eintrag ein Kommentar ist und der Text leer bleibt
   */
  public void aendern(
      final long vorgangId,
      final long eintragId,
      final @Nullable String text,
      final Instant geschehenAm) {
    if (Zeitpunktgrenze.inDerZukunft(geschehenAm, clock.instant())) {
      throw new ZeitpunktInDerZukunft();
    }
    final Eintrag vorhanden =
        eintraege
            .findById(eintragId)
            .filter(eintrag -> eintrag.vorgangId() == vorgangId)
            .orElseThrow(EintragNichtGefunden::new);
    eintraege.save(vorhanden.geaendert(text, geschehenAm, clock.instant()));
  }
}
