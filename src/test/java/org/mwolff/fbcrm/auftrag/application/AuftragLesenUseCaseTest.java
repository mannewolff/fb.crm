package org.mwolff.fbcrm.auftrag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
import org.mwolff.fbcrm.auftrag.domain.AuftragRepository;
import org.mwolff.fbcrm.vorgang.domain.VorgangRepository;

/**
 * Die beiden Lesewege des Auftrags: der Auftrag selbst und die Auskunft am Angebot.
 *
 * <p>Der erste traegt die Nummer des Quell-Angebots, weil Kriterium 3 verlangt, am Auftrag zu
 * sehen, aus welchem Angebot er entstand — eine Kennung allein zeigt kein Wort.
 *
 * <p>Der zweite ist die Auskunft, die die Angebotsansicht braucht, ohne dass {@code angebot} das
 * Modul {@code auftrag} kennt (Plan E3): ob zu diesem Angebot schon ein Auftrag besteht und ob das
 * Anlegen heute zulaessig ist. <b>{@code anlegbar} sagt nichts darueber, ob schon ein Auftrag
 * haengt</b> — das sieht die Oberflaeche daran, dass {@code auftrag} gefuellt ist; hier stehen
 * Angebotszustand und Abschlussstand des Vorgangs.
 */
@ExtendWith(MockitoExtension.class)
class AuftragLesenUseCaseTest {

  private static final long AUFTRAG = 7L;

  @Mock private AuftragRepository auftraege;
  @Mock private AngebotRepository angebote;
  @Mock private VorgangRepository vorgaenge;

  @InjectMocks private AuftragLesenUseCase useCase;

  private void angebotIst(final Angebotszustand zustand) {
    when(angebote.findById(Auftragsdoppel.ANGEBOT))
        .thenReturn(Optional.of(Auftragsdoppel.angebot(zustand)));
  }

  private void vorgangIst(final boolean abgeschlossen) {
    when(vorgaenge.findById(Auftragsdoppel.VORGANG))
        .thenReturn(Optional.of(Auftragsdoppel.vorgang(abgeschlossen)));
  }

  @Test
  void lese_thenAnswersWithTheAuftragAndTheNummerOfItsAngebot() {
    // Given — Kriterium 3.
    when(auftraege.findById(AUFTRAG)).thenReturn(Optional.of(Auftragsdoppel.auftrag(AUFTRAG)));
    angebotIst(Angebotszustand.ANGENOMMEN);

    // When
    final AuftragAnsicht ansicht = useCase.lese(AUFTRAG);

    // Then
    assertThat(ansicht.auftrag().nummer()).isEqualTo(Auftragsdoppel.AUFTRAGSNUMMER);
    assertThat(ansicht.angebotNummer()).isEqualTo(Auftragsdoppel.ANGEBOTSNUMMER);
    verifyNoInteractions(vorgaenge);
  }

  @Test
  void lese_givenAnUnknownAuftrag_thenRejects() {
    // Given
    when(auftraege.findById(AUFTRAG)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.lese(AUFTRAG)).isInstanceOf(AuftragNichtGefunden.class);
    verifyNoInteractions(angebote, vorgaenge);
  }

  @Test
  void zuAngebot_givenAnAcceptedAngebotWithoutAnAuftrag_thenEmptyAndAnlegbar() {
    // Given — Plan E3: die Taste der Angebotsansicht haengt an dieser Auskunft.
    angebotIst(Angebotszustand.ANGENOMMEN);
    vorgangIst(false);
    when(auftraege.findByAngebot(Auftragsdoppel.ANGEBOT)).thenReturn(Optional.empty());

    // When
    final AuftragAmAngebot auskunft = useCase.zuAngebot(Auftragsdoppel.ANGEBOT);

    // Then
    assertThat(auskunft.auftrag()).isNull();
    assertThat(auskunft.anlegbar()).isTrue();
  }

  @Test
  void zuAngebot_givenAnAuftrag_thenCarriesItWithTheAngebotNummer() {
    // Given — F9: hoechstens einer, und er traegt die Nummer seiner Quelle.
    angebotIst(Angebotszustand.ANGENOMMEN);
    vorgangIst(false);
    when(auftraege.findByAngebot(Auftragsdoppel.ANGEBOT))
        .thenReturn(Optional.of(Auftragsdoppel.auftrag(AUFTRAG)));

    // When
    final AuftragAmAngebot auskunft = useCase.zuAngebot(Auftragsdoppel.ANGEBOT);

    // Then
    assertThat(auskunft.auftrag()).isNotNull();
    assertThat(auskunft.auftrag().angebotNummer()).isEqualTo(Auftragsdoppel.ANGEBOTSNUMMER);
    assertThat(auskunft.anlegbar()).isTrue();
  }

  @Test
  void zuAngebot_givenAnAngebotThatIsNotAccepted_thenNotAnlegbarAndAsksNoVorgang() {
    // Given — Kriterium 1. Der Abschlussstand des Vorgangs entscheidet dann nichts mehr.
    angebotIst(Angebotszustand.VERSENDET);
    when(auftraege.findByAngebot(Auftragsdoppel.ANGEBOT)).thenReturn(Optional.empty());

    // When
    final AuftragAmAngebot auskunft = useCase.zuAngebot(Auftragsdoppel.ANGEBOT);

    // Then
    assertThat(auskunft.anlegbar()).isFalse();
    verifyNoInteractions(vorgaenge);
  }

  @Test
  void zuAngebot_atAClosedVorgang_thenNotAnlegbar() {
    // Given — Kriterium 11.
    angebotIst(Angebotszustand.ANGENOMMEN);
    vorgangIst(true);
    when(auftraege.findByAngebot(Auftragsdoppel.ANGEBOT)).thenReturn(Optional.empty());

    // When
    final AuftragAmAngebot auskunft = useCase.zuAngebot(Auftragsdoppel.ANGEBOT);

    // Then
    assertThat(auskunft.anlegbar()).isFalse();
  }

  @Test
  void zuAngebot_givenAnUnknownAngebot_thenRejects() {
    // Given — fuer das fehlende Angebot gilt der 404 des Angebot-Moduls.
    when(angebote.findById(Auftragsdoppel.ANGEBOT)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.zuAngebot(Auftragsdoppel.ANGEBOT))
        .isInstanceOf(AngebotNichtGefunden.class);
    verifyNoInteractions(auftraege, vorgaenge);
  }
}
