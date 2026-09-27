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
 * Das Aendern von Titel, Firma und Ansprechpartner eines Vorgangs (Kriterien 10, 21, 23).
 *
 * <p>Die Wahlregel ist dieselbe wie beim Anlegen, mit <b>einer</b> Ausnahme aus E19: Die bereits
 * zugeordnete, inzwischen stillgelegte Firma oder der stillgelegte Ansprechpartner bleibt zulaessig
 * — sonst waere ein Vorgang nach dem Stilllegen seiner Firma nicht mehr speicherbar (Kriterium 23).
 * Eine <b>andere</b> stillgelegte Zuordnung wird abgewiesen.
 *
 * <p>Ein abgeschlossener Vorgang bleibt aenderbar (Kriterium 10, E26): Der Abschlussstand wirkt auf
 * die Uebersicht, nicht als Schreibsperre.
 */
class VorgangAendernUseCaseTest {

  private static final long FIRMA = 7L;
  private static final long ANDERE_FIRMA = 8L;
  private static final long PARTNER = 3L;
  private static final long ANDERER_PARTNER = 4L;
  private static final long VORGANG = 1L;
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant JETZT = Instant.parse("2026-09-25T09:30:00Z");

  private final Ports.Vorgaenge vorgaenge = new Ports.Vorgaenge();
  private final Ports.Firmen firmen = new Ports.Firmen();
  private final Ports.Partner partner = new Ports.Partner();

  private VorgangAendernUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase =
        new VorgangAendernUseCase(
            vorgaenge, new Zuordnungswahl(firmen, partner), Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private static Firma firma(final long id, final boolean aktiv) {
    return new Firma(
        id,
        "Firma " + id,
        new Anschrift(null, null, null, null),
        null,
        null,
        aktiv,
        ANGELEGT,
        ANGELEGT);
  }

  private static Ansprechpartner partner(final long id, final long firmaId, final boolean aktiv) {
    return new Ansprechpartner(
        id, firmaId, "Max", "Mueller", null, null, null, null, aktiv, ANGELEGT, ANGELEGT);
  }

  private long vorhanden(final Long ansprechpartnerId, final boolean abgeschlossen) {
    return vorgaenge
        .save(
            new Vorgang(
                null,
                12L,
                "Website-Relaunch",
                FIRMA,
                ansprechpartnerId,
                null,
                null,
                abgeschlossen,
                ANGELEGT,
                ANGELEGT))
        .requireId();
  }

  private static VorgangDaten daten(
      final String titel, final long firmaId, final Long ansprechpartnerId) {
    return new VorgangDaten(titel, firmaId, ansprechpartnerId, null, null);
  }

  private Vorgang gespeicherter() {
    return vorgaenge.findById(VORGANG).orElseThrow();
  }

  @Test
  void aendern_thenWritesTitleFirmaAndAnsprechpartner() {
    // Given — Kriterium 10.
    final long id = vorhanden(null, false);
    firmen.mit(firma(FIRMA, true)).mit(firma(ANDERE_FIRMA, true));
    partner.mit(partner(ANDERER_PARTNER, ANDERE_FIRMA, true));

    // When
    useCase.aendern(id, daten("Neuer Titel", ANDERE_FIRMA, Long.valueOf(ANDERER_PARTNER)));

    // Then
    assertThat(gespeicherter())
        .extracting(Vorgang::titel, Vorgang::firmaId, Vorgang::ansprechpartnerId)
        .containsExactly("Neuer Titel", ANDERE_FIRMA, Long.valueOf(ANDERER_PARTNER));
  }

  @Test
  void aendern_thenKeepsNumberAndCreationTime() {
    // Given — Kriterium 8: die Nummer aendert sich nie.
    final long id = vorhanden(null, false);
    firmen.mit(firma(FIRMA, true));

    // When
    useCase.aendern(id, daten("Neuer Titel", FIRMA, null));

    // Then
    assertThat(gespeicherter())
        .extracting(Vorgang::nummer, Vorgang::createdAt)
        .containsExactly(12L, ANGELEGT);
  }

  @Test
  void aendern_thenTakesTheChangeTimeFromTheClock() {
    // Given
    final long id = vorhanden(null, false);
    firmen.mit(firma(FIRMA, true));

    // When
    useCase.aendern(id, daten("Neuer Titel", FIRMA, null));

    // Then
    assertThat(gespeicherter().updatedAt()).isEqualTo(JETZT);
  }

  @Test
  void aendern_givenAClosedVorgang_thenIsStillPossibleAndStaysClosed() {
    // Given — Kriterium 10 und E26: keine Schreibsperre am abgeschlossenen Vorgang.
    final long id = vorhanden(null, true);
    firmen.mit(firma(FIRMA, true));

    // When
    useCase.aendern(id, daten("Neuer Titel", FIRMA, null));

    // Then
    assertThat(gespeicherter())
        .extracting(Vorgang::titel, Vorgang::abgeschlossen)
        .containsExactly("Neuer Titel", true);
  }

  @Test
  void aendern_thenClearsTheAnsprechpartner() {
    // Given — Kriterium 5: der Ansprechpartner ist optional, auch nachtraeglich.
    final long id = vorhanden(Long.valueOf(PARTNER), false);
    firmen.mit(firma(FIRMA, true));

    // When
    useCase.aendern(id, daten("Website-Relaunch", FIRMA, null));

    // Then
    assertThat(gespeicherter().ansprechpartnerId()).isNull();
  }

  @Test
  void aendern_givenAPaddedTitle_thenStoresItTrimmed() {
    // Given — E9.
    final long id = vorhanden(null, false);
    firmen.mit(firma(FIRMA, true));

    // When
    useCase.aendern(id, daten("  Neuer Titel  ", FIRMA, null));

    // Then
    assertThat(gespeicherter().titel()).isEqualTo("Neuer Titel");
  }

  @Test
  void aendern_givenAnUnknownId_thenThrowsVorgangNichtGefunden() {
    // Given
    firmen.mit(firma(FIRMA, true));

    // When / Then
    assertThatThrownBy(() -> useCase.aendern(4711L, daten("Neuer Titel", FIRMA, null)))
        .isInstanceOf(VorgangNichtGefunden.class);
  }

  @Test
  void aendern_givenABlankTitle_thenIsRefusedBeforeTouchingTheRepository() {
    // When / Then — die Normalisierung steht vor jedem Zugriff auf den Bestand.
    assertThatThrownBy(() -> useCase.aendern(4711L, daten("   ", FIRMA, null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aendern_givenABlankTitle_thenKeepsTheStoredVorgang() {
    // Given — Kriterium 10: ohne Speichern bleibt der alte Stand.
    final long id = vorhanden(null, false);
    firmen.mit(firma(FIRMA, true));

    // When
    assertThatThrownBy(() -> useCase.aendern(id, daten("  ", FIRMA, null)))
        .isInstanceOf(IllegalArgumentException.class);

    // Then
    assertThat(gespeicherter().titel()).isEqualTo("Website-Relaunch");
  }

  @Test
  void aendern_givenTheAlreadyAssignedRetiredFirma_thenIsAccepted() {
    // Given — Kriterium 23, E19: die bestehende Zuordnung bleibt speicherbar.
    final long id = vorhanden(null, false);
    firmen.mit(firma(FIRMA, false));

    // When
    useCase.aendern(id, daten("Neuer Titel", FIRMA, null));

    // Then
    assertThat(gespeicherter().titel()).isEqualTo("Neuer Titel");
  }

  @Test
  void aendern_givenAnotherRetiredFirma_thenThrowsFirmaNichtWaehlbar() {
    // Given — E19: die Ausnahme gilt nur fuer die bereits zugeordnete Firma.
    final long id = vorhanden(null, false);
    firmen.mit(firma(FIRMA, true)).mit(firma(ANDERE_FIRMA, false));

    // When / Then
    assertThatThrownBy(() -> useCase.aendern(id, daten("Neuer Titel", ANDERE_FIRMA, null)))
        .isInstanceOf(FirmaNichtWaehlbar.class);
  }

  @Test
  void aendern_givenAnUnknownFirma_thenThrowsFirmaNichtWaehlbar() {
    // Given
    final long id = vorhanden(null, false);

    // When / Then
    assertThatThrownBy(() -> useCase.aendern(id, daten("Neuer Titel", FIRMA, null)))
        .isInstanceOf(FirmaNichtWaehlbar.class);
  }

  @Test
  void aendern_givenTheAlreadyAssignedRetiredAnsprechpartner_thenIsAccepted() {
    // Given — Kriterium 23, E19.
    final long id = vorhanden(Long.valueOf(PARTNER), false);
    firmen.mit(firma(FIRMA, true));
    partner.mit(partner(PARTNER, FIRMA, false));

    // When
    useCase.aendern(id, daten("Neuer Titel", FIRMA, Long.valueOf(PARTNER)));

    // Then
    assertThat(gespeicherter())
        .extracting(Vorgang::titel, Vorgang::ansprechpartnerId)
        .containsExactly("Neuer Titel", Long.valueOf(PARTNER));
  }

  @Test
  void aendern_givenAnotherRetiredAnsprechpartner_thenThrowsAnsprechpartnerNichtWaehlbar() {
    // Given — E19: die Ausnahme gilt nur fuer den bereits zugeordneten Ansprechpartner.
    final long id = vorhanden(Long.valueOf(PARTNER), false);
    firmen.mit(firma(FIRMA, true));
    partner.mit(partner(PARTNER, FIRMA, false)).mit(partner(ANDERER_PARTNER, FIRMA, false));

    // When / Then
    assertThatThrownBy(
            () -> useCase.aendern(id, daten("Neuer Titel", FIRMA, Long.valueOf(ANDERER_PARTNER))))
        .isInstanceOf(AnsprechpartnerNichtWaehlbar.class);
  }

  @Test
  void aendern_givenARetiredAnsprechpartnerWhereNoneWasAssigned_thenIsRefused() {
    // Given — ohne bisherige Zuordnung gibt es keine Ausnahme, auf die sich etwas stuetzen koennte.
    final long id = vorhanden(null, false);
    firmen.mit(firma(FIRMA, true));
    partner.mit(partner(PARTNER, FIRMA, false));

    // When / Then
    assertThatThrownBy(
            () -> useCase.aendern(id, daten("Neuer Titel", FIRMA, Long.valueOf(PARTNER))))
        .isInstanceOf(AnsprechpartnerNichtWaehlbar.class);
  }

  @Test
  void aendern_givenAnAnsprechpartnerOfAnotherFirma_thenThrowsAnsprechpartnerNichtWaehlbar() {
    // Given — Kriterium 6: die Zugehoerigkeit gilt ohne Ausnahme, auch fuer einen aktiven Partner.
    final long id = vorhanden(Long.valueOf(PARTNER), false);
    firmen.mit(firma(FIRMA, true)).mit(firma(ANDERE_FIRMA, true));
    partner.mit(partner(PARTNER, FIRMA, true));

    // When / Then — die Firma wechselt, der bisherige Ansprechpartner bleibt stehen.
    assertThatThrownBy(
            () -> useCase.aendern(id, daten("Neuer Titel", ANDERE_FIRMA, Long.valueOf(PARTNER))))
        .isInstanceOf(AnsprechpartnerNichtWaehlbar.class);
  }

  @Test
  void aendern_givenAnUnknownAnsprechpartner_thenThrowsAnsprechpartnerNichtWaehlbar() {
    // Given
    final long id = vorhanden(null, false);
    firmen.mit(firma(FIRMA, true));

    // When / Then
    assertThatThrownBy(
            () -> useCase.aendern(id, daten("Neuer Titel", FIRMA, Long.valueOf(PARTNER))))
        .isInstanceOf(AnsprechpartnerNichtWaehlbar.class);
  }
}
