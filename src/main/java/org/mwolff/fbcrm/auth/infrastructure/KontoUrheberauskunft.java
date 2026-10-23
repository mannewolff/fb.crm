package org.mwolff.fbcrm.auth.infrastructure;

import java.util.Optional;
import org.mwolff.fbcrm.common.Urheber;
import org.mwolff.fbcrm.common.Urheberauskunft;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Setzt den Port {@link Urheberauskunft} ueber die Tabelle {@code account} um (Plan #415, E6).
 *
 * <p>Gelesen wird nur der Anzeigename. Ein stillgelegtes Konto behaelt ihn: Wer vor dem Stilllegen
 * geaendert hat, steht weiter mit Namen in der Meldung.
 */
@Component
class KontoUrheberauskunft implements Urheberauskunft {

  private final SpringDataAccountRepository jpa;

  KontoUrheberauskunft(final SpringDataAccountRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Urheber> zu(final long konto) {
    return jpa.findById(konto).map(zeile -> new Urheber(konto, zeile.getDisplayName()));
  }
}
