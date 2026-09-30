package org.mwolff.fbcrm.angebot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.AnsprechpartnerRepository;

/**
 * Das Aendern eines Angebots (Issue #127, Kriterium 5).
 *
 * <p>Das Angebot wird als Ganzes geschrieben (E8): Datum, Ansprechpartner, Beschreibung und die
 * vollstaendige Positionsliste in der gewuenschten Reihenfolge — in jedem Status.
 *
 * <p>Der zweite Gegenstand sind die Positionskennungen (Plan #169, E2). Eine Position mit Kennung
 * sagt „dieselbe Position wie vorher"; gueltig sind darum nur die Kennungen der Positionen des
 * geladenen Angebots, und jede hoechstens einmal. Eine fremde und eine doppelte Kennung sind {@link
 * PositionenNichtWaehlbar}, und am Bestand kommt kein {@code save} an — die Pruefung laeuft vor dem
 * Schreiben.
 *
 * <p>Der dritte Gegenstand ist die Bindung berechneter Positionen (#160, Kriterium 28): Eine
 * Position, die in einer Rechnung steht, darf nicht entfallen und weder ihre Einheit noch ihre
 * Abrechnungsart wechseln — sonst verloere die Rechnung ihren Bezug. Text, Menge, Preis und die
 * Reihenfolge bleiben frei. Welche Positionen das sind, sagt {@link Positionsverwendung}; hier
 * antwortet darauf ein Doppel, damit das Angebot nichts vom Modul {@code rechnung} wissen muss.
 *
 * <p>Der vierte Gegenstand ist die Wahl des Ansprechpartners: Ein <em>neu</em> gewaehlter muss zur
 * Firma gehoeren und aktiv sein. Der bereits gespeicherte bleibt waehlbar, auch wenn er inzwischen
 * stillgelegt ist — sonst liesse sich ein Angebot nach dem Stilllegen seines Ansprechpartners gar
 * nicht mehr speichern. Eine abgewiesene Wahl schreibt nichts; den Nachweis fuehrt {@code
 * verifyNoMoreInteractions} nach dem einen Lesezugriff.
 */
@ExtendWith(MockitoExtension.class)
class AngebotAendernUseCaseTest {

  private static final long ANGEBOT = 11L;
  private static final long ANDERE_PERSON = 9L;
  private static final Instant JETZT = Instant.parse("2026-09-28T09:30:00Z");
  private static final LocalDate NEUES_DATUM = LocalDate.of(2026, 9, 25);
  private static final String NEUER_TEXT = "Ueberarbeitete Beschreibung";

  @Mock private AngebotRepository angebote;
  @Mock private AnsprechpartnerRepository personen;

  private final Verwendungsdoppel verwendung = new Verwendungsdoppel();

  private AngebotAendernUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase =
        new AngebotAendernUseCase(
            angebote,
            new Ansprechpartnerwahl(personen),
            verwendung,
            Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  /*
   * Ein Doppel statt eines Mocks: Der Port wird nur auf den Erfolgspfaden gefragt, ein
   * vorgestelltes when(...) waere in jedem Abweisungstest eine ungenutzte Stubbung. Die Karte je
   * Angebot haelt zugleich fest, dass nach DIESEM Angebot gefragt wird — nach einem anderen
   * gefragt, kaeme die leere Menge zurueck und die Bindung griffe nicht.
   */
  private static final class Verwendungsdoppel implements Positionsverwendung {

    private final Map<Long, Set<Long>> jeAngebot = new HashMap<>();

    @Override
    public Set<Long> verwendeteKennungen(final long angebotId) {
      return jeAngebot.getOrDefault(Long.valueOf(angebotId), Set.of());
    }
  }

  private void inEinerRechnung(final Long... kennungen) {
    verwendung.jeAngebot.put(Long.valueOf(ANGEBOT), Set.of(kennungen));
  }

  private static Angebotsposition mitEinheit(
      final Angebotsposition position, final Einheit einheit) {
    return new Angebotsposition(
        position.id(),
        position.bezeichnung(),
        position.abrechnungsmodus(),
        position.menge(),
        einheit,
        position.einzelpreis());
  }

  private static Angebotsposition mitModus(
      final Angebotsposition position, final Abrechnungsmodus modus) {
    return new Angebotsposition(
        position.id(),
        position.bezeichnung(),
        modus,
        position.menge(),
        position.einheit(),
        position.einzelpreis());
  }

  private static AngebotDaten daten(
      final @Nullable Long ansprechpartnerId, final List<Angebotsposition> positionen) {
    return new AngebotDaten(NEUES_DATUM, ansprechpartnerId, NEUER_TEXT, positionen);
  }

  private static Ansprechpartner person(final long id, final long firmaId, final boolean aktiv) {
    return new Ansprechpartner(
        Long.valueOf(id),
        firmaId,
        "Eva",
        "Adler",
        null,
        null,
        null,
        null,
        aktiv,
        Angebotsdoppel.ANGELEGT,
        Angebotsdoppel.ANGELEGT);
  }

  private static Angebotsposition mitKennung(
      final Angebotsposition position, final @Nullable Long id) {
    return new Angebotsposition(
        id,
        position.bezeichnung(),
        position.abrechnungsmodus(),
        position.menge(),
        position.einheit(),
        position.einzelpreis());
  }

  private static Angebotsposition ohneKennung(final Angebotsposition position) {
    return mitKennung(position, null);
  }

  private void angebotIst(final Angebot angebot) {
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.of(angebot));
  }

  private Angebot aendere(final AngebotDaten daten) {
    when(angebote.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));
    return useCase.aendere(ANGEBOT, daten);
  }

  @ParameterizedTest
  @EnumSource(Angebotsstatus.class)
  void aendere_givenAnyStatus_thenWritesTheNewValues(final Angebotsstatus status) {
    // Given — Kriterium 5: das Angebot bleibt in jedem Status aenderbar.
    angebotIst(Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT, status)));

    // When
    final Angebot geaendert = aendere(daten(null, List.of(Angebotsdoppel.KONZEPTION)));

    // Then
    assertThat(geaendert)
        .satisfies(
            a -> assertThat(a.status()).isEqualTo(status),
            a -> assertThat(a.angebotDatum()).isEqualTo(NEUES_DATUM),
            a -> assertThat(a.beschreibung()).isEqualTo(NEUER_TEXT),
            a -> assertThat(a.ansprechpartnerId()).isNull(),
            a -> assertThat(a.positionen()).containsExactly(Angebotsdoppel.KONZEPTION));
    verifyNoInteractions(personen);
  }

  @Test
  void aendere_thenTakesTheSubmittedOrderOfPositionen() {
    // Given — E8: die Liste ist die Reihenfolge.
    angebotIst(Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT)));

    // When
    final Angebot geaendert =
        aendere(daten(null, List.of(Angebotsdoppel.SCHULUNG, Angebotsdoppel.KONZEPTION)));

    // Then
    assertThat(geaendert.positionen())
        .containsExactly(Angebotsdoppel.SCHULUNG, Angebotsdoppel.KONZEPTION);
  }

  @Test
  void aendere_thenKeepsIdentityAndStampsTheChange() {
    // Given — Kennung, Firma und Anlagezeitpunkt bleiben.
    angebotIst(Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT)));

    // When
    final Angebot geaendert = aendere(daten(null, List.of()));

    // Then
    assertThat(geaendert.requireId()).isEqualTo(ANGEBOT);
    assertThat(geaendert.firmaId()).isEqualTo(Angebotsdoppel.FIRMA);
    assertThat(geaendert.createdAt()).isEqualTo(Angebotsdoppel.ANGELEGT);
    assertThat(geaendert.updatedAt()).isEqualTo(JETZT);
  }

  @Test
  void aendere_withANewActiveContactOfTheFirma_thenCarriesIt() {
    // Given
    angebotIst(Angebotsdoppel.angebot(ANGEBOT));
    when(personen.findById(ANDERE_PERSON))
        .thenReturn(Optional.of(person(ANDERE_PERSON, Angebotsdoppel.FIRMA, true)));

    // When
    final Angebot geaendert = aendere(daten(ANDERE_PERSON, List.of()));

    // Then
    assertThat(geaendert.ansprechpartnerId()).isEqualTo(ANDERE_PERSON);
  }

  @Test
  void aendere_keepingTheStoredContactThatIsNowStillgelegt_thenAllowed() {
    // Given — der gespeicherte Ansprechpartner wurde nach der Anlage stillgelegt.
    angebotIst(Angebotsdoppel.angebot(ANGEBOT));
    when(personen.findById(Angebotsdoppel.ANSPRECHPARTNER))
        .thenReturn(
            Optional.of(person(Angebotsdoppel.ANSPRECHPARTNER, Angebotsdoppel.FIRMA, false)));

    // When
    final Angebot geaendert = aendere(daten(Angebotsdoppel.ANSPRECHPARTNER, List.of()));

    // Then
    assertThat(geaendert.ansprechpartnerId()).isEqualTo(Angebotsdoppel.ANSPRECHPARTNER);
  }

  @Test
  void aendere_withANewStillgelegterContact_thenRejectsAndWritesNothing() {
    // Given
    angebotIst(Angebotsdoppel.angebot(ANGEBOT));
    when(personen.findById(ANDERE_PERSON))
        .thenReturn(Optional.of(person(ANDERE_PERSON, Angebotsdoppel.FIRMA, false)));

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, daten(ANDERE_PERSON, List.of())))
        .isInstanceOf(AnsprechpartnerNichtWaehlbar.class);
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }

  @Test
  void aendere_withAContactOfAnotherFirma_thenRejectsAndWritesNothing() {
    // Given
    angebotIst(Angebotsdoppel.angebot(ANGEBOT));
    when(personen.findById(ANDERE_PERSON))
        .thenReturn(Optional.of(person(ANDERE_PERSON, Angebotsdoppel.FREMDE_FIRMA, true)));

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, daten(ANDERE_PERSON, List.of())))
        .isInstanceOf(AnsprechpartnerNichtWaehlbar.class);
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }

  @Test
  void aendere_keepingTheStoredContactButItBelongsToAnotherFirma_thenRejects() {
    // Given — auch der unveraenderte muss zur Firma gehoeren; nur das Stilllegen ist ihm erlaubt.
    angebotIst(Angebotsdoppel.angebot(ANGEBOT));
    when(personen.findById(Angebotsdoppel.ANSPRECHPARTNER))
        .thenReturn(
            Optional.of(person(Angebotsdoppel.ANSPRECHPARTNER, Angebotsdoppel.FREMDE_FIRMA, true)));

    // When / Then
    assertThatThrownBy(
            () -> useCase.aendere(ANGEBOT, daten(Angebotsdoppel.ANSPRECHPARTNER, List.of())))
        .isInstanceOf(AnsprechpartnerNichtWaehlbar.class);
  }

  @Test
  void aendere_withAnUnknownContact_thenRejects() {
    // Given
    angebotIst(Angebotsdoppel.angebot(ANGEBOT));
    when(personen.findById(ANDERE_PERSON)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, daten(ANDERE_PERSON, List.of())))
        .isInstanceOf(AnsprechpartnerNichtWaehlbar.class);
  }

  @Test
  void aendere_keepingThePositionIdsOfTheAngebot_thenWritesThem() {
    // Given — Plan #169, E2: Die Kennungen des geladenen Angebots gehen durch und bleiben dran.
    angebotIst(Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT)));

    // When — dieselben Positionen, umgestellt.
    final Angebot geaendert =
        aendere(daten(null, List.of(Angebotsdoppel.SCHULUNG, Angebotsdoppel.KONZEPTION)));

    // Then
    assertThat(geaendert.positionen())
        .extracting(Angebotsposition::id)
        .containsExactly(Angebotsdoppel.SCHULUNG_ID, Angebotsdoppel.KONZEPTION_ID);
  }

  @Test
  void aendere_withAPositionWithoutAnId_thenTreatsItAsNew() {
    // Given — die Kennung ist freiwillig; ohne sie ist die Position neu.
    angebotIst(Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT)));
    final Angebotsposition neue = ohneKennung(Angebotsdoppel.KONZEPTION);

    // When
    final Angebot geaendert = aendere(daten(null, List.of(neue)));

    // Then
    assertThat(geaendert.positionen()).containsExactly(neue);
  }

  @Test
  void aendere_withAPositionIdOfAnotherAngebot_thenRejectsAndWritesNothing() {
    // Given — eine Kennung, die zu keiner Position dieses Angebots gehoert.
    angebotIst(Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT)));
    final Angebotsposition fremde =
        mitKennung(Angebotsdoppel.KONZEPTION, Angebotsdoppel.FREMDE_POSITION);

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, daten(null, List.of(fremde))))
        .isInstanceOf(PositionenNichtWaehlbar.class);
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }

  @Test
  void aendere_withTheSamePositionIdTwice_thenRejectsAndWritesNothing() {
    // Given — zwei Positionen koennen nicht dieselbe Zeile fortschreiben.
    angebotIst(Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT)));
    final List<Angebotsposition> doppelt =
        List.of(
            Angebotsdoppel.KONZEPTION,
            mitKennung(Angebotsdoppel.SCHULUNG, Angebotsdoppel.KONZEPTION_ID));

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, daten(null, doppelt)))
        .isInstanceOf(PositionenNichtWaehlbar.class);
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }

  @Test
  void aendere_droppingAPositionThatIsInARechnung_thenRejectsAndWritesNothing() {
    // Given — #160, Kriterium 28: Die berechnete Position verloere sonst ihren Bezug.
    angebotIst(Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT)));
    inEinerRechnung(Angebotsdoppel.KONZEPTION_ID);

    // When / Then
    assertThatThrownBy(
            () -> useCase.aendere(ANGEBOT, daten(null, List.of(Angebotsdoppel.SCHULUNG))))
        .isInstanceOf(PositionInRechnungVerwendet.class)
        .hasMessageContaining(Angebotsdoppel.KONZEPTION.bezeichnung())
        .asInstanceOf(InstanceOfAssertFactories.type(PositionInRechnungVerwendet.class))
        .extracting(PositionInRechnungVerwendet::felder)
        .satisfies(
            felder ->
                assertThat(felder)
                    .containsOnlyKeys(PositionInRechnungVerwendet.FELD)
                    .hasEntrySatisfying(
                        PositionInRechnungVerwendet.FELD,
                        meldungen ->
                            assertThat(meldungen)
                                .singleElement()
                                .asString()
                                .contains(Angebotsdoppel.KONZEPTION.bezeichnung())));
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }

  @Test
  void aendere_changingTheEinheitOfAPositionInARechnung_thenRejectsAndWritesNothing() {
    // Given — die Einheit steht so auf der Rechnung; sie zu wechseln aenderte deren Aussage.
    angebotIst(Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT)));
    inEinerRechnung(Angebotsdoppel.KONZEPTION_ID);
    final List<Angebotsposition> umgestellt =
        List.of(mitEinheit(Angebotsdoppel.KONZEPTION, Einheit.STUNDE), Angebotsdoppel.SCHULUNG);

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, daten(null, umgestellt)))
        .isInstanceOf(PositionInRechnungVerwendet.class)
        .hasMessageContaining(Angebotsdoppel.KONZEPTION.bezeichnung());
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }

  @Test
  void aendere_changingTheAbrechnungsmodusOfAPositionInARechnung_thenRejectsAndWritesNothing() {
    // Given — dasselbe fuer die Abrechnungsart: Aufwand und Festpreis sind nicht dasselbe.
    angebotIst(Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT)));
    inEinerRechnung(Angebotsdoppel.KONZEPTION_ID);
    final List<Angebotsposition> umgestellt =
        List.of(
            mitModus(Angebotsdoppel.KONZEPTION, Abrechnungsmodus.FESTPREIS),
            Angebotsdoppel.SCHULUNG);

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, daten(null, umgestellt)))
        .isInstanceOf(PositionInRechnungVerwendet.class)
        .hasMessageContaining(Angebotsdoppel.KONZEPTION.bezeichnung());
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }

  @Test
  void aendere_changingTextMengeAndPreisOfAPositionInARechnung_thenWrites() {
    // Given — Text, Menge und Preis bleiben frei; die Rechnung haelt ihre eigenen Werte fest.
    angebotIst(Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT)));
    inEinerRechnung(Angebotsdoppel.KONZEPTION_ID);
    final Angebotsposition neuGefasst =
        new Angebotsposition(
            Angebotsdoppel.KONZEPTION_ID,
            "Konzeption und Abstimmung",
            Angebotsdoppel.KONZEPTION.abrechnungsmodus(),
            new BigDecimal("4.00"),
            Angebotsdoppel.KONZEPTION.einheit(),
            new BigDecimal("999.00"));

    // When — und zugleich umgeordnet: die Reihenfolge ist ebenfalls frei.
    final Angebot geaendert = aendere(daten(null, List.of(Angebotsdoppel.SCHULUNG, neuGefasst)));

    // Then
    assertThat(geaendert.positionen()).containsExactly(Angebotsdoppel.SCHULUNG, neuGefasst);
  }

  @Test
  void aendere_droppingAPositionThatIsInNoRechnung_thenWrites() {
    // Given — nur die erste Position steht in einer Rechnung; die zweite ist frei.
    angebotIst(Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT)));
    inEinerRechnung(Angebotsdoppel.KONZEPTION_ID);

    // When
    final Angebot geaendert = aendere(daten(null, List.of(Angebotsdoppel.KONZEPTION)));

    // Then
    assertThat(geaendert.positionen()).containsExactly(Angebotsdoppel.KONZEPTION);
  }

  @Test
  void aendere_whenTheAngebotIsUnknown_thenRejects() {
    // Given
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, daten(null, List.of())))
        .isInstanceOf(AngebotNichtGefunden.class);
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }
}
