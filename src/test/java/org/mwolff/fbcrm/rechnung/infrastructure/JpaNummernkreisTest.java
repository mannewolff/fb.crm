package org.mwolff.fbcrm.rechnung.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Der Zaehler des Nummernkreises gegen ein gemocktes Spring-Data-Repository.
 *
 * <p>Geprueft wird der Adapter selbst: welche Anweisungen er in welcher Reihenfolge gibt und welche
 * Zahl er daraus herausgibt. Dass die Sperre auf der Jahreszeile wirklich sperrt, kann nur eine
 * echte Datenbank zeigen — das steht in {@code RechnungNummernkreisIT}. Dass der Adapter die Zeile
 * <b>erst anlegt und dann sperrt</b> und dass er die gezogene Zahl fortschreibt, zeigt sich hier.
 */
@ExtendWith(MockitoExtension.class)
class JpaNummernkreisTest {

  private static final int ZAEHLERJAHR = 2026;

  @Mock private SpringDataRechnungNummernkreisRepository jpa;

  @InjectMocks private JpaNummernkreis nummernkreis;

  private static RechnungNummernkreisEntity zeile(final int naechsteNummer) {
    final RechnungNummernkreisEntity entity = new RechnungNummernkreisEntity();
    entity.setNaechsteNummer(naechsteNummer);
    return entity;
  }

  @Test
  void lies_givenAStoredJahr_thenAnswersWithItsStand() {
    // Given
    when(jpa.findById(ZAEHLERJAHR)).thenReturn(Optional.of(zeile(7)));

    // When
    final int stand = nummernkreis.lies(ZAEHLERJAHR);

    // Then
    assertThat(stand).isEqualTo(7);
  }

  @Test
  void lies_givenAJahrWithoutARow_thenAnswersWithTheStandBeforeTheFirstZug() {
    // Given — die Zeile entsteht erst beim ersten Zug des Jahres; vorher steht der Kreis auf 1.
    when(jpa.findById(ZAEHLERJAHR)).thenReturn(Optional.empty());

    // When
    final int stand = nummernkreis.lies(ZAEHLERJAHR);

    // Then
    assertThat(stand).isEqualTo(1);
  }

  @Test
  void setze_thenWritesTheStandOfTheJahr() {
    // When
    nummernkreis.setze(ZAEHLERJAHR, 42);

    // Then
    verify(jpa).setzeZaehler(ZAEHLERJAHR, 42);
  }

  @Test
  void ziehe_thenAnswersWithTheStandAndSchreibtTheNextOneFort() {
    // Given
    final RechnungNummernkreisEntity zeile = zeile(7);
    when(jpa.sperreUndLies(ZAEHLERJAHR)).thenReturn(zeile);

    // When
    final int gezogen = nummernkreis.ziehe(ZAEHLERJAHR);

    // Then — herausgegeben wird der vorgefundene Stand, fortgeschrieben der naechste.
    assertThat(gezogen).isEqualTo(7);
    assertThat(zeile.getNaechsteNummer()).isEqualTo(8);
  }

  @Test
  void ziehe_thenLegtDieZeileAnBevorSieSperrtUndSchreibtSieAusdruecklich() {
    // Given — die Reihenfolge ist die Zusage: Ein FOR UPDATE auf eine fehlende Zeile sperrt nichts,
    // und ohne das ausdrueckliche Schreiben sahe ein zweiter Zug derselben Transaktion die alte
    // Zahl.
    final RechnungNummernkreisEntity zeile = zeile(1);
    when(jpa.sperreUndLies(ZAEHLERJAHR)).thenReturn(zeile);

    // When
    nummernkreis.ziehe(ZAEHLERJAHR);

    // Then
    final InOrder reihenfolge = Mockito.inOrder(jpa);
    reihenfolge.verify(jpa).legeJahrAn(ZAEHLERJAHR);
    reihenfolge.verify(jpa).sperreUndLies(ZAEHLERJAHR);
    reihenfolge.verify(jpa).save(zeile);
    reihenfolge.verifyNoMoreInteractions();
  }
}
