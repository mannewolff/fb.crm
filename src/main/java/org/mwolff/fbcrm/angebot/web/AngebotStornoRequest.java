package org.mwolff.fbcrm.angebot.web;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Der Rumpf des Stornos (Issue #334, Plan #332 E4, E5).
 *
 * <p>Der Grund ist freiwillig: Ein fehlendes Feld, {@code null} und ein ganz fehlender Rumpf
 * heissen „ohne Grund". {@code @Size} misst wie beim Kommentar den <b>eingereichten</b> Text; die
 * Grenze steht ein zweites Mal in der Domaene und als CHECK {@code angebot_storno_grund_laenge},
 * gemessen am getrimmten Text.
 *
 * @param grund warum storniert wird, oder {@code null}
 */
public record AngebotStornoRequest(@Size(max = 2000) @Nullable String grund) {}
