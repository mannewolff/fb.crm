package org.mwolff.fbcrm.angebot.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring-Data-Zugriff auf {@code angebot}.
 *
 * <p>Ohne eigene Abfrage: Gelesen wird ein Angebot ueber seine Kennung, geschrieben als Ganzes.
 * Listen und Auswertungen kommen mit den Paketen, die sie brauchen.
 */
interface SpringDataAngebotRepository extends JpaRepository<AngebotEntity, Long> {}
