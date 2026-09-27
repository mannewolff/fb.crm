package org.mwolff.fbcrm.angebot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsstand;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
import org.mwolff.fbcrm.angebot.domain.Belegempfaenger;
import org.mwolff.fbcrm.angebot.domain.BelegnummerRepository;
import org.mwolff.fbcrm.angebot.domain.DokumentSpeicher;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.vorgang.application.EreignisVermerkenUseCase;
import org.mwolff.fbcrm.vorgang.application.VorgangNichtGefunden;
import org.mwolff.fbcrm.vorgang.domain.VorgangRepository;

/**
 * Das Versenden eines Angebots (Kriterien 10 bis 16, 19, 25).
 *
 * <p>Der Anwendungsfall zieht in <b>einer</b> Transaktion die Nummer, kopiert die Anschriften, legt
 * das PDF ab, schreibt das Angebot fest, loest die uebrigen offenen Angebote ab und vermerkt jeden
 * Wechsel in der Historie. Zwei Zusagen sind hier der eigentliche Gegenstand.
 *
 * <p><b>Ein abgewiesener Versuch verbraucht keine Nummer</b> (Kriterium 12, E22). Geprueft wird das
 * mit {@code verifyNoInteractions} am Nummernkreis, am Drucker und am Objektspeicher — auf allen
 * drei Abweisungswegen: fehlende Angaben, abgeschlossener Vorgang, kein Entwurf. Ein Rollback gaebe
 * die Nummer zwar zurueck, aber die Reihenfolge ist die Zusage, nicht ihr Ersatz.
 *
 * <p><b>Das Jahr der Nummer ist das Kalenderjahr der Geschaeftszone</b> (Kriterium 11, E12). Der
 * {@code Clock}-Bean der Anwendung ist {@code systemUTC()}; deshalb steht hier ein Fall auf beiden
 * Seiten der Berliner Jahresgrenze.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AngebotVersendenUseCaseTest {

  private static final long ANGEBOT = 11L;
  private static final long ZWEITES = 12L;
  private static final long DRITTES = 13L;
  private static final Instant JETZT = Instant.parse("2026-09-28T09:30:00Z");
  private static final String NUMMER = "A-2026-001";
  private static final String SCHLUESSEL = "angebot/11/beleg.pdf";
  private static final byte[] PDF = "%PDF-1.7 Beleg".getBytes(StandardCharsets.UTF_8);

  @Mock private AngebotRepository angebote;
  @Mock private VorgangRepository vorgaenge;
  @Mock private Versandunterlagen unterlagen;
  @Mock private BelegnummerRepository belegnummern;
  @Mock private Belegdrucker drucker;
  @Mock private DokumentSpeicher speicher;
  @Mock private EreignisVermerkenUseCase ereignisse;

  private AngebotVersendenUseCase useCase(final Instant zeitpunkt) {
    return new AngebotVersendenUseCase(
        angebote,
        vorgaenge,
        unterlagen,
        belegnummern,
        drucker,
        speicher,
        ereignisse,
        Clock.fixed(zeitpunkt, ZoneOffset.UTC));
  }

  private AngebotVersendenUseCase useCase() {
    return useCase(JETZT);
  }

  /** Die vollstaendige Lage: ein versandfaehiger Entwurf an einem offenen Vorgang mit Firma. */
  private void alleAngabenLiegenVor() {
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.of(Angebotsdoppel.entwurf(ANGEBOT)));
    // Nach dem Schreiben liefert der Bestand das Angebot dieses Versands selbst zurueck — und zwar
    // festgeschrieben. Dass es sich nicht selbst abloest, haengt allein an der Pruefung der
    // Kennung.
    when(angebote.findByVorgang(Angebotsdoppel.VORGANG))
        .thenReturn(List.of(Angebotsdoppel.festgeschrieben(ANGEBOT, Angebotszustand.VERSENDET)));
    when(angebote.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));
    when(vorgaenge.findById(Angebotsdoppel.VORGANG))
        .thenReturn(Optional.of(Versanddoppel.vorgang()));
    when(unterlagen.zu(any())).thenReturn(Versanddoppel.belegangaben());
    when(belegnummern.zieheNummer(anyInt())).thenReturn(NUMMER);
    when(drucker.drucke(any())).thenReturn(PDF);
    when(speicher.lege(eq(ANGEBOT), any())).thenReturn(SCHLUESSEL);
  }

  private Angebot versende() {
    return useCase().versende(ANGEBOT).angebot();
  }

  @Test
  void versende_thenWritesTheAngebotAsFestgeschrieben() {
    // Given — Kriterium 10: aus dem Entwurf wird ein festes Dokument.
    alleAngabenLiegenVor();

    // When
    final Angebot versendet = versende();

    // Then
    assertThat(versendet.zustand()).isEqualTo(Angebotszustand.VERSENDET);
    assertThat(versendet.nummer()).isEqualTo(NUMMER);
    assertThat(versendet.versendetAm()).isEqualTo(JETZT);
    assertThat(versendet.pdfSchluessel()).isEqualTo(SCHLUESSEL);
  }

  @Test
  void versende_thenDrawsExactlyOneNumber() {
    // Given — Kriterium 11: die laufende Nummer ist lueckenlos.
    alleAngabenLiegenVor();

    // When
    versende();

    // Then
    verify(belegnummern, times(1)).zieheNummer(2026);
  }

  @Test
  void versende_thenStoresThePrintedBelegUnderTheAngebot() {
    // Given — Kriterium 14: ausgeliefert wird spaeter dieses Objekt, nichts Nachgerechnetes.
    alleAngabenLiegenVor();

    // When
    versende();

    // Then
    verify(speicher).lege(ANGEBOT, PDF);
  }

  @Test
  void versende_thenWritesOneEreignisWhenNothingElseWasOpen() {
    // Given — Kriterium 19: der Wechsel erscheint in der Historie des Vorgangs.
    alleAngabenLiegenVor();

    // When
    versende();

    // Then
    verify(ereignisse).vermerken(Angebotsdoppel.VORGANG, "Angebot " + NUMMER + " versendet");
    verify(ereignisse, times(1)).vermerken(anyLong(), anyString());
  }

  @Test
  void versende_withTwoOpenAngebote_thenSupersedesThemAndWritesThreeEreignisse() {
    // Given — Kriterium 25: eine Nachverhandlung traegt dieselbe Chance nicht doppelt.
    alleAngabenLiegenVor();
    when(angebote.findByVorgang(Angebotsdoppel.VORGANG))
        .thenReturn(
            List.of(
                Angebotsdoppel.festgeschrieben(ANGEBOT, Angebotszustand.VERSENDET),
                Angebotsdoppel.festgeschrieben(ZWEITES, Angebotszustand.VERSENDET),
                Angebotsdoppel.festgeschrieben(DRITTES, Angebotszustand.VERSENDET)));

    // When
    versende();

    // Then
    verify(ereignisse).vermerken(Angebotsdoppel.VORGANG, "Angebot A-2026-012 abgeloest");
    verify(ereignisse).vermerken(Angebotsdoppel.VORGANG, "Angebot A-2026-013 abgeloest");
    verify(ereignisse, times(3)).vermerken(anyLong(), anyString());
  }

  @Test
  void versende_withTwoOpenAngebote_thenWritesThemBackAsAbgeloest() {
    // Given — Kriterium 25.
    alleAngabenLiegenVor();
    when(angebote.findByVorgang(Angebotsdoppel.VORGANG))
        .thenReturn(
            List.of(
                Angebotsdoppel.festgeschrieben(ANGEBOT, Angebotszustand.VERSENDET),
                Angebotsdoppel.festgeschrieben(ZWEITES, Angebotszustand.VERSENDET)));

    // When
    versende();

    // Then — genau zwei Schreibvorgaenge: das festgeschriebene und das abgeloeste Angebot.
    verify(angebote, times(2)).save(any());
    verify(angebote)
        .save(Angebotsdoppel.festgeschrieben(ZWEITES, Angebotszustand.VERSENDET).abgeloest(JETZT));
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotszustand.class,
      names = {"ENTWURF", "ANGENOMMEN", "ABGELEHNT", "ABGELOEST"})
  void versende_thenLeavesEveryAngebotThatIsNotOpenAlone(final Angebotszustand zustand) {
    // Given — abgeloest wird nur, was offen ist (Kriterium 25).
    alleAngabenLiegenVor();
    when(angebote.findByVorgang(Angebotsdoppel.VORGANG))
        .thenReturn(
            List.of(
                Angebotsdoppel.festgeschrieben(ANGEBOT, Angebotszustand.VERSENDET),
                zustand == Angebotszustand.ENTWURF
                    ? Angebotsdoppel.entwurf(ZWEITES, Angebotsdoppel.VORGANG, List.of())
                    : Angebotsdoppel.festgeschrieben(ZWEITES, zustand)));

    // When
    versende();

    // Then — nur das festgeschriebene Angebot selbst wird geschrieben, nur ein Ereignis.
    verify(angebote, times(1)).save(any());
    verify(ereignisse, times(1)).vermerken(anyLong(), anyString());
  }

  @Test
  void versende_givenAnExpiredOpenAngebot_thenSupersedesItAsWell() {
    // Given — Kriterium 18: die verstrichene Gueltigkeit schliesst ein Angebot nicht.
    alleAngabenLiegenVor();
    when(angebote.findByVorgang(Angebotsdoppel.VORGANG))
        .thenReturn(
            List.of(
                Angebotsdoppel.festgeschrieben(ANGEBOT, Angebotszustand.VERSENDET),
                Angebotsdoppel.festgeschrieben(ZWEITES, Angebotszustand.VERSENDET)));

    // When — eine Uhr lange nach dem Ende der Gueltigkeit (20.10.2026).
    useCase(Instant.parse("2026-12-01T09:00:00Z")).versende(ANGEBOT);

    // Then
    verify(ereignisse).vermerken(Angebotsdoppel.VORGANG, "Angebot A-2026-012 abgeloest");
  }

  @Test
  void versende_thenTheBelegCarriesTheCopiedAnschriften() {
    // Given — R8: die Kopien gehen an das festgeschriebene Angebot (gebaut von Belegangaben).
    alleAngabenLiegenVor();

    // When
    final Angebot versendet = versende();

    // Then
    assertThat(versendet.empfaenger())
        .isEqualTo(
            new Belegempfaenger(
                Versanddoppel.FIRMENNAME,
                Versanddoppel.FIRMENANSCHRIFT,
                Versanddoppel.ANSPRECHPARTNER_TEXT));
    assertThat(versendet.absender()).isNotNull();
  }

  @Test
  void versende_whenAnAngabeIsMissing_thenDrawsNoNumberAndStoresNothing() {
    // Given — Kriterium 12, E22: geprueft wird vor jedem Zug am Nummernkreis.
    alleAngabenLiegenVor();
    when(unterlagen.zu(any()))
        .thenReturn(
            Versanddoppel.belegangabenMit(
                Versanddoppel.eigeneAngabenMit(null, new Anschrift(null, null, null, null))));

    // When / Then
    assertThatThrownBy(() -> useCase().versende(ANGEBOT)).isInstanceOf(VersandUnvollstaendig.class);
    verifyNoInteractions(belegnummern, drucker, speicher, ereignisse);
    verify(angebote, never()).save(any());
  }

  @Test
  void versende_atAClosedVorgang_thenRejectsAndDrawsNoNumber() {
    // Given — Kriterium 9, E13: am abgeschlossenen Vorgang wird nichts festgeschrieben.
    alleAngabenLiegenVor();
    when(vorgaenge.findById(Angebotsdoppel.VORGANG))
        .thenReturn(
            Optional.of(Versanddoppel.vorgang(Long.valueOf(Versanddoppel.ANSPRECHPARTNER), true)));

    // When / Then
    assertThatThrownBy(() -> useCase().versende(ANGEBOT)).isInstanceOf(VorgangAbgeschlossen.class);
    verifyNoInteractions(belegnummern, drucker, speicher, ereignisse);
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotszustand.class,
      names = {"VERSENDET", "ANGENOMMEN", "ABGELEHNT", "ABGELOEST"})
  void versende_whenTheAngebotIsAlreadyFestgeschrieben_thenRejectsAndDrawsNoNumber(
      final Angebotszustand zustand) {
    // Given — Kriterium 13: versendet wird nur ein Entwurf.
    alleAngabenLiegenVor();
    when(angebote.findById(ANGEBOT))
        .thenReturn(Optional.of(Angebotsdoppel.festgeschrieben(ANGEBOT, zustand)));

    // When / Then
    assertThatThrownBy(() -> useCase().versende(ANGEBOT)).isInstanceOf(AngebotNichtAenderbar.class);
    verifyNoInteractions(belegnummern, drucker, speicher, ereignisse);
    verify(angebote, never()).save(any());
  }

  @Test
  void versende_whenTheAngebotIsUnknown_thenRejects() {
    // Given
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase().versende(ANGEBOT)).isInstanceOf(AngebotNichtGefunden.class);
    verifyNoInteractions(belegnummern, drucker, speicher);
  }

  @Test
  void versende_whenTheVorgangIsUnknown_thenRejects() {
    // Given
    alleAngabenLiegenVor();
    when(vorgaenge.findById(Angebotsdoppel.VORGANG)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase().versende(ANGEBOT)).isInstanceOf(VorgangNichtGefunden.class);
    verifyNoInteractions(belegnummern, drucker, speicher);
  }

  @ParameterizedTest
  @CsvSource({"2026-12-31T23:30:00Z, 2027", "2026-12-31T22:30:00Z, 2026"})
  void versende_atTheTurnOfTheYear_thenTheYearComesFromTheGeschaeftszone(
      final String zeitpunkt, final int jahr) {
    // Given — E12: 23:30 UTC ist in Europe/Berlin der 1. Januar, 22:30 UTC noch der 31. Dezember.
    alleAngabenLiegenVor();

    // When
    useCase(Instant.parse(zeitpunkt)).versende(ANGEBOT);

    // Then
    verify(belegnummern).zieheNummer(jahr);
  }

  @Test
  void versende_thenTheAnsichtCarriesTheStandOfToday() {
    // Given — E4: der Stand entsteht aus dem Vergleich mit dem heutigen Tag, nicht aus der Spalte.
    alleAngabenLiegenVor();

    // When
    final AngebotAnsicht ansicht = useCase().versende(ANGEBOT);

    // Then
    assertThat(ansicht.stand()).isEqualTo(Angebotsstand.VERSENDET);
  }
}
