package org.mwolff.fbcrm.auth.domain;

/**
 * Die Rolle eines Kontos.
 *
 * <p>Vorerst gibt es genau einen Wert: {@code ADMIN}. Weitere Rollen entstehen mit dem ersten
 * Fachplan, der Rechte beruehrt (CLAUDE.md); die Spalte {@code account.role} traegt sie schon heute
 * und muss dafuer nicht wandern.
 */
public enum Role {
  ADMIN
}
