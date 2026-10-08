package org.mwolff.fbcrm.instance.web;

/**
 * Der Versionsstand der laufenden Instanz (E10).
 *
 * <p><b>Genau ein Feld, und dabei bleibt es.</b> Die Antwort verlangt zwar eine Sitzung, aber sie
 * geht an jeden Angemeldeten und landet in der Schiene. Alles Weitere ueber den Bau — Zeitpunkt,
 * Commit, Java-Version — waere eine Auskunft, die die Oberflaeche nicht braucht und ein Angreifer
 * mit einer Sitzung gern haette. {@code InstanceControllerIT} zaehlt die Felder nach.
 *
 * @param version die Version des Maven-Laufs, der dieses Artefakt gebaut hat
 */
public record InstanceResponse(String version) {}
