package org.mwolff.fbcrm.angebot.application;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.domain.Belegabsender;
import org.mwolff.fbcrm.angebot.domain.Belegempfaenger;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.Firma;

/**
 * Die Nachbarn eines Angebots, aus denen sein Beleg entsteht (R8, Kriterium 12).
 *
 * <p>Zwei Aufgaben haengen an diesen drei Angaben, und beide sollen dieselben sehen: Die
 * Versandpruefung fragt sie auf Vollstaendigkeit ({@link VersandVoraussetzungen}), und das
 * Festschreiben macht daraus die beiden Kopien. Lagen sie getrennt vor, koennte eine Pruefung eine
 * Firma sehen und die Kopie eine andere.
 *
 * <p>Die Kopien entstehen hier und nicht im Anwendungsfall: Sie sind eine reine Abbildung dieser
 * Angaben — kein Bestand, keine Uhr —, und der Anwendungsfall traegt danach nur noch die
 * Reihenfolge der Schritte.
 *
 * @param firma die Firma des Angebots
 * @param ansprechpartner der Ansprechpartner des Angebots, oder {@code null}
 * @param eigeneAngaben die Selbstauskunft der Instanz
 */
record Belegangaben(
    Firma firma, @Nullable Ansprechpartner ansprechpartner, EigeneAngaben eigeneAngaben) {

  /**
   * Die Empfaengerkopie: Name und Anschrift der Firma, dazu die Person, wenn es eine gibt.
   *
   * <p>Eine Kopie und kein Verweis (R8): Zieht die Firma spaeter um, zeigt das versendete Angebot
   * weiterhin die Anschrift, die der Kunde auf seinem Dokument gelesen hat.
   */
  Belegempfaenger empfaenger() {
    return new Belegempfaenger(firma.name(), firma.anschrift(), person());
  }

  /**
   * Die Absenderkopie, aus demselben Grund: Eine neue Bankverbindung aendert kein altes Angebot.
   */
  Belegabsender absender() {
    // Der Name ist Pflicht, sobald ein Beleg hinausgeht; dass er steht, hat die Versandpruefung
    // entschieden (Kriterium 12).
    return new Belegabsender(
        Objects.requireNonNull(eigeneAngaben.name()),
        eigeneAngaben.anschrift(),
        eigeneAngaben.email(),
        eigeneAngaben.telefon(),
        eigeneAngaben.steuernummer(),
        eigeneAngaben.umsatzsteuerId(),
        eigeneAngaben.bankverbindung());
  }

  /* Ein Ansprechpartner ist nicht noetig (Kriterium 12); fehlt er, traegt der Beleg nur die Firma. */
  private @Nullable String person() {
    final Ansprechpartner benannt = ansprechpartner;
    return benannt == null ? null : Personenname.von(benannt);
  }
}
