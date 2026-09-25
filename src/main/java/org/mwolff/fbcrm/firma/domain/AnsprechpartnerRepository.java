package org.mwolff.fbcrm.firma.domain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Port auf den Bestand der Ansprechpartner; die Umsetzung liegt in {@code firma.infrastructure}.
 */
public interface AnsprechpartnerRepository {

  /** Der Ansprechpartner zu einer technischen Id, oder leer. */
  Optional<Ansprechpartner> findById(long id);

  /**
   * Legt den Ansprechpartner an oder schreibt ihn fort und liefert ihn mit gesetzter Id zurueck.
   */
  Ansprechpartner save(Ansprechpartner ansprechpartner);

  /**
   * Alle Ansprechpartner einer Firma — aktive und stillgelegte —, sortiert nach Nachnamen ohne
   * Ruecksicht auf Gross- und Kleinschreibung und bei gleichem Nachnamen nach Id.
   *
   * @param firmaId Kennung der Firma
   */
  List<Ansprechpartner> findByFirma(long firmaId);

  /**
   * Die Zahl der <b>aktiven</b> Ansprechpartner je Firma.
   *
   * <p>Die Abbildung traegt zu <b>jeder</b> angefragten Kennung einen Eintrag: Eine Firma ohne
   * aktiven Ansprechpartner steht dort mit {@code 0}, nicht gar nicht. Der Aufrufer braucht dafuer
   * keinen Ersatzwert zu kennen.
   *
   * @param firmaIds Kennungen der Firmen, nach denen gefragt wird
   */
  Map<Long, Long> zaehleAktiveJeFirma(Collection<Long> firmaIds);
}
