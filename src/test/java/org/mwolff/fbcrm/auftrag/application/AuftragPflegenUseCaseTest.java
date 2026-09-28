package org.mwolff.fbcrm.auftrag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
import org.mwolff.fbcrm.auftrag.domain.Auftrag;
import org.mwolff.fbcrm.auftrag.domain.AuftragRepository;
import org.mwolff.fbcrm.auftrag.domain.Auftragsstatus;
import org.mwolff.fbcrm.vorgang.application.EreignisVermerkenUseCase;
import org.mwolff.fbcrm.vorgang.domain.VorgangRepository;

/**
 * Das Pflegen eines Auftrags (Kriterien 7, 8; F3, F6).
 *
 * <p>Ein Uebergang fuer alle vier aenderbaren Angaben und keine vier einzelnen (Plan E8): Kriterium
 * 7 nennt sie in einem Atemzug. Die Positionen kommen in den Daten gar nicht vor — was sich nicht
 * aendern darf, taucht in der Signatur nicht auf (F3).
 *
 * <p><b>Das Ereignis nur beim echten Wechsel</b> (Plan E9): Ein Aufruf, der nur die Bestellnummer
 * aendert, schreibt keine Zeile in die Historie. Die Zeile, die beim Wechsel entsteht, traegt das
 * <b>Wort</b> des Zielstatus — und darum prueft jeder der drei Faelle den Text namentlich.
 *
 * <p><b>Keine Sperre am abgeschlossenen Vorgang</b> (Plan E14, Kriterium 11): Dieser Anwendungsfall
 * kennt den Vorgang gar nicht, und genau das ist die einfachste Form dieser Zusage.
 */
@ExtendWith(MockitoExtension.class)
class AuftragPflegenUseCaseTest {

  private static final long AUFTRAG = 7L;
  private static final Instant JETZT = Instant.parse("2026-09-28T10:15:00Z");
  private static final LocalDate NEUER_TAG = LocalDate.of(2026, 10, 5);
  private static final LocalDate NEU_AB = LocalDate.of(2026, 11, 1);
  private static final LocalDate NEU_BIS = LocalDate.of(2027, 1, 31);

  @Mock private AuftragRepository auftraege;
  @Mock private AngebotRepository angebote;
  @Mock private EreignisVermerkenUseCase ereignisse;

  private AuftragPflegenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase =
        new AuftragPflegenUseCase(
            auftraege, angebote, ereignisse, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private void imBestand(final Auftragsstatus status) {
    when(auftraege.findById(AUFTRAG))
        .thenReturn(Optional.of(Auftragsdoppel.auftrag(AUFTRAG, status)));
    when(auftraege.save(any(Auftrag.class))).thenAnswer(aufruf -> aufruf.getArgument(0));
    when(angebote.findById(Auftragsdoppel.ANGEBOT))
        .thenReturn(Optional.of(Auftragsdoppel.angebot(Angebotszustand.ANGENOMMEN)));
  }

  private static AuftragPflegedaten daten(final Auftragsstatus status) {
    return new AuftragPflegedaten(NEUER_TAG, "BST-0815", NEU_AB, NEU_BIS, status);
  }

  private Auftrag geschrieben() {
    final ArgumentCaptor<Auftrag> gespeichert = ArgumentCaptor.forClass(Auftrag.class);
    verify(auftraege).save(gespeichert.capture());
    return gespeichert.getValue();
  }

  @Test
  void pflege_thenCarriesAllFourFieldsAndLeavesTheRestAlone() {
    // Given — Kriterium 7: genau vier Angaben sind aenderbar, die Positionen nicht (F3).
    imBestand(Auftragsstatus.OFFEN);

    // When
    final AuftragAnsicht ansicht = useCase.pflege(AUFTRAG, daten(Auftragsstatus.IN_ARBEIT));

    // Then
    final Auftrag gespeichert = geschrieben();
    assertThat(gespeichert.auftragDatum()).isEqualTo(NEUER_TAG);
    assertThat(gespeichert.kundenbestellnummer()).isEqualTo("BST-0815");
    assertThat(gespeichert.leistungAb()).isEqualTo(NEU_AB);
    assertThat(gespeichert.leistungBis()).isEqualTo(NEU_BIS);
    assertThat(gespeichert.status()).isEqualTo(Auftragsstatus.IN_ARBEIT);
    assertThat(gespeichert.nummer()).isEqualTo(Auftragsdoppel.AUFTRAGSNUMMER);
    assertThat(gespeichert.angebotId()).isEqualTo(Auftragsdoppel.ANGEBOT);
    assertThat(gespeichert.positionen())
        .isEqualTo(Auftragsdoppel.auftrag(AUFTRAG, Auftragsstatus.OFFEN).positionen());
    assertThat(gespeichert.updatedAt()).isEqualTo(JETZT);
    assertThat(ansicht.angebotNummer()).isEqualTo(Auftragsdoppel.ANGEBOTSNUMMER);
  }

  @Test
  void pflege_thenClearsTheOptionalFieldsThatCameEmpty() {
    // Given — was leer eingereicht wird, steht danach leer da und bleibt nicht stehen.
    imBestand(Auftragsstatus.OFFEN);

    // When
    useCase.pflege(
        AUFTRAG, new AuftragPflegedaten(NEUER_TAG, null, null, null, Auftragsstatus.OFFEN));

    // Then
    final Auftrag gespeichert = geschrieben();
    assertThat(gespeichert.kundenbestellnummer()).isNull();
    assertThat(gespeichert.leistungAb()).isNull();
    assertThat(gespeichert.leistungBis()).isNull();
  }

  @Test
  void pflege_withoutAStatusChange_thenWritesNoEreignis() {
    // Given — Plan E9: ein Aufruf, der nur die Bestellnummer aendert, ist kein Statuswechsel.
    imBestand(Auftragsstatus.IN_ARBEIT);

    // When
    useCase.pflege(AUFTRAG, daten(Auftragsstatus.IN_ARBEIT));

    // Then
    verify(ereignisse, never()).vermerken(anyLong(), anyString());
  }

  @ParameterizedTest(name = "{0} → {1} vermerkt „{2}\"")
  @CsvSource({
    "IN_ARBEIT, OFFEN, offen",
    "OFFEN, IN_ARBEIT, in Arbeit",
    "OFFEN, ABGESCHLOSSEN, abgeschlossen"
  })
  void pflege_withAStatusChange_thenWritesExactlyOneEreignisNamingTheStatus(
      final Auftragsstatus vorher, final Auftragsstatus nachher, final String wort) {
    // Given — Kriterium 8, Plan E9.
    imBestand(vorher);

    // When
    useCase.pflege(AUFTRAG, daten(nachher));

    // Then
    verify(ereignisse)
        .vermerken(
            Auftragsdoppel.VORGANG,
            "Auftrag " + Auftragsdoppel.AUFTRAGSNUMMER + " auf „" + wort + "\" gesetzt");
  }

  @Test
  void pflege_fromAbgeschlossenBackToInArbeit_thenIsAllowed() {
    // Given — F6: es gibt keinen endgueltigen Status, der Wechsel geht in jede Richtung.
    imBestand(Auftragsstatus.ABGESCHLOSSEN);

    // When
    useCase.pflege(AUFTRAG, daten(Auftragsstatus.IN_ARBEIT));

    // Then
    assertThat(geschrieben().status()).isEqualTo(Auftragsstatus.IN_ARBEIT);
  }

  @Test
  void pflege_givenAnUnknownAuftrag_thenRejectsAndWritesNothing() {
    // Given
    when(auftraege.findById(AUFTRAG)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.pflege(AUFTRAG, daten(Auftragsstatus.OFFEN)))
        .isInstanceOf(AuftragNichtGefunden.class);
    verifyNoInteractions(angebote, ereignisse);
    verify(auftraege, never()).save(any(Auftrag.class));
  }

  @Test
  void pflege_thenNeverAsksForTheVorgang() {
    // When / Then — Plan E14: die Sperre am abgeschlossenen Vorgang reicht nur bis zum Anlegen,
    // und dieser Anwendungsfall kennt den Vorgang darum gar nicht.
    assertThat(
            Stream.of(AuftragPflegenUseCase.class.getDeclaredFields()).map(Field::getType).toList())
        .doesNotContain(VorgangRepository.class);
  }
}
