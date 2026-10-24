package org.mwolff.fbcrm.auth.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.Role;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Verwaltung der Konten durch einen C-Level: sehen, anlegen, aendern, stilllegen, wieder
 * aktivieren (Plan #414, A6, A12, A13, E5, E6; fachliche Quelle #409, Kriterien 3 bis 8).
 *
 * <p>Wer das darf, entscheidet die Zugangsregel ({@code SecurityConfig}, {@code
 * /api/mitarbeitende/**} nur C-Level); dieser Anwendungsfall prueft die Rolle des Aufrufers nicht
 * ein zweites Mal.
 *
 * <p><b>Die Adresse ist eindeutig</b> ohne Ruecksicht auf die Schreibweise und gegen jedes Konto,
 * auch ein stillgelegtes (E6): {@link AccountRepository#findByEmail} liefert beide. Zwei
 * gleichzeitige Aufrufe mit derselben Adresse kommen an der Pruefung beide vorbei; den zweiten
 * haelt der Index {@code account_email_key}, und seine Verletzung wird in dieselbe {@link
 * AdresseBereitsVergeben} uebersetzt. Dass sie hier ankommt und nicht erst beim Commit hinter dem
 * Anwendungsfall, sagt {@link AccountRepository#save} zu.
 *
 * <p><b>Der letzte aktive C-Level bleibt</b> (A6). Vor jedem Stilllegen und jeder Aenderung sperrt
 * {@link AccountRepository#aktiveCLevelMitSperre()} die aktiven C-Level-Konten bis zum Ende der
 * Transaktion, und erst danach wird das betroffene Konto gelesen und gezaehlt. Zwei gleichzeitige
 * Herabstufungen der beiden letzten C-Level warten so aufeinander, und die zweite sieht nur noch
 * ein aktives C-Level-Konto — ihr eigenes. Gesperrt wird auch bei einer Aenderung, die die Rolle
 * nicht beruehrt: Ob sie es tut, steht erst nach dem Lesen fest, und gelesen wird nach der Sperre.
 *
 * <p><b>Zuerst der Stand</b> (#411, Kriterium 4; Plan #415, E19): Aendern, Stilllegen und
 * Wiederaktivieren lesen das Konto mit {@link AccountRepository#findByIdMitSperre} und vergleichen
 * die vorgelegte Version, bevor sie etwas pruefen oder schreiben. Wer auf einem alten Stand
 * handelt, bekommt {@link KontoGeaendert}. Die Sperre haelt das Konto bis zum Ende der Transaktion
 * fest: Zwei gleichzeitige Schritte auf demselben Stand warten aufeinander, und der zweite sieht
 * die Version des ersten. Die Version zaehlt das Konto selbst ({@link Account}); An- und Abmelden
 * lassen sie stehen.
 *
 * <p><b>Was eine Sitzung beendet</b> (E5): eine neue Adresse und das Stilllegen — beide zaehlen die
 * Sitzungs-Generation hoch ({@link Account#mitAdresse}, {@link Account#stillgelegt}). Name und
 * Rolle tun es nicht; der Filter liest sie bei jeder Anfrage frisch, sie wirken also beim naechsten
 * Schritt.
 *
 * <p><b>Die Begruessung</b> (A13) entsteht in derselben Transaktion wie das Konto: Scheitert die
 * Anlage, geht auch keine Mail hinaus.
 */
@Service
@Transactional
public class MitarbeitendeVerwaltung {

  private final AccountRepository accounts;
  private final BegruessungsmailUseCase begruessung;
  private final Clock clock;

  public MitarbeitendeVerwaltung(
      final AccountRepository accounts,
      final BegruessungsmailUseCase begruessung,
      final Clock clock) {
    this.accounts = accounts;
    this.begruessung = begruessung;
    this.clock = clock;
  }

  /**
   * Das Ergebnis des Anlegens.
   *
   * @param konto das angelegte Konto mit seiner Kennung
   * @param begruessungMailVersandt ob ein Zustellauftrag fuer die Begruessung entstanden ist
   */
  public record Angelegt(Account konto, boolean begruessungMailVersandt) {}

  /** Jedes Konto, aktiv oder stillgelegt, nach Anzeigenamen sortiert (Kriterium 3). */
  @Transactional(readOnly = true)
  public List<Account> liste() {
    return accounts.findAllOrderedByDisplayName();
  }

  /**
   * Legt ein aktives Konto an und stellt die Begruessung ein (Kriterium 4).
   *
   * @param email Anmeldeadresse; fuehrender und folgender Leerraum faellt weg
   * @param name Anzeigename; fuehrender und folgender Leerraum faellt weg
   * @param rolle Rolle des neuen Kontos
   * @throws AdresseBereitsVergeben wenn ein anderes Konto die Adresse traegt
   */
  public Angelegt anlegen(final String email, final String name, final Role rolle) {
    final String adresse = email.strip();
    pruefeAdresseFrei(adresse, null);
    final Instant jetzt = clock.instant();
    final Account angelegt =
        speichere(new Account(null, adresse, name.strip(), rolle, true, 0, jetzt, jetzt, 0L, null));
    return new Angelegt(angelegt, begruessung.sende(angelegt.email(), angelegt.displayName()));
  }

  /**
   * Schreibt Adresse, Name und Rolle fort (Kriterium 6).
   *
   * @param id technische Id des Kontos
   * @param email die kuenftige Anmeldeadresse; eine andere als bisher beendet die Sitzung
   * @param name der kuenftige Anzeigename
   * @param rolle die kuenftige Rolle
   * @param version der vorgelegte Stand aus {@code If-Match}
   * @param handelnder Kennung des handelnden Kontos
   * @return das geaenderte Konto
   * @throws KontoNichtGefunden wenn es das Konto nicht gibt
   * @throws KontoGeaendert wenn das Konto inzwischen einen anderen Stand hat als den vorgelegten
   * @throws AdresseBereitsVergeben wenn ein anderes Konto die Adresse traegt
   * @throws LetzterCLevel wenn das Konto der letzte aktive C-Level ist und eine andere Rolle
   *     bekommen soll
   */
  public Account aendern(
      final long id,
      final String email,
      final String name,
      final Role rolle,
      final long version,
      final long handelnder) {
    final List<Account> aktiveCLevel = accounts.aktiveCLevelMitSperre();
    final Account konto = vorhandenes(id, version, handelnder);
    final String adresse = email.strip();
    pruefeAdresseFrei(adresse, id);
    if (rolle != Role.C_LEVEL) {
      schuetzeLetztenCLevel(aktiveCLevel, id);
    }
    final Instant jetzt = clock.instant();
    final Account umbenannt =
        konto.mitNamen(name.strip(), jetzt, handelnder).mitRolle(rolle, jetzt, handelnder);
    return speichere(
        konto.email().equals(adresse)
            ? umbenannt
            : umbenannt.mitAdresse(adresse, jetzt, handelnder));
  }

  /**
   * Legt das Konto still; die Person ist sofort abgemeldet (Kriterium 7).
   *
   * @param id technische Id des Kontos
   * @param version der vorgelegte Stand aus {@code If-Match}
   * @param handelnder Kennung des handelnden Kontos
   * @return das stillgelegte Konto
   * @throws KontoNichtGefunden wenn es das Konto nicht gibt
   * @throws KontoGeaendert wenn das Konto inzwischen einen anderen Stand hat als den vorgelegten
   * @throws LetzterCLevel wenn das Konto der letzte aktive C-Level ist
   */
  public Account stilllegen(final long id, final long version, final long handelnder) {
    final List<Account> aktiveCLevel = accounts.aktiveCLevelMitSperre();
    final Account konto = vorhandenes(id, version, handelnder);
    schuetzeLetztenCLevel(aktiveCLevel, id);
    return accounts.save(konto.stillgelegt(clock.instant(), handelnder));
  }

  /**
   * Nimmt ein stillgelegtes Konto wieder in Betrieb (Kriterium 7).
   *
   * <p>Ohne Sperre der C-Level-Konten: Ein weiteres aktives Konto nimmt keinem C-Level etwas weg.
   * Gesperrt wird nur das Konto selbst, gegen dessen Stand verglichen wird.
   *
   * @param id technische Id des Kontos
   * @param version der vorgelegte Stand aus {@code If-Match}
   * @param handelnder Kennung des handelnden Kontos
   * @return das wieder aktive Konto
   * @throws KontoNichtGefunden wenn es das Konto nicht gibt
   * @throws KontoGeaendert wenn das Konto inzwischen einen anderen Stand hat als den vorgelegten
   */
  public Account aktivieren(final long id, final long version, final long handelnder) {
    return accounts.save(
        vorhandenes(id, version, handelnder).aktiviert(clock.instant(), handelnder));
  }

  /**
   * Liest das Konto unter Zeilensperre und weist einen veralteten Stand ab.
   *
   * @param id technische Id des Kontos
   * @param version der vorgelegte Stand aus {@code If-Match}
   * @param handelnder Kennung des handelnden Kontos
   */
  private Account vorhandenes(final long id, final long version, final long handelnder) {
    final Account konto = accounts.findByIdMitSperre(id).orElseThrow(KontoNichtGefunden::new);
    KontoGeaendert.pruefe(konto, version, handelnder);
    return konto;
  }

  /**
   * Weist eine Adresse ab, die ein anderes Konto traegt.
   *
   * @param adresse die gewuenschte Adresse
   * @param eigeneId das Konto, das sie behalten darf, oder {@code null} beim Anlegen
   */
  private void pruefeAdresseFrei(final String adresse, final @Nullable Long eigeneId) {
    if (accounts
        .findByEmail(adresse)
        .filter(anderes -> !Objects.equals(anderes.id(), eigeneId))
        .isPresent()) {
      throw new AdresseBereitsVergeben();
    }
  }

  /**
   * Weist ab, wenn das Konto der letzte aktive C-Level ist.
   *
   * @param aktiveCLevel die gesperrten aktiven C-Level-Konten
   * @param id das Konto, das seine Rolle oder seinen Zustand verlieren soll
   */
  private static void schuetzeLetztenCLevel(final List<Account> aktiveCLevel, final long id) {
    final boolean betroffen = aktiveCLevel.stream().anyMatch(k -> k.requireId() == id);
    if (betroffen && aktiveCLevel.size() == 1) {
      throw new LetzterCLevel();
    }
  }

  private Account speichere(final Account konto) {
    try {
      return accounts.save(konto);
    } catch (final DataIntegrityViolationException indexVerletzt) {
      throw new AdresseBereitsVergeben(indexVerletzt);
    }
  }
}
