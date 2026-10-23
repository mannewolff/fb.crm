package org.mwolff.fbcrm.angebot.infrastructure;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Die Uebernahme der Kommentare ohne Verfasser durch das erste Konto (Plan #414, E13).
 *
 * <p>Gegenstand ist, dass genau die Kennung des neuen Kontos an die Anweisung geht. Dass die
 * Anweisung nur die Zeilen ohne Verfasser trifft und in der Transaktion der Einrichtung laeuft,
 * belegt {@code MehrereKontenMigrationIT} am echten Schema.
 */
@ExtendWith(MockitoExtension.class)
class KommentarUebernahmeTest {

  private static final long KONTO = 7L;

  @Mock private SpringDataAngebotKommentarRepository zeilen;

  @InjectMocks private KommentarUebernahme uebernahme;

  @Test
  void erstesKontoAngelegt_thenGivesEveryKommentarWithoutVerfasserToThatAccount() {
    // When
    uebernahme.erstesKontoAngelegt(KONTO);

    // Then
    verify(zeilen).gibVerfasserloseAn(KONTO);
    verifyNoMoreInteractions(zeilen);
  }
}
