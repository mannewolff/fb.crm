package org.mwolff.fbcrm.rechnung.application;

import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.springframework.stereotype.Service;

/**
 * Die eine Frage, ob eine Rechnungsnummer vergeben ist, ueber beide Bestaende (#254, Kriterien 4
 * und 5; Plan #259, E3).
 *
 * <p>Eine Rechnungsnummer kommt nur einmal vor, gleich ob fb.crm die Rechnung geschrieben hat oder
 * sie nachgetragen wurde. Die Datenbank sichert das je Tabelle, ueber beide Tabellen kann sie es
 * nicht — darum steht die Frage an genau dieser einen Stelle, und Stellen, Einstellungen,
 * Nachtragen und Aendern fragen hier.
 *
 * <p>Die Schreibweise uebergehen die Repositories selbst; der Dienst reicht die Nummer unveraendert
 * weiter und setzt nichts zusammen.
 */
@Service
public class Rechnungsnummern {

  private final RechnungRepository rechnungen;
  private final NachgetrageneRechnungRepository nachgetragene;

  Rechnungsnummern(
      final RechnungRepository rechnungen, final NachgetrageneRechnungRepository nachgetragene) {
    this.rechnungen = rechnungen;
    this.nachgetragene = nachgetragene;
  }

  /**
   * Ob eine Rechnung — geschrieben oder nachgetragen — diese Nummer schon traegt.
   *
   * @param nummer die zu pruefende Rechnungsnummer
   */
  public boolean vergeben(final String nummer) {
    return rechnungen.existiertNummer(nummer) || nachgetragene.existiertNummer(nummer);
  }

  /**
   * Dasselbe fuer das Aendern einer nachgetragenen Rechnung: Ihre eigene Zeile zaehlt nicht mit.
   *
   * @param nummer die zu pruefende Rechnungsnummer
   * @param id Kennung der nachgetragenen Rechnung, die geaendert wird
   */
  public boolean vergebenVonAnderer(final String nummer, final long id) {
    return rechnungen.existiertNummer(nummer) || nachgetragene.existiertNummerAusser(nummer, id);
  }
}
