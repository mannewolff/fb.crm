package org.mwolff.fbcrm.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.auth.Konten;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.Role;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Die Verwaltung der Konten durch einen C-Level (Plan #414, A6, A12, A13, E6; fachliche Quelle
 * #409, Kriterien 3 bis 8).
 *
 * <p>Drei Riegel stehen im Mittelpunkt: Eine Adresse, die ein anderes Konto traegt — auch ein
 * stillgelegtes —, wird abgewiesen, ob die Pruefung sie findet oder erst der Index der Datenbank;
 * der letzte aktive C-Level laesst sich weder stilllegen noch herabstufen, und gezaehlt wird dafuer
 * erst nach der Sperre; eine neue Adresse beendet die Sitzung, Name und Rolle tun es nicht.
 */
@ExtendWith(MockitoExtension.class)
class MitarbeitendeVerwaltungTest {

  private static final Instant ANGELEGT = Instant.parse("2026-10-01T08:00:00Z");
  private static final Instant JETZT = Instant.parse("2026-10-23T10:00:00Z");
  private static final long CHEFIN = 1L;
  private static final long ANNA = 2L;
  private static final String CHEFIN_MAIL = "chefin@firma.de";
  private static final String ANNA_MAIL = "anna@firma.de";

  @Mock private AccountRepository accounts;
  @Mock private BegruessungsmailUseCase begruessung;
  @Captor private ArgumentCaptor<Account> gespeichert;

  private MitarbeitendeVerwaltung verwaltung;

  @BeforeEach
  void baueDieVerwaltung() {
    verwaltung =
        new MitarbeitendeVerwaltung(accounts, begruessung, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private static Account konto(
      final long id, final String email, final Role rolle, final boolean aktiv) {
    return Konten.mit(id, email, rolle, aktiv, 3, ANGELEGT);
  }

  private static Account chefin() {
    return konto(CHEFIN, CHEFIN_MAIL, Role.C_LEVEL, true);
  }

  private static Account anna(final Role rolle) {
    return konto(ANNA, ANNA_MAIL, rolle, true);
  }

  private void speichernGibtZurueck() {
    when(accounts.save(any(Account.class))).thenAnswer(aufruf -> aufruf.getArgument(0));
  }

  // --- liste --------------------------------------------------------------------------------

  @Test
  void liste_thenReturnsEveryAccountFromTheRepository() {
    // Given
    final List<Account> alle = List.of(anna(Role.EMPLOYEE), chefin());
    when(accounts.findAllOrderedByDisplayName()).thenReturn(alle);

    // When / Then
    assertThat(verwaltung.liste()).isEqualTo(alle);
  }

  // --- anlegen ------------------------------------------------------------------------------

  @Test
  void anlegen_thenStoresAnActiveAccountWithTheGivenRoleAndSendsTheGreeting() {
    // Given
    when(accounts.findByEmail(ANNA_MAIL)).thenReturn(Optional.empty());
    when(accounts.save(any(Account.class)))
        .thenAnswer(aufruf -> Konten.mitId(ANNA, aufruf.getArgument(0)));
    when(begruessung.sende(ANNA_MAIL, "Anna Berg")).thenReturn(true);

    // When
    final MitarbeitendeVerwaltung.Angelegt ergebnis =
        verwaltung.anlegen("  " + ANNA_MAIL + " ", " Anna Berg  ", Role.EMPLOYEE);

    // Then
    verify(accounts).save(gespeichert.capture());
    final Account neu = gespeichert.getValue();
    assertThat(neu.id()).isNull();
    assertThat(neu.email()).isEqualTo(ANNA_MAIL);
    assertThat(neu.displayName()).isEqualTo("Anna Berg");
    assertThat(neu.role()).isEqualTo(Role.EMPLOYEE);
    assertThat(neu.aktiv()).isTrue();
    assertThat(neu.sessionGeneration()).isZero();
    assertThat(neu.createdAt()).isEqualTo(JETZT);
    assertThat(neu.updatedAt()).isEqualTo(JETZT);
    assertThat(ergebnis.konto().requireId()).isEqualTo(ANNA);
    assertThat(ergebnis.begruessungMailVersandt()).isTrue();
  }

  @Test
  void anlegen_givenMailDisabled_thenReportsThatNoGreetingWentOut() {
    // Given
    when(accounts.findByEmail(ANNA_MAIL)).thenReturn(Optional.empty());
    speichernGibtZurueck();
    when(begruessung.sende(ANNA_MAIL, "Anna Berg")).thenReturn(false);

    // When
    final MitarbeitendeVerwaltung.Angelegt ergebnis =
        verwaltung.anlegen(ANNA_MAIL, "Anna Berg", Role.READ_ONLY);

    // Then
    assertThat(ergebnis.begruessungMailVersandt()).isFalse();
    assertThat(ergebnis.konto().role()).isEqualTo(Role.READ_ONLY);
  }

  @Test
  void anlegen_givenAnAddressOfADeactivatedAccount_thenRefusedWithoutMail() {
    // Given — E6: auch ein stillgelegtes Konto blockiert seine Adresse.
    when(accounts.findByEmail("Anna@Firma.de"))
        .thenReturn(Optional.of(konto(ANNA, ANNA_MAIL, Role.EMPLOYEE, false)));

    // When / Then
    assertThatExceptionOfType(AdresseBereitsVergeben.class)
        .isThrownBy(() -> verwaltung.anlegen("Anna@Firma.de", "Anna Berg", Role.EMPLOYEE));
    verify(accounts, never()).save(any(Account.class));
    verifyNoInteractions(begruessung);
  }

  @Test
  void anlegen_givenTheIndexRefusesTheAddress_thenTheSameRefusalWithoutMail() {
    // Given — ein gleichzeitiges Anlegen derselben Adresse kommt erst am Index an.
    final DataIntegrityViolationException index =
        new DataIntegrityViolationException("account_email_key");
    when(accounts.findByEmail(ANNA_MAIL)).thenReturn(Optional.empty());
    when(accounts.save(any(Account.class))).thenThrow(index);

    // When / Then
    assertThatExceptionOfType(AdresseBereitsVergeben.class)
        .isThrownBy(() -> verwaltung.anlegen(ANNA_MAIL, "Anna Berg", Role.EMPLOYEE))
        .withCause(index)
        .withMessage(null);
    verifyNoInteractions(begruessung);
  }

  // --- aendern ------------------------------------------------------------------------------

  @Test
  void aendern_givenAnUnknownId_thenNotFound() {
    // Given
    when(accounts.findById(9L)).thenReturn(Optional.empty());

    // When / Then
    assertThatExceptionOfType(KontoNichtGefunden.class)
        .isThrownBy(() -> verwaltung.aendern(9L, ANNA_MAIL, "Anna", Role.EMPLOYEE));
    verify(accounts, never()).save(any(Account.class));
  }

  @Test
  void aendern_thenLocksTheActiveCLevelsBeforeReadingTheAccount() {
    // Given
    when(accounts.aktiveCLevelMitSperre()).thenReturn(List.of(chefin()));
    when(accounts.findById(ANNA)).thenReturn(Optional.of(anna(Role.EMPLOYEE)));
    when(accounts.findByEmail(ANNA_MAIL)).thenReturn(Optional.of(anna(Role.EMPLOYEE)));
    speichernGibtZurueck();

    // When
    verwaltung.aendern(ANNA, ANNA_MAIL, "Anna Berg", Role.EMPLOYEE);

    // Then — A6: gelesen wird, was nach der Sperre gilt.
    final InOrder reihenfolge = inOrder(accounts);
    reihenfolge.verify(accounts).aktiveCLevelMitSperre();
    reihenfolge.verify(accounts).findById(ANNA);
  }

  @Test
  void aendern_givenANewName_thenRenamesWithoutEndingTheSession() {
    // Given
    when(accounts.aktiveCLevelMitSperre()).thenReturn(List.of(chefin()));
    when(accounts.findById(ANNA)).thenReturn(Optional.of(anna(Role.EMPLOYEE)));
    when(accounts.findByEmail(ANNA_MAIL)).thenReturn(Optional.of(anna(Role.EMPLOYEE)));
    speichernGibtZurueck();

    // When
    final Account geaendert = verwaltung.aendern(ANNA, ANNA_MAIL, " Anna Mueller ", Role.EMPLOYEE);

    // Then
    assertThat(geaendert.displayName()).isEqualTo("Anna Mueller");
    assertThat(geaendert.email()).isEqualTo(ANNA_MAIL);
    assertThat(geaendert.sessionGeneration()).isEqualTo(3);
    assertThat(geaendert.updatedAt()).isEqualTo(JETZT);
    assertThat(geaendert.createdAt()).isEqualTo(ANGELEGT);
  }

  @Test
  void aendern_givenANewAddress_thenEndsTheSession() {
    // Given — Kriterium 6: nach einer neuen Adresse meldet sich die Person neu an.
    when(accounts.aktiveCLevelMitSperre()).thenReturn(List.of(chefin()));
    when(accounts.findById(ANNA)).thenReturn(Optional.of(anna(Role.EMPLOYEE)));
    when(accounts.findByEmail("anna.berg@firma.de")).thenReturn(Optional.empty());
    speichernGibtZurueck();

    // When
    final Account geaendert =
        verwaltung.aendern(ANNA, " anna.berg@firma.de ", "Anna Berg", Role.EMPLOYEE);

    // Then
    assertThat(geaendert.email()).isEqualTo("anna.berg@firma.de");
    assertThat(geaendert.sessionGeneration()).isEqualTo(4);
  }

  @Test
  void aendern_givenTheAddressOfAnotherAccount_thenRefused() {
    // Given — gefunden ohne Ruecksicht auf die Schreibweise, auch stillgelegt.
    when(accounts.aktiveCLevelMitSperre()).thenReturn(List.of(chefin()));
    when(accounts.findById(ANNA)).thenReturn(Optional.of(anna(Role.EMPLOYEE)));
    when(accounts.findByEmail("CHEFIN@firma.de"))
        .thenReturn(Optional.of(konto(CHEFIN, CHEFIN_MAIL, Role.C_LEVEL, false)));

    // When / Then
    assertThatExceptionOfType(AdresseBereitsVergeben.class)
        .isThrownBy(() -> verwaltung.aendern(ANNA, "CHEFIN@firma.de", "Anna", Role.EMPLOYEE));
    verify(accounts, never()).save(any(Account.class));
  }

  @Test
  void aendern_givenTheIndexRefusesTheAddress_thenTheSameRefusal() {
    // Given
    final DataIntegrityViolationException index =
        new DataIntegrityViolationException("account_email_key");
    when(accounts.aktiveCLevelMitSperre()).thenReturn(List.of(chefin()));
    when(accounts.findById(ANNA)).thenReturn(Optional.of(anna(Role.EMPLOYEE)));
    when(accounts.findByEmail("neu@firma.de")).thenReturn(Optional.empty());
    when(accounts.save(any(Account.class))).thenThrow(index);

    // When / Then
    assertThatExceptionOfType(AdresseBereitsVergeben.class)
        .isThrownBy(() -> verwaltung.aendern(ANNA, "neu@firma.de", "Anna", Role.EMPLOYEE))
        .withCause(index);
  }

  @Test
  void aendern_givenANewRole_thenTheRoleChangesWithoutEndingTheSession() {
    // Given — E5: die Rolle wirkt beim naechsten Schritt, ohne Abmeldung.
    when(accounts.aktiveCLevelMitSperre()).thenReturn(List.of(chefin()));
    when(accounts.findById(ANNA)).thenReturn(Optional.of(anna(Role.EMPLOYEE)));
    when(accounts.findByEmail(ANNA_MAIL)).thenReturn(Optional.of(anna(Role.EMPLOYEE)));
    speichernGibtZurueck();

    // When
    final Account geaendert = verwaltung.aendern(ANNA, ANNA_MAIL, "Anna", Role.READ_ONLY);

    // Then
    assertThat(geaendert.role()).isEqualTo(Role.READ_ONLY);
    assertThat(geaendert.sessionGeneration()).isEqualTo(3);
  }

  @Test
  void aendern_givenTheLastActiveCLevelDemoted_thenRefused() {
    // Given — Kriterium 8.
    when(accounts.aktiveCLevelMitSperre()).thenReturn(List.of(chefin()));
    when(accounts.findById(CHEFIN)).thenReturn(Optional.of(chefin()));
    when(accounts.findByEmail(CHEFIN_MAIL)).thenReturn(Optional.of(chefin()));

    // When / Then
    assertThatExceptionOfType(LetzterCLevel.class)
        .isThrownBy(() -> verwaltung.aendern(CHEFIN, CHEFIN_MAIL, "Chefin", Role.EMPLOYEE));
    verify(accounts, never()).save(any(Account.class));
  }

  @Test
  void aendern_givenTheLastActiveCLevelKeepsTheRole_thenRenames() {
    // Given — ohne Rollenwechsel greift der Riegel nicht.
    when(accounts.aktiveCLevelMitSperre()).thenReturn(List.of(chefin()));
    when(accounts.findById(CHEFIN)).thenReturn(Optional.of(chefin()));
    when(accounts.findByEmail(CHEFIN_MAIL)).thenReturn(Optional.of(chefin()));
    speichernGibtZurueck();

    // When
    final Account geaendert = verwaltung.aendern(CHEFIN, CHEFIN_MAIL, "Chefin", Role.C_LEVEL);

    // Then
    assertThat(geaendert.displayName()).isEqualTo("Chefin");
  }

  @Test
  void aendern_givenASecondActiveCLevel_thenTheDemotionPasses() {
    // Given — Kriterium 8: auch das eigene Konto, solange ein anderer C-Level bleibt.
    final Account zweite = konto(ANNA, ANNA_MAIL, Role.C_LEVEL, true);
    when(accounts.aktiveCLevelMitSperre()).thenReturn(List.of(chefin(), zweite));
    when(accounts.findById(CHEFIN)).thenReturn(Optional.of(chefin()));
    when(accounts.findByEmail(CHEFIN_MAIL)).thenReturn(Optional.of(chefin()));
    speichernGibtZurueck();

    // When
    final Account geaendert = verwaltung.aendern(CHEFIN, CHEFIN_MAIL, "Chefin", Role.EMPLOYEE);

    // Then
    assertThat(geaendert.role()).isEqualTo(Role.EMPLOYEE);
  }

  @Test
  void aendern_givenAnEmployeeWhileOneOtherCLevelIsLeft_thenTheRoleChanges() {
    // Given — die eine Chefin ist nicht betroffen.
    when(accounts.aktiveCLevelMitSperre()).thenReturn(List.of(chefin()));
    when(accounts.findById(ANNA)).thenReturn(Optional.of(anna(Role.EMPLOYEE)));
    when(accounts.findByEmail(ANNA_MAIL)).thenReturn(Optional.of(anna(Role.EMPLOYEE)));
    speichernGibtZurueck();

    // When
    final Account geaendert = verwaltung.aendern(ANNA, ANNA_MAIL, "Anna", Role.READ_ONLY);

    // Then
    assertThat(geaendert.role()).isEqualTo(Role.READ_ONLY);
  }

  // --- stilllegen und aktivieren ------------------------------------------------------------

  @Test
  void stilllegen_givenAnUnknownId_thenNotFound() {
    // Given
    when(accounts.findById(9L)).thenReturn(Optional.empty());

    // When / Then
    assertThatExceptionOfType(KontoNichtGefunden.class).isThrownBy(() -> verwaltung.stilllegen(9L));
  }

  @Test
  void stilllegen_thenLocksTheActiveCLevelsBeforeReadingTheAccount() {
    // Given
    when(accounts.aktiveCLevelMitSperre()).thenReturn(List.of(chefin()));
    when(accounts.findById(ANNA)).thenReturn(Optional.of(anna(Role.EMPLOYEE)));
    speichernGibtZurueck();

    // When
    verwaltung.stilllegen(ANNA);

    // Then
    final InOrder reihenfolge = inOrder(accounts);
    reihenfolge.verify(accounts).aktiveCLevelMitSperre();
    reihenfolge.verify(accounts).findById(ANNA);
  }

  @Test
  void stilllegen_thenDeactivatesAndEndsTheSession() {
    // Given — Kriterium 7.
    when(accounts.aktiveCLevelMitSperre()).thenReturn(List.of(chefin()));
    when(accounts.findById(ANNA)).thenReturn(Optional.of(anna(Role.EMPLOYEE)));
    speichernGibtZurueck();

    // When
    verwaltung.stilllegen(ANNA);

    // Then
    verify(accounts).save(gespeichert.capture());
    assertThat(gespeichert.getValue().aktiv()).isFalse();
    assertThat(gespeichert.getValue().sessionGeneration()).isEqualTo(4);
    assertThat(gespeichert.getValue().updatedAt()).isEqualTo(JETZT);
  }

  @Test
  void stilllegen_givenTheLastActiveCLevel_thenRefused() {
    // Given — Kriterium 8, auch fuer das eigene Konto.
    when(accounts.aktiveCLevelMitSperre()).thenReturn(List.of(chefin()));
    when(accounts.findById(CHEFIN)).thenReturn(Optional.of(chefin()));

    // When / Then
    assertThatExceptionOfType(LetzterCLevel.class).isThrownBy(() -> verwaltung.stilllegen(CHEFIN));
    verify(accounts, never()).save(any(Account.class));
  }

  @Test
  void stilllegen_givenASecondActiveCLevel_thenPasses() {
    // Given
    final Account zweite = konto(ANNA, ANNA_MAIL, Role.C_LEVEL, true);
    when(accounts.aktiveCLevelMitSperre()).thenReturn(List.of(chefin(), zweite));
    when(accounts.findById(CHEFIN)).thenReturn(Optional.of(chefin()));
    speichernGibtZurueck();

    // When
    verwaltung.stilllegen(CHEFIN);

    // Then
    verify(accounts).save(gespeichert.capture());
    assertThat(gespeichert.getValue().aktiv()).isFalse();
  }

  @Test
  void aktivieren_thenReactivatesTheAccount() {
    // Given
    when(accounts.findById(ANNA))
        .thenReturn(Optional.of(konto(ANNA, ANNA_MAIL, Role.EMPLOYEE, false)));
    speichernGibtZurueck();

    // When
    verwaltung.aktivieren(ANNA);

    // Then
    verify(accounts).save(gespeichert.capture());
    assertThat(gespeichert.getValue().aktiv()).isTrue();
    assertThat(gespeichert.getValue().updatedAt()).isEqualTo(JETZT);
    verify(accounts, never()).aktiveCLevelMitSperre();
  }

  @Test
  void aktivieren_givenAnUnknownId_thenNotFound() {
    // Given
    when(accounts.findById(9L)).thenReturn(Optional.empty());

    // When / Then
    assertThatExceptionOfType(KontoNichtGefunden.class).isThrownBy(() -> verwaltung.aktivieren(9L));
    verify(begruessung, never()).sende(anyString(), anyString());
  }
}
