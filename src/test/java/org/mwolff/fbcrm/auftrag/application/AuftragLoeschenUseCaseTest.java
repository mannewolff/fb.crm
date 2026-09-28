package org.mwolff.fbcrm.auftrag.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.auftrag.domain.AuftragRepository;
import org.mwolff.fbcrm.vorgang.application.EreignisVermerkenUseCase;

/**
 * Das Loeschen eines Auftrags (Kriterium 15, Plan E15).
 *
 * <p><b>Geprueft wird nichts ausser der Existenz.</b> Die Bedingung „solange weder Zeiten noch
 * Rechnungen daran haengen" steht in Kriterium 15 ausdruecklich als heute immer erfuellt — es gibt
 * weder Zeiten noch Rechnungen. Ein Riegel vor einer Tuer, hinter der nichts ist, wird beim Bauen
 * der Ideen #6 und #7 leichter uebersehen als eine fehlende Pruefung.
 *
 * <p>Positionen, Zeile und Ereignis gehoeren in <b>eine</b> Transaktion; dass sie es tun, weist
 * {@code AuftragLoeschenIT} nach. Hier steht, dass alle drei ueberhaupt geschehen — und dass beim
 * unbekannten Auftrag keines davon geschieht.
 */
@ExtendWith(MockitoExtension.class)
class AuftragLoeschenUseCaseTest {

  private static final long AUFTRAG = 7L;

  @Mock private AuftragRepository auftraege;
  @Mock private EreignisVermerkenUseCase ereignisse;

  @InjectMocks private AuftragLoeschenUseCase useCase;

  @Test
  void loesche_thenRemovesTheAuftragAndNotesTheEreignis() {
    // Given — Kriterium 15.
    when(auftraege.findById(AUFTRAG)).thenReturn(Optional.of(Auftragsdoppel.auftrag(AUFTRAG)));

    // When
    useCase.loesche(AUFTRAG);

    // Then — der Bestand loescht Positionen und Zeile in einem Zug (AuftragRepository.loesche).
    verify(auftraege).loesche(AUFTRAG);
    verify(ereignisse)
        .vermerken(
            Auftragsdoppel.VORGANG, "Auftrag " + Auftragsdoppel.AUFTRAGSNUMMER + " geloescht");
  }

  @Test
  void loesche_givenAnUnknownAuftrag_thenRejectsAndRemovesNothing() {
    // Given
    when(auftraege.findById(AUFTRAG)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.loesche(AUFTRAG)).isInstanceOf(AuftragNichtGefunden.class);
    verify(auftraege, never()).loesche(AUFTRAG);
    verifyNoInteractions(ereignisse);
  }
}
