package org.mwolff.fbcrm.startseite.web;

import org.mwolff.fbcrm.startseite.application.Zeitraum;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Wandelt den Adressparameter {@code zeitraum} in einen {@link Zeitraum} (Plan #274, E3).
 *
 * <p>Mit dem Summentyp faellt die Spring-eigene Wandlung weg, nicht die Zusage: Ein Wert, der gar
 * kein Zeitraum ist, bleibt eine fehlerhafte Anfrage. Der Wurf hier kommt als {@code
 * MethodArgumentTypeMismatchException} im vorhandenen {@code TypeMismatchException}-Zweig des
 * {@code GlobalExceptionHandler} an und wird dort zur 400. Die Regel selbst steht allein in {@link
 * Zeitraum#aus(String)} — hier wird sie nicht ein zweites Mal geschrieben.
 *
 * <p>Ob der Zeitraum auch zur Wahl steht, prueft der Converter nicht: Ein nicht waehlbarer wirkt im
 * Anwendungsfall wie ein fehlender (Plan #208, E18).
 */
@Component
class ZeitraumConverter implements Converter<String, Zeitraum> {

  @Override
  public Zeitraum convert(final String roh) {
    return Zeitraum.aus(roh)
        .orElseThrow(() -> new IllegalArgumentException("Kein Zeitraum: JJJJ-MM oder JJJJ"));
  }
}
