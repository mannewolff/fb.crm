package org.mwolff.fbcrm.common;

import org.jspecify.annotations.Nullable;

/**
 * Etwas, das nach dem Speichern eine technische Id traegt.
 *
 * <p>Vor der Persistierung ist die Id {@code null}. {@link #requireId()} buendelt den Uebergang an
 * einer Stelle, statt die Pruefung ueber die Aufrufer zu verteilen (CLAUDE-java.md §6.2).
 *
 * <p>Genau eine abstrakte Methode, aber bewusst kein funktionales Interface: Identifiable
 * beschreibt eine Rolle, die Entitaeten tragen, und wird nie als Lambda geschrieben.
 * {@code @FunctionalInterface} behauptete eine Absicht, die nicht besteht — daher die
 * Unterdrueckung der PMD-Regel statt der Annotation.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface Identifiable {

  /** Die technische Id — {@code null}, solange die Instanz nicht gespeichert ist. */
  @Nullable Long id();

  /**
   * Die Id einer gespeicherten Instanz.
   *
   * @throws IllegalStateException wenn die Instanz noch keine Id hat
   */
  default long requireId() {
    final Long vorhandene = id();
    if (vorhandene == null) {
      throw new IllegalStateException(
          getClass().getSimpleName() + " ist noch nicht gespeichert und hat keine Id.");
    }
    return vorhandene;
  }
}
