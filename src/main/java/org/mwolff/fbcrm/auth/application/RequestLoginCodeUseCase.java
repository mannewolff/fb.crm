package org.mwolff.fbcrm.auth.application;

import java.time.Clock;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.mwolff.fbcrm.auth.domain.LoginCode;
import org.mwolff.fbcrm.auth.domain.LoginCodeRepository;
import org.mwolff.fbcrm.auth.domain.SecretHasher;
import org.mwolff.fbcrm.common.NachDemCommit;
import org.mwolff.fbcrm.common.NumericCodes;
import org.mwolff.fbcrm.mail.MailProperties;
import org.mwolff.fbcrm.mail.application.EnqueueMailUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stellt einen Einmalcode aus — fuer die Anmeldung wie fuer die Einrichtung (E8).
 *
 * <p><b>Er kennt kein Konto.</b> Bei der Einrichtung gibt es im Augenblick der Anforderung noch
 * keines, darum nimmt er den Anzeigenamen als Parameter. Ob ueberhaupt ausgestellt wird,
 * entscheidet der Aufrufer, nachdem er gezaehlt und seine eigene Vorbedingung geprueft hat.
 *
 * <p><b>Alles in einer Transaktion</b> (E12): den bisherigen Code entwerten, den neuen anlegen und
 * den Zustellauftrag einstellen. Scheitert der Auftrag, rollt die Transaktion zurueck, und der
 * bisherige Code gilt mit seiner Ablaufzeit weiter (Kriterium 4).
 *
 * <p><b>Mail oder Protokoll, nie beides</b> (E10): Bei eingeschaltetem Versand geht der Code nur in
 * den Zustellauftrag und nie ins Protokoll (Kriterium 7). Bei ausgeschaltetem entsteht kein Auftrag
 * — er liesse den Code im Klartext in einer Tabelle liegen, die niemand leert —, und der Code steht
 * im Protokoll, aber erst nach dem Commit (E11): Ein Rollback liesse sonst einen Code dort stehen,
 * den es nie gab.
 *
 * <p>Gespeichert wird nur der Argon2id-Hash des Codes (E2).
 */
@Service
public class RequestLoginCodeUseCase {

  /** Der Betreff jeder Mail mit einem Code. Der Code selbst steht nicht darin. */
  static final String BETREFF = "fb.crm: dein Code fuer die Anmeldung";

  /**
   * Der Text der Mail.
   *
   * <p>Die Adresse der Instanz steht als Klartext auf einer eigenen Zeile und fuehrt zu keiner
   * Seite, die anmeldet (E26). Die Platzhalter werden ersetzt, nicht formatiert: Ein Format-String
   * mit {@code %s} zwaenge zu {@code %n} als Zeilenende (SpotBugs), und das waere auf einem
   * Windows-Server ein {@code \r\n} im Rumpf einer Mail, die ihre Zeilenenden selbst mitbringt.
   */
  private static final String VORLAGE =
      """
      Hallo {name},

      dein Code fuer die Anmeldung bei fb.crm lautet:

      {code}

      Er gilt einmal und laeuft nach {minuten} Minuten ab. Er gehoert zu der Instanz unter

      {instanz}

      Kam die Anforderung nicht von dir, ist nichts geschehen — dann kannst du diese Nachricht
      ignorieren.
      """;

  private static final Logger LOG = LoggerFactory.getLogger(RequestLoginCodeUseCase.class);

  private final LoginCodeRepository loginCodes;
  private final SecretHasher hasher;
  private final NumericCodes codes;
  private final EnqueueMailUseCase postausgang;
  private final AuthProperties auth;
  private final MailProperties mail;
  private final Clock clock;

  public RequestLoginCodeUseCase(
      final LoginCodeRepository loginCodes,
      final SecretHasher hasher,
      final NumericCodes codes,
      final EnqueueMailUseCase postausgang,
      final AuthProperties auth,
      final MailProperties mail,
      final Clock clock) {
    this.loginCodes = loginCodes;
    this.hasher = hasher;
    this.codes = codes;
    this.postausgang = postausgang;
    this.auth = auth;
    this.mail = mail;
    this.clock = clock;
  }

  /**
   * Stellt einen neuen Code zu der Adresse aus — fuer jede Adresse gleich.
   *
   * @param email Adresse, an die der Code geht
   * @param empfaenger Anzeigename fuer die Anrede in der Mail
   */
  @Transactional
  public void ausstellen(final String email, final String empfaenger) {
    final String code = codes.newCode();
    loginCodes.deleteByEmail(email);
    final LoginCode zeile =
        loginCodes.save(
            LoginCode.ausgestellt(email, hasher.hash(code), clock.instant(), auth.loginCodeTtl()));
    final LoginCodeAusgabe ausgabe = new LoginCodeAusgabe(code, zeile.expiresAt());
    if (mail.enabled()) {
      postausgang.enqueue(email, BETREFF, text(ausgabe, empfaenger));
    } else {
      NachDemCommit.fuehreAus(() -> protokolliere(ausgabe));
    }
  }

  /*
   * Die Zeile nennt die Adresse nicht (CLAUDE-security.md: keine Klartext-PII im Protokoll). Wer
   * den Code aus dem Protokoll holt, hat ihn eben selbst angefordert.
   */
  private static void protokolliere(final LoginCodeAusgabe ausgabe) {
    LOG.info(
        "Mailversand ausgeschaltet — Einmalcode {} gilt bis {}.",
        ausgabe.code(),
        ausgabe.expiresAt());
  }

  private String text(final LoginCodeAusgabe ausgabe, final String empfaenger) {
    // Der Anzeigename kommt vom Menschen und wird zuletzt eingesetzt: Enthielte er selbst einen
    // Platzhalter, ersetzte ihn danach niemand mehr.
    return VORLAGE
        .replace("{code}", ausgabe.code())
        .replace("{minuten}", String.valueOf(auth.loginCodeTtl().toMinutes()))
        .replace("{instanz}", mail.baseUrl())
        .replace("{name}", empfaenger);
  }
}
