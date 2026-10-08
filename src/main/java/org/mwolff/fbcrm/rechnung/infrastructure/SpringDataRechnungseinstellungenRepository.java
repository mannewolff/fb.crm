package org.mwolff.fbcrm.rechnung.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring-Data-Zugriff auf {@code rechnung_einstellungen}.
 *
 * <p>Ohne eigene Abfrage: Es gibt genau eine Zeile mit fester Kennung, {@code findById} und {@code
 * save} genuegen.
 */
interface SpringDataRechnungseinstellungenRepository
    extends JpaRepository<RechnungseinstellungenEntity, Short> {}
