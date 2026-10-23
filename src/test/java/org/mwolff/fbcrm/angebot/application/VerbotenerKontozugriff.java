package org.mwolff.fbcrm.angebot.application;

import org.mwolff.fbcrm.auth.application.GetCurrentAccountUseCase;

/**
 * Der Verstoss, den {@code ArchitectureTest} verbieten soll — eine Klasse in {@code angebot}, die
 * an {@code auth.domain} vorbei in {@code auth} greift (Issue #420; Plan #414, A7).
 *
 * <p><b>Diese Klasse ist absichtlich falsch gebaut</b> und steht nur im Testbaum: Die Regel {@code
 * angebotModule_thenReachesAuthOnlyThroughItsDomain} laeuft einmal gegen den Produktionscode, wo
 * sie nichts findet, und einmal gegen genau diese Klasse, wo sie anschlagen muss — nach dem Muster
 * von {@link VerbotenerZeitzugriff}.
 *
 * <p>Der Produktionsweg ist der Port {@code AccountRepository} in {@code auth.domain}: Der
 * Anwendungsfall der Kommentare holt die Namen dort, statt einen Anwendungsfall von {@code auth} zu
 * rufen.
 */
public final class VerbotenerKontozugriff {

  private final GetCurrentAccountUseCase konten;

  /**
   * @param konten der Anwendungsfall, den dieses Modul gar nicht kennen darf
   */
  public VerbotenerKontozugriff(final GetCurrentAccountUseCase konten) {
    this.konten = konten;
  }

  /** Greift ueber die Modulgrenze an der Domaene vorbei — genau das soll die Regel finden. */
  public String name(final long kontoId) {
    return konten.byId(kontoId).displayName();
  }
}
