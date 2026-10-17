package org.mwolff.fbcrm.angebot.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mwolff.fbcrm.angebot.application.StatusGrenzeErreicht;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;

/**
 * Das Angebot als Fachobjekt: die Rechenregel der Summe, das Aendern in jedem Status und der
 * Statuswechsel um eine Stufe (Issue #127).
 *
 * <p>Die Summe ist die Summe der <b>gerundeten</b> Positionsbetraege und nicht die gerundete Summe
 * der ungerundeten — deshalb stehen unten zwei Positionen, deren Einzelrundungen sich zu einem
 * anderen Wert addieren als die Rundung ihrer Summe.
 *
 * <p><b>Dazu die Art des Angebots</b> (Issue #226): Das Kennzeichen {@code intern} und der Status
 * sagen dasselbe oder das Angebot entsteht nicht (E3), und {@code umgestellt} setzt beide zusammen.
 *
 * <p><b>Dazu der Titel</b> (Issue #316, Plan #315 E1): nicht leer und hoechstens 200 Zeichen, am
 * Rand von Leerraum befreit, und {@code null} fuer ein Angebot von vor dieser Aenderung.
 */
class AngebotTest {

  private static final LocalDate ANGEBOTSDATUM = LocalDate.of(2026, 9, 20);
  private static final Instant ANGELEGT = Instant.parse("2026-09-20T08:00:00Z");
  private static final Instant JETZT = Instant.parse("2026-09-27T10:30:00Z");

  /** 2,5 × 1.000,01 € = 2.500,025 € und damit gerundet 2.500,03 €. */
  private static final Angebotsposition KONZEPTION =
      new Angebotsposition(
          null,
          "Konzeption",
          Abrechnungsmodus.AUFWAND,
          new BigDecimal("2.50"),
          Einheit.PERSONENTAG,
          new BigDecimal("1000.01"),
          Year.of(2026));

  /** 1,5 × 0,01 € = 0,015 € und damit gerundet 0,02 €. */
  private static final Angebotsposition KLEINKRAM =
      new Angebotsposition(
          null,
          "Kleinkram",
          Abrechnungsmodus.AUFWAND,
          new BigDecimal("1.50"),
          Einheit.STUNDE,
          new BigDecimal("0.01"),
          Year.of(2026));

  private static final Angebotsposition PAUSCHALE =
      new Angebotsposition(
          null,
          "Schulungstag",
          Abrechnungsmodus.FESTPREIS,
          BigDecimal.ONE,
          Einheit.PAUSCHAL,
          new BigDecimal("1200.00"),
          Year.of(2026));

  private static Angebot angebot(final Angebotsstatus status) {
    return angebot(status, List.of(KONZEPTION));
  }

  /**
   * Das Angebot zu einem Status — das Kennzeichen kommt aus dem Status.
   *
   * <p>Es ist nicht frei waehlbar: Die Invariante bindet beides aneinander, und ein Testdoppel, das
   * sich daran nicht haelt, wirft schon beim Bauen. Wer die Abweisung prueft, baut darum von Hand.
   */
  private static Angebot angebot(
      final Angebotsstatus status, final List<Angebotsposition> positionen) {
    return new Angebot(
        7L,
        3L,
        8L,
        status.intern(),
        status,
        ANGEBOTSDATUM,
        "Website-Relaunch",
        "Neugestaltung der Website",
        positionen,
        ANGELEGT,
        ANGELEGT);
  }

  private static Angebot mitTitel(final @Nullable String titel) {
    return new Angebot(
        7L,
        3L,
        8L,
        false,
        Angebotsstatus.ANGELEGT,
        ANGEBOTSDATUM,
        titel,
        "Neugestaltung der Website",
        List.of(KONZEPTION),
        ANGELEGT,
        ANGELEGT);
  }

  private static Angebot angebot(final boolean intern, final Angebotsstatus status) {
    return new Angebot(
        7L,
        3L,
        8L,
        intern,
        status,
        ANGEBOTSDATUM,
        "Website-Relaunch",
        "Neugestaltung der Website",
        List.of(KONZEPTION),
        ANGELEGT,
        ANGELEGT);
  }

  @Test
  void summe_thenAddsTheAmountsRoundedPerPosition() {
    // Given — einzeln gerundet 2.500,03 € + 0,02 €; die Rundung der Summe ergaebe 2.500,04 €.
    final Angebot angebot = angebot(Angebotsstatus.ANGELEGT, List.of(KONZEPTION, KLEINKRAM));

    // When
    final BigDecimal summe = angebot.summe();

    // Then
    assertThat(summe).isEqualTo(new BigDecimal("2500.05"));
  }

  @Test
  void summe_givenNoPosition_thenZeroWithTwoDecimals() {
    // Given — ein frisch angelegtes Angebot hat noch keine Position.
    final Angebot angebot = angebot(Angebotsstatus.ANGELEGT, List.of());

    // When / Then
    assertThat(angebot.summe()).isEqualTo(new BigDecimal("0.00"));
  }

  @ParameterizedTest
  @EnumSource(Angebotsstatus.class)
  void geaendert_givenAnyStatus_thenCarriesTheNewValuesAndKeepsTheStatus(
      final Angebotsstatus status) {
    // Given — Kriterium 5: das Angebot bleibt in jedem Status aenderbar.
    final LocalDate neuesDatum = LocalDate.of(2026, 9, 25);

    // When
    final Angebot geaendert =
        angebot(status)
            .geaendert(neuesDatum, 9L, "Wartung", "Betreuung", List.of(PAUSCHALE), JETZT);

    // Then
    assertThat(geaendert)
        .satisfies(
            a -> assertThat(a.status()).isEqualTo(status),
            a -> assertThat(a.angebotDatum()).isEqualTo(neuesDatum),
            a -> assertThat(a.ansprechpartnerId()).isEqualTo(9L),
            a -> assertThat(a.titel()).isEqualTo("Wartung"),
            a -> assertThat(a.beschreibung()).isEqualTo("Betreuung"),
            a -> assertThat(a.positionen()).containsExactly(PAUSCHALE),
            a -> assertThat(a.updatedAt()).isEqualTo(JETZT),
            a -> assertThat(a.createdAt()).isEqualTo(ANGELEGT),
            a -> assertThat(a.firmaId()).isEqualTo(3L),
            a -> assertThat(a.id()).isEqualTo(7L));
  }

  @Test
  void geaendert_givenNoContactAndNoText_thenKeepsThemAbsent() {
    // When
    final Angebot geaendert =
        angebot(Angebotsstatus.ANGELEGT)
            .geaendert(ANGEBOTSDATUM, null, "Wartung", null, List.of(), JETZT);

    // Then
    assertThat(geaendert)
        .satisfies(
            a -> assertThat(a.ansprechpartnerId()).isNull(),
            a -> assertThat(a.beschreibung()).isNull(),
            a -> assertThat(a.positionen()).isEmpty());
  }

  @Test
  void statusWeiter_givenAPlacedOffer_thenOneStepFurtherAndEverythingElseKept() {
    // When
    final Angebot weiter = angebot(Angebotsstatus.ABGEGEBEN).statusWeiter(JETZT);

    // Then
    assertThat(weiter)
        .satisfies(
            a -> assertThat(a.status()).isEqualTo(Angebotsstatus.BESTELLT),
            a -> assertThat(a.updatedAt()).isEqualTo(JETZT),
            a -> assertThat(a.createdAt()).isEqualTo(ANGELEGT),
            a -> assertThat(a.id()).isEqualTo(7L),
            a -> assertThat(a.firmaId()).isEqualTo(3L),
            a -> assertThat(a.ansprechpartnerId()).isEqualTo(8L),
            a -> assertThat(a.angebotDatum()).isEqualTo(ANGEBOTSDATUM),
            a -> assertThat(a.beschreibung()).isEqualTo("Neugestaltung der Website"),
            a -> assertThat(a.positionen()).containsExactly(KONZEPTION));
  }

  @Test
  void statusWeiter_givenABilledOffer_thenRejected() {
    // Given
    final Angebot angebot = angebot(Angebotsstatus.ABGERECHNET);

    // When / Then
    assertThatThrownBy(() -> angebot.statusWeiter(JETZT)).isInstanceOf(StatusGrenzeErreicht.class);
  }

  @Test
  void statusZurueck_givenADoneOffer_thenOneStepBack() {
    // When
    final Angebot zurueck = angebot(Angebotsstatus.ERLEDIGT).statusZurueck(JETZT);

    // Then
    assertThat(zurueck)
        .satisfies(
            a -> assertThat(a.status()).isEqualTo(Angebotsstatus.BESTELLT),
            a -> assertThat(a.updatedAt()).isEqualTo(JETZT));
  }

  @Test
  void statusZurueck_givenACreatedOffer_thenRejected() {
    // Given
    final Angebot angebot = angebot(Angebotsstatus.ANGELEGT);

    // When / Then
    assertThatThrownBy(() -> angebot.statusZurueck(JETZT)).isInstanceOf(StatusGrenzeErreicht.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotsstatus.class,
      mode = EnumSource.Mode.EXCLUDE,
      names = {"ABGERECHNET", "LAEUFT", "ABGESCHLOSSEN"})
  void abgerechnet_givenAnyOtherStatus_thenJumpsStraightToAbgerechnet(final Angebotsstatus status) {
    // Given — #160, Kriterium 27: abgerechnet wird aus jedem Status erreicht, nicht Stufe fuer
    // Stufe. Die internen Status stehen hier nicht: Interne Arbeit wird nicht abgerechnet, und die
    // Invariante weist den Sprung ab (#226).

    // When
    final Angebot abgerechnet = angebot(status).abgerechnet(JETZT);

    // Then
    assertThat(abgerechnet)
        .satisfies(
            a -> assertThat(a.status()).isEqualTo(Angebotsstatus.ABGERECHNET),
            a -> assertThat(a.updatedAt()).isEqualTo(JETZT),
            a -> assertThat(a.createdAt()).isEqualTo(ANGELEGT),
            a -> assertThat(a.id()).isEqualTo(7L),
            a -> assertThat(a.firmaId()).isEqualTo(3L),
            a -> assertThat(a.ansprechpartnerId()).isEqualTo(8L),
            a -> assertThat(a.angebotDatum()).isEqualTo(ANGEBOTSDATUM),
            a -> assertThat(a.beschreibung()).isEqualTo("Neugestaltung der Website"),
            a -> assertThat(a.positionen()).containsExactly(KONZEPTION));
  }

  @Test
  void abgerechnet_givenAnAlreadyBilledOffer_thenUnchanged() {
    // Given — es ist schon abgerechnet; der Zug ist ein Nichts und traegt darum auch keinen neuen
    // Zeitstempel ein.
    final Angebot angebot = angebot(Angebotsstatus.ABGERECHNET);

    // When
    final Angebot nochmal = angebot.abgerechnet(JETZT);

    // Then
    assertThat(nochmal).isEqualTo(angebot);
  }

  @Test
  void konstruktor_givenInternWithAStatusOfTheCustomerOffer_thenRejected() {
    // Given — E3: Das Kennzeichen und der Status sagen dasselbe, oder das Angebot entsteht nicht.

    // When / Then
    assertThatThrownBy(() -> angebot(true, Angebotsstatus.BESTELLT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("BESTELLT");
  }

  @Test
  void konstruktor_givenNotInternWithAStatusOfTheInternalWork_thenRejected() {
    // When / Then — und dieselbe Abweisung in die andere Richtung.
    assertThatThrownBy(() -> angebot(false, Angebotsstatus.LAEUFT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("LAEUFT");
  }

  @Test
  void umgestellt_towardsInternal_thenSetsTheFlagAndTheStatusTogether() {
    // Given — Kriterium 1 von #207: ein abgegebenes Angebot wird zur internen Arbeit.
    final Angebot angebot = angebot(Angebotsstatus.ABGEGEBEN);

    // When
    final Angebot umgestellt = angebot.umgestellt(true, JETZT);

    // Then
    assertThat(umgestellt)
        .satisfies(
            a -> assertThat(a.intern()).isTrue(),
            a -> assertThat(a.status()).isEqualTo(Angebotsstatus.LAEUFT),
            a -> assertThat(a.updatedAt()).isEqualTo(JETZT),
            a -> assertThat(a.createdAt()).isEqualTo(ANGELEGT),
            a -> assertThat(a.id()).isEqualTo(7L),
            a -> assertThat(a.firmaId()).isEqualTo(3L),
            a -> assertThat(a.ansprechpartnerId()).isEqualTo(8L),
            a -> assertThat(a.angebotDatum()).isEqualTo(ANGEBOTSDATUM),
            a -> assertThat(a.beschreibung()).isEqualTo("Neugestaltung der Website"),
            a -> assertThat(a.positionen()).containsExactly(KONZEPTION));
  }

  @Test
  void umgestellt_givenABilledOffer_thenEndsUpInTheClosedInternalStatus() {
    // Given — von Hand auf abgerechnet gestellt; nach innen ist das die getane Arbeit.
    final Angebot angebot = angebot(Angebotsstatus.ABGERECHNET);

    // When
    final Angebot umgestellt = angebot.umgestellt(true, JETZT);

    // Then
    assertThat(umgestellt.status()).isEqualTo(Angebotsstatus.ABGESCHLOSSEN);
    assertThat(umgestellt.intern()).isTrue();
  }

  @Test
  void umgestellt_backTowardsTheCustomerOffer_thenSetsTheFlagAndTheStatusTogether() {
    // Given — Kriterium 8 von #207 baut darauf auf: der Weg zurueck nach aussen.
    final Angebot angebot = angebot(Angebotsstatus.ABGESCHLOSSEN);

    // When
    final Angebot umgestellt = angebot.umgestellt(false, JETZT);

    // Then
    assertThat(umgestellt.intern()).isFalse();
    assertThat(umgestellt.status()).isEqualTo(Angebotsstatus.ERLEDIGT);
    assertThat(umgestellt.updatedAt()).isEqualTo(JETZT);
  }

  @ParameterizedTest
  @EnumSource(Angebotsstatus.class)
  void geaendert_givenAnyStatus_thenCarriesTheArtOnUnchanged(final Angebotsstatus status) {
    // Given — Aendern sagt nichts ueber die Art des Angebots.

    // When
    final Angebot geaendert =
        angebot(status)
            .geaendert(ANGEBOTSDATUM, 9L, "Wartung", "Betreuung", List.of(PAUSCHALE), JETZT);

    // Then
    assertThat(geaendert.intern()).isEqualTo(status.intern());
    assertThat(geaendert.status()).isEqualTo(status);
  }

  @Test
  void statusWeiter_givenRunningInternalWork_thenReachesTheClosedInternalStatus() {
    // Given — die interne Reihe hat ihre eigenen zwei Stufen.

    // When
    final Angebot weiter = angebot(Angebotsstatus.LAEUFT).statusWeiter(JETZT);

    // Then
    assertThat(weiter.status()).isEqualTo(Angebotsstatus.ABGESCHLOSSEN);
    assertThat(weiter.intern()).isTrue();
  }

  @Test
  void statusZurueck_givenClosedInternalWork_thenReachesTheRunningInternalStatus() {
    // When
    final Angebot zurueck = angebot(Angebotsstatus.ABGESCHLOSSEN).statusZurueck(JETZT);

    // Then
    assertThat(zurueck.status()).isEqualTo(Angebotsstatus.LAEUFT);
    assertThat(zurueck.intern()).isTrue();
  }

  @Test
  void statusWeiter_givenClosedInternalWork_thenRejected() {
    // Given — am Ende der internen Reihe geht es nicht weiter; nach ABGERECHNET fuehrt kein Weg.
    final Angebot angebot = angebot(Angebotsstatus.ABGESCHLOSSEN);

    // When / Then
    assertThatThrownBy(() -> angebot.statusWeiter(JETZT)).isInstanceOf(StatusGrenzeErreicht.class);
  }

  @Test
  void statusZurueck_givenRunningInternalWork_thenRejected() {
    // Given
    final Angebot angebot = angebot(Angebotsstatus.LAEUFT);

    // When / Then
    assertThatThrownBy(() -> angebot.statusZurueck(JETZT)).isInstanceOf(StatusGrenzeErreicht.class);
  }

  @Test
  void positionen_thenAreACopyAndNotTheHandedInList() {
    // Given — der Record ist unveraenderlich, auch wenn der Aufrufer seine Liste behaelt.
    final List<Angebotsposition> uebergeben = new ArrayList<>(List.of(KONZEPTION));
    final Angebot angebot = angebot(Angebotsstatus.ANGELEGT, uebergeben);

    // When
    uebergeben.clear();

    // Then
    assertThat(angebot.positionen()).containsExactly(KONZEPTION);
  }

  @Test
  void konstruktor_givenNoTitle_thenAcceptedAsAnOfferFromBeforeTheTitle() {
    // Given — #314, Kriterium 4: ein bestehendes Angebot hat keinen Titel und bleibt gueltig.

    // When / Then
    assertThat(mitTitel(null).titel()).isNull();
  }

  @Test
  void konstruktor_givenWhitespaceAroundTheTitle_thenStripsIt() {
    // When / Then — E1: Leerraum am Rand gehoert nicht zum Titel.
    assertThat(mitTitel("  Website-Relaunch \t").titel()).isEqualTo("Website-Relaunch");
  }

  @Test
  void konstruktor_givenATitleOfOnlyWhitespace_thenRejected() {
    // When / Then — #314, Kriterium 2: ein Titel nur aus Leerzeichen zaehlt als fehlend.
    assertThatThrownBy(() -> mitTitel("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Titel");
  }

  @Test
  void konstruktor_givenATitleOf201Characters_thenRejected() {
    // When / Then — #314, Kriterium 1: hoechstens 200 Zeichen.
    assertThatThrownBy(() -> mitTitel("x".repeat(201)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("200");
  }

  @Test
  void konstruktor_givenATitleOf200Characters_thenAccepted() {
    // Given — die Grenze selbst geht durch.
    final String titel = "x".repeat(200);

    // When / Then
    assertThat(mitTitel(titel).titel()).isEqualTo(titel);
  }

  @Test
  void konstruktor_givenATitleOf200CharactersAfterStripping_thenAccepted() {
    // Given — gezaehlt wird nach dem Abschneiden des Leerraums.
    final String titel = "x".repeat(200);

    // When / Then
    assertThat(mitTitel(" " + titel + " ").titel()).isEqualTo(titel);
  }

  @Test
  void umgestellt_thenKeepsTheTitle() {
    // When
    final Angebot umgestellt = angebot(Angebotsstatus.ABGEGEBEN).umgestellt(true, JETZT);

    // Then
    assertThat(umgestellt.titel()).isEqualTo("Website-Relaunch");
  }

  @Test
  void statusWeiter_thenKeepsTheTitle() {
    // When
    final Angebot weiter = angebot(Angebotsstatus.ABGEGEBEN).statusWeiter(JETZT);

    // Then
    assertThat(weiter.titel()).isEqualTo("Website-Relaunch");
  }

  @Test
  void geaendert_givenANewTitle_thenCarriesIt() {
    // Given — Issue #317: der Aenderungsweg schreibt den Titel mit.

    // When
    final Angebot geaendert =
        angebot(Angebotsstatus.ANGELEGT)
            .geaendert(ANGEBOTSDATUM, 9L, "Wartung", "Betreuung", List.of(PAUSCHALE), JETZT);

    // Then
    assertThat(geaendert.titel()).isEqualTo("Wartung");
  }

  @Test
  void geaendert_givenAnOfferWithoutTitle_thenGetsTheNewTitle() {
    // When — #314, Kriterium 4: ein Angebot von vor dem Titel bekommt ihn beim Aendern.
    final Angebot geaendert =
        mitTitel(null)
            .geaendert(ANGEBOTSDATUM, 9L, "Wartung", "Betreuung", List.of(PAUSCHALE), JETZT);

    // Then
    assertThat(geaendert.titel()).isEqualTo("Wartung");
  }

  @Test
  void geaendert_givenATitleWithSurroundingBlanks_thenStripsIt() {
    // When — getrimmt und geprueft wird im Konstruktor (E1).
    final Angebot geaendert =
        angebot(Angebotsstatus.ANGELEGT)
            .geaendert(ANGEBOTSDATUM, 9L, "  Wartung  ", null, List.of(), JETZT);

    // Then
    assertThat(geaendert.titel()).isEqualTo("Wartung");
  }

  @Test
  void geaendert_givenABlankTitle_thenRejected() {
    // When / Then — der Konstruktor weist den leeren Titel auch auf dem Aenderungsweg ab (E1).
    assertThatThrownBy(
            () ->
                angebot(Angebotsstatus.ANGELEGT)
                    .geaendert(ANGEBOTSDATUM, 9L, "   ", null, List.of(), JETZT))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
