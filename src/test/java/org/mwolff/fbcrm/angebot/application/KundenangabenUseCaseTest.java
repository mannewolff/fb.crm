package org.mwolff.fbcrm.angebot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.AnsprechpartnerRepository;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;

/**
 * Die Namen von Firma und Ansprechpartner eines Angebots (Issue #126).
 *
 * <p>Drei Ausgaenge beim Ansprechpartner: keiner am Angebot, einer im Bestand, einer im Bestand
 * nicht mehr. Nur der mittlere traegt einen Namen; der letzte laesst die Ansicht nicht scheitern.
 */
@ExtendWith(MockitoExtension.class)
class KundenangabenUseCaseTest {

  private static final long ANGEBOT = 11L;

  @Mock private FirmaRepository firmen;
  @Mock private AnsprechpartnerRepository personen;

  private KundenangabenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new KundenangabenUseCase(firmen, personen);
  }

  private void firmaLiegtVor() {
    when(firmen.findById(Kundendoppel.FIRMA)).thenReturn(Optional.of(Kundendoppel.firma()));
  }

  @Test
  void zu_thenNamesTheFirmaAndTheContact() {
    // Given
    firmaLiegtVor();
    when(personen.findById(Kundendoppel.ANSPRECHPARTNER))
        .thenReturn(Optional.of(Kundendoppel.ansprechpartner()));

    // When
    final Kundenangaben kunde = useCase.zu(Angebotsdoppel.angebot(ANGEBOT));

    // Then
    assertThat(kunde)
        .isEqualTo(new Kundenangaben(Kundendoppel.FIRMENNAME, Kundendoppel.ANSPRECHPARTNER_TEXT));
  }

  @Test
  void zu_withoutAContactAtTheAngebot_thenAsksNobodyAndNamesOnlyTheFirma() {
    // Given
    firmaLiegtVor();
    final Angebot ohne = Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT));

    // When
    final Kundenangaben kunde = useCase.zu(ohne);

    // Then
    assertThat(kunde).isEqualTo(new Kundenangaben(Kundendoppel.FIRMENNAME, null));
    verifyNoInteractions(personen);
  }

  @Test
  void zu_whenTheContactIsGoneFromTheBestand_thenNamesOnlyTheFirma() {
    // Given — am Angebot vermerkt, im Bestand nicht mehr da.
    firmaLiegtVor();
    when(personen.findById(Kundendoppel.ANSPRECHPARTNER)).thenReturn(Optional.empty());

    // When
    final Kundenangaben kunde = useCase.zu(Angebotsdoppel.angebot(ANGEBOT));

    // Then
    assertThat(kunde.ansprechpartnerName()).isNull();
  }

  @Test
  void zu_whenTheContactHasNoVorname_thenNamesOnlyTheNachname() {
    // Given — dieselbe Regel wie auf dem Beleg.
    firmaLiegtVor();
    final Ansprechpartner person = Kundendoppel.ansprechpartner();
    when(personen.findById(Kundendoppel.ANSPRECHPARTNER))
        .thenReturn(
            Optional.of(
                person.geaendert(
                    " ",
                    person.nachname(),
                    person.rolle(),
                    person.email(),
                    person.telefonFestnetz(),
                    person.telefonMobil(),
                    person.updatedAt())));

    // When
    final Kundenangaben kunde = useCase.zu(Angebotsdoppel.angebot(ANGEBOT));

    // Then
    assertThat(kunde.ansprechpartnerName()).isEqualTo("Adler");
  }

  @Test
  void zu_whenTheFirmaIsMissing_thenReports() {
    // Given — ein Widerspruch im Bestand: Firmen werden nie geloescht.
    when(firmen.findById(Kundendoppel.FIRMA)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.zu(Angebotsdoppel.angebot(ANGEBOT)))
        .isInstanceOf(FirmaNichtGefunden.class);
  }
}
