package org.mwolff.fbcrm.angebot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.params.provider.Arguments.arguments;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
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
 * <p>Das Angebot wird als Ganzes geschrieben (E8): Datum, Ansprechpartner, Beschreibung, das
 * Kennzeichen seiner Art und die vollstaendige Positionsliste in der gewuenschten Reihenfolge — in
 * jedem Status.
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
 *
 * <p><b>Der fuenfte Gegenstand ist das Kennzeichen der Art</b> (Issue #227, Kriterium 8 von #207).
 * Es laesst sich setzen und entfernen, solange aus dem Angebot keine Rechnung entstanden ist; das
 * sagt der Port {@link Rechnungsbindung}, auf den hier ebenfalls ein Doppel antwortet. Mit dem
 * Kennzeichen wandert der Status in die Reihe der neuen Art, und die Pflicht von Menge, Einheit und
 * Preis haengt an der <em>Ziel</em>art: Ein Angebot an einen Kunden braucht alle vier Angaben, die
 * interne Arbeit keine davon (E7, E8).
 */
@ExtendWith(MockitoExtension.class)
class AngebotAendernUseCaseTest {

  private static final long ANGEBOT = 11L;
  private static final long ANDERE_PERSON = 9L;
  private static final Instant JETZT = Instant.parse("2026-09-28T09:30:00Z");
  private static final LocalDate NEUES_DATUM = LocalDate.of(2026, 9, 25);
  private static final String NEUER_TEXT = "Ueberarbeitete Beschreibung";

  /** Die Bezeichnung, mit der eine neue Position eingereicht wird. */
  private static final String NEUE_ARBEIT = "Umbau der Ablage";

  @Mock private AngebotRepository angebote;
  @Mock private AnsprechpartnerRepository personen;

  private final Verwendungsdoppel verwendung = new Verwendungsdoppel();
  private final Bindungsdoppel bindung = new Bindungsdoppel();

  private AngebotAendernUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase =
        new AngebotAendernUseCase(
            angebote,
            new Ansprechpartnerwahl(personen),
            verwendung,
            bindung,
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

  /*
   * Dasselbe fuer die Rechnungsbindung, und aus demselben Grund ein Doppel: Gefragt wird nur beim
   * Artwechsel. Das Doppel merkt sich jede Frage — nur so laesst sich zeigen, dass eine Aenderung
   * ohne Artwechsel den Port gar nicht bemueht (Issue #227).
   */
  private static final class Bindungsdoppel implements Rechnungsbindung {

    private final Set<Long> mitRechnung = new HashSet<>();
    private final List<Long> gefragt = new ArrayList<>();

    @Override
    public boolean rechnungVorhanden(final long angebotId) {
      gefragt.add(Long.valueOf(angebotId));
      return mitRechnung.contains(Long.valueOf(angebotId));
    }
  }

  private void inEinerRechnung(final Long... kennungen) {
    verwendung.jeAngebot.put(Long.valueOf(ANGEBOT), Set.of(kennungen));
  }

  private void mitRechnung() {
    bindung.mitRechnung.add(Long.valueOf(ANGEBOT));
  }

  private static Positionsangabe mitEinheit(final Positionsangabe angabe, final Einheit einheit) {
    return new Positionsangabe(
        angabe.id(),
        angabe.bezeichnung(),
        angabe.abrechnungsmodus(),
        angabe.menge(),
        einheit,
        angabe.einzelpreis());
  }

  private static Positionsangabe mitModus(
      final Positionsangabe angabe, final Abrechnungsmodus modus) {
    return new Positionsangabe(
        angabe.id(),
        angabe.bezeichnung(),
        modus,
        angabe.menge(),
        angabe.einheit(),
        angabe.einzelpreis());
  }

  private static Positionsangabe mitKennung(final Positionsangabe angabe, final @Nullable Long id) {
    return new Positionsangabe(
        id,
        angabe.bezeichnung(),
        angabe.abrechnungsmodus(),
        angabe.menge(),
        angabe.einheit(),
        angabe.einzelpreis());
  }

  private static Positionsangabe ohneKennung(final Positionsangabe angabe) {
    return mitKennung(angabe, null);
  }

  /** Die gespeicherten Positionen als die Angaben, die die Maske zu ihnen einreicht. */
  private static List<Positionsangabe> angaben(final Angebotsposition... positionen) {
    return Arrays.stream(positionen).map(Angebotsdoppel::angabe).toList();
  }

  private static AngebotDaten daten(
      final @Nullable Long ansprechpartnerId, final List<Positionsangabe> positionen) {
    return daten(ansprechpartnerId, false, positionen);
  }

  private static AngebotDaten daten(
      final @Nullable Long ansprechpartnerId,
      final boolean intern,
      final List<Positionsangabe> positionen) {
    return new AngebotDaten(NEUES_DATUM, ansprechpartnerId, NEUER_TEXT, intern, positionen);
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

    // When — dieselbe Art wie bisher: das Kennzeichen wechselt nicht.
    final Angebot geaendert =
        aendere(daten(null, status.intern(), angaben(Angebotsdoppel.KONZEPTION)));

    // Then
    assertThat(geaendert)
        .satisfies(
            a -> assertThat(a.status()).isEqualTo(status),
            a -> assertThat(a.intern()).isEqualTo(status.intern()),
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
        aendere(daten(null, angaben(Angebotsdoppel.SCHULUNG, Angebotsdoppel.KONZEPTION)));

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

    final AngebotDaten aenderung = daten(ANDERE_PERSON, List.of());

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, aenderung))
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

    final AngebotDaten aenderung = daten(ANDERE_PERSON, List.of());

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, aenderung))
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

    final AngebotDaten aenderung = daten(Angebotsdoppel.ANSPRECHPARTNER, List.of());

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, aenderung))
        .isInstanceOf(AnsprechpartnerNichtWaehlbar.class);
  }

  @Test
  void aendere_withAnUnknownContact_thenRejects() {
    // Given
    angebotIst(Angebotsdoppel.angebot(ANGEBOT));
    when(personen.findById(ANDERE_PERSON)).thenReturn(Optional.empty());

    final AngebotDaten aenderung = daten(ANDERE_PERSON, List.of());

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, aenderung))
        .isInstanceOf(AnsprechpartnerNichtWaehlbar.class);
  }

  @Test
  void aendere_keepingThePositionIdsOfTheAngebot_thenWritesThem() {
    // Given — Plan #169, E2: Die Kennungen des geladenen Angebots gehen durch und bleiben dran.
    angebotIst(Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT)));

    // When — dieselben Positionen, umgestellt.
    final Angebot geaendert =
        aendere(daten(null, angaben(Angebotsdoppel.SCHULUNG, Angebotsdoppel.KONZEPTION)));

    // Then
    assertThat(geaendert.positionen())
        .extracting(Angebotsposition::id)
        .containsExactly(Angebotsdoppel.SCHULUNG_ID, Angebotsdoppel.KONZEPTION_ID);
  }

  @Test
  void aendere_withAPositionWithoutAnId_thenTreatsItAsNew() {
    // Given — die Kennung ist freiwillig; ohne sie ist die Position neu.
    angebotIst(Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT)));
    final Positionsangabe neue = ohneKennung(Angebotsdoppel.KONZEPTION_ANGABE);

    // When
    final Angebot geaendert = aendere(daten(null, List.of(neue)));

    // Then
    assertThat(geaendert.positionen())
        .singleElement()
        .satisfies(
            position -> assertThat(position.id()).isNull(),
            position ->
                assertThat(position.bezeichnung())
                    .isEqualTo(Angebotsdoppel.KONZEPTION.bezeichnung()));
  }

  @Test
  void aendere_withAPositionIdOfAnotherAngebot_thenRejectsAndWritesNothing() {
    // Given — eine Kennung, die zu keiner Position dieses Angebots gehoert.
    angebotIst(Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT)));
    final Positionsangabe fremde =
        mitKennung(Angebotsdoppel.KONZEPTION_ANGABE, Angebotsdoppel.FREMDE_POSITION);

    final AngebotDaten aenderung = daten(null, List.of(fremde));

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, aenderung))
        .isInstanceOf(PositionenNichtWaehlbar.class);
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }

  @Test
  void aendere_withTheSamePositionIdTwice_thenRejectsAndWritesNothing() {
    // Given — zwei Positionen koennen nicht dieselbe Zeile fortschreiben.
    angebotIst(Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT)));
    final List<Positionsangabe> doppelt =
        List.of(
            Angebotsdoppel.KONZEPTION_ANGABE,
            mitKennung(Angebotsdoppel.SCHULUNG_ANGABE, Angebotsdoppel.KONZEPTION_ID));

    final AngebotDaten aenderung = daten(null, doppelt);

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, aenderung))
        .isInstanceOf(PositionenNichtWaehlbar.class);
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }

  @Test
  void aendere_droppingAPositionThatIsInARechnung_thenRejectsAndWritesNothing() {
    // Given — #160, Kriterium 28: Die berechnete Position verloere sonst ihren Bezug.
    angebotIst(Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT)));
    inEinerRechnung(Angebotsdoppel.KONZEPTION_ID);

    final AngebotDaten aenderung = daten(null, angaben(Angebotsdoppel.SCHULUNG));

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, aenderung))
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
    final List<Positionsangabe> umgestellt =
        List.of(
            mitEinheit(Angebotsdoppel.KONZEPTION_ANGABE, Einheit.STUNDE),
            Angebotsdoppel.SCHULUNG_ANGABE);

    final AngebotDaten aenderung = daten(null, umgestellt);

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, aenderung))
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
    final List<Positionsangabe> umgestellt =
        List.of(
            mitModus(Angebotsdoppel.KONZEPTION_ANGABE, Abrechnungsmodus.FESTPREIS),
            Angebotsdoppel.SCHULUNG_ANGABE);

    final AngebotDaten aenderung = daten(null, umgestellt);

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, aenderung))
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
    final Positionsangabe neuGefasst =
        new Positionsangabe(
            Angebotsdoppel.KONZEPTION_ID,
            "Konzeption und Abstimmung",
            Angebotsdoppel.KONZEPTION.abrechnungsmodus(),
            new BigDecimal("4.00"),
            Angebotsdoppel.KONZEPTION.einheit(),
            new BigDecimal("999.00"));

    // When — und zugleich umgeordnet: die Reihenfolge ist ebenfalls frei.
    final Angebot geaendert =
        aendere(daten(null, List.of(Angebotsdoppel.SCHULUNG_ANGABE, neuGefasst)));

    // Then
    assertThat(geaendert.positionen())
        .extracting(Angebotsposition::bezeichnung, Angebotsposition::einzelpreis)
        .containsExactly(
            tuple(Angebotsdoppel.SCHULUNG.bezeichnung(), Angebotsdoppel.SCHULUNG.einzelpreis()),
            tuple("Konzeption und Abstimmung", new BigDecimal("999.00")));
  }

  @Test
  void aendere_droppingAPositionThatIsInNoRechnung_thenWrites() {
    // Given — nur die erste Position steht in einer Rechnung; die zweite ist frei.
    angebotIst(Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT)));
    inEinerRechnung(Angebotsdoppel.KONZEPTION_ID);

    // When
    final Angebot geaendert = aendere(daten(null, angaben(Angebotsdoppel.KONZEPTION)));

    // Then
    assertThat(geaendert.positionen()).containsExactly(Angebotsdoppel.KONZEPTION);
  }

  @Test
  void aendere_whenTheAngebotIsUnknown_thenRejects() {
    // Given
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.empty());

    final AngebotDaten aenderung = daten(null, List.of());

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, aenderung))
        .isInstanceOf(AngebotNichtGefunden.class);
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }

  @ParameterizedTest
  @CsvSource({
    "ANGELEGT,LAEUFT",
    "ABGEGEBEN,LAEUFT",
    "BESTELLT,LAEUFT",
    "ERLEDIGT,ABGESCHLOSSEN",
    "ABGERECHNET,ABGESCHLOSSEN"
  })
  void aendere_settingTheInternalFlag_thenMovesTheStatusIntoTheInternalRow(
      final Angebotsstatus vorher, final Angebotsstatus nachher) {
    // Given — Issue #227, Kriterium 8 von #207. ABGERECHNET ist dabei erreichbar, ohne dass eine
    // Rechnung besteht: AngebotStatusUseCase.weiter schaltet dorthin von Hand (Review-Fund 1).
    angebotIst(Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT, vorher)));

    // When
    final Angebot geaendert =
        aendere(daten(null, true, angaben(Angebotsdoppel.KONZEPTION, Angebotsdoppel.SCHULUNG)));

    // Then
    assertThat(geaendert.intern()).isTrue();
    assertThat(geaendert.status()).isEqualTo(nachher);
  }

  @ParameterizedTest
  @CsvSource({"LAEUFT,BESTELLT", "ABGESCHLOSSEN,ERLEDIGT"})
  void aendere_clearingTheInternalFlag_thenMovesTheStatusBackToTheCustomerRow(
      final Angebotsstatus vorher, final Angebotsstatus nachher) {
    // Given — der Weg zurueck: die interne Arbeit wird wieder ein Angebot an einen Kunden.
    angebotIst(Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT, vorher)));

    // When
    final Angebot geaendert =
        aendere(daten(null, false, angaben(Angebotsdoppel.KONZEPTION, Angebotsdoppel.SCHULUNG)));

    // Then
    assertThat(geaendert.intern()).isFalse();
    assertThat(geaendert.status()).isEqualTo(nachher);
  }

  @Test
  void aendere_settingTheInternalFlag_thenKeepsTheStoredMengeEinheitAndPreis() {
    // Given — Kriterium 8: Beim Wechsel nach innen bleiben die Werte gespeichert, obwohl die Maske
    // sie dann nicht mehr zeigt und darum auch nicht mehr sendet.
    angebotIst(Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT)));
    final Positionsangabe nurBezeichnung =
        new Positionsangabe(Angebotsdoppel.KONZEPTION_ID, "Konzeption", null, null, null, null);

    // When
    final Angebot geaendert = aendere(daten(null, true, List.of(nurBezeichnung)));

    // Then
    assertThat(geaendert.positionen()).containsExactly(Angebotsdoppel.KONZEPTION);
  }

  @Test
  void aendere_withANewPositionAtAnInternalAngebot_thenPrefillsAufwandStundeAndZero() {
    // Given — E8: Die vier Spalten bleiben pflichtig; eine neue interne Position bekommt darum
    // Werte, die nichts behaupten.
    angebotIst(
        Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT, Angebotsstatus.LAEUFT)));
    final Positionsangabe neue = new Positionsangabe(null, NEUE_ARBEIT, null, null, null, null);

    // When
    final Angebot geaendert = aendere(daten(null, true, List.of(neue)));

    // Then
    assertThat(geaendert.positionen())
        .containsExactly(
            new Angebotsposition(
                null,
                NEUE_ARBEIT,
                Abrechnungsmodus.AUFWAND,
                BigDecimal.ZERO,
                Einheit.STUNDE,
                BigDecimal.ZERO));
  }

  @Test
  void aendere_changingTheArtWhileARechnungExists_thenRejectsAndWritesNothing() {
    // Given — Kriterium 8: Solange aus dem Angebot eine Rechnung entstanden ist, bleibt die Art,
    // wie sie ist. Der Entwurf zaehlt dabei wie die gestellte Rechnung; welche Rechnungen das sind,
    // unterscheidet RechnungsPositionsverwendungTest.
    angebotIst(Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT)));
    mitRechnung();

    final AngebotDaten aenderung = daten(null, true, angaben(Angebotsdoppel.KONZEPTION));

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, aenderung))
        .isInstanceOf(KennzeichenNichtAenderbar.class)
        .asInstanceOf(InstanceOfAssertFactories.type(KennzeichenNichtAenderbar.class))
        .extracting(KennzeichenNichtAenderbar::felder)
        .satisfies(
            felder ->
                assertThat(felder)
                    .containsExactly(
                        entry(
                            KennzeichenNichtAenderbar.FELD,
                            List.of(KennzeichenNichtAenderbar.MELDUNG))));
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }

  @Test
  void aendere_withARechnungButWithoutChangingTheArt_thenWritesAndNeverAsksTheBindung() {
    // Given — gefragt wird nur beim Artwechsel: Wer nur den Text aendert, soll die Rechnungen des
    // Angebots gar nicht erst lesen lassen.
    angebotIst(Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT)));
    mitRechnung();

    // When
    final Angebot geaendert = aendere(daten(null, false, angaben(Angebotsdoppel.KONZEPTION)));

    // Then
    assertThat(geaendert.beschreibung()).isEqualTo(NEUER_TEXT);
    assertThat(bindung.gefragt).isEmpty();
  }

  private static Stream<Arguments> fehlendeAngaben() {
    return Stream.of(
        arguments(
            new Positionsangabe(
                null, NEUE_ARBEIT, Abrechnungsmodus.AUFWAND, null, Einheit.STUNDE, BigDecimal.TEN),
            "positionen[0].menge"),
        arguments(
            new Positionsangabe(
                null, NEUE_ARBEIT, Abrechnungsmodus.AUFWAND, BigDecimal.ONE, null, BigDecimal.TEN),
            "positionen[0].einheit"),
        arguments(
            new Positionsangabe(
                null, NEUE_ARBEIT, Abrechnungsmodus.AUFWAND, BigDecimal.ONE, Einheit.STUNDE, null),
            "positionen[0].einzelpreis"),
        arguments(
            new Positionsangabe(
                null, NEUE_ARBEIT, null, BigDecimal.ONE, Einheit.STUNDE, BigDecimal.TEN),
            "positionen[0].abrechnungsmodus"));
  }

  @ParameterizedTest
  @MethodSource("fehlendeAngaben")
  void aendere_clearingTheInternalFlagWithAnIncompletePosition_thenRejectsAtThatField(
      final Positionsangabe angabe, final String feld) {
    // Given — E7: Ein Angebot an einen Kunden braucht alle vier Angaben, und welche fehlt, sagt die
    // Antwort am Feld der Position.
    angebotIst(
        Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT, Angebotsstatus.LAEUFT)));

    final AngebotDaten aenderung = daten(null, false, List.of(angabe));

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, aenderung))
        .isInstanceOf(Positionsangaben.class)
        .asInstanceOf(InstanceOfAssertFactories.type(Positionsangaben.class))
        .extracting(Positionsangaben::felder)
        .satisfies(
            felder ->
                assertThat(felder)
                    .containsOnlyKeys(feld)
                    .containsEntry(feld, List.of(Positionsangaben.ANGABE_FEHLT)));
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }

  @Test
  void aendere_withAnIncompletePositionAtAnExternalAngebot_thenNamesEveryMissingField() {
    // Given — alle fehlenden Angaben auf einmal: Wer vier nachtragen muss, soll es in einem Gang
    // tun koennen. Die zweite Position zeigt, dass der Platz im Feldnamen steht.
    angebotIst(Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT)));
    final List<Positionsangabe> liste =
        List.of(
            Angebotsdoppel.KONZEPTION_ANGABE,
            new Positionsangabe(null, NEUE_ARBEIT, null, null, null, null));

    final AngebotDaten aenderung = daten(null, false, liste);

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, aenderung))
        .isInstanceOf(Positionsangaben.class)
        .asInstanceOf(InstanceOfAssertFactories.type(Positionsangaben.class))
        .extracting(Positionsangaben::felder)
        .satisfies(
            felder ->
                assertThat(felder)
                    .containsOnlyKeys(
                        "positionen[1].menge",
                        "positionen[1].einheit",
                        "positionen[1].einzelpreis",
                        "positionen[1].abrechnungsmodus"));
  }

  @Test
  void aendere_atAnInternalAngebotWithoutChangingTheArt_thenKeepsTheStoredValues() {
    // Given — die Pflicht haengt an der Zielart, nicht am Wechsel: Auch wer ein internes Angebot
    // nur umbenennt, schickt Menge, Einheit und Preis nicht mit.
    angebotIst(
        Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT, Angebotsstatus.LAEUFT)));
    final Positionsangabe umbenannt =
        new Positionsangabe(Angebotsdoppel.KONZEPTION_ID, "Umbau", null, null, null, null);

    // When
    final Angebot geaendert = aendere(daten(null, true, List.of(umbenannt)));

    // Then
    assertThat(geaendert.positionen())
        .singleElement()
        .satisfies(
            position -> assertThat(position.bezeichnung()).isEqualTo("Umbau"),
            position -> assertThat(position.menge()).isEqualTo(Angebotsdoppel.KONZEPTION.menge()),
            position ->
                assertThat(position.einheit()).isEqualTo(Angebotsdoppel.KONZEPTION.einheit()),
            position ->
                assertThat(position.einzelpreis())
                    .isEqualTo(Angebotsdoppel.KONZEPTION.einzelpreis()));
  }
}
