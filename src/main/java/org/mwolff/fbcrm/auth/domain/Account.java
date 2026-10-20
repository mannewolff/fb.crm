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
 * mit {@link #mitNaechsterGeneration} beim Abmelden (E7).
 *
 * <p>Unveraenderlich: {@link #mitNaechsterGeneration} liefert ein neues Konto, statt dieses zu
 * aendern. Der Zeitpunkt der Aenderung kommt von aussen, weil die Domaene keine Uhr kennt
 * (CLAUDE-java.md §6.2).
 *
 * @param id technische Id — {@code null}, solange das Konto nicht gespeichert ist
 * @param email Anmeldeadresse; eindeutig ohne Ruecksicht auf Gross- und Kleinschreibung
 * @param displayName Anzeigename in der Oberflaeche
 * @param passwordHash Argon2id-Hash; nie das Passwort selbst
 * @param role Rolle des Kontos
 * @param sessionGeneration Generation, gegen die ein Session-Token geprueft wird
 * @param createdAt Zeitpunkt der Anlage
 * @param updatedAt Zeitpunkt der letzten Aenderung
 */
public record Account(
    @Nullable Long id,
    String email,
    String displayName,
    String passwordHash,
    Role role,
    int sessionGeneration,
    Instant createdAt,
    Instant updatedAt)
    implements Identifiable {

  /**
   * Dasselbe Konto mit um eins erhoehter Sitzungs-Generation; jedes vorher ausgestellte
   * Session-Token gilt danach nicht mehr (E7).
   *
   * @param geaendertAm Zeitpunkt der Aenderung
   */
  public Account mitNaechsterGeneration(final Instant geaendertAm) {
    return new Account(
        id, email, displayName, passwordHash, role, sessionGeneration + 1, createdAt, geaendertAm);
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
