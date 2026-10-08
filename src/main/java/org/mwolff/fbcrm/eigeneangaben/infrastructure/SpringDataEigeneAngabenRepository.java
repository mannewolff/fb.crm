package org.mwolff.fbcrm.eigeneangaben.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring-Data-Zugriff auf {@code eigene_angaben}.
 *
 * <p>Ohne eigene Abfrage: Es gibt genau eine Zeile mit fester Kennung, {@code findById} und {@code
 * save} genuegen.
 */
interface SpringDataEigeneAngabenRepository extends JpaRepository<EigeneAngabenEntity, Short> {}
