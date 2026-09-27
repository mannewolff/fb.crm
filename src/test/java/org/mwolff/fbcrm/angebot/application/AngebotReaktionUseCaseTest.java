package org.mwolff.fbcrm.angebot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsstand;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
import org.mwolff.fbcrm.vorgang.application.EreignisVermerkenUseCase;

/**
 * Die Reaktion des Kunden auf ein Angebot (Kriterien 17, 18, 19).
 *
 * <p>Drei Zusagen sind hier der Gegenstand.
 *
 * <p><b>Aus welchen Zustaenden reagiert werden darf.</b> Aus {@code VERSENDET} und aus {@code
 * ABGELOEST}, jeweils unabhaengig vom Datum — der Fall aus F13: Nach einer Nachverhandlung sagt der
 * Kunde „wir nehmen das erste". Aus {@code ENTWURF}, {@code ANGENOMMEN} und {@code ABGELEHNT} ist
 * es 409, und dann wird nichts geschrieben; geprueft mit {@code verifyNoInteractions} an der
 * Historie und {@code never()} am Bestand.
 *
 * <p><b>Was die Annahme abloest.</b> Kriterium 17: die uebrigen noch <i>offenen</i> Angebote
 * desselben Vorgangs — und nur die. Die Ablehnung loest nichts ab.
 *
 * <p><b>Was in der Historie steht.</b> Je Wechsel ein Ereignis: die Reaktion selbst und je
 * abgeloestes Angebot eines. Bei zwei weiteren offenen Angeboten sind es drei, bei einer Ablehnung
 * eines.
 *
 * <p>Vom Vorgang fragt dieser Anwendungsfall nichts: Die Schreibsperre am abgeschlossenen Vorgang
 * zieht Kriterium 9 nur um Anlegen und Versenden (E13). Dass sie hier nicht greift, ist deshalb
 * nicht mit einem Doppel zu zeigen — es gibt keinen Port dafuer; der Nachweis steht in {@code
 * AngebotReaktionIT} gegen die echte Datenbank.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AngebotReaktionUseCaseTest {

  private static final long ANGEBOT = 11L;
  private static final long ZWEITES = 12L;
  private static final long DRITTES = 13L;
  private static final Instant JETZT = Instant.parse("2026-09-28T09:30:00Z");

  /** Lange nach dem Ende der Gueltigkeit (20.10.2026) aus {@link Angebotsdoppel}. */
  private static final Instant NACH_ABLAUF = Instant.parse("2026-12-01T09:00:00Z");

  @Mock private AngebotRepository angebote;
  @Mock private EreignisVermerkenUseCase ereignisse;

  private AngebotReaktionUseCase useCase(final Instant zeitpunkt) {
    return new AngebotReaktionUseCase(angebote, ereignisse, Clock.fixed(zeitpunkt, ZoneOffset.UTC));
  }

  private AngebotReaktionUseCase useCase() {
    return useCase(JETZT);
  }

  /** Ein Angebot im gegebenen Zustand, allein an seinem Vorgang. */
  private void liegtVor(final Angebotszustand zustand) {
    final Angebot angebot = Angebotsdoppel.festgeschrieben(ANGEBOT, zustand);
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.of(angebot));
    when(angebote.findByVorgang(Angebotsdoppel.VORGANG)).thenReturn(List.of(angebot));
    when(angebote.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));
  }

  /**
   * Derselbe Vorgang, aber mit weiteren Angeboten neben dem, um das es geht.
   *
   * <p>Das Angebot dieser Reaktion steht selbst mit in der Liste — so, wie der Bestand es liefert.
   * Dass es sich nicht selbst abloest, haengt allein an der Pruefung der Kennung.
   */
  private void amVorgangLiegenAuch(final Angebot... weitere) {
    final List<Angebot> alle = new ArrayList<>();
    alle.add(Angebotsdoppel.festgeschrieben(ANGEBOT, Angebotszustand.VERSENDET));
    alle.addAll(Arrays.asList(weitere));
    when(angebote.findByVorgang(Angebotsdoppel.VORGANG)).thenReturn(List.copyOf(alle));
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotszustand.class,
      names = {"VERSENDET", "ABGELOEST"})
  void nimmAn_fromVersendetOrAbgeloest_thenWritesTheAngebotAsAngenommen(
      final Angebotszustand zustand) {
    // Given — Kriterium 17, F13: annehmbar bleibt auch, was ein spaeterer Versand abgeloest hat.
    liegtVor(zustand);

    // When
    final Angebot angenommen = useCase().nimmAn(ANGEBOT).angebot();

    // Then
    assertThat(angenommen.zustand()).isEqualTo(Angebotszustand.ANGENOMMEN);
    assertThat(angenommen.reaktionAm()).isEqualTo(JETZT);
    verify(angebote).save(angenommen);
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotszustand.class,
      names = {"VERSENDET", "ABGELOEST"})
  void lehneAb_fromVersendetOrAbgeloest_thenWritesTheAngebotAsAbgelehnt(
      final Angebotszustand zustand) {
    // Given — Kriterium 17.
    liegtVor(zustand);

    // When
    final Angebot abgelehnt = useCase().lehneAb(ANGEBOT).angebot();

    // Then
    assertThat(abgelehnt.zustand()).isEqualTo(Angebotszustand.ABGELEHNT);
    assertThat(abgelehnt.reaktionAm()).isEqualTo(JETZT);
    verify(angebote).save(abgelehnt);
  }

  @Test
  void nimmAn_atAnExpiredAngebot_thenSucceeds() {
    // Given — Kriterium 18, F5: die verstrichene Gueltigkeit schliesst ein Angebot nicht.
    liegtVor(Angebotszustand.VERSENDET);

    // When
    final AngebotAnsicht ansicht = useCase(NACH_ABLAUF).nimmAn(ANGEBOT);

    // Then
    assertThat(ansicht.angebot().zustand()).isEqualTo(Angebotszustand.ANGENOMMEN);
    assertThat(ansicht.stand()).isEqualTo(Angebotsstand.ANGENOMMEN);
  }

  @Test
  void lehneAb_atAnExpiredAngebot_thenSucceeds() {
    // Given — Kriterium 18, F5.
    liegtVor(Angebotszustand.VERSENDET);

    // When
    final AngebotAnsicht ansicht = useCase(NACH_ABLAUF).lehneAb(ANGEBOT);

    // Then
    assertThat(ansicht.angebot().zustand()).isEqualTo(Angebotszustand.ABGELEHNT);
    assertThat(ansicht.stand()).isEqualTo(Angebotsstand.ABGELEHNT);
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotszustand.class,
      names = {"ENTWURF", "ANGENOMMEN", "ABGELEHNT"})
  void nimmAn_fromAnUnreachableZustand_thenRejectsAndWritesNothing(final Angebotszustand zustand) {
    // Given — Kriterium 17: beide Reaktionen sind endgueltig, ein Entwurf war nie beim Kunden.
    liegtVor(zustand);

    // When / Then
    assertThatThrownBy(() -> useCase().nimmAn(ANGEBOT)).isInstanceOf(AngebotNichtAenderbar.class);
    verify(angebote, never()).save(any());
    verifyNoInteractions(ereignisse);
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotszustand.class,
      names = {"ENTWURF", "ANGENOMMEN", "ABGELEHNT"})
  void lehneAb_fromAnUnreachableZustand_thenRejectsAndWritesNothing(final Angebotszustand zustand) {
    // Given — Kriterium 17.
    liegtVor(zustand);

    // When / Then
    assertThatThrownBy(() -> useCase().lehneAb(ANGEBOT)).isInstanceOf(AngebotNichtAenderbar.class);
    verify(angebote, never()).save(any());
    verifyNoInteractions(ereignisse);
  }

  @Test
  void nimmAn_thenWritesOneEreignisWhenNothingElseWasOpen() {
    // Given — Kriterium 19: der Wechsel erscheint mit Zeitpunkt in der Historie.
    liegtVor(Angebotszustand.VERSENDET);

    // When
    useCase().nimmAn(ANGEBOT);

    // Then
    verify(ereignisse).vermerken(Angebotsdoppel.VORGANG, "Angebot A-2026-011 angenommen");
    verify(ereignisse, times(1)).vermerken(anyLong(), anyString());
  }

  @Test
  void nimmAn_withTwoOtherOpenAngebote_thenSupersedesThemAndWritesThreeEreignisse() {
    // Given — Kriterium 17: die Annahme beendet die uebrigen offenen Angebote des Vorgangs.
    liegtVor(Angebotszustand.VERSENDET);
    amVorgangLiegenAuch(
        Angebotsdoppel.festgeschrieben(ZWEITES, Angebotszustand.VERSENDET),
        Angebotsdoppel.festgeschrieben(DRITTES, Angebotszustand.VERSENDET));

    // When
    useCase().nimmAn(ANGEBOT);

    // Then — das angenommene und die beiden abgeloesten Angebote, dazu drei Ereignisse.
    verify(angebote)
        .save(Angebotsdoppel.festgeschrieben(ZWEITES, Angebotszustand.VERSENDET).abgeloest(JETZT));
    verify(angebote)
        .save(Angebotsdoppel.festgeschrieben(DRITTES, Angebotszustand.VERSENDET).abgeloest(JETZT));
    verify(angebote, times(3)).save(any());
    verify(ereignisse).vermerken(Angebotsdoppel.VORGANG, "Angebot A-2026-011 angenommen");
    verify(ereignisse).vermerken(Angebotsdoppel.VORGANG, "Angebot A-2026-012 abgeloest");
    verify(ereignisse).vermerken(Angebotsdoppel.VORGANG, "Angebot A-2026-013 abgeloest");
    verify(ereignisse, times(3)).vermerken(anyLong(), anyString());
  }

  @Test
  void lehneAb_withTwoOtherOpenAngebote_thenSupersedesNothingAndWritesOneEreignis() {
    // Given — Kriterium 17 nennt allein die Annahme; eine Absage laesst die anderen offen.
    liegtVor(Angebotszustand.VERSENDET);
    amVorgangLiegenAuch(
        Angebotsdoppel.festgeschrieben(ZWEITES, Angebotszustand.VERSENDET),
        Angebotsdoppel.festgeschrieben(DRITTES, Angebotszustand.VERSENDET));

    // When
    useCase().lehneAb(ANGEBOT);

    // Then
    verify(angebote, times(1)).save(any());
    verify(ereignisse).vermerken(Angebotsdoppel.VORGANG, "Angebot A-2026-011 abgelehnt");
    verify(ereignisse, times(1)).vermerken(anyLong(), anyString());
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotszustand.class,
      names = {"ENTWURF", "ANGENOMMEN", "ABGELEHNT", "ABGELOEST"})
  void nimmAn_thenLeavesEveryAngebotThatIsNotOpenAlone(final Angebotszustand zustand) {
    // Given — abgeloest wird nur, was offen ist (Kriterium 17).
    liegtVor(Angebotszustand.VERSENDET);
    amVorgangLiegenAuch(
        zustand == Angebotszustand.ENTWURF
            ? Angebotsdoppel.entwurf(ZWEITES, Angebotsdoppel.VORGANG, List.of())
            : Angebotsdoppel.festgeschrieben(ZWEITES, zustand));

    // When
    useCase().nimmAn(ANGEBOT);

    // Then — nur das angenommene Angebot selbst wird geschrieben, nur ein Ereignis.
    verify(angebote, times(1)).save(any());
    verify(ereignisse, times(1)).vermerken(anyLong(), anyString());
  }

  @Test
  void nimmAn_givenAnExpiredOpenAngebot_thenSupersedesItAsWell() {
    // Given — Kriterium 18: die verstrichene Gueltigkeit macht ein Angebot alt, nicht geschlossen.
    liegtVor(Angebotszustand.VERSENDET);
    amVorgangLiegenAuch(Angebotsdoppel.festgeschrieben(ZWEITES, Angebotszustand.VERSENDET));

    // When — eine Uhr lange nach dem Ende der Gueltigkeit.
    useCase(NACH_ABLAUF).nimmAn(ANGEBOT);

    // Then
    verify(ereignisse).vermerken(Angebotsdoppel.VORGANG, "Angebot A-2026-012 abgeloest");
  }

  @Test
  void nimmAn_whenTheAngebotIsUnknown_thenRejects() {
    // Given
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase().nimmAn(ANGEBOT)).isInstanceOf(AngebotNichtGefunden.class);
    verify(angebote, never()).save(any());
    verifyNoInteractions(ereignisse);
  }

  @Test
  void lehneAb_whenTheAngebotIsUnknown_thenRejects() {
    // Given
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase().lehneAb(ANGEBOT)).isInstanceOf(AngebotNichtGefunden.class);
    verify(angebote, never()).save(any());
    verifyNoInteractions(ereignisse);
  }
}
