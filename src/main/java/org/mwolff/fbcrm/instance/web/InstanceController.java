package org.mwolff.fbcrm.instance.web;

import java.util.Objects;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Der Versionsstand der laufenden Instanz (K11, E10).
 *
 * <p>Die Zahl kommt aus {@link BuildProperties} und damit aus {@code build-info.properties}, das
 * der Maven-Lauf schreibt — sie ist die Version des Artefakts, das gerade laeuft. Bewusst
 * <b>kein</b> zur Bauzeit ins Frontend geschriebener Wert: Nach einem Hot-Deploy stuende dort die
 * Zahl des Frontend-Builds, nicht die der Anwendung dahinter.
 *
 * <p>Der Pfad steht nicht in der offenen Liste der {@code SecurityConfig} und faellt damit unter
 * {@code /api/**} — ohne Sitzung 401. Das haelt den Versionsstand von der oeffentlichen Seite fern
 * und ist der Grund, warum die Auth-Platte vor der Anmeldung keine Version traegt.
 */
@RestController
@RequestMapping("/api/instance")
public class InstanceController {

  /**
   * Was der Endpunkt sagt, wenn {@code build-info.properties} keine Version fuehrt.
   *
   * <p>{@link BuildProperties#getVersion()} darf null liefern; der Maven-Lauf schreibt die Version
   * immer mit. Bliebe die Stelle ungedeckt, faellt ein kaputter Bau erst als NullPointerException
   * beim Anwender auf — so faellt er als Wort in der Schiene auf.
   */
  static final String OHNE_VERSION = "unbekannt";

  private final BuildProperties bau;

  public InstanceController(final BuildProperties bau) {
    this.bau = bau;
  }

  /** Sagt der angemeldeten Oberflaeche, welcher Stand hier laeuft (K11). */
  @GetMapping
  public InstanceResponse instance() {
    return new InstanceResponse(Objects.requireNonNullElse(bau.getVersion(), OHNE_VERSION));
  }
}
