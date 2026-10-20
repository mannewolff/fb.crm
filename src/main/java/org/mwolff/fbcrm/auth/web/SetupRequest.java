package org.mwolff.fbcrm.auth.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Die Eingaben der Einrichtungsseite, wenn der Code der Einrichtung bestaetigt wird (E9).
 *
 * <p>Schluessel, Anzeigename und Adresse kommen ein zweites Mal mit, wie schon bei der Anforderung
 * ({@link SetupCodeRequest}): In der Datenbank entsteht zwischen den beiden Schritten kein
 * Zwischenzustand, und der Schluessel wird im Augenblick der Kontoanlage erneut geprueft.
 *
 * <p>Geprueft wird nur die Form, nie der Inhalt: Ob der Einmal-Schluessel stimmt, ob die Instanz
 * noch frei ist und ob der Code passt, beantwortet ausschliesslich {@code SetupAccountUseCase}.
 * Eine Wiederholung der Adresse gibt es nicht mehr (E15): Den Vertipper faengt der Code ab, der an
 * die vertippte Adresse geht.
 *
 * <p>Die Laengengrenzen sind dieselben wie bei {@link SetupCodeRequest} und {@link LoginRequest}.
 *
 * @param bootstrapToken der Einmal-Schluessel aus {@code FBCRM_BOOTSTRAP_ADMIN_TOKEN}
 * @param displayName der Anzeigename in der Oberflaeche
 * @param email die Anmeldeadresse des ersten Kontos, an die der Code ging
 * @param code der eingegebene Code
 */
public record SetupRequest(
    @NotBlank @Size(max = 512) String bootstrapToken,
    @NotBlank @Size(max = 200) String displayName,
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(max = 32) String code) {}
