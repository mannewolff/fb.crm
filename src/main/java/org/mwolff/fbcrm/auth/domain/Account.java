package org.mwolff.fbcrm.auth.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Identifiable;

/**
 * Ein Konto der Instanz.
 *
 * <p>Die <b>Sitzungs-Generation</b> ist der Hebel, mit dem bestehende Sitzungen enden: Jedes
 * Session-Token traegt die Generation seiner Ausstellung, und ein Token mit alter Generation gilt
 * nicht mehr — auf jedem Geraet, auch dem ausloesenden (CLAUDE-security.md). Hochgezaehlt wird sie
 * mit {@link #mitNaechsterGeneration} beim Abmelden (E7), mit {@link #mitAdresse} und mit {@link
 * #stillgelegt}: Nach einer neuen Adresse meldet sich die Person neu an, und ein stillgelegtes
 * Konto ist sofort abgemeldet (fachliche Quelle #409, Kriterien 6 und 7). Rolle und Name aendern
 * die Generation nicht — sie werden je Anfrage gelesen und wirken ohne neue Anmeldung.
 *
 * <p><b>Stillgelegt statt geloescht.</b> Wer ausscheidet, dessen Konto bleibt mit {@code aktiv =
 * false} stehen; seine Kommentare behalten ihren Urheber, und {@link #aktiviert} holt es zurueck.
 *
 * <p><b>Die Version zaehlt die Domaene selbst</b> (Plan #415, E19; #411, Kriterium 4). Die Zeile
 * {@code account} schreiben auch An- und Abmelden fort, ein JPA-{@code @Version} liesse darum jede
 * Kontenmaske nach einer blossen Anmeldung veralten. Deshalb erhoehen allein die Uebergaenge der
 * Kontenverwaltung — {@link #mitRolle}, {@link #mitAdresse}, {@link #mitNamen}, {@link
 * #stillgelegt} und {@link #aktiviert} — die Version und setzen den Urheber; {@link
 * #mitNaechsterGeneration} laesst beide stehen.
 *
 * <p>Unveraenderlich: Jede Fortschreibung liefert ein neues Konto, statt dieses zu aendern. Der
 * Zeitpunkt der Aenderung kommt von aussen, weil die Domaene keine Uhr kennt (CLAUDE-java.md §6.2).
 *
 * @param id technische Id — {@code null}, solange das Konto nicht gespeichert ist
 * @param email Anmeldeadresse; eindeutig ohne Ruecksicht auf Gross- und Kleinschreibung
 * @param displayName Anzeigename in der Oberflaeche
 * @param role Rolle des Kontos
 * @param aktiv ob sich das Konto anmelden darf; {@code false} heisst stillgelegt
 * @param sessionGeneration Generation, gegen die ein Session-Token geprueft wird
 * @param createdAt Zeitpunkt der Anlage
 * @param updatedAt Zeitpunkt der letzten Aenderung
 * @param version der Stand des Kontos, wie ihn {@code If-Match} vorlegt; 0 bei der Anlage
 * @param geaendertVon Kennung des Kontos, das dieses Konto zuletzt verwaltet hat, oder {@code
 *     null}, solange es das nicht weiss
 */
public record Account(
    @Nullable Long id,
    String email,
    String displayName,
    Role role,
    boolean aktiv,
    int sessionGeneration,
    Instant createdAt,
    Instant updatedAt,
    long version,
    @Nullable Long geaendertVon)
    implements Identifiable {

  /**
   * Dasselbe Konto mit um eins erhoehter Sitzungs-Generation; jedes vorher ausgestellte
   * Session-Token gilt danach nicht mehr (E7).
   *
   * <p>Version und Urheber bleiben: Abmelden ist keine Aenderung, die eine offene Kontenmaske
   * veralten laesst (Plan #415, E19).
   *
   * @param geaendertAm Zeitpunkt der Aenderung
   */
  public Account mitNaechsterGeneration(final Instant geaendertAm) {
    return new Account(
        id,
        email,
        displayName,
        role,
        aktiv,
        sessionGeneration + 1,
        createdAt,
        geaendertAm,
        version,
        geaendertVon);
  }

  /**
   * Dasselbe Konto mit einer anderen Rolle; die Sitzungs-Generation bleibt.
   *
   * @param neueRolle die Rolle, die das Konto kuenftig traegt
   * @param geaendertAm Zeitpunkt der Aenderung
   * @param konto Kennung des handelnden Kontos
   */
  public Account mitRolle(final Role neueRolle, final Instant geaendertAm, final long konto) {
    return new Account(
        id,
        email,
        displayName,
        neueRolle,
        aktiv,
        sessionGeneration,
        createdAt,
        geaendertAm,
        version + 1,
        konto);
  }

  /**
   * Dasselbe Konto unter einer anderen Anmeldeadresse; jede bestehende Sitzung endet.
   *
   * @param neueAdresse die Anmeldeadresse, unter der sich die Person kuenftig anmeldet
   * @param geaendertAm Zeitpunkt der Aenderung
   * @param konto Kennung des handelnden Kontos
   */
  public Account mitAdresse(final String neueAdresse, final Instant geaendertAm, final long konto) {
    return new Account(
        id,
        neueAdresse,
        displayName,
        role,
        aktiv,
        sessionGeneration + 1,
        createdAt,
        geaendertAm,
        version + 1,
        konto);
  }

  /**
   * Dasselbe Konto mit einem anderen Anzeigenamen; die Sitzungs-Generation bleibt.
   *
   * @param neuerName der Anzeigename, den Oberflaeche und Kommentare kuenftig zeigen
   * @param geaendertAm Zeitpunkt der Aenderung
   * @param konto Kennung des handelnden Kontos
   */
  public Account mitNamen(final String neuerName, final Instant geaendertAm, final long konto) {
    return new Account(
        id,
        email,
        neuerName,
        role,
        aktiv,
        sessionGeneration,
        createdAt,
        geaendertAm,
        version + 1,
        konto);
  }

  /**
   * Dasselbe Konto, stillgelegt; jede bestehende Sitzung endet.
   *
   * @param geaendertAm Zeitpunkt der Aenderung
   * @param konto Kennung des handelnden Kontos
   */
  public Account stillgelegt(final Instant geaendertAm, final long konto) {
    return new Account(
        id,
        email,
        displayName,
        role,
        false,
        sessionGeneration + 1,
        createdAt,
        geaendertAm,
        version + 1,
        konto);
  }

  /**
   * Dasselbe Konto, wieder aktiv; die Sitzungs-Generation bleibt, denn das Stilllegen hat die alten
   * Sitzungen schon beendet.
   *
   * @param geaendertAm Zeitpunkt der Aenderung
   * @param konto Kennung des handelnden Kontos
   */
  public Account aktiviert(final Instant geaendertAm, final long konto) {
    return new Account(
        id,
        email,
        displayName,
        role,
        true,
        sessionGeneration,
        createdAt,
        geaendertAm,
        version + 1,
        konto);
  }

  /**
   * Ob ein Session-Token mit dieser Generation noch gilt.
   *
   * @param generation die Generation aus dem vorgelegten Token
   */
  public boolean matchesGeneration(final long generation) {
    return sessionGeneration == generation;
  }
}
