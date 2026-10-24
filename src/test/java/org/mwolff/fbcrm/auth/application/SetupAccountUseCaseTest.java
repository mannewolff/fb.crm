package org.mwolff.fbcrm.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mwolff.fbcrm.auth.infrastructure.LoginAttemptLimiter.Zaehlart.ADRESSE_STUNDE;
import static org.mwolff.fbcrm.auth.infrastructure.LoginAttemptLimiter.Zaehlart.ADRESSE_TAG;
import static org.mwolff.fbcrm.auth.infrastructure.LoginAttemptLimiter.Zaehlart.HERKUNFT;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.mwolff.fbcrm.auth.Konten;
import org.mwolff.fbcrm.auth.SetupProperties;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.ErstesKontoFolge;
import org.mwolff.fbcrm.auth.domain.LoginCode;
import org.mwolff.fbcrm.auth.domain.LoginCodeRepository;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.auth.domain.SecretHasher;
import org.mwolff.fbcrm.auth.domain.SessionTokens;
import org.mwolff.fbcrm.auth.infrastructure.FailedLoginDelay;
import org.mwolff.fbcrm.auth.infrastructure.LoginAttemptLimiter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Die Einrichtung der frischen Instanz per Einmal-Schluessel und Code (E5, E9, K3).
 *
 * <p>Zwei Faelle tragen dieses Paket. Der erste ist der <b>leere konfigurierte Schluessel</b>:
 * {@code docker-compose.yml} Z. 47 f. sagt „leer = deaktiviert" zu. Ein konstanter Vergleich gegen
 * den Leerstring nimmt aber genau die leere Eingabe an — die Zusage waere ins Gegenteil verkehrt.
 * Der zweite ist das <b>Wettrennen</b>: Kommt die Abweisung der Datenbank, muss sie aussehen wie
 * „Konto existiert bereits", sonst verriete der Unterschied, welcher der beiden Riegel griff.
 *
 * <p>Fuer die Anforderung des Codes laeuft die Zaehlbremse <b>echt</b> und bespitzelt: Nur so
 * laesst sich zeigen, dass gezaehlt wird, bevor der Schluessel geprueft ist, und dass die sechste
 * Anforderung in der Stunde auch mit falschem Schluessel an der Bremse endet (Kriterium 6, letzter
 * Satz zu Kriterium 5). Die Ausstellung ist gemockt: Sie ist der einzige Weg zu einem
 * Zustellauftrag, und was sie dabei tut, weist {@code RequestLoginCodeUseCaseTest} nach. Die
 * Einloesung des Codes laeuft echt, gemockt sind nur der Bestand der Codes und der Hasher.
 */
@ExtendWith(MockitoExtension.class)
class SetupAccountUseCaseTest {

  private static final String MAIL = "manne@example.org";
  private static final String NAME = "Manne";
  private static final String CODE = "042424";
  private static final String HASH = "$argon2id$v=19$hash-des-codes";
  private static final String SCHLUESSEL = "einmal-schluessel-der-einrichtung";
  private static final String TOKEN = "nutzlast.signatur";
  private static final Instant JETZT = Instant.parse("2026-09-18T10:00:00Z");
  private static final long KONTO_ID = 1L;
  private static final String IP = "203.0.113.7";
  private static final String FALSCH = "falscher-schluessel";

  @Mock private AccountRepository accounts;
  @Mock private SecretHasher hasher;
  @Mock private SessionTokens tokens;
  @Mock private RequestLoginCodeUseCase ausstellung;
  @Mock private LoginCodeRepository loginCodes;
  @Mock private FailedLoginDelay delay;
  @Mock private ErstesKontoFolge folge;

  /** Wie oft die Einrichtung gerade in ihrer Transaktion steht; 0 heisst ausserhalb. */
  private int offeneTransaktionen;

  /**
   * Eine Transaktion ohne Datenbank: Sie zaehlt nur mit, ob der Rueckruf in ihr laeuft — so zeigt
   * der Test, dass Anlage und Folge in derselben Klammer stehen (E13).
   */
  private final TransactionOperations transaktion =
      new TransactionOperations() {
        @Override
        public <T> T execute(final TransactionCallback<T> rueckruf) {
          offeneTransaktionen++;
          try {
            return rueckruf.doInTransaction(null);
          } finally {
            offeneTransaktionen--;
          }
        }
      };

  private final LoginAttemptLimiter zaehler =
      spy(new LoginAttemptLimiter(schalter(), Clock.fixed(JETZT, ZoneOffset.UTC)));

  private static AuthProperties schalter() {
    return new AuthProperties(
        "geheimnis-mit-mindestens-32-zeichen-laenge",
        Duration.ofDays(1),
        "fbcrm_session",
        true,
        10,
        Duration.ofMinutes(15),
        5,
        Duration.ofHours(1),
        10,
        Duration.ofHours(24),
        Duration.ofMinutes(10),
        5,
        List.of());
  }

  private SetupAccountUseCase einrichtung(final String konfigurierterSchluessel) {
    return new SetupAccountUseCase(
        accounts,
        tokens,
        zaehler,
        delay,
        ausstellung,
        new CodeEinloesung(
            loginCodes, hasher, zaehler, schalter(), Clock.fixed(JETZT, ZoneOffset.UTC)),
        schalter(),
        new SetupProperties(konfigurierterSchluessel),
        folge,
        transaktion,
        Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  /** Zu MAIL liegt ein frischer Code der Einrichtung, und der eingegebene stimmt. */
  private void codeStimmt() {
    when(loginCodes.findByEmail(MAIL))
        .thenReturn(Optional.of(LoginCode.ausgestellt(MAIL, HASH, JETZT, Duration.ofMinutes(10))));
    when(hasher.matches(CODE, HASH)).thenReturn(true);
  }

  /**
   * Der vollstaendig vorbereitete Erfolgsfall: kein Konto, der Code stimmt, das Token liegt bereit.
   */
  private SetupAccountUseCase frischeInstanz() {
    when(accounts.existsAnyAccount()).thenReturn(false);
    codeStimmt();
    when(accounts.save(any(Account.class)))
        .thenAnswer(
            aufruf -> {
              final Account uebergeben = aufruf.getArgument(0);
              return Konten.mitId(KONTO_ID, uebergeben);
            });
    when(tokens.encode(KONTO_ID, 0L, JETZT, Duration.ofDays(1))).thenReturn(TOKEN);
    return einrichtung(SCHLUESSEL);
  }

  private static LoginResult richteEin(final SetupAccountUseCase einrichtung) {
    return einrichtung.initialize(MAIL, NAME, CODE, SCHLUESSEL, IP);
  }

  private Account gespeichertesKonto() {
    final ArgumentCaptor<Account> gespeichert = ArgumentCaptor.forClass(Account.class);
    verify(accounts).save(gespeichert.capture());
    return gespeichert.getValue();
  }

  @Test
  void initialize_givenTheRightKeyOnAFreshInstance_thenCreatesACLevelAccount() {
    // Given
    final SetupAccountUseCase einrichtung = frischeInstanz();

    // When
    richteEin(einrichtung);

    // Then — das erste Konto ist C-Level, ohne dass jemand nach einer Rolle fragt (#409, 2).
    assertThat(gespeichertesKonto().role()).isEqualTo(Role.C_LEVEL);
  }

  @Test
  void initialize_givenTheRightKeyOnAFreshInstance_thenCreatesAnActiveAccount() {
    // Given
    final SetupAccountUseCase einrichtung = frischeInstanz();

    // When
    richteEin(einrichtung);

    // Then
    assertThat(gespeichertesKonto().aktiv()).isTrue();
  }

  @Test
  void initialize_givenTheRightCode_thenUsesItUp() {
    // Given
    final SetupAccountUseCase einrichtung = frischeInstanz();

    // When
    richteEin(einrichtung);

    // Then — der Code der Einrichtung gilt nur einmal (Kriterium 4).
    verify(loginCodes).deleteByEmail(MAIL);
  }

  @Test
  void initialize_givenAWrongCode_thenFailsLikeALoginAndCreatesNoAccount() {
    // Given — fuer den falschen Code hilft derselbe Schritt wie bei der Anmeldung: ein neuer Code.
    when(accounts.existsAnyAccount()).thenReturn(false);
    when(loginCodes.findByEmail(MAIL))
        .thenReturn(Optional.of(LoginCode.ausgestellt(MAIL, HASH, JETZT, Duration.ofMinutes(10))));
    when(hasher.matches(CODE, HASH)).thenReturn(false);
    final SetupAccountUseCase einrichtung = einrichtung(SCHLUESSEL);

    // When
    assertThatExceptionOfType(LoginFailed.class).isThrownBy(() -> richteEin(einrichtung));

    // Then
    verify(accounts, never()).save(any(Account.class));
    verify(delay).apply();
  }

  @Test
  void initialize_givenNoCodeForTheAddress_thenFailsAndCreatesNoAccount() {
    // Given — vertippte Adresse: Zu der richtigen ging nie ein Code (Kriterium 6).
    when(accounts.existsAnyAccount()).thenReturn(false);
    when(loginCodes.findByEmail(MAIL)).thenReturn(Optional.empty());
    final SetupAccountUseCase einrichtung = einrichtung(SCHLUESSEL);

    // When
    assertThatExceptionOfType(LoginFailed.class).isThrownBy(() -> richteEin(einrichtung));

    // Then
    verify(accounts, never()).save(any(Account.class));
  }

  @Test
  void initialize_givenAWrongKey_thenNeverLooksAtTheCode() {
    // Given — ein falscher Schluessel endet wie bisher, ohne den Code anzuruehren.
    final SetupAccountUseCase einrichtung = einrichtung(SCHLUESSEL);

    // When
    assertThatExceptionOfType(SetupRefused.class)
        .isThrownBy(() -> einrichtung.initialize(MAIL, NAME, CODE, FALSCH, IP));

    // Then
    verify(loginCodes, never()).findByEmail(anyString());
    verify(delay, never()).apply();
  }

  @Test
  void initialize_givenAWrongKey_thenHasCountedTheInputAtTheOrigin() {
    // Given — ein vor dem Zaehlen gepruefter Schluessel liesse die Bestaetigung ungebremst.
    final SetupAccountUseCase einrichtung = einrichtung(SCHLUESSEL);

    // When
    assertThatExceptionOfType(SetupRefused.class)
        .isThrownBy(() -> einrichtung.initialize(MAIL, NAME, CODE, FALSCH, IP));

    // Then
    verify(zaehler).record(HERKUNFT, IP);
  }

  @Test
  void initialize_givenTenInputsFromTheOrigin_thenRefusesAtTheBrakeBeforeTheKey() {
    // Given — Kriterium 6: die Bremse je Herkunft gilt auch fuer die Einrichtungsseite.
    final SetupAccountUseCase einrichtung = einrichtung(SCHLUESSEL);
    IntStream.range(0, 10)
        .forEach(
            unbenutzt ->
                assertThatExceptionOfType(SetupRefused.class)
                    .isThrownBy(() -> einrichtung.initialize(MAIL, NAME, CODE, FALSCH, IP)));

    // When / Then
    assertThatExceptionOfType(TooManyLoginAttempts.class)
        .isThrownBy(() -> einrichtung.initialize(MAIL, NAME, CODE, FALSCH, IP));
  }

  @Test
  void initialize_givenTheRightKeyOnAFreshInstance_thenKeepsTheEnteredAddressAndName() {
    // Given
    final SetupAccountUseCase einrichtung = frischeInstanz();

    // When
    richteEin(einrichtung);

    // Then
    assertThat(gespeichertesKonto())
        .extracting(Account::email, Account::displayName)
        .containsExactly(MAIL, NAME);
  }

  @Test
  void initialize_givenTheRightKeyOnAFreshInstance_thenStampsTheAccountWithTheInjectedClock() {
    // Given
    final SetupAccountUseCase einrichtung = frischeInstanz();

    // When
    richteEin(einrichtung);

    // Then
    assertThat(gespeichertesKonto())
        .extracting(Account::createdAt, Account::updatedAt)
        .containsExactly(JETZT, JETZT);
  }

  @Test
  void initialize_givenTheRightKeyOnAFreshInstance_thenStartsAtSessionGenerationZero() {
    // Given
    final SetupAccountUseCase einrichtung = frischeInstanz();

    // When
    richteEin(einrichtung);

    // Then
    assertThat(gespeichertesKonto().sessionGeneration()).isZero();
  }

  @Test
  void initialize_givenTheRightKeyOnAFreshInstance_thenAnswersWithTheSavedAccount() {
    // Given
    final SetupAccountUseCase einrichtung = frischeInstanz();

    // When
    final LoginResult ergebnis = richteEin(einrichtung);

    // Then
    assertThat(ergebnis.account().requireId()).isEqualTo(KONTO_ID);
  }

  @Test
  void initialize_givenTheRightKeyOnAFreshInstance_thenSignsInTheOperatorImmediately() {
    // Given — K3: einrichten und anmelden sind ein Schritt, nicht zwei.
    final SetupAccountUseCase einrichtung = frischeInstanz();

    // When
    final LoginResult ergebnis = richteEin(einrichtung);

    // Then
    assertThat(ergebnis.cookie())
        .isEqualTo(new SessionCookie("fbcrm_session", TOKEN, Duration.ofDays(1), true));
  }

  @Test
  void initialize_givenTheRightKeyOnAFreshInstance_thenHandsTheNewAccountToTheFolge() {
    // Given — E13: Kommentare ohne Verfasser bekommt das erste Konto.
    final SetupAccountUseCase einrichtung = frischeInstanz();

    // When
    richteEin(einrichtung);

    // Then
    final InOrder reihenfolge = inOrder(accounts, folge);
    reihenfolge.verify(accounts).save(any(Account.class));
    reihenfolge.verify(folge).erstesKontoAngelegt(KONTO_ID);
  }

  @Test
  void initialize_givenTheRightKeyOnAFreshInstance_thenSavesAndHandsOverInOneTransaction() {
    // Given — E13: scheitert die Folge, entsteht auch das Konto nicht.
    when(accounts.existsAnyAccount()).thenReturn(false);
    codeStimmt();
    when(tokens.encode(KONTO_ID, 0L, JETZT, Duration.ofDays(1))).thenReturn(TOKEN);
    final SetupAccountUseCase einrichtung = einrichtung(SCHLUESSEL);
    final List<Integer> beimSpeichern = new ArrayList<>();
    final List<Integer> beiDerFolge = new ArrayList<>();
    when(accounts.save(any(Account.class)))
        .thenAnswer(
            aufruf -> {
              beimSpeichern.add(offeneTransaktionen);
              return Konten.mitId(KONTO_ID, aufruf.getArgument(0));
            });
    doAnswer(
            aufruf -> {
              beiDerFolge.add(offeneTransaktionen);
              return null;
            })
        .when(folge)
        .erstesKontoAngelegt(KONTO_ID);

    // When
    richteEin(einrichtung);

    // Then — beide in derselben einen Klammer, danach ist sie geschlossen.
    assertThat(beimSpeichern).containsExactly(1);
    assertThat(beiDerFolge).containsExactly(1);
    assertThat(offeneTransaktionen).isZero();
  }

  @Test
  void initialize_givenAWrongKey_thenRefuses() {
    // Given
    final SetupAccountUseCase einrichtung = einrichtung(SCHLUESSEL);

    // When / Then
    assertThatExceptionOfType(SetupRefused.class)
        .isThrownBy(() -> einrichtung.initialize(MAIL, NAME, CODE, FALSCH, IP));
  }

  @Test
  void initialize_givenAWrongKey_thenNeverTouchesTheAccounts() {
    // Given — ohne gueltigen Schluessel wird nicht einmal nachgesehen, ob es ein Konto gibt.
    final SetupAccountUseCase einrichtung = einrichtung(SCHLUESSEL);

    // When
    assertThatExceptionOfType(SetupRefused.class)
        .isThrownBy(() -> einrichtung.initialize(MAIL, NAME, CODE, FALSCH, IP));

    // Then
    verify(accounts, never()).save(any(Account.class));
  }

  @Test
  void initialize_givenAKeyThatIsOnlyAPrefix_thenRefuses() {
    // Given — der Vergleich ist auf Gleichheit, nicht auf Anfang.
    final SetupAccountUseCase einrichtung = einrichtung(SCHLUESSEL);

    // When / Then
    assertThatExceptionOfType(SetupRefused.class)
        .isThrownBy(() -> einrichtung.initialize(MAIL, NAME, CODE, "einmal-schluessel", IP));
  }

  @Test
  void initialize_givenAnEmptyConfiguredKey_thenRefusesAnEmptyInputAsWell() {
    // Given — E5: „leer = deaktiviert" aus docker-compose.yml Z. 47 f. Ein konstanter Vergleich
    // gegen den Leerstring naehme genau diese Eingabe an.
    final SetupAccountUseCase einrichtung = einrichtung("");

    // When / Then
    assertThatExceptionOfType(SetupRefused.class)
        .isThrownBy(() -> einrichtung.initialize(MAIL, NAME, CODE, "", IP));
  }

  @Test
  void initialize_givenAnEmptyConfiguredKey_thenRefusesAnyInput() {
    // Given
    final SetupAccountUseCase einrichtung = einrichtung("");

    // When / Then
    assertThatExceptionOfType(SetupRefused.class)
        .isThrownBy(() -> einrichtung.initialize(MAIL, NAME, CODE, "irgendwas", IP));
  }

  @Test
  void initialize_givenAnEmptyConfiguredKey_thenNeverTouchesTheAccounts() {
    // Given
    final SetupAccountUseCase einrichtung = einrichtung("");

    // When
    assertThatExceptionOfType(SetupRefused.class)
        .isThrownBy(() -> einrichtung.initialize(MAIL, NAME, CODE, "", IP));

    // Then
    verify(accounts, never()).save(any(Account.class));
  }

  @Test
  void initialize_givenAnInstanceThatAlreadyHasAnAccount_thenRefuses() {
    // Given — K3: der Einmal-Schluessel wirkt genau einmal.
    when(accounts.existsAnyAccount()).thenReturn(true);
    final SetupAccountUseCase einrichtung = einrichtung(SCHLUESSEL);

    // When / Then
    assertThatExceptionOfType(SetupRefused.class).isThrownBy(() -> richteEin(einrichtung));
  }

  @Test
  void initialize_givenAnInstanceThatAlreadyHasAnAccount_thenStoresNothing() {
    // Given
    when(accounts.existsAnyAccount()).thenReturn(true);
    final SetupAccountUseCase einrichtung = einrichtung(SCHLUESSEL);

    // When
    assertThatExceptionOfType(SetupRefused.class).isThrownBy(() -> richteEin(einrichtung));

    // Then
    verify(accounts, never()).save(any(Account.class));
  }

  @Test
  void initialize_givenALostRaceAgainstTheUniqueIndex_thenRefusesLikeAnExistingAccount() {
    // Given — zwei gleichzeitige Aufrufe mit derselben Adresse kommen beide durch
    // existsAnyAccount; was sie trennt, ist account_email_key auf Ebene der Datenbank (A5).
    when(accounts.existsAnyAccount()).thenReturn(false);
    codeStimmt();
    when(accounts.save(any(Account.class)))
        .thenThrow(new DataIntegrityViolationException("account_email_key"));
    final SetupAccountUseCase einrichtung = einrichtung(SCHLUESSEL);

    // When / Then
    assertThatExceptionOfType(SetupRefused.class).isThrownBy(() -> richteEin(einrichtung));
  }

  @Test
  void initialize_givenALostRaceAgainstTheUniqueIndex_thenNeverIssuesASession() {
    // Given
    when(accounts.existsAnyAccount()).thenReturn(false);
    codeStimmt();
    when(accounts.save(any(Account.class)))
        .thenThrow(new DataIntegrityViolationException("account_email_key"));
    final SetupAccountUseCase einrichtung = einrichtung(SCHLUESSEL);

    // When
    assertThatExceptionOfType(SetupRefused.class).isThrownBy(() -> richteEin(einrichtung));

    // Then
    verify(tokens, never()).encode(anyLong(), anyLong(), any(Instant.class), any(Duration.class));
  }

  @Test
  void initialize_givenALostRaceAgainstTheUniqueIndex_thenNeverCallsTheFolge() {
    // Given — ohne neues Konto gibt es keines, dem Kommentare zufallen koennten (E13).
    when(accounts.existsAnyAccount()).thenReturn(false);
    codeStimmt();
    when(accounts.save(any(Account.class)))
        .thenThrow(new DataIntegrityViolationException("account_email_key"));
    final SetupAccountUseCase einrichtung = einrichtung(SCHLUESSEL);

    // When
    assertThatExceptionOfType(SetupRefused.class).isThrownBy(() -> richteEin(einrichtung));

    // Then
    verify(folge, never()).erstesKontoAngelegt(anyLong());
  }

  @Test
  void initialize_givenALostRaceAgainstTheUniqueIndex_thenNeverCarriesTheDatabaseTextOutwards() {
    // Given — der Text der Datenbank nennt Tabelle und Index; er gehoert nicht in die Antwort.
    when(accounts.existsAnyAccount()).thenReturn(false);
    codeStimmt();
    when(accounts.save(any(Account.class)))
        .thenThrow(new DataIntegrityViolationException("account_email_key"));
    final SetupAccountUseCase einrichtung = einrichtung(SCHLUESSEL);

    // When / Then
    assertThatExceptionOfType(SetupRefused.class)
        .isThrownBy(() -> richteEin(einrichtung))
        .withMessage(null);
  }

  @Test
  void isInitialized_givenAnInstanceWithAnAccount_thenIsTrue() {
    // Given
    when(accounts.existsAnyAccount()).thenReturn(true);

    // When / Then
    assertThat(einrichtung(SCHLUESSEL).isInitialized()).isTrue();
  }

  @Test
  void isInitialized_givenAFreshInstance_thenIsFalse() {
    // Given
    when(accounts.existsAnyAccount()).thenReturn(false);

    // When / Then
    assertThat(einrichtung(SCHLUESSEL).isInitialized()).isFalse();
  }

  /** Fuenf Anforderungen, die jede an ihrem Riegel abgewiesen werden — nicht an der Bremse. */
  private static void fordereFuenfmalAn(final SetupAccountUseCase einrichtung, final String key) {
    IntStream.range(0, 5)
        .forEach(
            unbenutzt ->
                assertThatExceptionOfType(SetupRefused.class)
                    .isThrownBy(() -> einrichtung.requestCode(MAIL, NAME, key, IP)));
  }

  @Test
  void requestCode_givenTheRightKeyOnAFreshInstance_thenIssuesACodeToTheEnteredAddress() {
    // Given — ein Konto gibt es noch nicht, darum geht der Anzeigename mit (E8).
    when(accounts.existsAnyAccount()).thenReturn(false);
    final SetupAccountUseCase einrichtung = einrichtung(SCHLUESSEL);

    // When
    einrichtung.requestCode(MAIL, NAME, SCHLUESSEL, IP);

    // Then
    verify(ausstellung).ausstellen(MAIL, NAME);
  }

  @Test
  void requestCode_givenTheRightKeyOnAFreshInstance_thenCreatesNoAccountYet() {
    // Given — erst der richtige Code legt das Konto an (Kriterium 6).
    when(accounts.existsAnyAccount()).thenReturn(false);
    final SetupAccountUseCase einrichtung = einrichtung(SCHLUESSEL);

    // When
    einrichtung.requestCode(MAIL, NAME, SCHLUESSEL, IP);

    // Then
    verify(accounts, never()).save(any(Account.class));
  }

  @Test
  void requestCode_givenAWrongKey_thenRefusesAndIssuesNoCode() {
    // Given — Kriterium 6: mit falschem Schluessel verschickt fb.crm keinen Code.
    final SetupAccountUseCase einrichtung = einrichtung(SCHLUESSEL);

    // When
    assertThatExceptionOfType(SetupRefused.class)
        .isThrownBy(() -> einrichtung.requestCode(MAIL, NAME, FALSCH, IP));

    // Then
    verify(ausstellung, never()).ausstellen(anyString(), anyString());
  }

  @Test
  void requestCode_givenAnEmptyConfiguredKey_thenRefusesAnEmptyInputAsWell() {
    // Given — E5: „leer = deaktiviert" gilt auch fuer die Anforderung des Codes.
    final SetupAccountUseCase einrichtung = einrichtung("");

    // When
    assertThatExceptionOfType(SetupRefused.class)
        .isThrownBy(() -> einrichtung.requestCode(MAIL, NAME, "", IP));

    // Then
    verify(ausstellung, never()).ausstellen(anyString(), anyString());
  }

  @Test
  void requestCode_givenAnInstanceThatAlreadyHasAnAccount_thenRefusesAndIssuesNoCode() {
    // Given — der Einmal-Schluessel wirkt genau einmal, auch fuer die Anforderung.
    when(accounts.existsAnyAccount()).thenReturn(true);
    final SetupAccountUseCase einrichtung = einrichtung(SCHLUESSEL);

    // When
    assertThatExceptionOfType(SetupRefused.class)
        .isThrownBy(() -> einrichtung.requestCode(MAIL, NAME, SCHLUESSEL, IP));

    // Then
    verify(ausstellung, never()).ausstellen(anyString(), anyString());
  }

  @Test
  void requestCode_givenAWrongKey_thenHasCountedInAllThreeKindsBeforeTheCheck() {
    // Given — ein vor dem Zaehlen gepruefter Schluessel liesse die Einrichtungsseite ungebremst.
    final SetupAccountUseCase einrichtung = einrichtung(SCHLUESSEL);
    final InOrder reihenfolge = inOrder(zaehler, ausstellung);

    // When
    assertThatExceptionOfType(SetupRefused.class)
        .isThrownBy(() -> einrichtung.requestCode(MAIL, NAME, FALSCH, IP));

    // Then
    reihenfolge.verify(zaehler).record(HERKUNFT, IP);
    reihenfolge.verify(zaehler).record(ADRESSE_STUNDE, MAIL);
    reihenfolge.verify(zaehler).record(ADRESSE_TAG, MAIL);
  }

  @Test
  void requestCode_givenFiveRequestsWithAWrongKey_thenRefusesTheSixthAtTheBrakeWithTheWait() {
    // Given — Kriterium 6: die Grenzen aus Kriterium 5 gelten auch fuer die Einrichtungsseite.
    final SetupAccountUseCase einrichtung = einrichtung(SCHLUESSEL);
    fordereFuenfmalAn(einrichtung, FALSCH);

    // When / Then
    assertThatExceptionOfType(TooManyLoginAttempts.class)
        .isThrownBy(() -> einrichtung.requestCode(MAIL, NAME, FALSCH, IP))
        .satisfies(gebremst -> assertThat(gebremst.restwartezeit()).isEqualTo(Duration.ofHours(1)));
  }

  @Test
  void requestCode_givenTheLimitReached_thenLooksNeitherAtTheInstanceNorIssues() {
    // Given — fuenf Anforderungen an einer schon eingerichteten Instanz.
    when(accounts.existsAnyAccount()).thenReturn(true);
    final SetupAccountUseCase einrichtung = einrichtung(SCHLUESSEL);
    fordereFuenfmalAn(einrichtung, SCHLUESSEL);
    clearInvocations(accounts);

    // When
    assertThatExceptionOfType(TooManyLoginAttempts.class)
        .isThrownBy(() -> einrichtung.requestCode(MAIL, NAME, SCHLUESSEL, IP));

    // Then
    verify(accounts, never()).existsAnyAccount();
    verify(ausstellung, never()).ausstellen(anyString(), anyString());
  }
}
