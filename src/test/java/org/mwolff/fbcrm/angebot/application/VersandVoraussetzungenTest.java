package org.mwolff.fbcrm.angebot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;
import org.mwolff.fbcrm.firma.domain.Firma;

/**
 * Die Pruefung vor dem Versenden (Kriterium 12, E22).
 *
 * <p>Zwei Aussagen sind der Gegenstand. Erstens: Gemeldet wird <b>alles</b> Fehlende auf einmal —
 * wer drei Angaben vergessen hat, soll das in einem Durchgang erfahren und nicht dreimal
 * hintereinander. Zweitens: Die Schluessel der Feldliste sind genau die vier aus E22, weil die
 * Oberflaeche sie namentlich kennt.
 *
 * <p>Die Pruefung ist rein — keine Uhr, kein Bestand, kein Nummernkreis. Genau deshalb steht sie
 * <b>vor</b> jedem Zug am Nummernkreis: Ein abgewiesener Versuch verbraucht keine Nummer (Kriterium
 * 12). Dass der Anwendungsfall sie dort aufruft, weist {@code AngebotVersendenUseCaseTest} nach.
 *
 * <p>Ein Ansprechpartner kommt hier nirgends vor. Das ist die Zusage aus Kriterium 12: Verlangt ist
 * die Firmenanschrift, nicht eine Person.
 */
class VersandVoraussetzungenTest {

  private static final LocalDate ANGEBOTSDATUM = Angebotsdoppel.ANGEBOTSDATUM;

  private static final Angebotsposition OHNE_BEZEICHNUNG =
      new Angebotsposition(
          "   ",
          Abrechnungsmodus.FESTPREIS,
          BigDecimal.ONE,
          Einheit.PAUSCHAL,
          new BigDecimal("100.00"));

  private static Angebot entwurf(
      final LocalDate gueltigBis, final List<Angebotsposition> positionen) {
    return Angebotsdoppel.entwurf(11L)
        .entwurfGeaendert(
            gueltigBis,
            Angebotsdoppel.BESCHREIBUNG,
            Angebotsdoppel.BEDINGUNGEN,
            positionen,
            Angebotsdoppel.ANGELEGT);
  }

  private static Angebot vollstaendigerEntwurf() {
    return entwurf(Angebotsdoppel.GUELTIG_BIS, List.of(Angebotsdoppel.KONZEPTION));
  }

  private static Map<String, List<String>> fehlend(final Angebot angebot) {
    return VersandVoraussetzungen.fehlend(
        angebot, Versanddoppel.firma(), Versanddoppel.eigeneAngaben());
  }

  private static Map<String, List<String>> fehlend(final Firma firma) {
    return VersandVoraussetzungen.fehlend(
        vollstaendigerEntwurf(), firma, Versanddoppel.eigeneAngaben());
  }

  private static Map<String, List<String>> fehlend(final EigeneAngaben angaben) {
    return VersandVoraussetzungen.fehlend(vollstaendigerEntwurf(), Versanddoppel.firma(), angaben);
  }

  /** Die drei Anschriften, denen je eine Pflichtangabe aus Kriterium 12 fehlt. */
  static List<Anschrift> unvollstaendigeAnschriften() {
    return List.of(
        new Anschrift(null, "28195", "Bremen", "Deutschland"),
        new Anschrift("Hauptstrasse 1", null, "Bremen", "Deutschland"),
        new Anschrift("Hauptstrasse 1", "28195", null, "Deutschland"),
        new Anschrift("Hauptstrasse 1", "28195", "   ", "Deutschland"));
  }

  @Test
  void fehlend_givenACompleteDraft_thenNamesNothing() {
    // Given — Kriterium 12: eine Position mit Bezeichnung, Gueltigkeit ab dem Angebotsdatum,
    // Firmenanschrift und eigene Angaben liegen vor.

    // When / Then
    assertThat(fehlend(vollstaendigerEntwurf())).isEmpty();
  }

  @Test
  void pruefe_givenACompleteDraft_thenPassesWithoutAnException() {
    // When / Then
    assertThatCode(
            () ->
                VersandVoraussetzungen.pruefe(
                    vollstaendigerEntwurf(), Versanddoppel.firma(), Versanddoppel.eigeneAngaben()))
        .doesNotThrowAnyException();
  }

  @Test
  void fehlend_withoutAnyPosition_thenNamesPositionen() {
    // Given — Kriterium 12: mindestens eine Position.

    // When / Then
    assertThat(fehlend(entwurf(Angebotsdoppel.GUELTIG_BIS, List.of())))
        .containsOnlyKeys(VersandVoraussetzungen.POSITIONEN);
  }

  @Test
  void fehlend_withABlankBezeichnung_thenNamesPositionen() {
    // Given — Kriterium 12: jede Bezeichnung nicht leer; auch Leerzeichen sind leer.

    // When / Then
    assertThat(
            fehlend(
                entwurf(
                    Angebotsdoppel.GUELTIG_BIS,
                    List.of(Angebotsdoppel.KONZEPTION, OHNE_BEZEICHNUNG))))
        .containsOnlyKeys(VersandVoraussetzungen.POSITIONEN);
  }

  @Test
  void fehlend_whenGueltigkeitIsBeforeTheAngebotsdatum_thenNamesGueltigBis() {
    // Given — Kriterium 12, E27: die Regel steht hier und nicht in der Datenbank.

    // When / Then
    assertThat(fehlend(entwurf(ANGEBOTSDATUM.minusDays(1), List.of(Angebotsdoppel.KONZEPTION))))
        .containsOnlyKeys(VersandVoraussetzungen.GUELTIG_BIS);
  }

  @Test
  void fehlend_whenGueltigkeitIsTheAngebotsdatum_thenNamesNothing() {
    // Given — der Tag selbst zaehlt noch mit: „nicht vor dem Angebotsdatum".

    // When / Then
    assertThat(fehlend(entwurf(ANGEBOTSDATUM, List.of(Angebotsdoppel.KONZEPTION)))).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("unvollstaendigeAnschriften")
  void fehlend_whenTheFirmaLacksAPartOfItsAnschrift_thenNamesFirma(final Anschrift anschrift) {
    // Given — Kriterium 12, F12: die Firma des Vorgangs traegt Strasse, PLZ und Ort.

    // When / Then
    assertThat(fehlend(Versanddoppel.firmaMit(anschrift)))
        .containsOnlyKeys(VersandVoraussetzungen.FIRMA);
  }

  @ParameterizedTest
  @MethodSource("unvollstaendigeAnschriften")
  void fehlend_whenTheEigeneAnschriftIsIncomplete_thenNamesEigeneAngaben(
      final Anschrift anschrift) {
    // Given — Kriterium 12, F12: unter „Eigene Angaben" stehen Name und Anschrift.

    // When / Then
    assertThat(fehlend(Versanddoppel.eigeneAngabenMit(Versanddoppel.EIGENER_NAME, anschrift)))
        .containsOnlyKeys(VersandVoraussetzungen.EIGENE_ANGABEN);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void fehlend_whenTheEigenerNameIsBlank_thenNamesEigeneAngaben(final String name) {
    // Given — Kriterium 12: der eigene Name ist Pflicht, sobald ein Beleg hinausgeht.

    // When / Then
    assertThat(fehlend(Versanddoppel.eigeneAngabenMit(name, Versanddoppel.EIGENE_ANSCHRIFT)))
        .containsOnlyKeys(VersandVoraussetzungen.EIGENE_ANGABEN);
  }

  @Test
  void fehlend_whenTheEigenerNameIsMissing_thenNamesEigeneAngaben() {
    // Given — dieselbe Lage mit {@code null} statt eines leeren Textes (E9 des Vorgang-Moduls).

    // When / Then
    assertThat(fehlend(Versanddoppel.eigeneAngabenMit(null, Versanddoppel.EIGENE_ANSCHRIFT)))
        .containsOnlyKeys(VersandVoraussetzungen.EIGENE_ANGABEN);
  }

  @Test
  void fehlend_whenSeveralThingsAreMissing_thenNamesAllOfThemAtOnce() {
    // Given — E22: wer drei Felder vergessen hat, soll das in einem Durchgang erfahren.
    final Anschrift leer = new Anschrift(null, null, null, null);

    // When
    final Map<String, List<String>> fehlend =
        VersandVoraussetzungen.fehlend(
            entwurf(ANGEBOTSDATUM.minusDays(1), List.of()),
            Versanddoppel.firmaMit(leer),
            Versanddoppel.eigeneAngabenMit(null, leer));

    // Then — genau die vier Schluessel aus E22, in dieser Reihenfolge.
    assertThat(fehlend)
        .containsOnlyKeys(
            VersandVoraussetzungen.POSITIONEN,
            VersandVoraussetzungen.GUELTIG_BIS,
            VersandVoraussetzungen.FIRMA,
            VersandVoraussetzungen.EIGENE_ANGABEN);
    assertThat(fehlend.keySet())
        .containsExactly(
            VersandVoraussetzungen.POSITIONEN,
            VersandVoraussetzungen.GUELTIG_BIS,
            VersandVoraussetzungen.FIRMA,
            VersandVoraussetzungen.EIGENE_ANGABEN);
  }

  @Test
  void fehlend_whenBothPositionRulesAreBroken_thenBothMessagesStandUnderOneKey() {
    // Given — „keine Position" und „Bezeichnung leer" sind zwei Maengel an einem Feld.

    // When
    final Map<String, List<String>> fehlend =
        fehlend(entwurf(Angebotsdoppel.GUELTIG_BIS, List.of(OHNE_BEZEICHNUNG)));

    // Then — die leere Bezeichnung, aber nicht „keine Position": es gibt ja eine.
    assertThat(fehlend.get(VersandVoraussetzungen.POSITIONEN)).hasSize(1);
  }

  @Test
  void pruefe_whenSomethingIsMissing_thenThrowsWithTheFieldList() {
    // Given — Kriterium 12: die Anwendung nennt die fehlende Angabe.

    // When / Then
    assertThatThrownBy(
            () ->
                VersandVoraussetzungen.pruefe(
                    entwurf(Angebotsdoppel.GUELTIG_BIS, List.of()),
                    Versanddoppel.firma(),
                    Versanddoppel.eigeneAngaben()))
        .isInstanceOfSatisfying(
            VersandUnvollstaendig.class,
            abgewiesen ->
                assertThat(abgewiesen.felder())
                    .containsOnlyKeys(VersandVoraussetzungen.POSITIONEN));
  }
}
