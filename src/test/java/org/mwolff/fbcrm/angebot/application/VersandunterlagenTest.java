package org.mwolff.fbcrm.angebot.application;

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
import org.mwolff.fbcrm.angebot.domain.Belegabsender;
import org.mwolff.fbcrm.angebot.domain.Belegempfaenger;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngabenRepository;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.AnsprechpartnerRepository;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;

/**
 * Die Nachbarn eines Angebots und die Kopien, die daraus entstehen (R8, Kriterium 12).
 *
 * <p>Gegenstand ist zweierlei: dass die drei Bestaende ueberhaupt gelesen werden, und dass daraus
 * <b>Kopien</b> werden und keine Verweise — der Firmenname steht danach als Text am Angebot, und
 * ein spaeterer Umzug der Firma aendert das versendete Dokument nicht.
 *
 * <p>Der Ansprechpartner ist der Fall mit den drei Ausgaengen: keiner am Angebot, einer am Angebot
 * und im Bestand, einer am Angebot und im Bestand nicht mehr. Nur der mittlere erscheint auf dem
 * Beleg; die beiden anderen lassen den Versand nicht scheitern, weil Kriterium 12 ihn nicht
 * verlangt.
 */
@ExtendWith(MockitoExtension.class)
class VersandunterlagenTest {

  private static final long ANGEBOT = 11L;

  @Mock private FirmaRepository firmen;
  @Mock private AnsprechpartnerRepository personen;
  @Mock private EigeneAngabenRepository eigeneAngaben;

  @InjectMocks private Versandunterlagen unterlagen;

  private void bestandLiegtVor() {
    when(firmen.findById(Versanddoppel.FIRMA)).thenReturn(Optional.of(Versanddoppel.firma()));
    when(eigeneAngaben.lies()).thenReturn(Versanddoppel.eigeneAngaben());
  }

  @Test
  void zu_thenCopiesTheFirmaAsTextAndNotAsAReference() {
    // Given — R8.
    bestandLiegtVor();
    when(personen.findById(Versanddoppel.ANSPRECHPARTNER))
        .thenReturn(Optional.of(Versanddoppel.ansprechpartner()));

    // When
    final Belegempfaenger empfaenger = unterlagen.zu(Angebotsdoppel.entwurf(ANGEBOT)).empfaenger();

    // Then
    assertThat(empfaenger)
        .isEqualTo(
            new Belegempfaenger(
                Versanddoppel.FIRMENNAME,
                Versanddoppel.FIRMENANSCHRIFT,
                Versanddoppel.ANSPRECHPARTNER_TEXT));
  }

  @Test
  void zu_thenCopiesTheEigeneAngabenIncludingBankverbindung() {
    // Given — Kriterium 1: aendere ich sie spaeter, bleiben versendete Angebote unberuehrt.
    bestandLiegtVor();

    // When
    final Belegabsender absender =
        unterlagen
            .zu(Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.entwurf(ANGEBOT)))
            .absender();

    // Then
    assertThat(absender)
        .isEqualTo(
            new Belegabsender(
                Versanddoppel.EIGENER_NAME,
                Versanddoppel.EIGENE_ANSCHRIFT,
                "manne@example.org",
                "0421 123456",
                "12/345/67890",
                "DE123456789",
                "Sparkasse, IBAN DE02 1203 0000 0000 2020 51"));
  }

  @Test
  void zu_withoutAnAnsprechpartnerAtTheAngebot_thenAsksNobodyAndCopiesOnlyTheFirma() {
    // Given — Kriterium 12: ein Ansprechpartner ist nicht noetig.
    bestandLiegtVor();

    // When
    final Belegempfaenger empfaenger =
        unterlagen
            .zu(Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.entwurf(ANGEBOT)))
            .empfaenger();

    // Then
    assertThat(empfaenger.ansprechpartner()).isNull();
    verifyNoInteractions(personen);
  }

  @Test
  void zu_whenTheAnsprechpartnerIsGone_thenCopiesOnlyTheFirma() {
    // Given — am Angebot vermerkt, im Bestand nicht mehr da; der Versand soll daran nicht
    // scheitern.
    bestandLiegtVor();
    when(personen.findById(Versanddoppel.ANSPRECHPARTNER)).thenReturn(Optional.empty());

    // When
    final Belegempfaenger empfaenger = unterlagen.zu(Angebotsdoppel.entwurf(ANGEBOT)).empfaenger();

    // Then
    assertThat(empfaenger.ansprechpartner()).isNull();
  }

  @Test
  void zu_givenAnAnsprechpartnerWithoutAVorname_thenTheBelegCarriesTheNachnameAlone() {
    // Given — der Vorname ist am Ansprechpartner optional; „ Adler" waere eine Zeile mit Luecke.
    bestandLiegtVor();
    when(personen.findById(Versanddoppel.ANSPRECHPARTNER)).thenReturn(Optional.of(ohneVorname()));

    // When
    final Belegempfaenger empfaenger = unterlagen.zu(Angebotsdoppel.entwurf(ANGEBOT)).empfaenger();

    // Then
    assertThat(empfaenger.ansprechpartner()).isEqualTo("Adler");
  }

  @Test
  void zu_givenAnAnsprechpartnerWithABlankVorname_thenTheBelegCarriesTheNachnameAlone() {
    // Given — dieselbe Lage mit Leerzeichen statt {@code null}.
    bestandLiegtVor();
    when(personen.findById(Versanddoppel.ANSPRECHPARTNER))
        .thenReturn(Optional.of(mitLeeremVornamen()));

    // When
    final Belegempfaenger empfaenger = unterlagen.zu(Angebotsdoppel.entwurf(ANGEBOT)).empfaenger();

    // Then
    assertThat(empfaenger.ansprechpartner()).isEqualTo("Adler");
  }

  @Test
  void zu_whenTheFirmaIsUnknown_thenRejects() {
    // Given — der Fremdschluessel schliesst das aus; ohne Firma gibt es keine Empfaengerkopie.
    when(firmen.findById(Versanddoppel.FIRMA)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> unterlagen.zu(Angebotsdoppel.entwurf(ANGEBOT)))
        .isInstanceOf(FirmaNichtGefunden.class);
    verifyNoInteractions(personen, eigeneAngaben);
  }

  private static Ansprechpartner ohneVorname() {
    final Ansprechpartner person = Versanddoppel.ansprechpartner();
    return person.geaendert(
        null,
        person.nachname(),
        person.rolle(),
        person.email(),
        person.telefonFestnetz(),
        person.telefonMobil(),
        person.updatedAt());
  }

  private static Ansprechpartner mitLeeremVornamen() {
    final Ansprechpartner person = Versanddoppel.ansprechpartner();
    return person.geaendert(
        "   ",
        person.nachname(),
        person.rolle(),
        person.email(),
        person.telefonFestnetz(),
        person.telefonMobil(),
        person.updatedAt());
  }
}
