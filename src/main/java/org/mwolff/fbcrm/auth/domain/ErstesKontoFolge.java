package org.mwolff.fbcrm.auth.domain;

/**
 * Was mit dem ersten Konto der Instanz folgt (Plan #414, E13).
 *
 * <p>Die Einrichtung ruft den Port in der Transaktion, in der das erste Konto entsteht. Umgesetzt
 * wird er in einem Fachmodul, das Daten ohne Konto vorfindet und sie diesem Konto gibt — heute die
 * Kommentare am Angebot aus einem Bestand ohne Konto. Der Port steht hier und nicht dort, damit die
 * Richtung der Abhaengigkeit bleibt, wie A7 sie festlegt: das Fachmodul kennt {@code auth.domain},
 * {@code auth} kennt kein Fachmodul.
 *
 * <p>Wer ihn umsetzt, schreibt in die Transaktion des Aufrufers: Scheitert die Folge, entsteht auch
 * das Konto nicht.
 */
/*
 * Genau eine Methode, aber bewusst kein @FunctionalInterface: Der Port beschreibt, was ein Modul
 * mit dem ersten Konto tut, und wird nie als Lambda geschrieben — dieselbe Ueberlegung wie bei
 * Rechnungsbindung.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface ErstesKontoFolge {

  /**
   * Das erste Konto der Instanz ist eben angelegt.
   *
   * @param kontoId die Kennung des neuen Kontos
   */
  void erstesKontoAngelegt(long kontoId);
}
