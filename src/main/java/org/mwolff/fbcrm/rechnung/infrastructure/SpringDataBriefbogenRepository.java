package org.mwolff.fbcrm.rechnung.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring-Data-Zugriff auf {@code briefbogen}.
 *
 * <p>Ohne eigene Abfrage: Es gibt genau eine Zeile mit fester Kennung, {@code findById} und {@code
 * save} genuegen.
 */
interface SpringDataBriefbogenRepository extends JpaRepository<BriefbogenEntity, Short> {}
