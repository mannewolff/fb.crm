package org.mwolff.fbcrm.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.Serial;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.LoginCode;
import org.mwolff.fbcrm.auth.domain.LoginCodeRepository;
import org.mwolff.fbcrm.auth.domain.SecretHasher;
import org.mwolff.fbcrm.auth.infrastructure.Argon2SecretHasher;
import org.mwolff.fbcrm.common.NumericCodes;
import org.mwolff.fbcrm.mail.MailProperties;
import org.mwolff.fbcrm.mail.application.EnqueueMailUseCase;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Die Ausstellung eines Einmalcodes (E2, E8, E10, E11, E12, E26).
 *
 * <p>Der Kern sind die beiden Ausgabewege, die sich ausschliessen: Bei eingeschaltetem Versand
 * entsteht ein Zustellauftrag und keine Protokollzeile, bei ausgeschaltetem eine Protokollzeile und
 * kein Auftrag (Kriterium 7).
 *
 * <p><b>Die Transaktion wird nachgebildet, nicht gemockt.</b> {@link Transaktionen} fuehrt ueber
 * {@link TransactionTemplate} eine echte Spring-Transaktion samt Synchronisation — nur ohne
 * Datenbank: Beim Beginn sichert sie den {@link Bestand}, beim Rollback stellt sie ihn wieder her.
 * Erst so laesst sich ohne Container zeigen, dass die Protokollzeile auf den Commit wartet (E11)
 * und ein gescheiterter Auftrag den bisherigen Code samt Ablaufzeit stehen laesst (E12). Den
 * Schluessel der echten Tabelle prueft {@code RequestLoginCodeUseCaseIT}.
 */
@ExtendWith(MockitoExtension.class)
class RequestLoginCodeUseCaseTest {

  private static final String MAIL = "manne@example.org";
  private static final String NAME = "Manne";
  private static final String INSTANZ = "https://crm.example.org";
  private static final Instant FRUEHER = Instant.parse("2026-10-20T09:55:00Z");
  private static final Instant JETZT = Instant.parse("2026-10-20T10:00:00Z");
  private static final Duration LAUFZEIT = Duration.ofMinutes(10);
  private static final String ALTER_CODE = "111111";
  private static final String CODE = "042424";
  private static final String ZWEITER_CODE = "987654";

  /** Ein Hash ist teuer (Argon2id) — einer fuer alle Tests genuegt. */
  private static final SecretHasher HASHER = new Argon2SecretHasher();

  @Mock private EnqueueMailUseCase postausgang;

  @Captor private ArgumentCaptor<String> rumpf;

  private final Bestand bestand = new Bestand();
  private final TransactionTemplate transaktion =
      new TransactionTemplate(new Transaktionen(bestand));
  private final ListAppender<ILoggingEvent> mitgeschrieben = new ListAppender<>();
  private final Logger protokoll = (Logger) LoggerFactory.getLogger(RequestLoginCodeUseCase.class);

  private @Nullable Level vorherigeStufe;

  /** Eine Zufallsquelle, die der Reihe nach die vorgegebenen Codes zieht. */
  private static NumericCodes codes(final Integer... folge) {
    final Deque<Integer> rest = new ArrayDeque<>(Arrays.asList(folge));
    return new NumericCodes(
        new SecureRandom() {
          @Serial private static final long serialVersionUID = 1L;

          @Override
          public int nextInt(final int schranke) {
            return rest.removeFirst();
          }
        });
  }

  private static AuthProperties schalter() {
    return new AuthProperties(
        "geheimnis-mit-mindestens-32-zeichen-laenge",
        Duration.ofDays(30),
        "fbcrm_session",
        true,
        10,
        Duration.ofMinutes(15),
        5,
        Duration.ofHours(1),
        10,
        Duration.ofHours(24),
        LAUFZEIT,
        5,
        List.of());
  }

  private RequestLoginCodeUseCase anwendungsfall(final boolean versand) {
    return new RequestLoginCodeUseCase(
        bestand,
        HASHER,
        codes(Integer.parseInt(CODE), Integer.parseInt(ZWEITER_CODE)),
        postausgang,
        schalter(),
        new MailProperties(versand, "no-reply@fbcrm.local", INSTANZ),
        Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  /** Stellt in einer Transaktion aus und schliesst sie mit dem Commit ab. */
  private void stelleAus(final boolean versand, final String email, final String empfaenger) {
    final RequestLoginCodeUseCase anwendungsfall = anwendungsfall(versand);
    transaktion.executeWithoutResult(status -> anwendungsfall.ausstellen(email, empfaenger));
  }

  private void stelleAus(final boolean versand) {
    stelleAus(versand, MAIL, NAME);
  }

  /** Der bisherige Code der Adresse, ausgestellt vor fuenf Minuten. */
  private LoginCode bisherigerCode() {
    return bestand.save(LoginCode.ausgestellt(MAIL, HASHER.hash(ALTER_CODE), FRUEHER, LAUFZEIT));
  }

  private String versandterText() {
    verify(postausgang).enqueue(eq(MAIL), anyString(), rumpf.capture());
    return rumpf.getValue();
  }

  private LoginCode gespeicherteZeile() {
    return bestand.findByEmail(MAIL).orElseThrow();
  }

  private List<String> protokollzeilen() {
    return mitgeschrieben.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  @BeforeEach
  void haengeDichAnDasProtokoll() {
    vorherigeStufe = protokoll.getLevel();
    protokoll.setLevel(Level.TRACE);
    mitgeschrieben.start();
    protokoll.addAppender(mitgeschrieben);
  }

  @AfterEach
  void loeseDichWiederAb() {
    protokoll.setLevel(vorherigeStufe);
    protokoll.detachAppender(mitgeschrieben);
    mitgeschrieben.stop();
  }

  @Test
  void ausstellen_withMailEnabled_thenEnqueuesExactlyOneMessageToTheAddress() {
    // When
    stelleAus(true);

    // Then
    verify(postausgang, times(1)).enqueue(eq(MAIL), anyString(), anyString());
  }

  @Test
  void ausstellen_withMailEnabled_thenWritesNoCodeIntoTheLog() {
    // When — Kriterium 7: mit eingeschaltetem Versand steht kein Code im Protokoll.
    stelleAus(true);

    // Then
    assertThat(protokollzeilen()).noneMatch(zeile -> zeile.contains(CODE));
    assertThat(protokollzeilen()).isEmpty();
  }

  @Test
  void ausstellen_withMailDisabled_thenWritesExactlyOneLogLineWithTheCode() {
    // When
    stelleAus(false);

    // Then
    assertThat(protokollzeilen()).singleElement().asString().contains(CODE);
  }

  @Test
  void ausstellen_withMailDisabled_thenTheLogLineNamesWhenTheCodeExpires() {
    // When
    stelleAus(false);

    // Then
    assertThat(protokollzeilen())
        .singleElement()
        .asString()
        .contains(JETZT.plus(LAUFZEIT).toString());
  }

  @Test
  void ausstellen_withMailDisabled_thenEnqueuesNothing() {
    // When — E10: ein unzustellbarer Auftrag liesse den Code im Klartext liegen.
    stelleAus(false);

    // Then
    verifyNoInteractions(postausgang);
  }

  @Test
  void ausstellen_withMailDisabled_thenWritesTheLogLineOnlyAfterTheCommit() {
    // Given
    final RequestLoginCodeUseCase anwendungsfall = anwendungsfall(false);

    // When — E11: innerhalb der Transaktion steht noch nichts im Protokoll.
    transaktion.executeWithoutResult(
        status -> {
          anwendungsfall.ausstellen(MAIL, NAME);
          assertThat(protokollzeilen()).isEmpty();
        });

    // Then — erst nach dem Commit.
    assertThat(protokollzeilen()).hasSize(1);
  }

  @Test
  void ausstellen_withMailDisabled_whenTheTransactionRollsBack_thenLeavesNoLogLine() {
    // Given
    final RequestLoginCodeUseCase anwendungsfall = anwendungsfall(false);

    // When — sonst stuende ein Code im Protokoll, den es nie gab.
    transaktion.executeWithoutResult(
        status -> {
          anwendungsfall.ausstellen(MAIL, NAME);
          status.setRollbackOnly();
        });

    // Then
    assertThat(protokollzeilen()).isEmpty();
  }

  @Test
  void ausstellen_thenStoresOnlyTheHashOfTheCode() {
    // When
    stelleAus(true);

    // Then — E2: die Zeile traegt den Argon2id-Hash, nie den Code.
    assertThat(gespeicherteZeile().codeHash()).doesNotContain(CODE);
    assertThat(HASHER.matches(CODE, gespeicherteZeile().codeHash())).isTrue();
  }

  @Test
  void ausstellen_thenLetsTheCodeExpireAfterTheConfiguredLifetime() {
    // When
    stelleAus(true);

    // Then
    assertThat(gespeicherteZeile().expiresAt()).isEqualTo(JETZT.plus(LAUFZEIT));
  }

  @Test
  void ausstellen_thenTheMailCarriesTheRawCodeAndNeverItsHash() {
    // When
    stelleAus(true);

    // Then
    assertThat(versandterText()).contains(CODE).doesNotContain(gespeicherteZeile().codeHash());
  }

  @Test
  void ausstellen_thenTheMailNamesTheLifetimeInMinutes() {
    // When
    stelleAus(true);

    // Then
    assertThat(versandterText()).contains("10 Minuten");
  }

  @Test
  void ausstellen_thenTheMailAddressesTheRecipientByName() {
    // When
    stelleAus(true);

    // Then
    assertThat(versandterText()).contains("Hallo " + NAME + ",");
  }

  @Test
  void ausstellen_thenTheMailNamesTheInstanceAsPlainTextWithoutALinkThatLogsIn() {
    // When — E26: die Adresse der Instanz, kein Pfad, kein Token.
    stelleAus(true);

    // Then
    assertThat(versandterText())
        .containsPattern("(?m)^" + INSTANZ + "$")
        .doesNotContain(INSTANZ + "/")
        .doesNotContainIgnoringCase("token");
  }

  @Test
  void ausstellen_givenANameWithAPlaceholder_thenInsertsTheNameLiterally() {
    // When — der Anzeigename kommt vom Menschen und wird zuletzt eingesetzt.
    stelleAus(true, MAIL, "{code}");

    // Then
    assertThat(versandterText()).contains("Hallo {code},");
  }

  @Test
  void ausstellen_givenAPreviousCode_thenLeavesExactlyOneRowForTheAddress() {
    // Given
    bisherigerCode();

    // When — Kriterium 4: der neue Code macht den alten ungueltig.
    stelleAus(true);

    // Then
    assertThat(bestand.zeilen()).hasSize(1);
    assertThat(HASHER.matches(CODE, gespeicherteZeile().codeHash())).isTrue();
    assertThat(HASHER.matches(ALTER_CODE, gespeicherteZeile().codeHash())).isFalse();
  }

  @Test
  void ausstellen_givenTwoRequests_thenTheRemainingRowBelongsToTheSecondCode() {
    // Given — ein Anwendungsfall, damit die zweite Anforderung den zweiten Code der Folge zieht.
    final RequestLoginCodeUseCase anwendungsfall = anwendungsfall(true);
    transaktion.executeWithoutResult(status -> anwendungsfall.ausstellen(MAIL, NAME));

    // When
    transaktion.executeWithoutResult(status -> anwendungsfall.ausstellen(MAIL, NAME));

    // Then
    assertThat(bestand.zeilen()).hasSize(1);
    assertThat(HASHER.matches(ZWEITER_CODE, gespeicherteZeile().codeHash())).isTrue();
  }

  @Test
  void ausstellen_whenEnqueueingFails_thenLeavesThePreviousCodeAndItsExpiryUntouched() {
    // Given
    final LoginCode bisheriger = bisherigerCode();
    doThrow(new DataIntegrityViolationException("Postausgangsfach nicht erreichbar"))
        .when(postausgang)
        .enqueue(anyString(), anyString(), anyString());

    // When — E12: Entwerten, Anlegen und Einstellen stehen in einer Transaktion.
    assertThatExceptionOfType(DataIntegrityViolationException.class)
        .isThrownBy(() -> stelleAus(true));

    // Then
    assertThat(gespeicherteZeile()).isEqualTo(bisheriger);
    assertThat(gespeicherteZeile().expiresAt()).isEqualTo(FRUEHER.plus(LAUFZEIT));
  }

  @Test
  void ausstellen_runsInATransaction() throws NoSuchMethodException {
    // Then — ohne die Grenze gaebe es keinen Rollback, auf den E12 baut.
    assertThat(
            RequestLoginCodeUseCase.class
                .getMethod("ausstellen", String.class, String.class)
                .isAnnotationPresent(Transactional.class))
        .isTrue();
  }

  @ParameterizedTest(name = "fuer {0}")
  @ValueSource(strings = {"manne@example.org", "niemand@example.org"})
  void ausstellen_givenAnyAddress_thenIssuesTheSameWay(final String email) {
    // When — E8: der Anwendungsfall kennt kein Konto und fragt keines nach.
    stelleAus(true, email, NAME);

    // Then
    assertThat(bestand.findByEmail(email)).isPresent();
    verify(postausgang).enqueue(eq(email), anyString(), anyString());
  }

  @Test
  void ausstellen_dependsOnNoAccount() {
    // Then — E8: wer kein Konto kennt, kann auch keines nachschlagen.
    assertThat(RequestLoginCodeUseCase.class.getDeclaredFields())
        .noneMatch(feld -> AccountRepository.class.isAssignableFrom(feld.getType()));
  }

  /**
   * Der Bestand der Codes im Speicher, mit dem Schluessel der echten Tabelle.
   *
   * <p>Wie der Index {@code login_code_email_key} weist er einen zweiten Code zu derselben Adresse
   * ab — ohne das Entwerten fiele das Anlegen also auf, statt die alte Zeile still zu ersetzen.
   */
  private static final class Bestand implements LoginCodeRepository {

    private Map<String, LoginCode> zeilen = new HashMap<>();
    private long naechsteId = 1;

    private static String schluessel(final String email) {
      return email.toLowerCase(Locale.ROOT);
    }

    List<LoginCode> zeilen() {
      return List.copyOf(zeilen.values());
    }

    Map<String, LoginCode> sichere() {
      return new HashMap<>(zeilen);
    }

    void stelleWiederHer(final Map<String, LoginCode> sicherung) {
      zeilen = sicherung;
    }

    @Override
    public Optional<LoginCode> findByEmail(final String email) {
      return Optional.ofNullable(zeilen.get(schluessel(email)));
    }

    @Override
    public LoginCode save(final LoginCode code) {
      if (code.id() == null && zeilen.containsKey(schluessel(code.email()))) {
        throw new DataIntegrityViolationException("login_code_email_key");
      }
      final LoginCode gespeichert =
          new LoginCode(
              code.id() == null ? naechsteId++ : code.id(),
              code.email(),
              code.codeHash(),
              code.expiresAt(),
              code.failedAttempts(),
              code.createdAt());
      zeilen.put(schluessel(code.email()), gespeichert);
      return gespeichert;
    }

    @Override
    public void deleteByEmail(final String email) {
      zeilen.remove(schluessel(email));
    }
  }

  /**
   * Eine Spring-Transaktion ueber dem {@link Bestand}: Beginn sichert, Rollback stellt wieder her.
   *
   * <p>Die Synchronisation — und damit der Haken nach dem Commit — fuehrt die Oberklasse.
   */
  private static final class Transaktionen extends AbstractPlatformTransactionManager {

    @Serial private static final long serialVersionUID = 1L;

    private final transient Bestand bestand;
    private transient @Nullable Map<String, LoginCode> sicherung;

    Transaktionen(final Bestand bestand) {
      this.bestand = bestand;
    }

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(final Object transaction, final TransactionDefinition definition) {
      sicherung = bestand.sichere();
    }

    @Override
    protected void doCommit(final DefaultTransactionStatus status) {
      sicherung = null;
    }

    @Override
    protected void doRollback(final DefaultTransactionStatus status) {
      bestand.stelleWiederHer(Objects.requireNonNull(sicherung));
      sicherung = null;
    }
  }
}
