package org.mwolff.fbcrm.auth.domain;

/**
 * Die Rolle eines Kontos (Plan #414, A3; fachliche Quelle #409).
 *
 * <p>Die Rolle bestimmt, was jemand darf. Durchgesetzt wird sie von der Zugangsregel der API, die
 * Oberflaeche fuehrt nur (CLAUDE.md, „Identity / Auth"). Es bleibt immer mindestens ein aktives
 * Konto mit {@link #C_LEVEL}.
 */
public enum Role {

  /**
   * Die Geschaeftsleitung: darf alles — das Fachliche, die Einstellungen (Eigene Angaben,
   * Einstellungen zur Rechnung) und die Verwaltung der Konten. Das erste Konto der Einrichtung
   * traegt diese Rolle.
   */
  C_LEVEL,

  /**
   * Mitarbeitende: duerfen alles Fachliche — Kunden, Angebote und Rechnungen anlegen, aendern und
   * stellen —, aber weder Konten verwalten noch die Einstellungen aendern.
   */
  EMPLOYEE,

  /** Nur lesen: sieht das Fachliche und die Einstellungen, aendert nichts. */
  READ_ONLY
}
