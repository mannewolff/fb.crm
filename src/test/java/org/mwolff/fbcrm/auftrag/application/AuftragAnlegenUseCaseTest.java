package org.mwolff.fbcrm.auftrag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
import org.mwolff.fbcrm.auftrag.domain.Auftrag;
import org.mwolff.fbcrm.auftrag.domain.AuftragRepository;
import org.mwolff.fbcrm.auftrag.domain.AuftragsnummerRepository;
import org.mwolff.fbcrm.auftrag.domain.Auftragsposition;
import org.mwolff.fbcrm.auftrag.domain.Auftragsstatus;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.vorgang.application.EreignisVermerkenUseCase;
import org.mwolff.fbcrm.vorgang.domain.VorgangRepository;

/**
 * Das Anlegen eines Auftrags aus einem angenommenen Angebot (Kriterien 1 bis 6, 8, 11; F2, F9).
 *
 * <p>Vier Vorbedingungen stehen vor dem ersten Schreibzugriff, und ihre Reihenfolge ist Teil der
 * Zusage: das Angebot muss es geben, es muss angenommen sein (Kriterium 1), der Vorgang darf nicht
 * abgeschlossen sein (Kriterium 11), und einen Auftrag darf es noch nicht geben (F9). Erst danach
 * folgt die Positionspruefung.
 *
 * <p><b>Der Preis kommt aus dem Angebot und nie aus der Anfrage</b> (Plan E7, Kriterium 2): Die
 * Anfrage traegt je Position nur Platz, Menge und „Stunden je Personentag". Bezeichnung,
 * Abrechnungsmodus, Einheit und Einzelpreis liest der Anwendungsfall aus dem Angebot — sonst waere
 * genau die Angabe vom Absender bestimmbar, die Kriterium 2 schuetzt.
 *
 * <p>Die Uhr steht auf 22:30 UTC: In der Geschaeftszone ist da bereits der naechste Tag. Nur so
 * zeigt sich, dass der vorbelegte Auftragstag und das Jahr der Nummer aus {@code
 * common.Geschaeftszone} kommen und nicht aus UTC.
 */
@ExtendWith(MockitoExtension.class)
class AuftragAnlegenUseCaseTest {

  private static final Instant JETZT = Instant.parse("2026-09-27T22:30:00Z");
  private static final LocalDate HEUTE_IN_BERLIN = LocalDate.of(2026, 9, 28);
  private static final LocalDate GEWUENSCHTER_TAG = LocalDate.of(2026, 9, 15);
  private static final int JAHR = 2026;

  @Mock private AuftragRepository auftraege;
  @Mock private AuftragsnummerRepository nummern;
  @Mock private AngebotRepository angebote;
  @Mock private VorgangRepository vorgaenge;
  @Mock private EreignisVermerkenUseCase ereignisse;

  private AuftragAnlegenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase =
        new AuftragAnlegenUseCase(
            auftraege,
            nummern,
            angebote,
            vorgaenge,
            ereignisse,
            Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private static AuftragPositionwahl wahl(
      final int platz, final String menge, final String stunden) {
    return new AuftragPositionwahl(
        platz, new BigDecimal(menge), stunden == null ? null : new BigDecimal(stunden));
  }

  private static AuftragDaten daten(final List<AuftragPositionwahl> positionen) {
    return new AuftragDaten(null, "BST-4711", null, null, positionen);
  }

  private static AuftragDaten beideZeilen() {
    return daten(List.of(wahl(1, "2.50", "7.50"), wahl(2, "1.00", null)));
  }

  private void angebotIst(final Angebotszustand zustand) {
    when(angebote.findById(Auftragsdoppel.ANGEBOT))
        .thenReturn(Optional.of(Auftragsdoppel.angebot(zustand)));
  }

  private void vorgangIstOffen() {
    when(vorgaenge.findById(Auftragsdoppel.VORGANG))
        .thenReturn(Optional.of(Auftragsdoppel.vorgang(false)));
  }

  /* Der ganze Weg frei: angenommenes Angebot, offener Vorgang, noch kein Auftrag. */
  private void wegFrei() {
    angebotIst(Angebotszustand.ANGENOMMEN);
    vorgangIstOffen();
    when(auftraege.findByAngebot(Auftragsdoppel.ANGEBOT)).thenReturn(Optional.empty());
  }

  private Auftrag legeAn(final AuftragDaten daten) {
    when(nummern.zieheNummer(JAHR)).thenReturn(Auftragsdoppel.AUFTRAGSNUMMER);
    when(auftraege.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));
    return useCase.anlegen(Auftragsdoppel.ANGEBOT, daten).auftrag();
  }

  @Test
  void anlegen_thenTakesTheAngebotspositionenUnchanged() {
    // Given — Kriterium 2, R7: Bezeichnung, Modus, Einheit und Einzelpreis kommen aus dem Angebot.
    wegFrei();

    // When
    final Auftrag angelegt = legeAn(beideZeilen());

    // Then
    assertThat(angelegt.positionen())
        .extracting(
            Auftragsposition::bezeichnung,
            Auftragsposition::abrechnungsmodus,
            Auftragsposition::einheit,
            Auftragsposition::einzelpreis)
        .containsExactly(
            tuple(
                "Konzeption",
                Abrechnungsmodus.AUFWAND,
                Einheit.PERSONENTAG,
                new BigDecimal("1000.01")),
            tuple(
                "Schulungstag",
                Abrechnungsmodus.FESTPREIS,
                Einheit.PAUSCHAL,
                new BigDecimal("1200.00")));
  }

  @Test
  void anlegen_thenLeavesTheAngebotUntouched() {
    // Given — R7: die Quelle bleibt, wie sie ist.
    wegFrei();

    // When
    legeAn(beideZeilen());

    // Then
    verify(angebote, never()).save(any());
  }

  @Test
  void anlegen_thenCarriesTheAgreedMengeAndTheStundenJePersonentag() {
    // Given — F2: die Menge darf verringert werden, der Preis nicht angetastet.
    wegFrei();

    // When
    final Auftrag angelegt = legeAn(daten(List.of(wahl(1, "1.00", "8.00"))));

    // Then
    assertThat(angelegt.positionen())
        .singleElement()
        .extracting(Auftragsposition::menge, Auftragsposition::stundenJePersonentag)
        .containsExactly(new BigDecimal("1.00"), new BigDecimal("8.00"));
  }

  @Test
  void anlegen_whenAPositionIsLeftOut_thenTheRestMovesUpWithoutAGap() {
    // Given — F2: entfernen ist erlaubt; die Reihenfolge der Liste ist der Platz (E24).
    wegFrei();

    // When
    final Auftrag angelegt = legeAn(daten(List.of(wahl(2, "1.00", null))));

    // Then
    assertThat(angelegt.positionen())
        .singleElement()
        .extracting(Auftragsposition::bezeichnung)
        .isEqualTo("Schulungstag");
  }

  @Test
  void anlegen_thenStartsAsOffenAtTheVorgangOfTheAngebot() {
    // Given — Kriterium 6.
    wegFrei();

    // When
    final Auftrag angelegt = legeAn(beideZeilen());

    // Then
    assertThat(angelegt.status()).isEqualTo(Auftragsstatus.OFFEN);
    assertThat(angelegt.vorgangId()).isEqualTo(Auftragsdoppel.VORGANG);
    assertThat(angelegt.angebotId()).isEqualTo(Auftragsdoppel.ANGEBOT);
  }

  @Test
  void anlegen_thenTakesItsNumberFromTheNummernkreisOfTheYearOfCreation() {
    // Given — Kriterium 3: das Jahr des Anlegens in der Geschaeftszone, nicht das des
    // Auftragsdatums.
    wegFrei();

    // When
    final Auftrag angelegt =
        legeAn(
            new AuftragDaten(
                LocalDate.of(2025, 12, 31), null, null, null, List.of(wahl(1, "2.50", "7.50"))));

    // Then
    assertThat(angelegt.nummer()).isEqualTo(Auftragsdoppel.AUFTRAGSNUMMER);
    verify(nummern).zieheNummer(JAHR);
  }

  @Test
  void anlegen_withoutAnAuftragDatum_thenUsesTodayInTheGeschaeftszone() {
    // Given — Fund 5: fehlt der Tag, setzt ihn der Anwendungsfall, nicht der Browser.
    wegFrei();

    // When
    final Auftrag angelegt = legeAn(daten(List.of(wahl(1, "2.50", "7.50"))));

    // Then
    assertThat(angelegt.auftragDatum()).isEqualTo(HEUTE_IN_BERLIN);
  }

  @Test
  void anlegen_withAnAuftragDatum_thenKeepsIt() {
    // Given — Kriterium 3: das Auftragsdatum ist frei setzbar.
    wegFrei();

    // When
    final Auftrag angelegt =
        legeAn(
            new AuftragDaten(GEWUENSCHTER_TAG, null, null, null, List.of(wahl(1, "2.50", "7.50"))));

    // Then
    assertThat(angelegt.auftragDatum()).isEqualTo(GEWUENSCHTER_TAG);
  }

  @Test
  void anlegen_thenKeepsBestellnummerAndLeistungszeitraum() {
    // Given — Kriterium 3: beide Angaben kommen aus der Anfrage.
    wegFrei();

    // When
    final Auftrag angelegt =
        legeAn(
            new AuftragDaten(
                null,
                "BST-4711",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 12, 31),
                List.of(wahl(1, "2.50", "7.50"))));

    // Then
    assertThat(angelegt.kundenbestellnummer()).isEqualTo("BST-4711");
    assertThat(angelegt.leistungAb()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(angelegt.leistungBis()).isEqualTo(LocalDate.of(2026, 12, 31));
  }

  @Test
  void anlegen_thenNotesTheEventExactlyOnceWithTheNumber() {
    // Given — Kriterium 8, Plan E9: in derselben Transaktion, ein Eintrag.
    wegFrei();

    // When
    legeAn(beideZeilen());

    // Then
    verify(ereignisse, times(1))
        .vermerken(
            Auftragsdoppel.VORGANG, "Auftrag " + Auftragsdoppel.AUFTRAGSNUMMER + " angelegt");
  }

  @Test
  void anlegen_thenAnswersWithTheNummerOfTheSourceAngebot() {
    // Given — Kriterium 3: am Auftrag ist zu sehen, aus welchem Angebot er entstand.
    wegFrei();
    when(nummern.zieheNummer(JAHR)).thenReturn(Auftragsdoppel.AUFTRAGSNUMMER);
    when(auftraege.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));

    // When
    final AuftragAnsicht ansicht = useCase.anlegen(Auftragsdoppel.ANGEBOT, beideZeilen());

    // Then
    assertThat(ansicht.angebotNummer()).isEqualTo(Auftragsdoppel.ANGEBOTSNUMMER);
  }

  @ParameterizedTest(name = "Zustand {0}")
  @EnumSource(value = Angebotszustand.class, names = "ANGENOMMEN", mode = EnumSource.Mode.EXCLUDE)
  void anlegen_fromAnAngebotThatIsNotAccepted_thenRejects(final Angebotszustand zustand) {
    // Given — Kriterium 1: nur aus einem angenommenen Angebot entsteht ein Auftrag.
    angebotIst(zustand);

    // When / Then
    assertThatThrownBy(() -> useCase.anlegen(Auftragsdoppel.ANGEBOT, beideZeilen()))
        .isInstanceOf(AngebotNichtWaehlbar.class);
    verify(auftraege, never()).save(any());
    verifyNoInteractions(nummern, ereignisse);
  }

  @Test
  void anlegen_atAClosedVorgang_thenRejectsAndWritesNothing() {
    // Given — Kriterium 11.
    angebotIst(Angebotszustand.ANGENOMMEN);
    when(vorgaenge.findById(Auftragsdoppel.VORGANG))
        .thenReturn(Optional.of(Auftragsdoppel.vorgang(true)));

    // When / Then
    assertThatThrownBy(() -> useCase.anlegen(Auftragsdoppel.ANGEBOT, beideZeilen()))
        .isInstanceOf(VorgangAbgeschlossen.class);
    verify(auftraege, never()).save(any());
    verifyNoInteractions(nummern, ereignisse);
  }

  @Test
  void anlegen_whenTheAngebotAlreadyHasAnAuftrag_thenRejects() {
    // Given — F9: zu einem Angebot gehoert hoechstens ein Auftrag.
    angebotIst(Angebotszustand.ANGENOMMEN);
    vorgangIstOffen();
    when(auftraege.findByAngebot(Auftragsdoppel.ANGEBOT))
        .thenReturn(Optional.of(Auftragsdoppel.auftrag(7L)));

    // When / Then
    assertThatThrownBy(() -> useCase.anlegen(Auftragsdoppel.ANGEBOT, beideZeilen()))
        .isInstanceOf(AuftragBereitsVorhanden.class);
    verify(auftraege, never()).save(any());
    verifyNoInteractions(nummern, ereignisse);
  }

  @Test
  void anlegen_fromAnUnknownAngebot_thenRejects() {
    // Given — fuer das fehlende Angebot gilt der 404 des Angebot-Moduls.
    when(angebote.findById(Auftragsdoppel.ANGEBOT)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.anlegen(Auftragsdoppel.ANGEBOT, beideZeilen()))
        .isInstanceOf(AngebotNichtGefunden.class);
    verify(auftraege, never()).save(any());
  }

  @ParameterizedTest(name = "Platz {0}")
  @ValueSource(ints = {0, 3})
  void anlegen_withAnUnknownPlatz_thenNamesTheField(final int platz) {
    // Given — Plan E20: der Platz ist index + 1 der Angebotsliste; 0 und 3 gibt es nicht.
    wegFrei();

    // When / Then
    assertThatThrownBy(
            () ->
                useCase.anlegen(
                    Auftragsdoppel.ANGEBOT, daten(List.of(wahl(platz, "1.00", "7.50")))))
        .isInstanceOf(AuftragsuebernahmeUngueltig.class)
        .extracting(fehler -> ((AuftragsuebernahmeUngueltig) fehler).felder())
        .satisfies(felder -> assertThat(felder).containsOnlyKeys("positionen[0].platz"));
    verify(auftraege, never()).save(any());
    verifyNoInteractions(nummern, ereignisse);
  }

  @Test
  void anlegen_withTheSamePlatzTwice_thenNamesTheField() {
    // Given — F2: keine Position hinzufuegen. Zweimal derselbe Platz waere genau das.
    wegFrei();

    // When / Then
    assertThatThrownBy(
            () ->
                useCase.anlegen(
                    Auftragsdoppel.ANGEBOT,
                    daten(List.of(wahl(1, "1.00", "7.50"), wahl(1, "1.00", "7.50")))))
        .isInstanceOf(AuftragsuebernahmeUngueltig.class)
        .extracting(fehler -> ((AuftragsuebernahmeUngueltig) fehler).felder())
        .satisfies(felder -> assertThat(felder).containsOnlyKeys("positionen[1].platz"));
  }

  @Test
  void anlegen_withAMengeAboveTheAngebot_thenNamesTheField() {
    // Given — Kriterium 2, F2: die Menge darf nicht erhoeht werden.
    wegFrei();

    // When / Then
    assertThatThrownBy(
            () ->
                useCase.anlegen(
                    Auftragsdoppel.ANGEBOT,
                    daten(List.of(wahl(1, "2.50", "7.50"), wahl(2, "2.00", null)))))
        .isInstanceOf(AuftragsuebernahmeUngueltig.class)
        .extracting(fehler -> ((AuftragsuebernahmeUngueltig) fehler).felder())
        .satisfies(felder -> assertThat(felder).containsOnlyKeys("positionen[1].menge"));
  }

  @Test
  void anlegen_withoutAnyMengeAboveZero_thenNamesTheList() {
    // Given — ein Auftrag ohne bestellte Menge ist kein Auftrag.
    wegFrei();

    // When / Then
    assertThatThrownBy(
            () -> useCase.anlegen(Auftragsdoppel.ANGEBOT, daten(List.of(wahl(1, "0.00", "7.50")))))
        .isInstanceOf(AuftragsuebernahmeUngueltig.class)
        .extracting(fehler -> ((AuftragsuebernahmeUngueltig) fehler).felder())
        .satisfies(felder -> assertThat(felder).containsOnlyKeys("positionen"));
  }

  @Test
  void anlegen_withStundenAtAFestpreisPosition_thenNamesTheField() {
    // Given — E10: der Faktor haengt am Abrechnungsmodus.
    wegFrei();

    // When / Then
    assertThatThrownBy(
            () -> useCase.anlegen(Auftragsdoppel.ANGEBOT, daten(List.of(wahl(2, "1.00", "7.50")))))
        .isInstanceOf(AuftragsuebernahmeUngueltig.class)
        .extracting(fehler -> ((AuftragsuebernahmeUngueltig) fehler).felder())
        .satisfies(
            felder -> assertThat(felder).containsOnlyKeys("positionen[0].stundenJePersonentag"));
  }

  @Test
  void anlegen_withoutStundenAtAnAufwandPosition_thenNamesTheSameField() {
    // Given — E10: ein Personentag ohne Stunden waere keine Umrechnung.
    wegFrei();

    // When / Then
    assertThatThrownBy(
            () -> useCase.anlegen(Auftragsdoppel.ANGEBOT, daten(List.of(wahl(1, "1.00", null)))))
        .isInstanceOf(AuftragsuebernahmeUngueltig.class)
        .extracting(fehler -> ((AuftragsuebernahmeUngueltig) fehler).felder())
        .satisfies(
            felder -> assertThat(felder).containsOnlyKeys("positionen[0].stundenJePersonentag"));
  }

  @Test
  void anlegen_whenThePruefungFails_thenNoNumberIsDrawn() {
    // Given — eine abgewiesene Uebernahme darf keine Nummer verbrauchen.
    wegFrei();

    // When / Then
    assertThatThrownBy(
            () -> useCase.anlegen(Auftragsdoppel.ANGEBOT, daten(List.of(wahl(9, "1.00", null)))))
        .isInstanceOf(AuftragsuebernahmeUngueltig.class);
    verifyNoInteractions(nummern, ereignisse);
  }
}
