package org.mwolff.fbcrm.vorgang.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;

/**
 * Das Anlegen eines Vorgangs (Kriterien 5, 6, 8).
 *
 * <p>Zwei Zusagen stehen hier im Mittelpunkt. Erstens die Wahlregel aus E19: Firma und
 * Ansprechpartner muessen aktiv sein, und der Ansprechpartner muss zur gewaehlten Firma gehoeren —
 * serverseitig, nicht nur in der Maske. Zweitens Kriterium 8: Die Nummer wird <b>nach</b> jeder
 * Probe gezogen, damit ein abgewiesenes Anlegen keine verbraucht.
 */
class VorgangAnlegenUseCaseTest {

  private static final long FIRMA = 7L;
  private static final long FREMDE_FIRMA = 8L;
  private static final long PARTNER = 3L;
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant JETZT = Instant.parse("2026-09-25T09:30:00Z");

  private final Ports.Vorgaenge vorgaenge = new Ports.Vorgaenge();
  private final Ports.Nummernkreis nummernkreis = new Ports.Nummernkreis();
  private final Ports.Firmen firmen = new Ports.Firmen();
  private final Ports.Partner partner = new Ports.Partner();

  private VorgangAnlegenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase =
        new VorgangAnlegenUseCase(
            vorgaenge,
            nummernkreis,
            new Zuordnungswahl(firmen, partner),
            Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private static Firma firma(final long id, final boolean aktiv) {
    return new Firma(
        id,
        "Adler AG",
        new Anschrift(null, null, null, null),
        null,
        null,
        aktiv,
        ANGELEGT,
        ANGELEGT);
  }

  private static Ansprechpartner partner(final long firmaId, final boolean aktiv) {
    return new Ansprechpartner(
        PARTNER, firmaId, "Max", "Mueller", null, null, null, null, aktiv, ANGELEGT, ANGELEGT);
  }

  private static VorgangDaten daten(final String titel, final Long ansprechpartnerId) {
    return new VorgangDaten(titel, FIRMA, ansprechpartnerId, null, null);
  }

  @Test
  void anlegen_thenTakesTheFirstFreeNumberFromTheNummernkreis() {
    // Given — Kriterium 8: die Nummern beginnen bei 1.
    firmen.mit(firma(FIRMA, true));

    // When
    final Vorgang angelegt = useCase.anlegen(daten("Website-Relaunch", null));

    // Then
    assertThat(angelegt.nummer()).isEqualTo(1L);
  }

  @Test
  void anlegen_twice_thenTheNumbersAscend() {
    // Given
    firmen.mit(firma(FIRMA, true));
    useCase.anlegen(daten("Website-Relaunch", null));

    // When
    final Vorgang zweiter = useCase.anlegen(daten("Schulung", null));

    // Then
    assertThat(zweiter.nummer()).isEqualTo(2L);
  }

  @Test
  void anlegen_thenTheVorgangIsOpen() {
    // Given — Kriterium 20: abgeschlossen wird eigens geschaltet.
    firmen.mit(firma(FIRMA, true));

    // When
    final Vorgang angelegt = useCase.anlegen(daten("Website-Relaunch", null));

    // Then
    assertThat(angelegt.abgeschlossen()).isFalse();
  }

  @Test
  void anlegen_thenTakesBothTimestampsFromTheClock() {
    // Given
    firmen.mit(firma(FIRMA, true));

    // When
    final Vorgang angelegt = useCase.anlegen(daten("Website-Relaunch", null));

    // Then
    assertThat(angelegt).extracting(Vorgang::createdAt, Vorgang::updatedAt).containsOnly(JETZT);
  }

  @Test
  void anlegen_thenReturnsTheVorgangWithTheIdFromTheRepository() {
    // Given — Kriterium 9: die Oberflaeche braucht die Kennung fuer den Weg zur Detailansicht.
    firmen.mit(firma(FIRMA, true));

    // When
    final Vorgang angelegt = useCase.anlegen(daten("Website-Relaunch", null));

    // Then
    assertThat(angelegt.id()).isEqualTo(1L);
  }

  @Test
  void anlegen_thenStoresTitleFirmaAndAnsprechpartner() {
    // Given — Kriterium 5.
    firmen.mit(firma(FIRMA, true));
    partner.mit(partner(FIRMA, true));

    // When
    useCase.anlegen(daten("Website-Relaunch", Long.valueOf(PARTNER)));

    // Then
    assertThat(vorgaenge.alle())
        .singleElement()
        .extracting(Vorgang::titel, Vorgang::firmaId, Vorgang::ansprechpartnerId)
        .containsExactly("Website-Relaunch", FIRMA, Long.valueOf(PARTNER));
  }

  @Test
  void anlegen_withoutAnAnsprechpartner_thenStoresNone() {
    // Given — Kriterium 5: der Ansprechpartner ist optional.
    firmen.mit(firma(FIRMA, true));

    // When
    final Vorgang angelegt = useCase.anlegen(daten("Website-Relaunch", null));

    // Then
    assertThat(angelegt.ansprechpartnerId()).isNull();
  }

  @Test
  void anlegen_givenAPaddedTitle_thenStoresItTrimmed() {
    // Given — E9: genau eine Schreibweise landet im Bestand.
    firmen.mit(firma(FIRMA, true));

    // When
    final Vorgang angelegt = useCase.anlegen(daten("  Website-Relaunch  ", null));

    // Then
    assertThat(angelegt.titel()).isEqualTo("Website-Relaunch");
  }

  @Test
  void anlegen_givenATitleOfWhitespaceOnly_thenIsRefused() {
    // When / Then — Kriterium 5: der Titel ist Pflicht, auch an der Transaktionsgrenze.
    assertThatThrownBy(() -> useCase.anlegen(daten("   ", null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void anlegen_givenATitleOfWhitespaceOnly_thenStoresNothing() {
    // When — Kriterium 5: „nichts wird gespeichert".
    assertThatThrownBy(() -> useCase.anlegen(daten("   ", null)))
        .isInstanceOf(IllegalArgumentException.class);

    // Then
    assertThat(vorgaenge.alle()).isEmpty();
  }

  @Test
  void anlegen_givenAnUnknownFirma_thenThrowsFirmaNichtWaehlbar() {
    // When / Then — die Firma steht im Rumpf und nicht im Pfad: 400, nicht 404.
    assertThatThrownBy(() -> useCase.anlegen(daten("Website-Relaunch", null)))
        .isInstanceOf(FirmaNichtWaehlbar.class);
  }

  @Test
  void anlegen_givenARetiredFirma_thenThrowsFirmaNichtWaehlbar() {
    // Given — E19: nur aktive Firmen stehen zur Wahl, und das setzt der Server durch.
    firmen.mit(firma(FIRMA, false));

    // When / Then
    assertThatThrownBy(() -> useCase.anlegen(daten("Website-Relaunch", null)))
        .isInstanceOf(FirmaNichtWaehlbar.class);
  }

  @Test
  void anlegen_givenAnUnknownAnsprechpartner_thenThrowsAnsprechpartnerNichtWaehlbar() {
    // Given
    firmen.mit(firma(FIRMA, true));

    // When / Then
    assertThatThrownBy(() -> useCase.anlegen(daten("Website-Relaunch", Long.valueOf(PARTNER))))
        .isInstanceOf(AnsprechpartnerNichtWaehlbar.class);
  }

  @Test
  void anlegen_givenARetiredAnsprechpartner_thenThrowsAnsprechpartnerNichtWaehlbar() {
    // Given — E19.
    firmen.mit(firma(FIRMA, true));
    partner.mit(partner(FIRMA, false));

    // When / Then
    assertThatThrownBy(() -> useCase.anlegen(daten("Website-Relaunch", Long.valueOf(PARTNER))))
        .isInstanceOf(AnsprechpartnerNichtWaehlbar.class);
  }

  @Test
  void anlegen_givenAnAnsprechpartnerOfAnotherFirma_thenThrowsAnsprechpartnerNichtWaehlbar() {
    // Given — Kriterium 6: zur Wahl stehen nur die Ansprechpartner der gewaehlten Firma.
    firmen.mit(firma(FIRMA, true));
    partner.mit(partner(FREMDE_FIRMA, true));

    // When / Then
    assertThatThrownBy(() -> useCase.anlegen(daten("Website-Relaunch", Long.valueOf(PARTNER))))
        .isInstanceOf(AnsprechpartnerNichtWaehlbar.class);
  }

  @Test
  void anlegen_givenARefusedAttempt_thenNoNumberWasDrawn() {
    // Given — Kriterium 8: ein abgewiesenes Anlegen verbraucht keine Nummer.
    firmen.mit(firma(FIRMA, false));

    // When
    assertThatThrownBy(() -> useCase.anlegen(daten("Website-Relaunch", null)))
        .isInstanceOf(FirmaNichtWaehlbar.class);

    // Then
    assertThat(nummernkreis.zuege()).isZero();
  }

  @Test
  void anlegen_afterARefusedAttempt_thenTheNextValidOneGetsNumberOne() {
    // Given — Kriterium 8, aus der Sicht des naechsten Aufrufers.
    assertThatThrownBy(() -> useCase.anlegen(daten("Website-Relaunch", null)))
        .isInstanceOf(FirmaNichtWaehlbar.class);
    firmen.mit(firma(FIRMA, true));

    // When
    final Vorgang angelegt = useCase.anlegen(daten("Website-Relaunch", null));

    // Then
    assertThat(angelegt.nummer()).isEqualTo(1L);
  }
}
