package org.mwolff.fbcrm.auth.domain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Port auf den Bestand der Konten; die Umsetzung liegt in {@code auth.infrastructure}. */
public interface AccountRepository {

  /** Das Konto zu einer technischen Id, oder leer. */
  Optional<Account> findById(long id);

  /** Das Konto zu einer Anmeldeadresse ohne Ruecksicht auf Gross- und Kleinschreibung. */
  Optional<Account> findByEmail(String email);

  /**
   * Ob es bereits irgendein Konto gibt — gleich welcher Rolle und welchen Zustands.
   *
   * <p>Der Riegel der Einrichtung: Mit dem ersten Konto ist die Instanz eingerichtet (Plan #414,
   * A5).
   */
  boolean existsAnyAccount();

  /** Jedes Konto, aktiv oder stillgelegt, nach Anzeigenamen sortiert. */
  List<Account> findAllOrderedByDisplayName();

  /**
   * Die Anzeigenamen zu den genannten Konten.
   *
   * <p>Eine Id ohne Konto fehlt in der Antwort, statt einen Platzhalter zu tragen.
   *
   * @param ids die Kennungen der Konten
   */
  Map<Long, String> displayNamesByIds(Collection<Long> ids);

  /**
   * Die aktiven C-Level-Konten, deren Zeilen bis zum Ende der Transaktion des Aufrufers gesperrt
   * sind (Plan #414, A6).
   *
   * <p>Gezaehlt wird im Anwendungsfall, nicht hier: Postgres weist eine Zaehlung mit Sperrklausel
   * ab. Zwei gleichzeitige Herabstufungen oder Stilllegungen warten so aufeinander, und die zweite
   * sieht, was die erste hinterliess — sonst kaemen beide durch eine ungesperrte Zaehlung, und es
   * bliebe kein aktiver C-Level. Ohne Transaktion des Aufrufers wird der Aufruf abgewiesen.
   */
  List<Account> aktiveCLevelMitSperre();

  /** Legt das Konto an oder schreibt es fort und liefert es mit gesetzter Id zurueck. */
  Account save(Account account);
}
